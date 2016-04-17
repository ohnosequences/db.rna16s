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

  def checkAncestors(id: String, ancestors: Set[String]): Boolean = {
    // Java to Scala
    def optional[T](jopt: java.util.Optional[T]): Option[T] =
      if (jopt.isPresent) Some(jopt.get) else None

    @scala.annotation.tailrec
    def checkAncestors_rec(node: TaxonNode): Boolean =
      optional(node.ncbiTaxonParent_inV) match {
        case None => false
        case Some(parent) =>
          if (ancestors.contains( parent.id() )) true
          else checkAncestors_rec(parent)
      }

    optional(graph.nCBITaxonIdIndex.getVertex(id))
      .map(checkAncestors_rec)
      .getOrElse(false)
  }
}


case object rna16s extends AnyBlastDB {

  val name = "era7bio.db.rna16s"

  val dbType = BlastDBType.nucl

  val rnaCentralRelease: RNACentral5.type = RNACentral5

  val s3location: S3Folder = S3Folder("resources.ohnosequences.com", "db/rna16s/")

  /* The name identifying an RNA corresponding to 16S */
  val geneNameFor16S = "16S rRNA"
  /* These are NCBI taxonomy IDs corresponding to taxa which is at best uniformative. The `String` value is the name of the corresponding taxon, for documentation purposes. */
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

  /* Here we want to keep sequences which */
  val predicate: (Row, FASTA.Value) => Boolean = { (row, fasta) =>
    /* - are annotated as encoding 16S */
    ( row.select(gene_name) == geneNameFor16S ||
      fasta.getV(header).description.toLowerCase.contains("16s") ||
      fasta.getV(header).description.toLowerCase.contains("small subunit ribosomal")
    ) &&
    /* - are annotated as rRNA */
     row.select(rna_type).toLowerCase.contains("rrna") &&
    /* - their taxonomy association is *not* one of those in `uninformativeTaxIDs` */
    (uninformativeTaxIDs contains row.select(tax_id)) &&
    /* - and the corresponding sequence is not shorter than 1300 BP */
    (fasta.getV(sequence).value.length >= 1300) &&
    /* - is a descendant of either Archaea or Bacteria */
    bio4jTaxonomyBundle.checkAncestors(
      row.select(tax_id), Set(
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
