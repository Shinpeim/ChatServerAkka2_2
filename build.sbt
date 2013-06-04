name := "ChatServerAkka2_2"

version := "0.1"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
"com.typesafe.akka" %% "akka-actor" % "2.2.0-RC1",
"org.apache.logging.log4j" %  "log4j-api"  % "2.0-beta6",
"org.apache.logging.log4j" %  "log4j-core" % "2.0-beta6"
)

resolvers ++= Seq(
"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)
