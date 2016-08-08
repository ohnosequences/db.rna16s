
```scala
package ohnosequences.db

import ohnosequences.blast.api.BlastDBType
import ohnosequences.awstools.s3._
```


## 16S RNA BLAST database

This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.


```scala
package object rna16s {

  val dbName = "ohnosequences.db.rna16s"
  val dbType = BlastDBType.nucl

  val s3prefix: S3Folder = S3Folder("resources.ohnosequences.com", generated.metadata.db.rna16s.organization) /
    generated.metadata.db.rna16s.artifact /
    generated.metadata.db.rna16s.version.stripSuffix("-SNAPSHOT") /
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