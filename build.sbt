import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbtghpackages.GitHubPackagesPlugin.autoImport.githubRepository

lazy val sharedSettings = Seq(
  organization := "com.freelance-stats",
  scalaVersion := "2.13.6",
  scalafmtOnCompile := true
)

lazy val githubPackagesConfig = Seq(
  githubOwner := "bijelic99",
  githubRepository := "freelance-stats-s3-client",
  githubTokenSource := TokenSource.GitConfig("github.token")
)

val AkkaVersion = "2.6.14"

lazy val root =
  (project in file("."))
    .settings(
      Seq(
        publishArtifact := false
      ) ++ sharedSettings ++ githubPackagesConfig: _*
    )
    .aggregate(s3Client, amazonAsyncS3Client)

lazy val s3Client =
  (project in file("modules/s3-client"))
    .settings(
      Seq(
        name := "s3-client",
        libraryDependencies ++= Seq(
          "joda-time" % "joda-time" % "2.10.13",
          "com.typesafe.akka" %% "akka-stream" % AkkaVersion
        )
      ) ++ sharedSettings ++ githubPackagesConfig: _*
    )

lazy val amazonAsyncS3Client =
  (project in file("modules/amazon-async-s3-client"))
    .settings(
      Seq(
        name := "amazon-async-s3-client",
        libraryDependencies ++= Seq(
          "software.amazon.awssdk" % "s3" % "2.17.81",
          "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
        )
      ) ++ sharedSettings ++ githubPackagesConfig: _*
    )
    .dependsOn(s3Client)