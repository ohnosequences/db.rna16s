resolvers := Seq(
  "Era7 private maven releases"  at s3("private.releases.era7.com").toHttps(s3region.value.toString),
  "Era7 private maven snapshots" at s3("private.snapshots.era7.com").toHttps(s3region.value.toString)
) ++ resolvers.value

libraryDependencies ++= Seq(
  "ohnosequences" %% "api-rnacentral"   % "0.2.0-14-g71466f5" ,
  "ohnosequences" %% "aws-scala-tools"  % "0.20.0",
  "ohnosequences" %% "fastarious"       % "0.12.0",
  "ohnosequences" %% "statika"          % "3.0.0"
) ++ testDependencies

val testDependencies = Seq(
  "org.scalatest" %% "scalatest"      % "3.0.4",
  "ohnosequences" %% "db-rnacentral"  % "0.11.1"
)
.map(_ % Test)

dependencyOverrides ++= Seq(
  // "org.apache.httpcomponents" % "httpclient" % "4.5.1",
  // "org.slf4j"                 % "slf4j-api"  % "1.7.7",
  // TODO: remove after updating bio4j-dist
  "ohnosequences" %% "aws-scala-tools" % "0.20.0",
  "ohnosequences" %% "loquat" % "2.0.0-RC4-26-g760c7a4"
)