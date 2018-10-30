libraryDependencies ++= mainDependencies ++ testDependencies

val mainDependencies = Seq(
  "ohnosequences" %% "aws-scala-tools" % "0.20.0"
)

val testDependencies = Seq(
  "ohnosequences" %% "db-rnacentral" % "0.12.2",
  "ohnosequences" %% "fastarious"    % "0.12.0",
  "org.scalatest" %% "scalatest"     % "3.0.4"
).map(_ % Test)
