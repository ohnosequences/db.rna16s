package ohnosequences.db.rna16s

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import ohnosequences.s3.{S3Object, request}
import java.io.File

/**
  * Helpers:
  * - Partial applications of functions from `s3`, using a standard S3Client
  *   built here, [[helpers.s3Client]], and with a default part size,
  *   [[helpers.partSize5MiB]]. All functions here map their Errors to an
  *   object of type [[Error.S3Error]].
  * - Method to check whether all the files for a version exist in S3
  */
private[rna16s] case object helpers {

  lazy val s3Client = AmazonS3ClientBuilder.standard().build()
  val partSize5MiB  = 5 * 1024 * 1024

  def getCheckedFile(s3Obj: S3Object, file: File) =
    request.getCheckedFile(s3Client)(s3Obj, file).left.map(Error.S3Error)

  def paranoidPutFile(file: File, s3Obj: S3Object) =
    request
      .paranoidPutFile(s3Client)(file, s3Obj, partSize5MiB)(
        data.hashingFunction
      )
      .left
      .map(Error.S3Error)

  def getCheckedFileIfDifferent(s3Obj: S3Object, file: File) =
    request
      .getCheckedFileIfDifferent(s3Client)(s3Obj, file)
      .left
      .map(Error.S3Error)

  /** Returns true when object does not exists or communication with S3
    * cannot be established */
  def objectExists(s3Obj: S3Object) =
    request
      .objectExists(s3Client)(s3Obj)
      .fold(
        err => true,
        identity
      )

  /**
    * Finds any object under [[data.s3Prefix(version)]] that could be overwritten
    * by [[mirrorNewVersion]].
    *
    * @param version is the version that specifies the S3 folder
    *
    * @return Some(object) with the first object found under
    * [[data.s3Prefix(version)]] if any, None otherwise.
    */
  def findVersionInS3(version: Version): Option[S3Object] =
    data
      .everything(version)
      .find(
        obj => objectExists(obj)
      )
}
