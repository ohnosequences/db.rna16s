import ohnosequences.db.rna16s, rna16s.{RNAID, TaxID}
import ohnosequences.db.rnacentral, rnacentral.{Database, DatabaseEntry}
import ohnosequences.db.rnacentral.{IDMapping, RNAType}
import java.io.File
import scala.collection.mutable.{Map => MutableMap, Set => MutableSet}
import ohnosequences.files.write

val version = rna16s.Version.v10_0
val localFolder = new File(s"./data/${version}")

val rnaCentralData = rnacentral.RNACentralData(
  new File(localFolder, "id_mapping.tsv"),
  new File(localFolder, "rnacentral_species_specific_ids.fasta")
)

val rnaType16s: RNAType = RNAType.rRNA

val databases = rna16s.rna16sIdentification.database.includedDatabases

val databaseEntryFrom: (String, String) => Option[DatabaseEntry] = {
  case (dbName, id) =>
    (Database from dbName) map { DatabaseEntry(_, id) }
}

val mappings = MutableMap[RNAID, MutableSet[TaxID]]()

val rows =
  rnacentral.iterators.right(
    IDMapping.rows(rnaCentralData)
  ).collect {
    case (id, dbName, dbID, taxID, rnaType, geneName) if (
      databaseEntryFrom(dbName, dbID).fold(false) { db =>
        databases contains db.database
      } &&
        RNAType.from(rnaType).fold(false) { _ == rnaType16s }
    ) =>
      (id, taxID)
  }.foreach {
    case (id, taxID) =>
      if (mappings.isDefinedAt(id))
        mappings(id) += taxID
      else
        mappings(id) = MutableSet(taxID)
  }

write.linesToFile(new File(localFolder, "mappings"))(rna16s.io.serializeMappings(mappings))




