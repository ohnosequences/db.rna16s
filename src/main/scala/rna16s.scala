package era7bio.db.rna16s

import ohnosequences.cosas._, types._, klists._
import ohnosequences.statika._
import ohnosequences.fastarious._, fasta._, ncbiHeaders._
import ohnosequences.blast.api._
import better.files._

trait AnyBlastDB {

  lazy val name: String = getClass.getName
  // TODO sequence header stuff; this should be lcl()
  // this is what will be added to the fasta headers
  lazy val ncbiID = ncbiHeaders.name(name)

  // TODO S3 Object address; AnyData?
  val sourceFasta: String
  val dbType: BlastDBType
}

// TODO this should get the input fasta from somewhere, download it and run makeblastdb etc
// blast should be a dependency
trait GenerateBlastDB[DB <: AnyBlastDB] extends Bundle() {

  val db: DB

  // TODO build NCBI header stuff with lcl
  def processInput(inputFasta: File, drop: FASTA.Value => Boolean, outputFasta: File): File = {

    fasta
      .parseFastaDropErrors(inputFasta.lines)
      .filterNot(drop)
      .map(fa => fa.update(header := FastaHeader(s"${fa.getV(header).id}|${toHeader(db.ncbiID)}")))
      .appendTo(outputFasta)

    outputFasta
  }

  def makeDB(inputFasta: File) =
    makeblastdb(
      argumentValues =
        in(inputFasta)                ::
        input_type(DBInputType.fasta) ::
        dbtype(db.dbType)             ::
        *[AnyDenotation],
      optionValues =
        makeblastdb.defaults.update( title(db.name) ).value
    )
}
