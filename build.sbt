Nice.scalaProject

name          := "db-rna16s"
organization  := "era7bio"
description   := "db-rna16s project"

// the repo name differs on github:
GithubRelease.repo := s"${organization.value}/db.rna16S"

scalaVersion := "2.11.8"

resolvers := Seq(
  "Era7 private maven releases"  at s3("private.releases.era7.com").toHttps(s3region.value.toString),
  "Era7 private maven snapshots" at s3("private.snapshots.era7.com").toHttps(s3region.value.toString)
) ++ resolvers.value

bucketSuffix  := "era7.com"

libraryDependencies ++= Seq(
  "ohnosequences" %% "fastarious"      % "0.6.0",
  "ohnosequences" %% "blast-api"       % "0.7.0",
  "ohnosequences" %% "statika"         % "2.0.0-M5",
  "era7bio"       %% "rnacentraldb"    % "0.2.1",
  "ohnosequences-bundles" %% "bio4j-dist" % "0.2.0",
  // Test:
  "era7"          %% "defaults"  % "0.1.0" % Test,
  "org.scalatest" %% "scalatest" % "2.2.6" % Test
)


dependencyOverrides ++= Set(
  "commons-logging"            % "commons-logging"     % "1.1.3",
  "commons-codec"              % "commons-codec"       % "1.7",
  "org.apache.httpcomponents"  % "httpclient"          % "4.5.1",
  "org.slf4j"                  % "slf4j-api"           % "1.7.7"
)


fatArtifactSettings

enablePlugins(BuildInfoPlugin)
buildInfoPackage := "generated.metadata"
buildInfoObject  := name.value.split("""\W""").map(_.capitalize).mkString
buildInfoOptions := Seq(BuildInfoOption.Traits("ohnosequences.statika.AnyArtifactMetadata"))
buildInfoKeys    := Seq[BuildInfoKey](
  organization,
  version,
  "artifact" -> name.value.toLowerCase,
  "artifactUrl" -> fatArtifactUrl.value
)

// For including test code in the fat artifact:
unmanagedSourceDirectories in Compile += (scalaSource in Test).value / "compats.scala"
