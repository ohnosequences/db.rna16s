package ohnosequences.db.rna16s

import java.io.File

case object output {

  def sequences(version: Version, localFolder: File): File =
    new File(localFolder, s"${version.toString}/db.rna16.fa")
}
