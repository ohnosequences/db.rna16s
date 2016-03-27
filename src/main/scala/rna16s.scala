package era7bio.db.rna16s

import ohnosequences.cosas._, types._, klists._
import better.files._

case object blastUtils {

  import ohnosequences.blast.api._

  def makeblastdbCmdFor(fastaFile: File): Seq[String] =
    makeblastdb(
      argumentValues =
        in(fastaFile)                 ::
        input_type(DBInputType.fasta) ::
        dbtype(BlastDBType.nucl)      ::
        *[AnyDenotation],
      optionValues = makeblastdb.defaults.update(
          title(fastaFile.name) :: *[AnyDenotation]
        ).value
    )
    .toSeq
}


case object generateBlastDB extends Bundle()
