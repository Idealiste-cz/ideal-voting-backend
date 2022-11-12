addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.1.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "0.9.0+24-55a887cd")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "0.75.1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.4",
)
