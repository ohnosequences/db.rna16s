package era7bio.db

import ohnosequences.blast.api.BlastDBType
import ohnosequences.awstools.s3._


/*
  ## 16S RNA BLAST database

  This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.
*/
package object rna16s {

  val dbName = "era7bio.db.rna16s"

  val dbType = BlastDBType.nucl

  val s3prefix: S3Folder = S3Folder("resources.ohnosequences.com", generated.metadata.db.rna16s.organization) /
    generated.metadata.db.rna16s.artifact /
    generated.metadata.db.rna16s.version.stripSuffix("-SNAPSHOT") /

  val blastDBS3: S3Folder  = rna16s.generate.outputS3Prefix
  val id2taxasS3: S3Object = rna16s.filter2.accepted.table.s3
}
