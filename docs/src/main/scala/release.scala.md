
```scala
package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  // TODO these should have a value!
  val fastaS3:    S3Object = ??? // dropInconsistentAssignments.output.fasta.s3
  val id2taxasS3: S3Object = ??? // dropInconsistentAssignments.output.table.s3
  val blastDBS3:  S3Folder = ??? // dropInconsistentAssignmentsAndGenerate.s3
}

```




[test/scala/dropRedundantAssignments.scala]: ../../test/scala/dropRedundantAssignments.scala.md
[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md
[test/scala/mg7pipeline.scala]: ../../test/scala/mg7pipeline.scala.md
[test/scala/compats.scala]: ../../test/scala/compats.scala.md
[test/scala/dropInconsistentAssignments.scala]: ../../test/scala/dropInconsistentAssignments.scala.md
[test/scala/pick16SCandidates.scala]: ../../test/scala/pick16SCandidates.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/release.scala]: release.scala.md