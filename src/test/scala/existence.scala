package ohnosequences.db.rna16s.test

import org.scalatest.FunSuite
import ohnosequences.db.rna16s, rna16s.{Version, helpers}

class Existence extends FunSuite {

  test("All supported versions exist") {
    Version.all foreach { version =>
      rna16s.data.everything(version).map { obj =>
        assert(
          helpers.objectExists(obj),
          s"- Version $version is not complete: object $obj does not exist."
        )
      }
    }
  }
}
