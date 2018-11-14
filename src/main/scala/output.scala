package ohnosequences.db.rna16s

import java.io.File

case object output {

  def sequences(localFolder: File): File =
    new File(localFolder, s"db.rna16.fa")
}
