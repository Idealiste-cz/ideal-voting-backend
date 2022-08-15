package cz.idealiste.idealvoting.server

import cats.implicits.*
import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.Db.Election
import cz.idealiste.idealvoting.server.Voting.*
import cz.idealiste.idealvoting.server.VotingLive.*
import org.apache.commons.lang3.StringUtils
import zio.*
import zio.config.*
import zio.config.magnolia.Descriptor
import zio.interop.catz.core.*

import java.time.OffsetDateTime

final case class VotingLive(
    config: Config,
    db: Db,
    votingSystem: VotingSystem,
    random: Random,
) extends Voting {

  // TODO: distinct type for Voter token and Admin token and represent internally as UUID
  private val generateToken: UIO[String] = random
    .nextIntBetween(97, 123)
    .replicateZIO(config.tokenLength)
    .map(_.map(_.toChar).mkString)

  private def mangleTitle(title: String): String = {
    StringUtils.stripAccents(title).replaceAll("[\\P{Alnum}]+", "-")
  }

  private def electionToView(election: Election, token: String): ElectionView = {
    val admin = AdminView(election.admin.email)
    val voter = election.voters.find(_.token === token).get
    ElectionView(election.metadata, admin, election.options, voter, election.result.map(ResultView(_, election.votes)))
  }

  private def electionToViewAdmin(election: Election): ElectionViewAdmin = {
    val voters = election.voters.map { case Voter(email, _, voted) => VoterView(email, voted) }
    ElectionViewAdmin(
      election.metadata,
      election.admin,
      election.options,
      voters,
      election.result.map(ResultView(_, election.votes)),
    )
  }

  def createElection(create: CreateElection, now: OffsetDateTime): Task[(String, String)] = {
    for {
      adminToken <- generateToken
      voters <- create.voters.traverse { email =>
        generateToken.map(Voter(email, _, voted = false))
      }
      titleMangled = mangleTitle(create.title)
      electionMetadata = ElectionMetadata(create.title, titleMangled, create.description, now)
      admin = Admin(create.admin, adminToken)
      options = create.options.zipWithIndex.map { case (CreateOption(title, description), id) =>
        BallotOption(id, title, description)
      }
      () <- ZIO.logDebug(s"Creating election $electionMetadata with admin $admin, voters $voters and options $options.")
      () <- db.createElection(electionMetadata, admin, options, voters)
      () <- ZIO.logInfo(s"Created election $titleMangled.")
    } yield (titleMangled, adminToken)
  }

  def viewElection(token: String): Task[Option[ElectionView]] = for {
    election <- db.readElection(token)
    electionView = election.map(electionToView(_, token))
  } yield electionView

  def viewElectionAdmin(token: String): Task[Option[ElectionViewAdmin]] = for {
    election <- db.readElectionAdmin(token)
    electionViewAdmin = election.map(electionToViewAdmin)
  } yield electionViewAdmin

  def castVote(token: String, preferences: List[Int]): Task[CastVoteResult] = {
    for {
      election <- db.readElection(token)
      result <- election match {
        case None =>
          ZIO.logInfo(s"Couldn't cast vote, because election for token $token not found.") >>
            ZIO.succeed(VoteInsertResult.TokenNotFound)
        case Some(election) =>
          Vote.make(preferences, election.optionsMap) match {
            case Left(invalidVote) =>
              ZIO.logInfo(
                s"Couldn't cast vote for election ${election.metadata.titleMangled} because of $invalidVote",
              ) >> ZIO.succeed(invalidVote)
            case Right(vote) =>
              db.castVote(token, vote)
                .flatTap(r => // TODO: logging for each case as in `endElection`
                  ZIO.logInfo(s"Casting vote for election ${election.metadata.titleMangled} with result $r."),
                )
          }
      }
    } yield result
  }

  def endElection(token: String, now: OffsetDateTime): Task[EndElectionResult] = for {
    election <- db.readElectionAdmin(token)
    result <- election match {
      case None =>
        ZIO.logInfo(s"Couldn't end election, because election for token $token not found.") >>
          ZIO.succeed(EndElectionResult.TokenNotFound)
      case Some(Election(_, _, _, _, _, _, Some(Result(ended, _)))) =>
        ZIO.logInfo(s"Couldn't end election, because election for token $token already ended on $ended.") >>
          ZIO.succeed(EndElectionResult.ElectionAlreadyEnded)
      case Some(election) =>
        val positions = votingSystem.computePositions(election.options.map(_.id), election.votes.map(_.preferences))
        db.endElection(token, positions, now).flatTap {
          case EndElectionResult.TokenNotFound =>
            ZIO.logInfo(s"Couldn't end election, because election for token $token not found (from DB).")
          case EndElectionResult.ElectionAlreadyEnded =>
            ZIO.logInfo(s"Couldn't end election, because election for token $token already ended (from DB).")
          case EndElectionResult.SuccessfullyEnded =>
            ZIO.logInfo(s"Successfully ended election for token $token with positions $positions.")
        }
    }
  } yield result
}

object VotingLive {

  private[server] val layer = ZLayer.fromFunction(apply _).map(_.prune[Voting])

  final case class Config(tokenLength: Int = 10)
  object Config {
    private[server] val layer = ZLayer.fromZIO(ZIO.service[server.Config].map(_.voting))
    implicit lazy val configDescriptor: ConfigDescriptor[Config] = Descriptor.descriptor[Config]
  }
}
