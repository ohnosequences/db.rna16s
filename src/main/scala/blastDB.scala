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

import better.files._

import rnaCentralTable._


trait AnyBlastDB {
  val dbType: BlastDBType

  val name: String

  private[db] val sourceFasta: S3Object
  private[db] val sourceTable: S3Object

  val predicate: Row => Boolean
}


case object blastBundle extends Blast("2.2.31")


class GenerateBlastDB[DB <: AnyBlastDB](val db: DB) extends Bundle(blastBundle) {

  val tableFormat = new TSVFormat {
    override val lineTerminator = "\n"
  }

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
      processSources(
        sourceTable,
        outputTable
      )(sourceFasta,
        outputFasta
      )
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

  // This is the main processing part, that is separate to facilitate local testing
  final def processSources(
    tableInFile: File,
    tableOutFile: File
  )(fastaInFile: File,
    fastaOutFile: File
  ) {
    tableOutFile.createIfNotExists()
    fastaOutFile.createIfNotExists()

    val tableReader = CSVReader.open(tableInFile.toJava)(tableFormat)
    val tableWriter = CSVWriter.open(tableOutFile.toJava, append = true)(tableFormat)

    processIterators(
      tableReader.iterator,
      fasta.parseFastaDropErrors(fastaInFile.lines)
    )

    tableReader.close()
    tableWriter.close()


    @scala.annotation.tailrec
    def processIterators(
      rows: Iterator[Row],
      fastas: Iterator[FASTA.Value]
    ): (Iterator[Row], Iterator[FASTA.Value]) = {

      // This is the end... my friend
      if (!rows.hasNext) (Iterator(), Iterator())
      else {
        val row: Row = rows.next()
        val id = row(HashID)
        val extID = s"${id}|lcl|${db.name}"

        // Dropping all next rows with the same HashID
        val nextRows = rows.dropWhile{ r => r(HashID) == id }
        // Dropping until we ecnounter a sequence with this HashID
        val nextFastas = fastas.dropWhile{ f => f.getV(header).id != id }

        if(!nextFastas.hasNext) {
          // println("No more fastas \"(")
          (Iterator(), Iterator())
        }
        else {
          // This is the fasta corresponding to `row`
          val fasta = nextFastas.next()

          // If the row satisfies the predicate, we write both it and the corresponding fasta
          if (db.predicate(row)) {
            // We want only ID to Taxa mapping
            tableWriter.writeRow(List(
              extID,
              row(TaxID)
            ))

            fastaOutFile.appendLine(
              fasta.update(
                header := FastaHeader(extID)
              ).asString
            )
          }
          // else {
          //   println(s"Skipping [${id}], because it doesn't satisfy the predicate")
          // }

          // And anyway continue the cycle
          processIterators(nextRows, nextFastas)
        }
      }
    }
  }

}
