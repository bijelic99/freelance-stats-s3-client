import sbtghpackages.GitHubPackagesPlugin.autoImport.githubRepository

organization := "com.freelance-stats"

scalaVersion := "2.13.6"

lazy val githubPackagesConfig = Seq[SettingsDefinition](
  githubOwner := "bijelic99",
  githubRepository := "freelance-stats-s3-client",
  githubTokenSource := TokenSource.GitConfig("github.token")
)

val AkkaVersion = "2.6.14"

lazy val root =
  (project in file("."))
    .settings(githubPackagesConfig: _*)
    .aggregate(s3Client)

lazy val s3Client =
  (project in file("modules/s3-client"))
    .settings(
      Seq(
        name := "s3-client",
        scalafmtOnCompile := true,
        libraryDependencies ++= Seq(
          "joda-time" % "joda-time" % "2.10.13",
          "com.typesafe.akka" %% "akka-stream" % AkkaVersion
        )
      ) ++ githubPackagesConfig: _*
    )