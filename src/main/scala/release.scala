package ohnosequences.db.rna16s.test

import org.scalatest.FunSuite
import ohnosequences.db
import ohnosequences.test.ReleaseOnlyTest
import ohnosequences.db.rnacentral.{Entry, iterators}
import ohnosequences.awstools.s3, s3.S3Object
import java.io.File

class DataGeneration extends FunSuite {

  val version = db.rnacentral.Version.latest

  /**
    * Processes all entries provided by the `entries` iterator and save the
    * result in `file`.
    * Returns `Some(file)` if both the process and the file save succeeded,
    * `None` otherwise.
    */
  def generateSequences(entries: Iterator[Entry], file: File): Option[File] =
    scala.util.Try {
      iterators
        .right(entries map rna16sIdentification.is16s)
        .map(fastaFormat.entryToFASTA)
        .appendTo(file)
    } match {
      case scala.util.Failure(e) => { println(e); None }
      case scala.util.Success(s) => Some(file)
    }

  test("Sequences generation and upload", ReleaseOnlyTest) {

    output.sequences.delete

    println("Downloading input files")

    downloadOrFail(
      s3Object = db.rnacentral.data.speciesSpecificFASTA(version),
      file = input.fasta
    )
    println("  FASTA downloaded")

    downloadOrFail(
      s3Object = db.rnacentral.data.idMappingTSV(version),
      file = input.idMapping
    )
    println("  TSV downloaded")

    println("Generating sequences")

    generateSequencesOrFail(
      entries = input.rnaCentralEntries,
      file = output.sequences
    )

    println("Uploading sequences")

    uploadOrFail(
      file = output.sequences,
      s3Object = db.rna16s.sequences
    )
  }

  // ScalaTest utils
  //////////////////////////////////////////////////////////////////////////////
  def getOrFail[X](msg: String): Option[X] => X =
    _.fold(fail(msg)) { x =>
      x
    }

  def generateSequencesOrFail(entries: Iterator[Entry], file: File): File =
    getOrFail(s"Could not process entries")(
      generateSequences(entries, file)
    )

  def downloadOrFail(s3Object: S3Object, file: File): File =
    getOrFail(s"Could not download ${s3Object} to ${file}")(
      s3Utils.downloadTo(s3Object, file)
    )

  def uploadOrFail(file: File, s3Object: S3Object): S3Object =
    getOrFail(s"Could not upload ${file} to ${s3Object}")(
      s3Utils.uploadTo(file, s3Object)
    )
}
