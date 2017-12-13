package ohnosequences.db

import ohnosequences.awstools.s3._

package object rna16s {

  val dbName: String = 
  "ohnosequences.db.rna16s"

  val asPath: String => String =
    _.replace('.', '/')

  val metadata: ohnosequences.statika.AnyArtifactMetadata = 
    generated.metadata.rna16s

  val s3prefix: S3Folder =
    s3"resources.ohnosequences.com" /
      asPath(dbName)                /
      metadata.version              /
}
