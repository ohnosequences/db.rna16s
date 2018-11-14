package ohnosequences.db

package object rna16s {

  type RNACentralVersion = ohnosequences.db.rnacentral.Version
  type +[A, B]           = Either[A, B]

  implicit final class PredicateOps[X](val p: X => Boolean) extends AnyVal {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }
}
