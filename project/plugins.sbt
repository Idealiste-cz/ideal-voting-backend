addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.3")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "1.1.3")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "1.0.0-M1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.10",
  "dev.guardrail" %% "guardrail-scala-http4s" % "1.0.0-M1",
)
addSbtPlugin("com.github.sideeffffect" % "sbt-github-actions-logger" % "0.1.1")
