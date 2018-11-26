package ohnosequences.db.rna16s

import ohnosequences.db.rnacentral
import rnacentral.{Database, DatabaseEntry, IDMapping, RNACentralData, RNAType}
import ohnosequences.s3.S3Object
import ohnosequences.files.{directory, write}
import java.io.File
import scala.collection.mutable.{Map => MutableMap}
import scala.util.{Failure, Success, Try}
import helpers._

case object release {

  /** Processes all entries provided by the `entries` iterator and extract on
    * a fasta file which has an RNAID + DNA sequence
    *
    * The mappings file gives for an RNA identifier the taxon ids that are
    * associated with it.
    *
    * @param entries an iterator over RNACentral entries
    * @param file where the sequences database is going to
    * be stored
    *
    * Returns `Right(file)` if both the process and the file save succeeded,
    * `Left(Error.FailedGeneration)` otherwise.
    */
  private def generateSequences(
      entries: Iterator[rnacentral.Entry],
      file: File
  ): Error + File =
    Try {
      rnacentral.iterators
        .right(entries map rna16sIdentification.is16s)
        .map(io.entryToFASTA)
        .appendTo(file)
    } match {
      case Failure(e) => Left(Error.FailedGeneration(e.toString))
      case Success(s) => Right(file)
    }

  /** Write a mappings file whose output is somewhat like:
    *   RNAID¹†TaxID¹,TaxID², TaxID³,...
    *   RNAID²†TaxID'¹,TaxID'², TaxID'³,...
    *   ...
    * The mappings file gives for an RNA identifier the taxon ids that are
    * associated with it.
    *
    * @param rnaCentralData the `RNACentralData` for which we want to extract
    * the mappings
    * @param file the file where we want to write the mapping
    * RNAID -> [TaxID]
    *
    * @return `Right(files)` if the reading of the corresponding `.tsv` file and
    * the writing of the result file are correct, `Left(error)` otherwise
    */
  /*private*/
  def generateMappings(rnaCentralData: RNACentralData,
                       file: File): Error + File = {
    val rnaType16s = RNAType.rRNA

    val databases = rna16sIdentification.database.includedDatabases

    val databaseEntryFrom: (String, String) => Option[DatabaseEntry] = {
      case (dbName, id) =>
        (Database from dbName) map { DatabaseEntry(_, id) }
    }

    val mappings = MutableMap[RNAID, Set[TaxID]]()

    // Filter those rows of mappings file coming from an
    // accepted database and whose data has RNA16s type
    Try {
      rnacentral.iterators
        .right(
          IDMapping.rows(rnaCentralData)
        )
        .collect {
          case (id, dbName, dbID, taxID, rnaType, geneName)
              if (
                databaseEntryFrom(dbName, dbID).fold(false) { db =>
                  databases contains db.database
                }
                  &&
                    RNAType.from(rnaType).fold(false) { _ == rnaType16s }
              ) =>
            (id, taxID)
        }
        .foreach {
          case (id, taxID) =>
            if (mappings.isDefinedAt(id))
              mappings(id) += taxID
            else
              mappings(id) = Set(taxID)
        }
    } match {
      case Failure(e) => Left(Error.FailedGeneration(e.toString))
      case Success(s) =>
        write
          .linesToFile(file)(io.serializeMappings(mappings.toMap))
          .left
          .map(Error.FileError)
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
    val rnaCentralData         = RNACentralData(idMappingsFile, fastaFile)
    lazy val rnaCentralEntries = input.rnaCentralEntries(localFolder)
    val sequencesFile          = output.sequences(localFolder)
    val mappingsFile           = output.mappings(localFolder)

    for {
      _ <- directory.createDirectory(localFolder).left.map(Error.FileError)
      _ <- getCheckedFileIfDifferent(inputFasta, fastaFile)
      _ <- getCheckedFileIfDifferent(inputIdMapping, idMappingsFile)
      _ <- generateSequences(rnaCentralEntries, sequencesFile)
      _ <- generateMappings(rnaCentralData, mappingsFile)
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
