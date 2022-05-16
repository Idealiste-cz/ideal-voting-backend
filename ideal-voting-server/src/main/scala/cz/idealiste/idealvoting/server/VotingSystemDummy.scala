package cz.idealiste.idealvoting.server

import zio._

final case class VotingSystemDummy() extends VotingSystem {
  def computePositions(options: List[Int], votes: List[List[Int]]): List[Int] = options.reverse
}

object VotingSystemDummy {

  private[server] val layer = (apply _).toLayer[VotingSystem]
}
