Nice.scalaProject

name          := "db.rna16s"
organization  := "era7bio"
description   := "db.rna16s project"

bucketSuffix  := "era7.com"

libraryDependencies ++= Seq(
  "ohnosequences" %% "fastarious"      % "0.5.1",
  "ohnosequences" %% "blast-api"       % "0.6.2",
  "ohnosequences" %% "statika"         % "2.0.0-M5",
  "ohnosequences" %% "aws-scala-tools" % "0.16.0",

  "era7"          %% "defaults"   % "0.1.0",

  "ohnosequences-bundles" %% "blast" % "0.3.0",

  "com.github.tototoshi" %% "scala-csv" % "1.2.2",

  "org.scalatest" %% "scalatest" % "2.2.6" % Test
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
