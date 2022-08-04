addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "0.8.0+12-c655e6fe")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "0.74.1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.3",
)
