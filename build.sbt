name := "FlashFry"

version := "1.2"

scalaVersion := "2.12.1"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

// set to true if you want to try and build for a GPU with aparapi

unmanagedBase <<= baseDirectory { base => base / "project" }

libraryDependencies += "com.github.samtools" % "htsjdk" % "2.8.1"

libraryDependencies += "org.scalatest" % "scalatest_2.12" % "3.2.0-SNAP4" % "test"

libraryDependencies += "com.typesafe.scala-logging" % "scala-logging_2.12" % "3.5.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.12" % "2.4.14"

// libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"

//  libraryDependencies += "com.aparapi" % "aparapi" % "1.3.4"
//  javacOptions += "-g"
//  javaOptions += "–Dcom.amd.aparapi.enableExecutionModeReporting=true"

// set the main class for the main 'run' task

// save some time in non-testing builds by killing asserts
//
// scalacOptions ++= Seq("-Xelide-below", sys.props.getOrElse("elide.below", "2500"))

mainClass in (Compile, packageBin) := Some("main.scala.Main")

mainClass in (Compile, run) := Some("main.scala.Main")


