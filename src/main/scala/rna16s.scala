package era7bio.db

import ohnosequences.blast.api._
import ohnosequences.awstools._, ec2._, InstanceType._, s3._, regions._
import ohnosequences.statika._, aws._

import era7.defaults._, loquats._

import rnaCentralTable._


case object rna16sDB extends AnyBlastDB {
  val name = "era7bio.db.rna16s"

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

  private[db] val sourceFasta: S3Object = s3folder / s"rnacentral.${ver}.fasta"
  private[db] val sourceTable: S3Object = s3folder / s"id2taxa.active.${ver}.tsv"

  /*
    Here we want to keep sequences which

    1. are annotated as encoding 16S
    2. their taxonomy association is *not* one of those in `uninformativeTaxIDs`
  */
  val predicate: Row => Boolean = { row =>
     (row(GeneName) == geneNameFor16S) &&
    !(uninformativeTaxIDs contains row(TaxID))
    // filter by length > 1000
  }
}

case object rna16sDBRelease {

  case object generateRna16sDB extends GenerateBlastDB(rna16sDB)

  case object compat extends Compatible(
    amznAMIEnv(
      AmazonLinuxAMI(Region.Ireland, HVM, InstanceStore),
      javaHeap = 20 // in G
    ),
    generateRna16sDB,
    generated.metadata.DbRna16s
  )

  def launch(user: AWSUser): List[String] = {
    EC2.create(user.profile)
      .runInstances(
        amount = 1,
        compat.instanceSpecs(
          c3.x2large,
          user.keypair.name,
          Some(ec2Roles.projects.name)
        )
      ).map { inst =>

        val id = inst.getInstanceId
        println(s"Launched [${id}]")
        id
      }
  }

}
