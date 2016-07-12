scalaVersion := "2.11.8" 

lazy val http4sVersion = "0.15.0-SNAPSHOT"

resolvers += Resolver.sonatypeRepo("snapshots")
 
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.log4s" %% "log4s" % "1.3.0",
  "org.slf4j" % "slf4j-simple" % "1.7.21"
)
