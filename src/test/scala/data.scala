package ohnosequences.db.rna16s.test

import ohnosequences.db.rna16s.{data => rna16sData, Version, output, Error}
import ohnosequences.db.rna16s.helpers.getCheckedFileIfDifferent
import ohnosequences.files.directory
import java.io.File

object data {

  def localFolder(version: Version): File =
    new File(s"./data/in/${version}/")

  private def getSequences(version: Version): Error + File = {
    val folder = localFolder(version)

    val sequences   = output.sequences(folder)
    val sequencesS3 = rna16sData.sequences(version)

    val maybeDir: Error + File =
      directory.createDirectory(folder).left.map(Error.FileError)

    maybeDir.flatMap(_ => getCheckedFileIfDifferent(sequencesS3, sequences))
  }

  lazy val sequencesv9_0  = getSequences(Version.v9_0)
  lazy val sequencesv10_0 = getSequences(Version.v10_0)

  def sequences(version: Version): Error + File =
    version match {
      case v: Version.v9_0  => sequencesv9_0
      case v: Version.v10_0 => sequencesv10_0
    }
}
