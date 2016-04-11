package era7bio.db.test

import era7bio.db.rna16sDBRelease._
import org.scalatest.FunSuite
import better.files._


class Dbrna16sTest extends FunSuite {

  test("Process some sample files") {

    generateRna16sDB.processSources(
      file"source.table.sample.tsv",
      file"output.table.sample.tsv".clear()
    )(file"source.sample.fasta",
      file"output.sample.fasta".clear()
    )
  }
}
