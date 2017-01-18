package ohnosequences.db

import ohnosequences.awstools.s3._

package object rna16s {

  val dbName = "ohnosequences.db.rna16s"

  private val metadata = generated.metadata.rna16s

  val s3prefix: S3Folder =
    s3"resources.ohnosequences.com" /
    metadata.organization /
    metadata.artifact /
    metadata.version /
}
