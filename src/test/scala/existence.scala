package ohnosequences.db.rna16s.test

import org.scalatest.FunSuite
import ohnosequences.db.rna16s, rna16s.{Version, helpers}
import org.scalatest.EitherValues._

class Existence extends FunSuite {

  test("All supported versions exist") {
    Version.all foreach { v =>
      val obj = rna16s.data.sequences(v)
      assert(
        helpers.objectExists(obj).right.value,
        s"- Version $v is not complete: object $obj does not exist."
      )
    }
  }
}
