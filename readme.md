# db.rna16s

[![](https://travis-ci.org/ohnosequences/db.rna16s.svg?branch=master)](https://travis-ci.org/ohnosequences/db.rna16s)
[![](https://img.shields.io/codacy/62caae6ae58f48dca6633f2f88ed8898.svg)](https://www.codacy.com/app/era7/db.rna16s)
[![](http://github-release-version.herokuapp.com/github/ohnosequences/db.rna16s/release.svg)](https://github.com/ohnosequences/db.rna16s/releases/latest)
[![](https://img.shields.io/badge/license-AGPLv3-blue.svg)](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-%28agpl-3.0%29)
[![](https://img.shields.io/badge/contact-gitter_chat-dd1054.svg)](https://gitter.im/ohnosequences/db.rna16s)

A comprehensive, compact, and automatically curated 16S database, where each sequence is assigned to a set of NCBI taxonomy IDs.

## Data sources

All data comes from the latest [RNACentral][RNACentral] release, the most comprehensive RNA sequence resource: **all** RNA sequences from *ENA*, *GreenGenes*, *RDP*, *RefSeq* or *SILVA*, among others, are included (see [here][RNACentral data sources] for the full list).

## Database generation and curation

We can divide this into three steps:

1. **Pick 16S candidate sequences** take all sequences which contain the full sequence of a 16S gene
2. **Drop redundant assignments and sequences** if a sequence `S` has an assignment to taxon `A`, and a sequence `s` which is a subsequence of `S` has the same assignment, we drop this assignment from `s`; sequences with no assignments left are dropped
3. **Drop inconsistent assignments** run MG7 with both input and reference the output of 2, and drop the original assignments which are far from the one obtained through MG7

For more details read the corresponding [code documentation][code docs].

## Database files and formats

The output of each step is in S3, at `s3://resources.ohnosequences.com/ohnosequences/db.rna16s/<version>/<step>/`. This folder has the following structure:


``` shell
<step>/
├── blastdb/                       # BLAST db files
│   └── ohnosequences.db.rna16s.fasta.*
├── output/
│   ├── <step>.csv                # assignments
│   └── <step>.fasta              # sequences
└── summary/
    └── <step>.csv                # summary accepted/rejected taxas
```


All csv files are *comma-separated* files with *Unix line endings* `\n`. Their structure:

1. **Assignments** `output/<step>.csv` has **2 columns**:
    1. Extended sequence ID, with format `<RNAcentral_ID>|lcl|ohnosequences.db.rna16s`
    2. List of taxonomic assignments IDs, separated by `;`

    Sample row:
    ``` csv
    URS0123213|lcl|ohnosequences.db.rna16s, 1234;45123;43131
    ```
2. **Summary** `summary/<step>.csv` has **3 columns**:
    1. Extended sequence ID, with format `<RNAcentral_ID>|lcl|ohnosequences.db.rna16s`
    2. List of taxonomic assignments IDs, separated by `;`
    3. List of taxonomic assignments IDs **dropped** by this step, separated by `;`

    Sample row:
    ``` csv
    URS0123213|lcl|ohnosequences.db.rna16s, 1234;3424, 45123;43131
    ```

## Usage

You can of course just download the data from S3; if you want to use it from Scala code, or with [MG7][MG7], there are some helpers available:

### Scala

Add this library as a dependency in your `build.sbt`:

```scala
libraryDependencies += "ohnosequences" %% "db-rna16s" % "<version>"
```

Then in the code you can refer to various constants from the `ohnosequences.db.rna16s` namespace. The most useful are defined as shortcuts in the `release` object:

- `release.fastaS3` is the S3 address of FASTA file with the database sequences
- `release.id2taxasS3` is the S3 address of the assignments table
- `release.blastDBS3` is the S3 address of the BLAST DB folder (S3 prefix)

You can then use any S3 API to get these files and do whatever you feel like with them.

### MG7

You can directly use `db.rna16s` as a reference database with [MG7][MG7]. For that, first define a reference database bundle object:

``` scala
import ohnosequences.mg7._
import era7bio.db.rna16s

case object rna16sRefDB extends ReferenceDB(
  rna16s.dbName,
  rna16s.release.blastDBS3,
  rna16s.release.id2taxasS3
)
```

Then you can use it in your `MG7Parameters` configuration as one of the `referenceDBs`.

----

> **NOTE** everything below will go to code documentation

We have developed various criteria and tuned them during long testing and manual review process to filter over *9 million sequences* of RNAcentral and retrieve only around **262 000** most representative and informative sequences. The source code in this repository allows us to make generation process of this database easily reproducible for any new release of RNAcentral.

## Filtering routine overview

Here goes an overview of the general filtering routine. For more details, check the code [documentation](docs/src/main/scala/). Filtering is split in several steps:

- `pick16SCandidates`: filters out unrelated and uninformative *sequences*
- `dropRedundantAssignments`: filters out uninformative *assignments*
- `dropInconsistentAssignments`: filters out *wrong* assignments

Some more details about each step.

### `pick16SCandidates` filter

This is an important filter, because out of the huge RNAcentral database it chooses only 16S sequences and filters out those that are badly classified or are not informative.
To pass this filter sequences have to satisfy the following predicate:

- the sequence ID is **not** in `blacklistedRNACentralIDs`
- the corresponding sequence is longer than `minimum16SLength`
- for each taxonomy association separately:
  + annotated as an `rRNA`
  + **not** one of those in `uninformativeTaxIDs`
  + a descendant of either *Archaea* or *Bacteria*
  + **not** a descendant of an *environmental* or *unclassified* taxon

You can lookup the mentioned constants in the [`pick16SCandidates`](docs/src/main/scala/pick16SCandidates.scala.md) code.

Only **~3.9%** of all RNAcentral sequences pass this filter.


### `dropRedundantAssignments` filter

This step filters out assignments that are covered by bigger sequences. For example, if a sequence `S` has an assignment to taxon `A`, and a sequence `s` which is a subsequence of `S` has the same assignment, it gets discarded for `s`. If this was the only assignment for `s`, then the sequence gets discarded.

If a bigger sequence is matched, all its subsequences will be matched as well, but it won't add any information to the results. So this filter allows to reduce the size of the database while leaving only most informative assignments.

After this step a BLAST database is generated from the sequences that passed the filter.

Around **72%** of the sequences from `pick16SCandidates` pass `dropInconsistentAssignments`. Among these sequences there are also some with a reduced number of assignments.


### `dropInconsistentAssignments` filter

This step actually consists of two:

- Run MG7 pipeline using the reference database from `dropRedundantAssignments` using its output sequences FASTA as input data. This will produce a table which matches each sequence the lowest common ancestor (LCA) of the assignments of any other (very) similar sequences.

- For each sequence we check the relation of its assignments with the corresponding LCA that we've got from MG7. If some assignment is too far away from the LCA in the taxonomic tree, it is discarded.  

  If a sequence has only one (single) assignment, it is not tested with the described filter, because

  + it may represent a rare organism that doesn't necessarily have relations with its taxonomic neighbors
  + it can be an organism that was originally misclassified and put in the taxonomy far away from its real relatives (that could be discovered later, for example)

After this step BLAST database is generated again.

Over **99.8%** of the sequences from `dropRedundantAssignments` pass `dropInconsistentAssignments` filter, because it's mostly about reducing the number of assignments and there are not many sequences that get all assignments discarded.

#### Example of a wrong assignment

Let's consider the following fragment of the taxonomic tree:

```
tribeX
├─ genusABC
│  ├─ ...
│  ...
│  └─ subgenusABC
│     ├─ speciesA
│     │  ├─ subspeciesA1
│     │  └─ subspeciesA2
│     ├─ speciesB1
│     ├─ speciesB2
│     └─ speciesC
...
└─ ...
   └─ ...
      └─ speciesX
```

And a sequence with following taxonomic assignments:

| ID   | Taxas                                |
|:-----|:-------------------------------------|
| seqA | subspeciesA1; subspeciesA2; speciesX |

In this case `speciesX` is *likely* a wrong assignment, because it's completely *unrelated* with the other, more specific assignments. If we will leave it in the database, the LCA of these nodes will be `tribeX`, instead of `speciesA`.

So we run MG7, and BLAST tells us that `seqA` is *very* similar to `seqB` and `seqC` with the following assignments:

| ID   | Taxas                |
|:-----|:---------------------|
| seqB | speciesB1; speciesB2 |
| seqC | speciesC             |

We take their LCA which is `subgenusABC` and look at its parent: `genusABC`. Each of the `seqA`'s assignments has to be a descendant of `genusABC` and `speciesX`, obviously, is not, so we discard it:

| ID   | Taxas                      |
|:-----|:---------------------------|
| seqA | subspeciesA1; subspeciesA2 |

[RNACentral]: https://rnacentral.org
[RNACentral data sources]: https://rnacentral.org/expert-databases
[MG7]: https://github.com/ohnosequences/mg7
[code docs]: docs/src/main/scala/
