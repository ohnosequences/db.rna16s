name          := "db.rna16s"
organization  := "ohnosequences"
description   := "A comprehensive, compact, and automatically curated 16S database"
bucketSuffix  := "era7.com"

crossScalaVersions := Seq("2.11.11", "2.12.3")
scalaVersion := crossScalaVersions.value.max

resolvers += "Era7 private maven releases" at s3("private.releases.era7.com").toHttps(s3region.value.toString)

libraryDependencies ++= Seq(
  // We only need statika compile-dependency for the artifact metadata in the S3 data references
  "ohnosequences" %% "statika" % "3.0.0",
  "ohnosequences" %% "aws-scala-tools" % "0.20.0"
) ++ Seq( // Test:
  "org.scalatest" %% "scalatest"     % "3.0.4",
  "ohnosequences" %% "db-rnacentral" % "0.10.0",
  "ohnosequences" %% "mg7"           % "1.0.0-RC1-28-gea105a1",
  "era7bio"       %% "defaults"      % "0.3.0-RC3"
).map { _ % Test }

dependencyOverrides ++= Seq(
  // TODO: remove after updating bio4j-dist
  "ohnosequences" %% "aws-scala-tools" % "0.20.0",
  "ohnosequences" %% "loquat" % "2.0.0-RC4-26-g760c7a4"
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
// publishFatArtifact in Release := true
