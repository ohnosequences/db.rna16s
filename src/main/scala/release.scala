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
    * Returns `Some(file)` if both the process and the file save succeeded,
    * `None` otherwise.
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
    * Perform the actual mirror of RNACentral, overwriting if necessary
    *
    * For [[data.input.idMappingTSVGZURL]], [[data.idMappingTSV]] is uploaded to
    * S3. For [[data.input.speciesSpecificFASTAGZURL]],
    * [[data.speciesSpecificFASTA]] is uploaded to S3.
    *
    * The process to mirror each of those files is:
    *   1. Download the `.gz` file from [[data.input.releaseURL]]
    *   2. Uncompress to obtain the file
    *   4. Upload the file ([[data.input.idMappingTSV]] and
    *   [[data.input.speciesSpecificFASTA]] resp.) to the folder [[data.prefix]].
    *
    * @return an Error + Set[S3Object], with a Right(set) with all the mirrored
    * S3 objects if everything worked as expected or with a Left(error) if an
    * error occurred. Several things could go wrong in this process; namely:
    *   - The input files could not be downloaded
    *   - The input files could not be uncompressed
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

    for {
      _ <- directory.createDirectory(localFolder).left.map(Error.FileError)
      _ <- getCheckedFile(inputFasta, fastaFile)
      _ <- getCheckedFile(inputIdMapping, mappingsFile)
      _ <- generateSequences(rnaCentralEntries, output.sequences)
      _ <- paranoidPutFile(output.sequences, s3Sequences)
    } yield {
      s3Sequences
    }
  }

  /**
    * Try to mirror a new version of RNACentral to S3.
    *
    * This method tries to download [[data.input.releaseURL]], uncompress it
    * and upload the corresponding files to the objects defined in
    * [[data.idMappingTSV]] and [[data.speciesSpecificFASTA]].
    *
    * It does so if and only if none of those two objects already exist in S3.
    * If any of them exists, nothing is downloaded nor uploaded and an error is
    * returned.
    *
    * @param version is the new version that wants to be released
    * @param localFolder is the localFolder where the downloaded files will be
    * stored.
    *
    * @return an Error + Set[S3Object], with a Right(set) with all the mirrored
    * S3 objects if everything worked as expected or with a Left(error) if an
    * error occurred. Several things could go wrong in this process; namely:
    *   - The objects already exist in S3
    *   - The input file could not be downloaded
    *   - The input file could not be uncompressed
    *   - The upload process failed, either because you have no permissions to
    *   upload to the objects under [[data.prefix]] or because some error
    *   occured during the upload itself.
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
