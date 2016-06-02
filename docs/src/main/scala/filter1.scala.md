
```scala
package era7bio.db.rna16s

import era7bio.db._, csvUtils._, collectionUtils._
import era7bio.db.rnacentral._, RNACentral5._
import bio4jTaxonomyBundle._

import ohnosequences.fastarious.fasta._
import ohnosequences.statika._

import com.github.tototoshi.csv._
import better.files._


case object filter1 extends FilterData(
  sourceTableS3 = RNACentral5.table,
  sourceFastaS3 = RNACentral5.fasta,
  outputS3Prefix = era7bio.db.rna16s.s3prefix / "filter1" /
)(
  deps = bio4jTaxonomyBundle
) {
```

We are using the ribosomal RNA type annotation on RNACentral as a first catch-all filter. We are aware of the existence of a gene annotation corresponding to 16S, that we are **not using** due to a significant amount of 16S sequences lacking the corresponding annotation.

```scala
  val ribosomalRNAType = "rRNA"
```

The sequence length threshold for a sequence to be admitted as 16S.

```scala
  val minimum16SLength: Int = 1300
```

Taxa IDs for archaea and bacteria

```scala
  val archaeaTaxonID  = 2157
  val bacteriaTaxonID = 2
```

These are NCBI taxonomy IDs corresponding to taxa which are at best uniformative. The `String` value is the name of the corresponding taxon, for documentation purposes.

```scala
  val uninformativeTaxIDsMap = Map(
    32644   -> "unclassified",
    2323    -> "unclassified Bacteria",
    4992    -> "unclassified Bacteria (miscellaneous)", // LOL
    118884  -> "unclassified Gammaproteobacteria",
    358574  -> "uncultured microorganism",
    155900  -> "uncultured organism",
    415540  -> "uncultured marine microorganism",
    198431  -> "uncultured prokaryote",
    77133   -> "uncultured bacterium",
    115547  -> "uncultured archaeon",
    56763   -> "uncultured marine archaeon",
    152507  -> "uncultured actinobacterium",
    1211    -> "uncultured cyanobacterium",
    153809  -> "uncultured proteobacterium",
    91750   -> "uncultured alpha proteobacterium",
    86027   -> "uncultured beta proteobacterium",
    86473   -> "uncultured gamma proteobacterium",
    34034   -> "uncultured delta proteobacterium",
    56765   -> "uncultured marine bacterium",
    115414  -> "uncultured marine alpha proteobacterium"
  )

  val uninformativeTaxIDs: Set[String] = uninformativeTaxIDsMap.keys.map(_.toString).toSet
```

and here we have RNACentral entries which we think are poorly assigned> This is list is by no means exhaustive, though its value can hardly be understimated.

```scala
  val blacklistedRNACentralIDs = Set(
    "URS00008CD63B",  // claims to be Lactobacilus plantarum, it is an Enterococcus
    "URS00008CCF2E",  // claims to be Candidatus Hepatobacter penaei, it is a Pseudomonas
    "URS00007EE21F",  // claims to be Pseudomonas sp. NT 6-08, it is a Staph aureus
    "URS00008C61AD",  // claims to be Yersinia pestis biovar Orientalis str. AS200901509, it is a Staph aureus
    "URS00008E71FD",  // claims to be Staphylococcus sciuri, it is a Pseudomonas
    "URS000089CEEE",  // claims to be Bacillus sp. W4(2008), it is a Pseudomonas
    "URS0000974DB8",  // claims to be Pseudomonas sp. CL3.1, it is a Bacillus
    "URS00008E9E3B",  // claims to be Pantoea sp. CR30, it is a Bacillus
    "URS00008DEF63",  // claims to be Microbacterium oxydans, it is a (fragment of) Bacillus
    "URS000082C8CF",  // claims to be Streptococcus pneumoniae, it is a Bacillus plus some chimeric sequence
    "URS0000874571",  // claims to be Bordetella, it is a Pseudomonas aeruginosa
    "URS00008A3994",  // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
    "URS00008898AD",  // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
    "URS0000215B45",  // claims to be Vibrio cholerae HC-02A1, it is an Enterococcus faecalis
    "URS00008239BE",  // claims to be Mycobacterium abscessus, it is an Acinetobacter
    "URS000074A9F2",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS0000735DC4",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS00005BB216",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS0000590E49",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS0000865688",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS000085F838",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS000074A9F2"   // claims to be Prolinoborus fasciculus, it is an Acinetobacter
  )
```


#### Database defining predicate

Sequences that satisfy this predicate (on themselves together with their annotation) are included in this database.


```scala
  def predicate(row: Row): Boolean = {
```

- are annotated as rRNA

```scala
     row.select(rna_type).contains(ribosomalRNAType)                                                    &&
```

- their taxonomy association is *not* one of those in `uninformativeTaxIDs`

```scala
    ( ! uninformativeTaxIDs.contains(row.select(tax_id)) )                                              &&
```

- is a descendant of either Archaea or Bacteria

```scala
    row.select(tax_id).isDescendantOfOneIn( Set( archaeaTaxonID.toString, bacteriaTaxonID.toString ) )  &&
```

- is not a descendant of an "environmental samples" or unclassified taxon

```scala
    ( ! row.select(tax_id).hasEnvironmentalSamplesAncestor )                                            &&
    ( ! row.select(tax_id).isDescendantOfUnclassifiedBacteria )                                         &&
    ( ! row.select(tax_id).hasDescendantOrItselfUnclassified )
  }

  // bundle to generate the DB (see the runBundles file in tests)
  def filterData(): Unit = {

    val groupedRows: Stream[(String, Stream[Row])] = source.table.reader.iterator.toStream.group{ _.select(id) }

    (groupedRows zip source.fasta.stream) foreach { case ((commonID, rows), fasta) =>

      if (commonID != fasta.getV(header).id)
        sys.error(s"ID [${commonID}] is not found in the FASTA. Check RNACentral filtering.")

      val (acceptedRows, rejectedRows) =
        if (
```

the common ID is blacklisted

```scala
            (blacklistedRNACentralIDs contains commonID) ||
```

or the corresponding sequence is shorter than the threshold

```scala
            (fasta.getV(sequence).value.length < minimum16SLength)
        ) {
```

then all rows are rejected

```scala
          (Seq[Row](), rows)
        } else {
```

otherwise they are partitioned according to the predicate

```scala
          rows.partition(predicate)
        }


      if (rejectedRows.nonEmpty) {
        rejectedRows.foreach( rejected.table.writer.writeRow )
        rejected.fasta.file.appendLine( fasta.asString )
      }

      if (acceptedRows.nonEmpty) {
        val extendedID: String = s"${commonID}|lcl|${era7bio.db.rna16s.dbName}"

        accepted.table.writer.writeRow(Seq(
          extendedID,
          acceptedRows.map{ _.select(tax_id) }.distinct.mkString(";")
        ))

        accepted.fasta.file.appendLine(
          fasta.update(
            header := FastaHeader(Seq(extendedID, fasta.getV(header).description).mkString(" ") )
          ).asString
        )
      }
    }
  }

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