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

case object blastBundle extends Blast("2.2.31")

// TODO this should get the input fasta from somewhere, download it and run makeblastdb etc
// blast should be a dependency
abstract class GenerateBlastDB[DB <: AnyBlastDB](val db: DB) extends Bundle(blastBundle) {

  val predicate: Row => Boolean

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

      transferManager.download(db.sourceFasta.bucket, db.sourceFasta.key, sourceFasta.toJava)
      transferManager.download(db.sourceTable.bucket, db.sourceTable.key, sourceTable.toJava)
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
      makeDB(outputFasta).toSeq
    )
  }

  // TODO build NCBI header stuff with lcl
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

        // If the row coplies to the predicate, we write it
        if (predicate(row)) {
          tableWriter.writeRow(row)
          fastasOutFile.append(
            fasta.update(
              header := FastaHeader(s"${fasta.getV(header).id}|${toHeader(db.ncbiID)}")
            ).asString
          )
        }

        // And anyway continue the cycle
        processSources(tableWriter, fastasOutFile)(nextRows, nextFastas)
      }
    }
  }

  def makeDB(inputFasta: File) =
    makeblastdb(
      argumentValues =
        in(inputFasta)                ::
        input_type(DBInputType.fasta) ::
        dbtype(db.dbType)             ::
        *[AnyDenotation],
      optionValues =
        title(db.name) ::
        *[AnyDenotation]
    )
}
