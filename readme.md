# 16S RNA Reference Database

<!-- [![](https://travis-ci.org/era7bio/db.rna16s.svg?branch=master)](https://travis-ci.org/era7bio/db.rna16s) -->
<!-- [![](https://img.shields.io/codacy/???.svg)](https://www.codacy.com/app/era7/db.rna16s) -->
[![](http://github-release-version.herokuapp.com/github/era7bio/db.rna16s/release.svg)](https://github.com/era7bio/db.rna16s/releases/latest)
[![](https://img.shields.io/badge/license-AGPLv3-blue.svg)](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-%28agpl-3.0%29)
[![](https://img.shields.io/badge/contact-gitter_chat-dd1054.svg)](https://gitter.im/era7bio/db.rna16s)

This is a reference database of 16S sequences based on the data from [RNAcentral](http://rnacentral.org/).

We have developed various criteria and tuned them during long testing and manual review process to filter over 9 million sequences of the original RNAcentral data and retrieve only around *262 000* most representative and informative sequences. The source code in this repository allows us to make generation process of this database easily reproducible for any new release of RNAcentral.


## General description of the filters

Here goes an overview of the general filtering routine. For more details, check the code [documentation](docs/src/main/scala/).


## Usage

### Released data in S3

The output of each release is stored in an S3 folder of the following format:

```
s3://resources.ohnosequences.com/<organization>/<artifact_name>/<release_version>/
```

Where these parameters values can be found in [`build.sbt`](build.sbt). They may change, but at the moment this address would look like `s3://resources.ohnosequences.com/era7bio/db.rna16s/0.9.0/`.

Inside of this folder there are various subfolders, such as `filter1/`, `filter2/`, etc. Each of them has the same structure:

```
filterN/
├── blastdb/                       # BLAST DB files
│   └── era7bio.db.rna16s.fasta.*
├── output/
│   ├── filterN.csv                # assignments table
│   └── filterN.fasta              # corresponding FASTA
└── summary/
    └── filterN.csv                # summary of accepted/rejected taxas
```

This schema may change with time, so it's always better to retrieve these paths from the code of the library.


### In-code usage

First add this library as a dependency in your `build.sbt`:

```scala
libraryDependencies += "era7bio" %% "db-rna16s" % "<latest_version>"
```

Then in the code you can refer to various constants in the `era7bio.db.rna16s` namespace. The most useful are defined as shortcuts in the `release` object:

- `release.fastaS3` is the S3 address of FASTA file with the database sequences
- `release.id2taxasS3` is the S3 address of the assignments table
- `release.blastDBS3` is the S3 address of the BLAST DB folder (S3 prefix)


#### MG7 Reference Database

To use it in [MG7](https://github.com/ohnosequences/mg7), first define a reference database bundle object:

```scala
import ohnosequences.mg7._
import era7bio.db.rna16s

case object rna16sRefDB extends ReferenceDB(
  rna16s.dbName,
  rna16s.release.blastDBS3,
  rna16s.release.id2taxasS3
)
```

Then you can use it in your `MG7Parameters` configuration as one of the `referenceDBs`.


<!-- ## Maintenance -->
