package ohnosequences.db

import ohnosequences.awstools.s3._

package object rna16s {

  type +[A, B] = Either[A, B]

  implicit class PredicateOps[X](val p: X => Boolean) {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }

  val dbName: String =
    "ohnosequences.db.rna16s"

  val asPath: String => String =
    _.replace('.', '/')

  // val metadata: ohnosequences.statika.AnyArtifactMetadata =
  //   ohnosequences.db.generated.metadata.rna16s

  val s3prefix: S3Folder =
    s3"resources.ohnosequences.com" /
      asPath(dbName) /
      "argh" /
  // metadata.version /
}
