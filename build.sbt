addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

import sbtrelease.ReleaseStateTransformations._

name := "bergae"

organization := "com.machinomy"

version := "0.0.3-SNAPSHOT"

mainClass := Some("com.machinomy.bergae.Main")

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "Machinomy Release" at "http://artifactory.machinomy.com/artifactory/release",
  "Machinomy Snapshot" at "http://artifactory.machinomy.com/artifactory/snapshot"
)

libraryDependencies ++= Seq(
  "com.machinomy" %% "crdt" % "0.0.3",
  "com.machinomy" %% "xicity" % "0.0.6-SNAPSHOT",
  "com.github.scopt" %% "scopt" % "3.5.0",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.55",
  "com.typesafe" % "config" % "1.3.0",
  "com.tumblr" %% "colossus" % "0.8.1",
  "com.github.etaty" %% "rediscala" % "1.6.0"
)

val circeVersion = "0.5.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

releaseUseGlobalVersion := false

def whenRelease(releaseStep: ReleaseStep): ReleaseStep =
  releaseStep.copy(state => if (Project.extract(state).get(isSnapshot)) state else releaseStep.action(state))

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  runClean,
//  runTest,
//  whenRelease(tagRelease),
  publishArtifacts
//  whenRelease(pushChanges)
)

publishTo := {
  val base = "http://artifactory.machinomy.com/artifactory"
  if (isSnapshot.value) {
    val timestamp = new java.util.Date().getTime
    Some("Machinomy" at s"$base/snapshot;build.timestamp=$timestamp")
  } else {
    Some("Machinomy" at s"$base/release")
  }
}

credentials += Credentials(new File("credentials.properties"))
