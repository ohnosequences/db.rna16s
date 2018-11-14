package ohnosequences.db.rna16s

import java.io.File
import ohnosequences.db.rnacentral.{Entry, RNACentralData, entries, iterators}

case object input {

  def rnaCentralData(localFolder: File): RNACentralData =
    RNACentralData(
      speciesSpecificFasta = data.local.fastaFile(localFolder),
      idMapping = data.local.idMappingFile(localFolder)
    )

  def rnaCentralEntries(localFolder: File): Iterator[Entry] = {
    val (malformedRows, entriesIterator) =
      entries.entriesFrom(rnaCentralData(localFolder))
    iterators right entriesIterator
  }
}
