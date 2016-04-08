package era7bio.db.rna16s

import ohnosequences.cosas._, types._, klists._
import ohnosequences.statika._
import ohnosequences.fastarious._, fasta._, ncbiHeaders._
import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

import ohnosequencesBundles.statika.Blast

import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._

import com.github.tototoshi.csv._

import better.files._

import rnaCentralTable._


trait AnyBlastDB {
  val dbType: BlastDBType

  lazy val name: String = getClass.getName

  private[rna16s] val sourceFasta: S3Object
  private[rna16s] val sourceTable: S3Object

  val predicate: Row => Boolean
}


case object blastBundle extends Blast("2.2.31")


class GenerateBlastDB[DB <: AnyBlastDB](val db: DB) extends Bundle(blastBundle) {

  // Files
  lazy val sources = file"sources"
  lazy val outputs = file"outputs"

  lazy val sourceFasta: File = sources / "source.fasta"
  lazy val sourceTable: File = sources / "source.table.tsv"

  lazy val outputFasta: File = outputs / "output.fasta"
  lazy val outputTable: File = outputs / "id2taxa.tsv"


  def instructions: AnyInstructions = {

    val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

    LazyTry {
      println(s"""Downloading the sources...
        |fasta: ${db.sourceFasta}
        |table: ${db.sourceTable}
        |""".stripMargin)

      transferManager.download(db.sourceFasta.bucket, db.sourceFasta.key, sourceFasta.toJava).waitForCompletion
      transferManager.download(db.sourceTable.bucket, db.sourceTable.key, sourceTable.toJava).waitForCompletion
    } -&-
    LazyTry {
      // FIXME: what is the line separator here?
      val tableReader = CSVReader.open(sourceTable.toJava)(new DefaultCSVFormat {})
      val tableWriter = CSVWriter.open(outputTable.toJava, append = true)(new DefaultCSVFormat {})

      processSources(
        tableWriter,
        outputFasta
      )(tableReader.iterator,
        fasta.parseFastaDropErrors(sourceFasta.lines)
      )

      tableReader.close()
      tableWriter.close()
    } -&-
    seqToInstructions(
      makeblastdb(
        argumentValues =
          in(outputFasta) ::
          input_type(DBInputType.fasta) ::
          dbtype(db.dbType) ::
          *[AnyDenotation],
        optionValues =
          title(db.name) ::
          *[AnyDenotation]
      ).toSeq
    )
  }

  @scala.annotation.tailrec
  final def processSources(
    tableWriter: CSVWriter,
    fastasOutFile: File
  )(rows: Iterator[Row],
    fastas: Iterator[FASTA.Value]
  ): (Iterator[Row], Iterator[FASTA.Value]) = {

    if (!rows.hasNext || !fastas.hasNext) (Iterator(), Iterator())
    else {
      val row: Row = rows.next()
      val id = row(HashID)

      // Dropping all next rows with the same HashID
      val nextRows = rows.dropWhile{ r => r(HashID) == id }
      // Dropping until we ecnounter a sequence with this HashID
      val nextFastas = fastas.dropWhile{ f => f.getV(header).id != id }

      if(!nextFastas.hasNext) (Iterator(), Iterator())
      else {
        // This is the fasta corresponding to `row`
        val fasta = nextFastas.next()

        // If the row satisfies the predicate, we write both it and the corresponding fasta
        if (db.predicate(row)) {
          // We want only ID to Taxa mapping
          tableWriter.writeRow(List(
            row(HashID),
            row(TaxID)
          ))

          fastasOutFile.append(
            fasta.update(
              header := FastaHeader(s"${fasta.getV(header).id}|lcl|${db.name}")
            ).asString
          )
        }

        // And anyway continue the cycle
        processSources(tableWriter, fastasOutFile)(nextRows, nextFastas)
      }
    }
  }

}
