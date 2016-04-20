
```scala
package era7bio.db

import ohnosequences.blast.api._
import ohnosequences.fastarious.fasta._
import ohnosequences.awstools._, ec2._, InstanceType._, s3._, regions._
import ohnosequences.statika._, aws._

import era7bio.db.RNACentral5._
import era7bio.db.csvUtils._

import ohnosequencesBundles.statika._

import com.thinkaurelius.titan.core._, schema._
import com.bio4j.model.ncbiTaxonomy.NCBITaxonomyGraph._
import com.bio4j.titan.model.ncbiTaxonomy._
import com.bio4j.titan.util.DefaultTitanGraph


case object bio4jTaxonomyBundle extends AnyBio4jDist {

  lazy val s3folder: S3Folder = S3Folder("resources.ohnosequences.com", "16s/bio4j-taxonomy/")

  lazy val configuration = DefaultBio4jTitanConfig(dbLocation)

  // the graph; its only (direct) use is for indexes
  // FIXME: this works but still with errors, should be fixed (something about transactions)
  lazy val graph: TitanNCBITaxonomyGraph =
    new TitanNCBITaxonomyGraph(
      new DefaultTitanGraph(TitanFactory.open(configuration))
    )


  type TaxonNode = com.bio4j.model.ncbiTaxonomy.vertices.NCBITaxon[
    DefaultTitanGraph,
    TitanVertex, VertexLabelMaker,
    TitanEdge, EdgeLabelMaker
  ]

  implicit class IdOps(val id: String) extends AnyVal {

    def isDescendantOfOneIn(ancestors: Set[String]): Boolean = {
      // Java to Scala
      def optional[T](jopt: java.util.Optional[T]): Option[T] =
        if (jopt.isPresent) Some(jopt.get) else None

      @ annotation.tailrec
      def isDescendantOfOneIn_rec(node: TaxonNode): Boolean =
        optional(node.ncbiTaxonParent_inV) match {
          case None => false
          case Some(parent) =>
            if (ancestors.contains( parent.id() )) true
            else isDescendantOfOneIn_rec(parent)
        }

      optional(graph.nCBITaxonIdIndex.getVertex(id))
        .map(isDescendantOfOneIn_rec)
        .getOrElse(false)
    }
  }
}
```


## 16S RNA BLAST database

This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.


```scala
case object rna16s extends AnyBlastDB {

  val name = "era7bio.db.rna16s"

  val dbType = BlastDBType.nucl

  val rnaCentralRelease: RNACentral5.type = RNACentral5

  val s3location: S3Folder = S3Folder("resources.ohnosequences.com", "db/rna16s/")
```

We are using the ribosomal RNA type annotation on RNACentral as a first catch-all filter. We are aware of the existence of a gene annotation corresponding to 16S, that we are **not using** due to a significant amount of 16S sequences lacking the corresponding annotation.

```scala
  val ribosomalRNAType = "rRNA"
```

The sequence length threshold for a sequence to be admitted as 16S.

```scala
  val minimum16SLength: Int = 1300
```

These are NCBI taxonomy IDs corresponding to taxa which are at best uniformative. The `String` value is the name of the corresponding taxon, for documentation purposes.

```scala
  val uninformativeTaxIDsMap = Map(
    32644   -> "unclassified",
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
    86027   -> "uncultured beta proteobacterium",
    86473   -> "uncultured gamma proteobacterium",
    34034   -> "uncultured delta proteobacterium",
    56765   -> "uncultured marine bacterium",
    115414  -> "uncultured marine alpha proteobacterium"
  )

  val uninformativeTaxIDs: Set[String] = uninformativeTaxIDsMap.keys.map(_.toString).toSet
```


#### Database defining predicate

Sequences that satisfy this predicate (on themselves together with their annotation) are included in this database.


```scala
  import bio4jTaxonomyBundle._
  val predicate: (Row, FASTA.Value) => Boolean = { (row, fasta) =>
```

- are annotated as rRNA

```scala
     row.select(rna_type).contains(ribosomalRNAType)        &&
```

- their taxonomy association is *not* one of those in `uninformativeTaxIDs`

```scala
    ( ! uninformativeTaxIDs.contains(row.select(tax_id)) )  &&
```

- the corresponding sequence is not shorter than the threshold

```scala
    (fasta.getV(sequence).value.length >= minimum16SLength) &&
```

- is a descendant of either Archaea or Bacteria

```scala
    row.select(tax_id).isDescendantOfOneIn(
      Set(
        "2157", // Archaea
        "2"     // Bacteria
      )
    )
  }

  // bundle to generate the DB (see the runBundles file in tests)
  case object generate extends GenerateBlastDB(this) {

    override val bundleDependencies: List[AnyBundle] = List(bio4jTaxonomyBundle)
  }

  // bundle to obtain and use the generated release
  case object release extends BlastDBRelease(generate)
}

```




[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md
[test/scala/compats.scala]: ../../test/scala/compats.scala.md
[test/scala/Dbrna16s.scala]: ../../test/scala/Dbrna16s.scala.md
[main/scala/rna16s.scala]: rna16s.scala.md