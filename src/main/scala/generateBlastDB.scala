package era7bio.db.rna16s

import era7bio.db._


case object generate extends GenerateBlastDB(
  dbType = rna16s.dbType,
  dbName = rna16s.dbName,
  sourceFastaS3 = filter2.acceptedS3Prefix / filter2.fastaName,
  outputS3Prefix = rna16s.s3prefix / "blastdb" /
)(
  deps = filter2
)
