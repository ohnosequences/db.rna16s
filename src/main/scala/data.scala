package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

/**
  # S3 data paths

  These are the S3 locations of this release files:

  1. The FASTA file with the sequences
  2. the sequence ID to NCBI taxonomy mapping, as a csv with two columns
  3. The folder containing the BLAST database
*/
case object data {

  lazy val fastaS3:    S3Object = s3prefix/ "release" / s"${dbName}.fasta"
  lazy val id2taxasS3: S3Object = s3prefix/ "release" / s"${dbName}.csv"
  lazy val blastDBS3:  S3Folder = s3prefix/ "release" / "blastdb" /
}
