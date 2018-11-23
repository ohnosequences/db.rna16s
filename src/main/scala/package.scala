package ohnosequences.db

package object rna16s {

  type RNACentralVersion = ohnosequences.db.rnacentral.Version
  type RNAID             = ohnosequences.db.rnacentral.RNAID
  type +[A, B]           = Either[A, B]
  type TaxID             = Int
  type Mappings          = Map[RNAID, Set[TaxID]]

  implicit final class PredicateOps[X](val p: X => Boolean) extends AnyVal {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }
}
