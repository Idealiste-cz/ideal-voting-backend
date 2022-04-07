addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "0.7.0+98-4f745947")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "0.70.0")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.3",
)
