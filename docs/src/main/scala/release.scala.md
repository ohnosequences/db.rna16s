
```scala
package era7bio.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  val id2taxasS3: S3Object = filter2.accepted.table.s3
  val blastDBS3:  S3Folder = generate.outputS3Prefix
}

```




[main/scala/bio4jTaxonomy.scala]: bio4jTaxonomy.scala.md
[main/scala/compats.scala]: compats.scala.md
[main/scala/filter1.scala]: filter1.scala.md
[main/scala/filter2.scala]: filter2.scala.md
[main/scala/generateBlastDB.scala]: generateBlastDB.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/release.scala]: release.scala.md
[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md