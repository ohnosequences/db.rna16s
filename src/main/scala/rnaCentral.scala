package era7bio.db

import ohnosequences.cosas._, types._, klists._
import ohnosequences.statika._
import ohnosequences.fastarious._, fasta._, ncbiHeaders._
import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

import ohnosequencesBundles.statika.Blast

import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._

import com.github.tototoshi.csv._

// TODO: move all this code to the era7bio/rnacentral repo (and use cosas/records)

case object rnaCentralTable {

  // TODO: use a better representation for the table row
  type Row = Seq[String]

  sealed trait Field

  case object HashID   extends Field
  case object SourceDB extends Field
  case object SourceID extends Field
  case object TaxID    extends Field
  case object RNAType  extends Field
  case object GeneName extends Field

  // Defines the order of the columns
  final val fields = Seq[Field](
    HashID,
    SourceDB,
    SourceID,
    TaxID,
    RNAType,
    GeneName
  )

  implicit def rowOps(row: Row): RowOps = RowOps(row)

}

case class RowOps(row: rnaCentralTable.Row) extends AnyVal {
  import rnaCentralTable._

  def toMap: Map[Field, String] = fields.zip(row).toMap

  def apply(field: Field): String = this.toMap.apply(field)
}
