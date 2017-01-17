
```scala
package ohnosequences.db

import ohnosequences.awstools.s3._
```


## 16S RNA BLAST database

This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.


```scala
package object rna16s {

  val dbName = "ohnosequences.db.rna16s"

  private val metadata = generated.metadata.rna16s

  val s3prefix: S3Folder =
    s3"resources.ohnosequences.com" /
    metadata.organization /
    metadata.artifact /
    metadata.version /
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