package ohnosequences.db.rna16s

import ohnosequences.db.rnacentral
import ohnosequences.s3.S3Object
import ohnosequences.files.{directory, write}
import java.io.File
import scala.collection.mutable.{Map => MutableMap}
import helpers._

case object release {

  /**
    * Processes all entries provided by the `entries` iterator and extract on
    * the one hand, a fasta file which has an RNAID + DNA sequence, and on the
    * other hand, a mappings file whose output is somewhat like:
    *   RNAID¹†TaxID¹,TaxID², TaxID³,...
    *   RNAID²†TaxID'¹,TaxID'², TaxID'³,...
    *   ...
    * The mappings file gives for an RNA identifier the taxon ids that are
    * associated with it.
    *
    * @param entries an iterator over RNACentral entries
    * @param sequencesFile the file where the sequences database is going to
    * be stored
    * @param mappingsFile the file where we want to write the mapping ç
    * RNAID -> [TaxID]
    *
    * @return `Right(files)` if both the processes and the file saves succeed,
    * where files is a tuple (sequencesFiles, mappingsFile).
    * `Left(error)` otherwise, where error can be due to a write failure
    * (Error.FileError) or due to a failed generation of the sequences file
    * (Error.FailedGeneration)
    */
  private def generateSequencesAndMappings(
      entries: Iterator[rnacentral.Entry],
      sequencesFile: File,
      mappingsFile: File
  ): Error + (File, File) = {
    type TaxID = Int

    val mappings = MutableMap[rnacentral.RNAID, Set[TaxID]]()

    scala.util.Try {
      // Write the sequences file
      rnacentral.iterators
        .right(entries map rna16sIdentification.is16s)
        /*
         This extracts the mapping RNAID -> [TaxIDs]
         and converts the entry to a FASTA entry
         */
        .map { entry =>
          entry.sequenceAnnotations.foreach { annotation =>
            val rnaID = annotation.rnaID
            val taxID = annotation.ncbiTaxonomyID

            if (mappings.isDefinedAt(rnaID))
              mappings(rnaID) += taxID
            else
              mappings(rnaID) = Set[TaxID](taxID)
          }

          io.entryToFASTA(entry)
        }
        .appendTo(sequencesFile)
    } match {
      case scala.util.Failure(e) => Left(Error.FailedGeneration(e.toString))
      case scala.util.Success(s) =>
        // Write the mappings file
        val mappingsWrite =
          write.linesToFile(mappingsFile)(io.serializeMappings(mappings.toMap))

        mappingsWrite.left.map(Error.FileError).right.map { _ =>
          (sequencesFile, mappingsFile)
        }
    }
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
    * @return an Error + S3Object, with a Right(s3Obj) with the S3 path of the
    * generated fasta if everything worked as expected or with a Left(error)
    * if an error occurred. Several things could go wrong in this process;
    * namely:
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
  ): Error + Set[S3Object] = {
    val rnacentralVersion = version.inputVersion

    val inputFasta     = rnacentral.data.speciesSpecificFASTA(rnacentralVersion)
    val inputIdMapping = rnacentral.data.idMappingTSV(rnacentralVersion)
    val s3Sequences    = data.sequences(version)
    val s3Mappings     = data.mappings(version)

    val idMappingsFile         = data.local.idMappingFile(localFolder)
    val fastaFile              = data.local.fastaFile(localFolder)
    lazy val rnaCentralEntries = input.rnaCentralEntries(localFolder)
    val sequencesFile          = output.sequences(localFolder)
    val mappingsFile           = output.mappings(localFolder)

    for {
      _ <- directory.createDirectory(localFolder).left.map(Error.FileError)
      _ <- getCheckedFileIfDifferent(inputFasta, fastaFile)
      _ <- getCheckedFileIfDifferent(inputIdMapping, idMappingsFile)
      _ <- generateSequencesAndMappings(rnaCentralEntries,
                                        sequencesFile,
                                        mappingsFile)
      _ <- paranoidPutFile(sequencesFile, s3Sequences)
      _ <- paranoidPutFile(mappingsFile, s3Mappings)
    } yield {
      Set(s3Sequences, s3Mappings)
    }
  }

  /**
    * Read the data from `db.rnacentral`, filter the 16S sequences and upload
    * the result to S3 if and only if the upload does not overwrite anything.
    *
    * For the RNACentral version associated to the version parameter, both the
    * ID Mappings and the sequences are downloaded, from which the 16S
    * sequences are filtered using [[rna16sIdentification.is16s]]. The result
    * of the filter is uploaded to the object returned by [[data.sequences]]
    * applied over the version parameter, if and only if the upload does not
    * overwrite anything.
    *
    * @return an Error + S3Object, with a Right(s3Obj) with the S3 path of the
    * generated fasta if everything worked as expected or with a Left(error) if
    * an error occurred. Several things could go wrong in this process; namely:
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
  ): Error + Set[S3Object] =
    findVersionInS3(version).fold(
      generateDB(version, localFolder)
    ) { obj =>
      Left(Error.S3ObjectExists(obj))
    }
}
