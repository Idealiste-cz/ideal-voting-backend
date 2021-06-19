package cz.idealiste.idealvoting.server

import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe._
import zio.system.System
import zio.doobie.liquibase._

final case class Config(
    dbTransactor: ZIODoobieLiquibase.Config,
    httpServer: Config.HttpServer,
    voting: Config.Voting,
)

object Config {
  object DbTransactor {
    val layer: RLayer[Has[Config], Has[ZIODoobieLiquibase.Config]] =
      ZIO.service[Config].map(_.dbTransactor).toLayer
  }

  final case class HttpServer(host: String, port: Int)
  object HttpServer {
    val layer: RLayer[Has[Config], Has[HttpServer]] = ZIO.service[Config].map(_.httpServer).toLayer
    implicit lazy val configDescriptor: ConfigDescriptor[HttpServer] =
      DeriveConfigDescriptor.descriptor[HttpServer]
  }

  final case class Voting(tokenLength: Int = 10)
  object Voting {
    val layer: RLayer[Has[Config], Has[Voting]] = ZIO.service[Config].map(_.voting).toLayer
    implicit lazy val configDescriptor: ConfigDescriptor[Voting] = DeriveConfigDescriptor.descriptor[Voting]
  }

  implicit lazy val configDescriptor: ConfigDescriptor[Config] = DeriveConfigDescriptor.descriptor[Config]
  def layer(args: List[String]): URLayer[System, Has[Config]] = (
    for {
      typesafe <- TypesafeConfigSource.fromDefaultLoader
      env <- ConfigSource.fromSystemEnv
      cmd = ConfigSource.fromCommandLineArgs(args)
      source = cmd <> env <> typesafe
      config <- ZIO.fromEither(read(implicitly[ConfigDescriptor[Config]].from(source)))
    } yield config
  ).orDie.toLayer
}
