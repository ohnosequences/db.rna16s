package ohnosequences.db

import ohnosequences.awstools.s3._

package object rna16s {

  val version: String =
    "9.0"

  val s3Prefix: S3Folder =
    s3"resources.ohnosequences.com" /
      "db" /
      "rna16s" /
      version /

  val sequences: S3Object =
    s3Prefix / "rna16s.fa"

  val fullDB: S3Object =
    s3Prefix / "rna16s.csv"
}
