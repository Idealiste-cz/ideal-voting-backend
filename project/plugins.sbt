addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "0.8.0+10-7e2239d2")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "0.73.0")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.3",
)
