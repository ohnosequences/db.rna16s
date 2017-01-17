
```scala
package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

case object data {

  lazy val fastaS3:    S3Object = s3prefix/ "release" / s"${dbName}.fasta"
  lazy val id2taxasS3: S3Object = s3prefix/ "release" / s"${dbName}.csv"
  lazy val blastDBS3:  S3Folder = s3prefix/ "release" / "blastdb" /
}

```




[main/scala/data.scala]: data.scala.md
[main/scala/package.scala]: package.scala.md
[test/scala/clusterSequences.scala]: ../../test/scala/clusterSequences.scala.md
[test/scala/compats.scala]: ../../test/scala/compats.scala.md
[test/scala/dropInconsistentAssignments.scala]: ../../test/scala/dropInconsistentAssignments.scala.md
[test/scala/dropRedundantAssignments.scala]: ../../test/scala/dropRedundantAssignments.scala.md
[test/scala/mg7pipeline.scala]: ../../test/scala/mg7pipeline.scala.md
[test/scala/package.scala]: ../../test/scala/package.scala.md
[test/scala/pick16SCandidates.scala]: ../../test/scala/pick16SCandidates.scala.md
[test/scala/releaseData.scala]: ../../test/scala/releaseData.scala.md
[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md