name := """drt-passenger-splits"""

organization := """uk.gov.homeoffice.borderforce"""

version := "1.0"

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io",
  "spray nightlies" at "http://nightlies.spray.io",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  Resolver.bintrayRepo("mfglabs", "maven"),
  Resolver.bintrayRepo("dwhjames", "maven")
)

val sprayVersion: String = "1.3.3"


libraryDependencies ++= Seq(
  "com.github.seratch" %% "awscala" % "0.5.+",
  "com.mfglabs" %% "commons-aws" % "0.9.0",
  "com.typesafe.akka" %% "akka-actor" % "2.4.6",
  "com.typesafe.akka" %% "akka-camel" % "2.4.6",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.6",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "io.spray" %% "spray-can" % sprayVersion,
  "io.spray" %% "spray-client" % sprayVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-json" % "1.3.2",
  "org.specs2" %% "specs2-core" % "3.8.4" % "test",
  "org.specs2" %% "specs2-scalacheck" % "3.8.4",
  //https://groups.google.com/forum/#!topic/spray-user/2T6SBp4OJeI for this exclusion
  "io.spray" %% "spray-testkit" % sprayVersion % "test" exclude("org.specs2", "specs2_2.11"),
  "com.typesafe.akka" %% "akka-testkit" % "2.4.6" % "test",
  "com.typesafe" % "config" % "1.3.0",
  "com.novocode" % "junit-interface" % "0.7" % "test->default"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

scalacOptions in Test ++= Seq("-Yrangepos")

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
