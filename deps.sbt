libraryDependencies ++= mainDependencies ++ testDependencies

val mainDependencies = Seq(
  "ohnosequences" %% "db-rnacentral" % "0.12.2-35-g7529225",
  "ohnosequences" %% "s3"            % "0.2.1",
  "ohnosequences" %% "files"         % "0.5.0"
)

val testDependencies = Seq(
  "ohnosequences" %% "fastarious" % "0.12.0",
  "org.scalatest" %% "scalatest"  % "3.0.5"
).map(_ % Test)
