
```scala
package era7bio.db.rna16s


case object generate extends era7bio.db.GenerateBlastDB(
  dbType = era7bio.db.rna16s.dbType,
  dbName = era7bio.db.rna16s.dbName,
  sourceFastaS3 = filter2.accepted.fasta.s3,
  outputS3Prefix = era7bio.db.rna16s.s3prefix / "blastdb" /
)()

```




[main/scala/bio4jTaxonomy.scala]: bio4jTaxonomy.scala.md
[main/scala/compats.scala]: compats.scala.md
[main/scala/filter1.scala]: filter1.scala.md
[main/scala/filter2.scala]: filter2.scala.md
[main/scala/generateBlastDB.scala]: generateBlastDB.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/release.scala]: release.scala.md
[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md