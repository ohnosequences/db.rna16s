package ohnosequences.db.rna16s

import java.io.File

case object output {

  def sequences(localFolder: File): File =
    new File(localFolder, "db.rna16.fa")

  def mappings(localFolder: File): File =
    new File(localFolder, "mappings")
}
