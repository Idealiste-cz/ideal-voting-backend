package cz.idealiste.idealvoting.server

import cats.implicits._
import cz.idealiste.idealvoting.server.Config
import monocle.syntax.all._
import org.testcontainers.containers.ComposeContainer
import pprint.PPrinter.BlackWhite
import zio._
import zio.testcontainers._

import java.io.File

object TestContainer {

  private[server] lazy val dockerCompose = ZLayer.fromTestContainer {
    new ComposeContainer(new File("docker-compose.yml"))
      .withExposedService("mariadb", 3306)
      .withExposedService("mailhog", 1025)
      .withExposedService("mailhog", 8025)
      .withLocalCompose(false)
  }

  private[server] lazy val layer = ZLayer.fromZIO {
    for {
      docker <- ZIO.service[ComposeContainer]
      host = docker.getServiceHost("mariadb", 3306)
      port = docker.getServicePort("mariadb", 3306).toInt
      config0 <- ZIO.service[Config]
      config = config0.focus(_.dbTransactor.hikari.jdbcUrl).replace(show"jdbc:mariadb://$host:$port/idealvoting")
      _ <- ZIO.logInfo(s"Modified test configuration:\n${BlackWhite(config)}.")
    } yield config
  }

}
