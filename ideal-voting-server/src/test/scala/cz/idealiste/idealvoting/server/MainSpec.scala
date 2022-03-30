package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import cz.idealiste.idealvoting.server.HttpLive._
import emil.MailAddress
import emil.javamail.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import org.http4s.{Method, Request, Status, Uri}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.magic._
import zio.random.Random
import zio.system.System
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment.TestEnvironment

object MainSpec extends DefaultRunnableSpec {

  @SuppressWarnings(Array("DisableSyntax.throw"))
  private def email(string: String): MailAddress =
    MailAddress.parseValidated(string).fold(e => throw e.head, m => m)

  def spec: ZSpec[TestEnvironment, Failure] =
    suite("Service")(
      testM("/status should return OK") {
        val response =
          ZIO.serviceWith[Http](_.httpApp.run(Request(method = Method.GET, uri = uri"/v1/status")))
        assertM(response.map(_.status))(equalTo(Status.Ok))
      },
      testM("/election POST should create an election") {
        val request = CreateElectionRequest(
          "election 1",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", Some("Option 2"))),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val response = ZIO.serviceWith[Http] { http =>
          val httpApp = http.httpApp
          for {
            response <- httpApp.run(
              Request(method = Method.POST, uri = uri"/v1/election").withEntity(request),
            )
            response <- response.as[LinksResponse]
            response <- httpApp.run(
              Request(
                method = Method.GET,
                uri = Uri.unsafeFromString(
                  response.links
                    .find(l => l.method === Method.GET && l.rel === "election-view-admin")
                    .get
                    .href,
                ),
              ),
            )
            response <- response.as[GetElectionAdminResponse]
          } yield response
        }
        assertM(response)(
          hasField("title", (r: GetElectionAdminResponse) => r.title, equalTo("election 1")) &&
            hasField(
              "titleMangled",
              (r: GetElectionAdminResponse) => r.titleMangled,
              equalTo("election-1"),
            ) &&
            hasField(
              "description",
              (r: GetElectionAdminResponse) => r.description,
              equalTo(None: Option[String]),
            ) &&
            hasField(
              "admin.name",
              (r: GetElectionAdminResponse) => r.admin.name,
              equalTo(Option("Admin 1")),
            ) &&
            hasField(
              "admin.address",
              (r: GetElectionAdminResponse) => r.admin.address,
              equalTo("admin1@a.net"),
            ) &&
            hasField(
              "options",
              (r: GetElectionAdminResponse) => r.options,
              equalTo(
                List(GetOptionResponse(0, "option1", None), GetOptionResponse(1, "option2", Some("Option 2"))),
              ),
            ) &&
            hasField(
              "voters",
              (r: GetElectionAdminResponse) => r.voters.map(r => (r.voter.name, r.voter.address, r.voted)),
              equalTo(
                List(
                  (Some("Voter 1"), "voter1@x.com", false),
                  (None, "voter2@y.org", false),
                ),
              ),
            ),
        )
      },
      testM("/election/.../<token> POST should cast a vote") {
        val requestCreate = CreateElectionRequest(
          "election 2",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", None)),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val requestCast = CastVoteRequest(List(1, 0))
        val responseViewAdmin = ZIO.serviceWith[Http] { http =>
          val httpApp = http.httpApp
          for {
            responseCreate <- httpApp
              .run(
                Request(method = Method.POST, uri = uri"/v1/election").withEntity(requestCreate),
              )
              .flatMap(_.as[LinksResponse])
            _ <- httpApp
              .run(
                Request(
                  method = Method.POST,
                  uri = Uri.unsafeFromString("/v1/election/uri-mangled-xyz/tvehbtrszd"),
                ).withEntity(requestCast),
              )
              .flatMap(_.as[LinksResponse])
            responseViewAdmin <- httpApp
              .run(
                Request(
                  method = Method.GET,
                  uri = Uri.unsafeFromString(
                    responseCreate.links
                      .find(l => l.method === Method.GET && l.rel === "election-view-admin")
                      .get
                      .href,
                  ),
                ),
              )
              .flatMap(_.as[GetElectionAdminResponse])
          } yield responseViewAdmin
        }
        assertM(responseViewAdmin)(
          hasField(
            "voters",
            (r: GetElectionAdminResponse) => r.voters.map(r => (r.voter.name, r.voter.address, r.voted)),
            equalTo(
              List(
                (Some("Voter 1"), "voter1@x.com", true),
                (None, "voter2@y.org", false),
              ),
            ),
          ),
        )
      },
      testM("/election/admin/.../<token> POST should end the election") {
        val requestCreate = CreateElectionRequest(
          "election 3",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", None)),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val requestCast = CastVoteRequest(List(1, 0))
        val responseResult = ZIO.serviceWith[Http] { http =>
          val httpApp = http.httpApp
          for {
            responseCreate <- httpApp
              .run(Request(method = Method.POST, uri = uri"/v1/election").withEntity(requestCreate))
              .flatMap(_.as[LinksResponse])
            _ <- httpApp
              .run(
                Request(method = Method.POST, uri = Uri.unsafeFromString("/v1/election/uri-mangled-xyz/tkpfinzdlw"))
                  .withEntity(requestCast),
              )
              .flatMap(_.as[LinksResponse])
            _ <- httpApp
              .run(
                Request(
                  method = Method.POST,
                  uri = Uri.unsafeFromString(
                    s"/v1/election/admin/uri-mangled-xyz/${responseCreate.links(0).parameters("token")}",
                  ),
                ),
              )
              .flatMap(_.as[LinksResponse])
            responseViewAdmin <- httpApp
              .run(
                Request(
                  method = Method.GET,
                  uri = Uri.unsafeFromString(
                    responseCreate.links.find(l => l.method === Method.GET && l.rel === "election-view-admin").get.href,
                  ),
                ),
              )
              .flatMap(_.as[GetElectionAdminResponse])
          } yield responseViewAdmin.result.map(r => (r.positions, r.votes))
        }
        assertM(responseResult)(equalTo(Some((List(1, 0), List(List(1, 0))))))
      },
    ).provideSomeLayerShared[Blocking with Random with System](testLayer.orDie) @@ sequential

  lazy val testLayerConfig: RLayer[Blocking with System, Has[Config] with Logging] =
    ZLayer.fromSomeMagic[Blocking with System, Has[Config] with Logging with Has[DockerComposeContainer]](
      Slf4jLogger.make((_, s) => s),
      Config.layer(List()),
      TestContainer.dockerCompose,
    ) >+> TestContainer.layer

  lazy val testLayer: RLayer[Blocking with Random with System, Has[Http]] =
    ZLayer.fromSomeMagic[Blocking with Random with System, Has[Http]](
      Clock.live,
      testLayerConfig,
      Main.httpLayer,
    )

}
