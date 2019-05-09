enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

name := "state-monad"
version := "0.1"

scalaVersion := "2.12.7"

scalacOptions ++= Seq(
  "-encoding", "UTF-8",   // source files are in UTF-8
  "-deprecation",         // warn about use of deprecated APIs
  "-unchecked",           // warn about unchecked type parameters
  "-feature",             // warn about misused language features
  "-language:higherKinds",// allow higher kinded types without `import scala.language.higherKinds`
  "-Xlint",               // enable handy linter warnings
  "-Xfatal-warnings",     // turn compiler warnings into errors
  "-Ypartial-unification" // allow the compiler to unify type constructors of different arities
)

libraryDependencies += "org.typelevel" %% "cats-core" % "1.4.0"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.19",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.19",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.19" % Test,
  "com.typesafe.akka" %% "akka-http" % "10.1.8",
  "com.typesafe.akka" %% "akka-stream" % "2.5.19",
//  "de.heikoseeberger" %% "akka-http-circe" % "1.25.2",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.8"
//  "io.circe" %% "circe-core" % "0.11.1",
//  "io.circe" %% "circe-generic" % "0.11.1",
//  "io.circe" %% "circe-parser" % "0.11.1"
)

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.94"
libraryDependencies += "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.94" % Test
libraryDependencies += "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.1"

libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"
libraryDependencies += "org.iq80.leveldb" % "leveldb" % "0.11"
libraryDependencies += "com.google.guava" % "guava" % "27.0.1-jre"

//Memory persistence for testing
resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1" % "test"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.5" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test


addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

grollHistoryRef := "lambdadays_2019"

mainClass := Some("vending.http.Server")
dockerEntrypoint := Seq("/opt/docker/bin/server")
dockerExposedPorts := Seq(9000, 9443)

