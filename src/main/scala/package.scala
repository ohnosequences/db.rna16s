package ohnosequences.db

import ohnosequences.blast.api.BlastDBType
import ohnosequences.awstools.s3._

/*
  ## 16S RNA BLAST database

  This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.
*/
package object rna16s {

  val dbName = "ohnosequences.db.rna16s"
  val dbType = BlastDBType.nucl

  private val metadata = ohnosequences.generated.metadata.db_rna16s

  val s3prefix: S3Folder = S3Folder("resources.ohnosequences.com", metadata.organization) /
    metadata.artifact /
    metadata.version /
}
