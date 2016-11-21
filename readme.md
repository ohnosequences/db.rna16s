# db.rna16s

[![](https://travis-ci.org/ohnosequences/db.rna16s.svg?branch=master)](https://travis-ci.org/ohnosequences/db.rna16s)
[![](https://img.shields.io/codacy/62caae6ae58f48dca6633f2f88ed8898.svg)](https://www.codacy.com/app/ohnosequences/db-rna16s)
[![](http://github-release-version.herokuapp.com/github/ohnosequences/db.rna16s/release.svg)](https://github.com/ohnosequences/db.rna16s/releases/latest)
[![](https://img.shields.io/badge/license-AGPLv3-blue.svg)](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-%28agpl-3.0%29)
[![](https://img.shields.io/badge/contact-gitter_chat-dd1054.svg)](https://gitter.im/ohnosequences/db.rna16s)

A comprehensive, compact, and automatically curated microbial 16S database, where each sequence is assigned to a set of NCBI taxonomy IDs.

## Data sources

All data comes from the latest [RNACentral][RNACentral] release, the most comprehensive RNA sequence resource: **all** RNA sequences from *ENA*, *GreenGenes*, *RDP*, *RefSeq* or *SILVA*, among others, are included (see [here][RNACentral data sources] for the full list).

## Database generation and curation

We can divide this into three steps:

1. **Pick 16S candidate sequences**: take all sequences which contain the full sequence of a 16S gene
2. **Drop redundant assignments and sequences**: if a sequence `S` has an assignment to taxon `A`, and a sequence `s` which is a subsequence of `S` has the same assignment, we drop this assignment from `s`; sequences with no assignments left are dropped
3. **Cluster sequences**: run MG7 with both input and reference the output of the step 2 and split all sequences on equivalence classes based on their BLAST-similarity
4. **Drop inconsistent assignments**: filter out those assignments from the step 2 that appear to be outliers in the corresponding similarity-clusters from the step 3

For more details read the corresponding [code documentation](docs/src/test/scala/).

## Database files and formats

The output of each step is in S3, at `s3://resources.ohnosequences.com/ohnosequences/db.rna16s/<version>/<step>/`. This folder has the following structure:


``` shell
<step>/
├── blastdb/                      # BLAST db files
│   └── ohnosequences.db.rna16s.fasta.*
├── output/
│   ├── <step>.csv                # assignments
│   └── <step>.fasta              # sequences
└── summary/
    └── <step>.csv                # summary accepted/rejected taxas
```


All csv files are *comma-separated* files with *Unix line endings* `\n`. Their structure:

1. **Assignments** `output/<step>.csv` has **2 columns**:
    1. Extended sequence ID, with format `gnl|ohnosequences.db.rna16s|<RNAcentral_ID>`
    2. List of taxonomic assignments IDs, separated by `;`

    Sample row:
    ``` csv
    gnl|ohnosequences.db.rna16s|URS0123213, 1234;45123;43131
    ```
2. **Summary** `summary/<step>.csv` has **3 columns**:
    1. Extended sequence ID, with format `gnl|ohnosequences.db.rna16s|<RNAcentral_ID>`
    2. List of taxonomic assignments IDs, separated by `;`
    3. List of taxonomic assignments IDs **dropped** by this step, separated by `;`

    Sample row:
    ``` csv
    gnl|ohnosequences.db.rna16s|URS0123213, 1234;3424, 45123;43131
    ```

## Usage

You can of course just download the data from S3; if you want to use it from Scala code, or with [MG7][MG7], there are some helpers available:

### Scala

Add this library as a dependency in your `build.sbt`:

```scala
libraryDependencies += "ohnosequences" %% "db-rna16s" % "<version>"
```

Then in the code you can refer to various constants from the `ohnosequences.db.rna16s` namespace. The most useful are defined as shortcuts in the `data` object:

- `data.fastaS3` is the S3 address of FASTA file with the database sequences
- `data.id2taxasS3` is the S3 address of the assignments table
- `data.blastDBS3` is the S3 address of the BLAST DB folder (S3 prefix)

You can then use any S3 API to get these files and do whatever you feel like with them.

### MG7

You can directly use `db.rna16s` as a reference database with [MG7][MG7]. For that, first define a reference database bundle object:

``` scala
import ohnosequences.mg7._
import era7bio.db.rna16s

case object rna16sRefDB extends ReferenceDB(
  rna16s.dbName,
  rna16s.data.blastDBS3,
  rna16s.data.id2taxasS3
)
```

Then you can use it in your `MG7Parameters` configuration as one of the `referenceDBs`.

[RNACentral]: https://rnacentral.org
[RNACentral data sources]: https://rnacentral.org/expert-databases
[MG7]: https://github.com/ohnosequences/mg7

## License

The *database* **db.rna16s** is made available under the Open Database License: http://opendatacommons.org/licenses/odbl/1.0/. The **contents** are available under its respective licenses. As far as we can tell all data included in **db.rna16s** could be considered free for any use; do note that sequences and annotations coming from SILVA, which has a restrictive license, are excluded from db.rna16s.

See the [open data commons FAQ](http://opendatacommons.org/faq/licenses/#db-versus-contents) for more on this distinction between database and contents.
