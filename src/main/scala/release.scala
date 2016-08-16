package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

case object data {

  lazy val fastaS3:    S3Object = s3prefix/ "release" / s"${dbName}.fasta"
  lazy val id2taxasS3: S3Object = s3prefix/ "release" / s"${dbName}.csv"
  lazy val blastDBS3:  S3Folder = s3prefix/ "release" / "blastdb" /
}
