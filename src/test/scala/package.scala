package ohnosequences.db.rna16s

package object test {

  type +[A, B] =
    Either[A, B]

  implicit final class PredicateOps[X](val p: X => Boolean) extends AnyVal {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }
}
