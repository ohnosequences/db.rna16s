name          := "db.rna16s"
organization  := "ohnosequences"
description   := "A comprehensive, compact, and automatically curated 16S database"

scalaVersion := "2.11.8"

resolvers := Seq(
  "Era7 private maven releases"  at s3("private.releases.era7.com").toHttps(s3region.value.toString),
  "Era7 private maven snapshots" at s3("private.snapshots.era7.com").toHttps(s3region.value.toString)
) ++ resolvers.value

bucketSuffix  := "era7.com"

libraryDependencies ++= Seq(
  "ohnosequences" %% "statika"   % "2.0.0",
  "ohnosequences" %% "blast-api" % "0.8.0",
  // Test:
  "ohnosequences" %% "fastarious"    % "0.8.0"                      % Test,
  "ohnosequences" %% "ncbitaxonomy"  % "0.2.0"                      % Test,
  "ohnosequences" %% "db-rnacentral" % "0.8.0"                      % Test,
  "era7bio"       %% "defaults"      % "0.3.0-RC2"                  % Test,
  "ohnosequences" %% "mg7"           % "1.0.0-M5-pr78-143-g50f6e1e" % Test
)

dependencyOverrides ++= Set(
  "org.apache.httpcomponents" % "httpclient" % "4.5.1",
  "org.slf4j"                 % "slf4j-api"  % "1.7.7"
)

generateStatikaMetadataIn(Compile)

// NOTE should be reestablished
wartremoverErrors in (Test, compile) := Seq()

assemblyMergeStrategy in assembly ~= { old => {
    case "log4j.properties"                       => MergeStrategy.filterDistinctLines
    case PathList("org", "apache", "commons", _*) => MergeStrategy.first
    case x                                        => old(x)
  }
}

// This includes tests sources in the assembled fat-jar:
fullClasspath in assembly := (fullClasspath in Test).value

// This turns on fat-jar publishing during release process:
publishFatArtifact in Release := true
