
```scala
package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  val fastaS3:    S3Object = dropInconsistentAssignments.output.fasta.s3
  val id2taxasS3: S3Object = dropInconsistentAssignments.output.table.s3
  val blastDBS3:  S3Folder = dropInconsistentAssignmentsAndGenerate.s3
}

```




[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md
[main/scala/dropRedundantAssignments.scala]: dropRedundantAssignments.scala.md
[main/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/compats.scala]: compats.scala.md
[main/scala/release.scala]: release.scala.md
[main/scala/dropInconsistentAssignments.scala]: dropInconsistentAssignments.scala.md
[main/scala/pick16SCandidates.scala]: pick16SCandidates.scala.md