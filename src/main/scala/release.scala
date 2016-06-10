package era7bio.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  val fastaS3:    S3Object = filter3.output.fasta.s3
  val id2taxasS3: S3Object = filter3.output.table.s3
  val blastDBS3:  S3Folder = filter3AndGenerate.s3
}
