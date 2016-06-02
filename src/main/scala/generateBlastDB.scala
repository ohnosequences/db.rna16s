package era7bio.db.rna16s


case object generate extends era7bio.db.GenerateBlastDB(
  dbType = era7bio.db.rna16s.dbType,
  dbName = era7bio.db.rna16s.dbName,
  sourceFastaS3 = filter2.accepted.fasta.s3,
  outputS3Prefix = era7bio.db.rna16s.s3prefix / "blastdb" /
)()
