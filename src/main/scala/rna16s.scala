package era7bio.db

import ohnosequences.blast.api._
import ohnosequences.awstools._, ec2._, InstanceType._, s3._, regions._
import ohnosequences.statika._, aws._

import era7.defaults._, loquats._

import rnaCentralTable._


case object rna16sDB extends AnyBlastDB {
  val dbType = BlastDBType.nucl

  private val ver = "5.0"
  private val s3folder = S3Folder("resources.ohnosequences.com", s"rnacentral/${ver}")

  private[db] val sourceFasta: S3Object = s3folder / s"rnacentral.${ver}.fasta"
  private[db] val sourceTable: S3Object = s3folder / s"id2taxa.active.${ver}.tsv"

  val predicate: Row => Boolean = { row =>
    row(GeneName) == "16S rRNA"
    // TODO: what else?
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
