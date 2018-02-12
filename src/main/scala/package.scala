package ohnosequences.db

import ohnosequences.awstools.s3._

package object rna16s {

  type +[A, B] =
    Either[A, B]

  implicit final class PredicateOps[X](val p: X => Boolean) {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }

  val version: String =
    "7.0"

  val asPath: String => String =
    _.replace('.', '/')

  val s3prefix: S3Folder =
    s3"resources.ohnosequences.com" /
      "db" /
      "rna16s" /
      version /
}
