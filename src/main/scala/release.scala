package era7bio.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  val id2taxasS3: S3Object = filter2.accepted.table.s3
  val blastDBS3:  S3Folder = generate.outputS3Prefix
}
