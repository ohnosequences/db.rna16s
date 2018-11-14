package ohnosequences.db.rna16s

import ohnosequences.files
import ohnosequences.s3

sealed abstract class Error {
  def msg: String
}

case object Error {

  final case class FileError(val err: files.Error) extends Error {
    val msg = err.msg
  }

  final case class S3Error(val err: s3.Error) extends Error {
    val msg = err.msg
  }

  final case class S3ObjectExists(val obj: s3.S3Object) extends Error {
    val msg = s"The S3 object $obj exists."
  }

  final case class FailedGeneration(val err: String) extends Error {
    val msg = s"An exception occurred during data generation: $err"
  }
}
