package era7bio.db.rna16s

import era7bio.db._

case class generateFrom(filterBundle: FilterData) extends GenerateBlastDB(
  dbType = era7bio.db.rna16s.dbType,
  dbName = era7bio.db.rna16s.dbName,
  sourceFastaS3  = filterBundle.accepted.fasta.s3,
  outputS3Prefix = filterBundle.accepted.s3 / "blastdb" /
)(deps = filterBundle)
