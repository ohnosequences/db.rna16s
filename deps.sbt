libraryDependencies ++= mainDependencies ++ testDependencies

val mainDependencies = Seq(
  "ohnosequences" %% "db-rnacentral" % "0.13.1",
  "ohnosequences" %% "faster"        % "0.2.0"
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5"
).map(_ % Test)
