name          := "db.rna16s"
organization  := "ohnosequences"
description   := "A 16S reference database"

scalaVersion := "2.11.8"

resolvers := Seq(
  "Era7 private maven releases"  at s3("private.releases.era7.com").toHttps(s3region.value.toString),
  "Era7 private maven snapshots" at s3("private.snapshots.era7.com").toHttps(s3region.value.toString)
) ++ resolvers.value

bucketSuffix  := "era7.com"

libraryDependencies ++= Seq(
  "era7bio"               %% "db-rnacentral"  % "0.6.0",
  "ohnosequences"         %% "fastarious"     % "0.6.0",
  "ohnosequences"         %% "blast-api"      % "0.7.0",
  "ohnosequences"         %% "statika"        % "2.0.0-M5",
  "ohnosequences"         %% "ncbitaxonomy"   % "0.1.0",
  "ohnosequences-bundles" %% "bio4j-dist"     % "0.2.0",
  // Test:
  "era7bio"       %% "defaults"  % "0.2.0"                      % Test,
  "ohnosequences" %% "mg7"       % "1.0.0-M5-pr78-64-gd886636"  % Test,
  "org.scalatest" %% "scalatest" % "2.2.6"                      % Test
)

dependencyOverrides ++= Set(
  "org.apache.httpcomponents" % "httpclient" % "4.5.1",
  "org.slf4j"                 % "slf4j-api"  % "1.7.7"
)

// NOTE should be reestablished
wartremoverErrors in (Test, compile) := Seq()

addFatArtifactPublishing(Test)

mergeStrategy in assembly ~= { old => {
    case "log4j.properties"                       => MergeStrategy.filterDistinctLines
    case PathList("org", "apache", "commons", _*) => MergeStrategy.first
    case x                                        => old(x)
  }
}

enablePlugins(BuildInfoPlugin)
buildInfoPackage := "generated.metadata.db"
buildInfoObject  := "rna16s"
buildInfoOptions := Seq(BuildInfoOption.Traits("ohnosequences.statika.AnyArtifactMetadata"))
buildInfoKeys    := Seq[BuildInfoKey](
  organization,
  version,
  "artifact" -> name.value.toLowerCase,
  "artifactUrl" -> fatArtifactUrl.value
)
