package era7bio.db.rna16s

import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

import rnaCentralTable._


case object rna16sDB extends AnyBlastDB {
  val dbType = BlastDBType.nucl

  private val ver = "5.0"
  private val s3folder = S3Folder("resources.ohnosequences.com", s"rnacentral/${ver}")

  private[rna16s] val sourceFasta: S3Object = s3folder / s"rnacentral.${ver}.fasta"
  private[rna16s] val sourceTable: S3Object = s3folder / s"id2taxa.active.${ver}.tsv"

  val predicate: Row => Boolean = { row =>
    row(GeneName) == "16s"
    // TODO: what else?
  }
}
