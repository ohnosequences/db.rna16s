package ohnosequences.db.rna16s
import ohnosequences.db.rnacentral
import ohnosequences.db.rna16s.s3Helpers.{getCheckedFile, paranoidPutFile}
import ohnosequences.s3.S3Object
import ohnosequences.files.directory
import java.io.File

case object release {

  /**
    * Processes all entries provided by the `entries` iterator and save the
    * result in `file`.
    * Returns `Right(file)` if both the process and the file save succeeded,
    * `Left(Error.FailedGeneration)` otherwise.
    */
  private def generateSequences(
      entries: Iterator[rnacentral.Entry],
      file: File
  ): Error + File =
    scala.util.Try {
      rnacentral.iterators
        .right(entries map rna16sIdentification.is16s)
        .map(fastaFormat.entryToFASTA)
        .appendTo(file)
    } match {
      case scala.util.Failure(e) => Left(Error.FailedGeneration(e.toString))
      case scala.util.Success(s) => Right(file)
    }

  /**
    * Read the data from `db.rnacentral`, filter the 16S sequences and upload
    * the result to S3.
    *
    * For the RNACentral version associated to the version parameter, both the
    * ID Mappings and the sequences are downloaded, from which the 16S
    * sequences are filtered using [[rna16sIdentification.is16s]]. The result
    * of the filter is uploaded to the object returned by [[data.sequences]]
    * applied over the version parameter.
    *
    * @note This method does not check if an overwrite will happen. Use
    * [[generateNewDB]] for that use case.
    *
    * @return an Error + S3Object, with a Right(s3Obj) with the S3 path of the generated fastaa if everything worked as expected or with a Left(error) if an error occurred. Several things could go wrong in this process; namely:
    *   - The local directory could not be created or accessed
    *   - The input files from `db.rnacentral` could not be downloaded
    *   - The [[generateSequences]] function failed with an exception
    *   - The upload process failed, either because you have no permissions to
    *   upload the objects or because some error occured during the upload
    *   itself.
    */
  private def generateDB(
      version: Version,
      localFolder: File
  ): Error + S3Object = {
    val rnacentralVersion = version.inputVersion

    val inputFasta     = rnacentral.data.speciesSpecificFASTA(rnacentralVersion)
    val inputIdMapping = rnacentral.data.idMappingTSV(rnacentralVersion)
    val s3Sequences    = data.sequences(version)

    val mappingsFile           = data.local.idMappingFile(version, localFolder)
    val fastaFile              = data.local.fastaFile(version, localFolder)
    lazy val rnaCentralEntries = input.rnaCentralEntries(version, localFolder)
    val outputFile             = output.sequences(version, localFolder)

    for {
      _ <- directory.createDirectory(localFolder).left.map(Error.FileError)
      _ <- getCheckedFile(inputFasta, fastaFile)
      _ <- getCheckedFile(inputIdMapping, mappingsFile)
      _ <- generateSequences(rnaCentralEntries, outputFile)
      _ <- paranoidPutFile(outputFile, s3Sequences)
    } yield {
      s3Sequences
    }
  }

  /**
    * Read the data from `db.rnacentral`, filter the 16S sequences and upload
    * the result to S3 if and only if the upload does not override anything.
    *
    * For the RNACentral version associated to the version parameter, both the
    * ID Mappings and the sequences are downloaded, from which the 16S
    * sequences are filtered using [[rna16sIdentification.is16s]]. The result
    * of the filter is uploaded to the object returned by [[data.sequences]]
    * applied over the version parameter, if and only if the upload does not
    * override anything.
    *
    * @return an Error + S3Object, with a Right(s3Obj) with the S3 path of the generated fastaa if everything worked as expected or with a Left(error) if an error occurred. Several things could go wrong in this process; namely:
    *   - The object [[data.sequences]] exists for the `version`, or the
    *   request to check its existence finished with errors.
    *   - The local directory could not be created or accessed
    *   - The input files from `db.rnacentral` could not be downloaded
    *   - The generation of the sequences failed
    *   - The upload process failed, either because you have no permissions to
    *   upload the objects or because some error occured during the upload
    *   itself.
    */
  def generateNewDB(
      version: Version,
      localFolder: File
  ): Error + S3Object =
    s3Helpers.objectExists(data.sequences(version)).flatMap { doesItExist =>
      if (doesItExist)
        Left(Error.S3ObjectExists(data.sequences(version)))
      else
        generateDB(version, localFolder)
    }
}
