package ohnosequences.db.rna16s

import ohnosequences.db.rnacentral
import ohnosequences.s3._
import ohnosequences.files.digest.DigestFunction
import ohnosequences.files.File

sealed abstract class Version(val name: String) {
  val inputVersion: RNACentralVersion

  override final def toString: String = name
}

object Version {

  lazy val all: Set[Version] =
    Set(v9_0, v10_0)

  case object v10_0 extends Version("10.0") {
    val inputVersion = rnacentral.Version._10_0
  }
  type v10_0 = v10_0.type

  case object v9_0 extends Version("9.0") {
    val inputVersion = rnacentral.Version._9_0
  }
  type v9_0 = v9_0.type
}

case object data {

  /**
    * Local files used when downloading input data from `db.rnacentral`
    */
  case object local {

    def idMappingFile(localFolder: File): File =
      new File(localFolder, "id_mapping.tsv")

    def fastaFile(localFolder: File): File =
      new File(localFolder, "rnacentral_species_specific_ids.fasta")
  }

  /**
    * Generator of S3 objects in a directory parametrized by a version.
    *
    * @param version is the version of the rna16S data whose paths we want to
    * obtain.
    *
    * @return a function `String => S3Object` that, given the name of a file,
    * returns an S3 object in a fixed S3 directory, which is parametrized by the
    * version passed.
    */
  def s3Prefix(version: Version): String => S3Object =
    file =>
      s3"resources.ohnosequences.com" /
        "ohnosequences" /
        "db" /
        "rna16s" /
        "unstable" /
        version.toString /
      file

  /**
    * Return the path of the S3 object containing the Rna16S sequences
    * corresponding to the version passed.
    */
  val sequences: Version => S3Object =
    s3Prefix(_)("rna16s.fa")

  /**
    * Return the path of the S3 object containing the mappings of RNAIDs
    * to taxon IDs sequences, corresponding to the version passed.
    */
  val mappings: Version => S3Object =
    s3Prefix(_)("mappings.fa")

  /**
    * Return all the objects that are stored for a version of the database
    */
  val everything: Version => Set[S3Object] =
    version => Set(sequences(version), mappings(version))

  /**
    * The function used to hash the content of the file that is uploaded to S3
    */
  val hashingFunction: DigestFunction = DigestFunction.SHA512
}
