package era7bio.db

import ohnosequences.cosas._, types._, klists._
import ohnosequences.statika._
import ohnosequences.fastarious.fasta._
import ohnosequences.blast.api._
import ohnosequences.awstools.s3._

import ohnosequencesBundles.statika.Blast

import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._

import com.github.tototoshi.csv._

import better.files._

import rnaCentralTable._

// TODO: move all this code to the era7bio/rnacentral repo

trait AnyBlastDB {
  val dbType: BlastDBType

  val name: String

  val predicate: (Row, FASTA.Value) => Boolean

  private[db] val sourceFasta: S3Object
  private[db] val sourceTable: S3Object

  val s3location: S3Folder

  // case object release extends Bundle() {
  //
  //   // This is where the DB will be downloaded
  //   val dbLocation: File = File(s3location.key)
  //   // This is what you pass to BLAST
  //   val dbName: File = dbLocation / s"${name}.fasta"
  //
  //   def instructions: AnyInstructions = {
  //     LazyTry {
  //       val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())
  //       transferManager.download(s3location, file".")
  //     } -&-
  //     say(s"Reference database ${name} was dowloaded to ${dbLocation.path}")
  //   }
  // }
}


case object blastBundle extends Blast("2.2.31")


class GenerateBlastDB[DB <: AnyBlastDB](val db: DB) extends Bundle(blastBundle) {

  val tableFormat = new TSVFormat {
    override val lineTerminator = "\n"
    // NOTE: this tsv has '\' inside fields
    override val escapeChar = '†'
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

      transferManager.download(
        db.sourceFasta.bucket, db.sourceFasta.key,
        sourceFasta.toJava
      ).waitForCompletion

      transferManager.download(
        db.sourceTable.bucket, db.sourceTable.key,
        sourceTable.toJava
      ).waitForCompletion
    } -&-
    LazyTry {
      println("Processing sources...")

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
    ) -&-
    LazyTry {
      val tableDestination = db.s3location / outputTable.name
      transferManager.upload(
        tableDestination.bucket, tableDestination.key,
        outputTable.toJava
      ).waitForCompletion
    }
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

    val rows: Iterator[Row] = tableReader.iterator
    val fastas: Iterator[FASTA.Value] = parseFastaDropErrors(fastaInFile.lines)

    // NOTE: here we rely on that the sources are prefileterd and don't have duplicate ID
    (rows zip fastas)
      .filter { case (row, fasta) => db.predicate(row, fasta) }
      .foreach { case (row, fasta) =>

        val extID = s"${row(HashID)}|lcl|${db.name}"

        tableWriter.writeRow(List(
          extID,
          row(TaxID)
        ))

        fastaOutFile.appendLine(
          fasta.update(
            header := FastaHeader(s"${extID} ${fasta.getV(header).description}")
          ).asString
        )
      }

    tableReader.close()
    tableWriter.close()
  }

}
