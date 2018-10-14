name := "yojik"

organization := "thomasylee"

version := "0.0.1"

scalaVersion := "2.12.5"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "utf8",
  "-feature",
  "-unchecked",
  "-Ywarn-unused",
  "-Ywarn-unused-import"
)

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= {
  val akkaV = "2.5.12"

  Seq(
    "com.github.marianobarrios" % "tls-channel" % "0.1.0",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV,
    "com.typesafe" % "config" % "1.3.3",
    "org.scala-lang.modules" %% "scala-xml" % "1.1.0",
    "org.scalamock" %% "scalamock" % "4.1.0" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
}

cancelable in Global := true

mainClass in (Compile, run) := Some("xyz.thomaslee.yojik.Main")
