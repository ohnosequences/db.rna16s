package ohnosequences.db

import ohnosequences.db.rnacentral, rnacentral.RNAID

package object rna16s {

  type RNACentralVersion = rnacentral.Version
  type +[A, B]           = Either[A, B]
  type TaxID             = Int
  type Mappings          = Map[RNAID, Set[TaxID]]

  implicit final class PredicateOps[X](val p: X => Boolean) extends AnyVal {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }
}
