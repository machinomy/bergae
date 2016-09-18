addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

name := "bergae"

organization := "com.machinomy"

version := "0.0.1-SNAPSHOT"

mainClass := Some("com.machinomy.bergae.Main")

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "Machinomy Release" at "http://artifactory.machinomy.com/artifactory/release",
  "Machinomy Snapshot" at "http://artifactory.machinomy.com/artifactory/snapshot"
)

libraryDependencies ++= Seq(
  "com.machinomy" %% "crdt" % "0.0.3",
  "com.machinomy" %% "xicity" % "0.0.5",
  "com.github.scopt" %% "scopt" % "3.5.0",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.55",
  "com.typesafe" % "config" % "1.3.0",
  "net.debasishg" %% "redisclient" % "3.2",
  "com.tumblr" %% "colossus" % "0.8.1"
)

val circeVersion = "0.5.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)