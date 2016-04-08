package era7bio.db.rna16s

import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

import rnaCentralTable._


case object rna16sDB extends AnyBlastDB {

  val dbType = BlastDBType.nucl
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

  val uninformativeTaxIDs = uninformativeTaxIDsMap.keys.map(_.toString).toSet

  private val ver = "5.0"
  private val s3folder = S3Folder("resources.ohnosequences.com", s"rnacentral/${ver}")

  private[rna16s] val sourceFasta: S3Object = s3folder / s"rnacentral.${ver}.fasta"
  private[rna16s] val sourceTable: S3Object = s3folder / s"id2taxa.active.${ver}.tsv"

  /*
    Here we want to keep sequences which

    1. are annotated as encoding 16S
    2. their taxonomy association is *not* one of those in `uninformativeTaxIDs`
  */
  val predicate: Row => Boolean = { row =>
    (row(GeneName) == geneNameFor16S) && !(uninformativeTaxIDs contains row(TaxID))
  }
}
