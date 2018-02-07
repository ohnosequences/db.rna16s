resolvers := Seq(
  "Era7 private maven releases" at s3("private.releases.era7.com").toHttps(
    s3region.value.toString),
  "Era7 private maven snapshots" at s3("private.snapshots.era7.com").toHttps(
    s3region.value.toString)
) ++ resolvers.value

libraryDependencies ++= Seq(
  "ohnosequences" %% "api-rnacentral"  % "0.2.0-14-g71466f5",
  "ohnosequences" %% "aws-scala-tools" % "0.20.0",
  "ohnosequences" %% "fastarious"      % "0.12.0",
) ++ testDependencies

val testDependencies = Seq(
  "org.scalatest" %% "scalatest"     % "3.0.4",
  "ohnosequences" %% "db-rnacentral" % "0.11.1"
).map(_ % Test)
