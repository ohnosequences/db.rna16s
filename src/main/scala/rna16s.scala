package era7bio.db

import ohnosequences.blast.api._
import ohnosequences.fastarious.fasta._
import ohnosequences.awstools._, ec2._, InstanceType._, s3._, regions._
import ohnosequences.statika._, aws._

import era7bio.db.RNACentral5._
import era7bio.db.csvUtils._


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
     (row.select(gene_name) == geneNameFor16S ||
      fasta.getV(header).description.toLowerCase.contains("16s")
     ) &&
     /* - their taxonomy association is *not* one of those in `uninformativeTaxIDs` */
    !(uninformativeTaxIDs contains row.select(tax_id)) &&
     /* - and the corresponding sequence is not shorter than 1000 BP */
     (fasta.getV(sequence).value.length >= 1000)
  }


  // bundle to generate the DB (see the runBundles file in tests)
  case object generate extends GenerateBlastDB(this)

  // bundle to obtain and use the generated release
  case object release extends BlastDBRelease(generate)
}
