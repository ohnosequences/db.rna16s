libraryDependencies ++= mainDependencies ++ testDependencies

val mainDependencies = Seq(
  "ohnosequences" %% "db-rnacentral" % "0.12.2-34-g9b872b0",
  "ohnosequences" %% "s3"            % "0.2.1"
)

val testDependencies = Seq(
  "ohnosequences" %% "fastarious" % "0.12.0",
  "org.scalatest" %% "scalatest"  % "3.0.5"
).map(_ % Test)
