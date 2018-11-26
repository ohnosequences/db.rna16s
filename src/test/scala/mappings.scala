package ohnosequences.db.rna16s.test

import org.scalatest.FunSuite
import ohnosequences.files.read
import ohnosequences.faster.FASTA
import ohnosequences.db.rna16s, rna16s.Version
import org.scalatest.EitherValues._

class Mappings extends FunSuite {

  test("There is a valid mapping to taxon ID for each sequence in the database") {
    Version.all foreach { version =>
      val sequencesFile = data.sequences(version).right.value
      val mappingsFile  = data.mappings(version).right.value

      read.withLines(sequencesFile) { lines =>
        val fasta = FASTA.parse(lines.buffered)

        val mappings = read
          .withLines(mappingsFile)(rna16s.io.deserializeMappings)
          .right
          .value

        fasta.foreach { entry =>
          val id = entry.header.id
          assert(mappings.isDefinedAt(id), s"$id has no mapping to taxon ID")
        }
      }
    }
  }
}
