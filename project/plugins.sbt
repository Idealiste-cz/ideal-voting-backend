addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.6.1")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "1.0.27-1-d4333dfd")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "1.0.0-M1")
addSbtPlugin("com.gradle" % "sbt-develocity" % "1.0.1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.7",
  "dev.guardrail" %% "guardrail-scala-http4s" % "1.0.0-M1",
)
