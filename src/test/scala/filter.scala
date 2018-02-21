package ohnosequences.db.rna16s.test

import ohnosequences.db.rna16s.fullDB
import ohnosequences.test.ReleaseOnlyTest
import ohnosequences.api.rnacentral.{Entry, iterators}
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import ohnosequences.awstools.s3, s3.S3Object
import ohnosequences.db.rnacentral.data.{speciesSpecificFASTA => s3InputFasta}
import ohnosequences.db.rnacentral.data.{idMappingTSV => s3InputTSV}
import java.io.File
import org.scalatest.{FunSuite, Matchers}, Matchers._

class DataGeneration extends FunSuite {

  /**
    * Returns `Some(file)` if the download from `s3Object` to `file` succeeded,
    * `None` otherwise.
    */
  def downloadTo(s3Object: S3Object, file: File): Option[File] = {
    val tm = TransferManagerBuilder
      .standard()
      .withS3Client(s3.defaultClient)
      .build()

    scala.util.Try {
      tm.download(
          s3Object.bucket,
          s3Object.key,
          file
        )
        .waitForCompletion()
    } match {
      case scala.util.Failure(e) => None
      case scala.util.Success(s) => Some(file)
    }
  }

  /**
    * Processes all entries provided by the `entries` iterator and save the
    * result in `file`.
    * Returns `Some(file)` if both the process and the file save succeeded,
    * `None` otherwise.
    */
  def processEntries(entries: Iterator[Entry], file: File): Option[File] =
    scala.util.Try {
      iterators
        .right(entries map rna16sIdentification.is16s)
        .map(fastaFormat.entryToFASTA)
        .appendTo(file)
    } match {
      case scala.util.Failure(e) => None
      case scala.util.Success(s) => Some(file)
    }

  /**
    * Returns `Some(s3Object)` if the upload from `file` to `s3Object` succeeded,
    * `None` otherwise.
    */
  def uploadTo(file: File, s3Object: S3Object): Option[S3Object] =
    scala.util.Try {
      s3.defaultClient.putObject(
        s3Object.bucket,
        s3Object.key,
        file
      )
    } match {
      case scala.util.Success(s) => Some(s3Object)
      case scala.util.Failure(e) => None
    }

  test("process all entries", ReleaseOnlyTest) {

    outFASTA.delete

    (for {
      fastaFile <- downloadTo(s3InputFasta, speciesSpecificFasta)
      tsvFile   <- downloadTo(s3InputTSV, idMapping)
      outFile   <- processEntries(allEntries, outFASTA)
      s3Object  <- uploadTo(outFile, fullDB)
    } yield {
      println(s"$fastaFile and $tsvFile downloaded")
      println(s"Database locally written to $outFile")
      println(s"Database uploaded to $s3Object")
    }) shouldBe defined

  }
}
