package ohnosequences.db.rna16s.test

import ohnosequences.test._
import ohnosequences.api.rnacentral._

class DataGeneration extends org.scalatest.FunSuite {

  test("process all entries", ReleaseOnlyTest) {

    outFASTA.delete

    iterators
      .right(allEntries map rna16sIdentification.is16s)
      .map(fastaFormat.entryToFASTA)
      .appendTo(outFASTA)
  }
}
