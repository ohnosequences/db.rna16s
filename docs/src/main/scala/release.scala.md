
```scala
package era7bio.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  val fastaS3:    S3Object = dropInconsistentAssignments.output.fasta.s3
  val id2taxasS3: S3Object = dropInconsistentAssignments.output.table.s3
  val blastDBS3:  S3Folder = dropInconsistentAssignmentsAndGenerate.s3
}

```




[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md
[main/scala/filter2.scala]: filter2.scala.md
[main/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/compats.scala]: compats.scala.md
[main/scala/filter1.scala]: filter1.scala.md
[main/scala/filter3.scala]: filter3.scala.md
[main/scala/release.scala]: release.scala.md