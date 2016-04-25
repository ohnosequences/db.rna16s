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

  /* **NOTE** all methods here work with a non-reflexive definition of ancestor/descendant */
  implicit class IdOps(val id: String) extends AnyVal {

    // Java to Scala
    private def optional[T](jopt: java.util.Optional[T]): Option[T] =
      if (jopt.isPresent) Some(jopt.get) else None

    def hasEnvironmentalSamplesAncestor: Boolean = {

      def hasEnvironmentalSamplesAncestor_rec(node: TaxonNode): Boolean =
        optional(node.ncbiTaxonParent_inV) match {
          case None => false
          case Some(parent) =>
            if (parent.name == "environmental samples") true
            else hasEnvironmentalSamplesAncestor_rec(parent)
        }

      optional(graph.nCBITaxonIdIndex.getVertex(id))
        .fold(false)(hasEnvironmentalSamplesAncestor_rec)
    }

    def isDescendantOfOneIn(ancestors: Set[String]): Boolean = {

      @annotation.tailrec
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

    private def unclassifiedBacteriaID = 2323

    def isDescendantOfUnclassifiedBacteria: Boolean = isDescendantOfOneIn( Set(unclassifiedBacteriaID.toString) )

    def hasDescendantOrItselfUnclassified: Boolean = {

      def hasDescendantOrItselfUnclassified_rec(node: TaxonNode): Boolean =
        optional(node.ncbiTaxonParent_inV) match {
          case None => false
          case Some(parent) =>
            if ( parent.name.contains("unclassified") ) true
            else hasDescendantOrItselfUnclassified_rec(parent)
        }

      optional(graph.nCBITaxonIdIndex.getVertex(id))
        .fold(false)(hasDescendantOrItselfUnclassified_rec)
    }
  }
}

/*
  ## 16S RNA BLAST database

  This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.
*/
case object rna16s extends AnyBlastDB {

  val name = "era7bio.db.rna16s"

  val dbType = BlastDBType.nucl

  val rnaCentralRelease: RNACentral5.type = RNACentral5

  val s3location: S3Folder = S3Folder("resources.ohnosequences.com", "db/rna16s/")

  /* We are using the ribosomal RNA type annotation on RNACentral as a first catch-all filter. We are aware of the existence of a gene annotation corresponding to 16S, that we are **not using** due to a significant amount of 16S sequences lacking the corresponding annotation. */
  val ribosomalRNAType = "rRNA"

  /* The sequence length threshold for a sequence to be admitted as 16S. */
  val minimum16SLength: Int = 1300

  /* Taxa IDs for archaea and bacteria */
  val archaeaTaxonID  = 2157
  val bacteriaTaxonID = 2

  /* These are NCBI taxonomy IDs corresponding to taxa which are at best uniformative. The `String` value is the name of the corresponding taxon, for documentation purposes. */
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

  /* and here we have RNACentral entries which we think are poorly assigned> This is list is by no means exhaustive, though its value can hardly be understimated. */
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
    "URS00008898AD"   // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
  )

  /*
    #### Database defining predicate

    Sequences that satisfy this predicate (on themselves together with their annotation) are included in this database.
  */
  import bio4jTaxonomyBundle._
  val predicate: (Row, FASTA.Value) => Boolean = { (row, fasta) =>
    /* is not blacklisted */
    ( ! blacklistedRNACentralIDs.contains(row.select(id)) )                                             &&
    /* - are annotated as rRNA */
     row.select(rna_type).contains(ribosomalRNAType)                                                    &&
    /* - their taxonomy association is *not* one of those in `uninformativeTaxIDs` */
    ( ! uninformativeTaxIDs.contains(row.select(tax_id)) )                                              &&
    /* - the corresponding sequence is not shorter than the threshold */
    (fasta.getV(sequence).value.length >= minimum16SLength)                                             &&
    /* - is a descendant of either Archaea or Bacteria */
    row.select(tax_id).isDescendantOfOneIn( Set( archaeaTaxonID.toString, bacteriaTaxonID.toString ) )  &&
    /* - is not a descendant of an "environmental samples" or unclassified taxon */
    ( ! row.select(tax_id).hasEnvironmentalSamplesAncestor )                                            &&
    ( ! row.select(tax_id).isDescendantOfUnclassifiedBacteria )                                         &&
    ( ! row.select(tax_id).hasDescendantOrItselfUnclassified )
  }

  // bundle to generate the DB (see the runBundles file in tests)
  case object generate extends GenerateBlastDB(this) {

    override val bundleDependencies: List[AnyBundle] =
      List[AnyBundle](bio4jTaxonomyBundle, blastBundle)
  }

  // bundle to obtain and use the generated release
  case object release extends BlastDBRelease(generate)
}
