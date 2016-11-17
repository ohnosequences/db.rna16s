/*
  # Pick 16S candidates

  In this first step we pick those sequences which contain a 16S gene sequence, based on their length and annotations. From the taxonomical point of view, we are only interested in sequences with at least one assignment to (a descendant of) *Bacteria* or *Archaea* which is *not* a descendant of "unclassified" taxa.

  The output of this step represents around `5%` of the sequences in RNACentral.
*/
package ohnosequences.db.rna16s.test

import ohnosequences.db._, csvUtils._, collectionUtils._
import ohnosequences.db.rnacentral._, RNACentral5._
import ohnosequences.ncbitaxonomy._, titan._
import ohnosequences.fastarious.fasta._
import ohnosequences.statika._
import com.github.tototoshi.csv._
import better.files._

case object pick16SCandidates extends FilterData(
  RNACentral5.table,
  RNACentral5.fasta,
  ohnosequences.db.rna16s.s3prefix
)(
  deps = ncbiTaxonomyBundle
)
{

  /* We are using the ribosomal RNA type annotation on RNACentral as a first catch-all filter. We are aware of the existence of a gene annotation corresponding to 16S, that we are **not using** due to a significant amount of 16S sequences lacking the corresponding annotation. */
  val ribosomalRNAType = "rRNA"

  /* The sequence length threshold for a sequence to be admitted as 16S. */
  val minimum16SLength: Int = 1300

  /* Taxon IDs for *Archaea*, *Bacteria* and the dreaded *Unclassified Bacteria* taxon */
  val bacteriaTaxonID        = "2"
  val archaeaTaxonID         = "2157"
  val unclassifiedBacteriaID = "2323"

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

  lazy val uninformativeTaxIDs: Set[String] = uninformativeTaxIDsMap.keySet.map(_.toString)

  /*
    ## Predicate defining a 16S candidate

    Sequences that satisfy this predicate (on themselves together with their annotation) are included in the output of this step.
  */
  private lazy val taxonomyGraph = ohnosequences.ncbitaxonomy.ncbiTaxonomyBundle.graph

  def rowPredicate(row: Row): Boolean = {
    val taxID = row.select(tax_id)

    /* - are annotated as rRNA */
    row.select(rna_type).contains(ribosomalRNAType) &&
    /* - are *not* from the SILVA database */
    ( row.select(db).trim.toLowerCase != "silva" )  &&
    /* - their taxonomy association is *not* one of those in `uninformativeTaxIDs` */
    ( ! uninformativeTaxIDs.contains(taxID) )       &&
    {
      taxonomyGraph.getTaxon(taxID).map(_.ancestors) match {
        case None => false // not in the DB
        case Some(ancestors) =>

    /* - is a descendant of either Archaea or Bacteria */
          ancestors.exists { ancestor =>
            ancestor.id == archaeaTaxonID ||
            ancestor.id == bacteriaTaxonID
          } &&
    /* - and is not a descendant of an environmental or unclassified taxon */
          ancestors.filter { ancestor =>
            ancestor.name == "environmental samples" ||
            ancestor.name.contains("unclassified")   ||
            ancestor.id == unclassifiedBacteriaID
          }.isEmpty
      }
    }
  }

  /* This predicate determines whether the *sequence* value is OK, and will be kept. */
  def sequencePredicate(fastaSeq: FastaSequence): Boolean = {
    val seq = fastaSeq.value

    ( seq.length >= minimum16SLength )              &&
    ( !(seq containsSlice "NNNNNNNN") )             &&
    ( (seq.count(_ == 'N') / seq.length) <= 0.01 )
  }

  // bundle to generate the DB (see the runBundles file in tests)
  def filterData(): Unit = {

    val groupedRows: Iterator[(String, Seq[Row])] =
      source.table.tsvReader.iterator.contiguousGroupBy { _.select(id) }

    val fastas: Iterator[FASTA.Value] = source.fasta.stream.toIterator

    (groupedRows zip fastas) foreach { case ((commonID, rows), fasta) =>

      if (commonID != fasta.getV(header).id)
        sys.error(s"ID [${commonID}] is not found in the FASTA. Check RNACentral filtering.")

      val (acceptedRows, rejectedRows) =
        if ( sequencePredicate(fasta.get(sequence).value) ) {
          /* if the sequence is OK, we partition the rows based on the predicate */
          rows.partition(rowPredicate)
        } else {
          (Seq[Row](), rows)
        }

      val extendedID: String = s"gnl|${ohnosequences.db.rna16s.dbName}|${commonID}"

      writeOutput(
        extendedID,
        acceptedRows.map{ _.select(tax_id) }.distinct,
        rejectedRows.map{ _.select(tax_id) }.distinct,
        fasta.update( header := FastaHeader(Seq(extendedID, fasta.getV(header).description).mkString(" ") ) )
      )
    }
  }
}
