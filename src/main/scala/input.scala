package ohnosequences.db.rna16s

import java.io.File
import ohnosequences.db.rnacentral.{Entry, RNACentralData, entries, iterators}

case object input {

  def rnaCentralData(version: Version, localFolder: File): RNACentralData =
    RNACentralData(
      speciesSpecificFasta = data.local.fastaFile(version, localFolder),
      idMapping = data.local.idMappingFile(version, localFolder)
    )

  def rnaCentralEntries(
      version: Version,
      localFolder: File
  ): Iterator[Entry] = {
    val (malformedRows, entriesIterator) =
      entries.entriesFrom(rnaCentralData(version, localFolder))
    iterators right entriesIterator
  }
}
