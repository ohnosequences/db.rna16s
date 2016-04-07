package era7bio.db.rna16s

// import ohnosequences.cosas._, types._, klists._
// import ohnosequences.statika._
import ohnosequences.fastarious._, fasta._, ncbiHeaders._
import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

trait AnyBlastDB {
  val dbType: BlastDBType

  lazy val name: String = getClass.getName

  // TODO sequence header stuff; this should be lcl()
  // this is what will be added to the fasta headers
  lazy val ncbiID = ncbiHeaders.name(name)

  private[rna16s] val sourceFasta: S3Object
  private[rna16s] val sourceTable: S3Object
}
