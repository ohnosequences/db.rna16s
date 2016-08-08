package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

case object release {

  val fastaS3:    S3Object = dropInconsistentAssignments.output.fasta.s3
  val id2taxasS3: S3Object = dropInconsistentAssignments.output.table.s3
  val blastDBS3:  S3Folder = dropInconsistentAssignmentsAndGenerate.s3
}
