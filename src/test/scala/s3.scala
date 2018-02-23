package ohnosequences.db.rna16s.test

import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import ohnosequences.awstools.s3, s3.S3Object
import java.io.File

case object s3Utils {

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
      case scala.util.Success(s) => Some(file)
      case scala.util.Failure(e) => None
    }
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
}
