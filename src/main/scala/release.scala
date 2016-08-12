package ohnosequences.db.rna16s

import ohnosequences.awstools.s3._

case object data {

  lazy val metadata = ohnosequences.generated.metadata.db_rna16s

  lazy val prefix: S3Folder =
    S3Folder("resources.ohnosequences.com", metadata.organization)/metadata.artifact/metadata.version/

  // TODO these should have a value!
  lazy val fastaS3:    S3Object = prefix/ ???     // dropInconsistentAssignments.output.fasta.s3
  lazy val id2taxasS3: S3Object = prefix/ ???     // dropInconsistentAssignments.output.table.s3
  lazy val blastDBS3:  S3Folder = prefix/ ??? /   // dropInconsistentAssignmentsAndGenerate.s3
}
