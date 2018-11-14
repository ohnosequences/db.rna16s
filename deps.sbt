libraryDependencies ++= mainDependencies ++ testDependencies

val mainDependencies = Seq(
  "ohnosequences" %% "db-rnacentral" % "0.13.1"
)

val testDependencies = Seq(
  "ohnosequences" %% "fastarious" % "0.12.0",
  "org.scalatest" %% "scalatest"  % "3.0.5"
).map(_ % Test)
