# 16S RNA Reference Database

<!-- [![](https://travis-ci.org/era7bio/db.rna16s.svg?branch=master)](https://travis-ci.org/era7bio/db.rna16s) -->
<!-- [![](https://img.shields.io/codacy/???.svg)](https://www.codacy.com/app/era7/db.rna16s) -->
[![](http://github-release-version.herokuapp.com/github/era7bio/db.rna16s/release.svg)](https://github.com/era7bio/db.rna16s/releases/latest)
[![](https://img.shields.io/badge/license-AGPLv3-blue.svg)](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-%28agpl-3.0%29)
[![](https://img.shields.io/badge/contact-gitter_chat-dd1054.svg)](https://gitter.im/era7bio/db.rna16s)

This is a reference database of 16S sequences based on the data from [RNAcentral release 5](http://blog.rnacentral.org/2016/03/rnacentral-release-5.html).

We have developed various criteria and tuned them during long testing and manual review process to filter over *9 million sequences* of RNAcentral and retrieve only around **262 000** most representative and informative sequences. The source code in this repository allows us to make generation process of this database easily reproducible for any new release of RNAcentral.


## Filtering routine overview

Here goes an overview of the general filtering routine. For more details, check the code [documentation](docs/src/main/scala/). Filtering is split in several steps:

- `filter1`: filters out unrelated and uninformative *sequences*
- `filter2`: filters out uninformative *assignments*
- `filter3`: filters out *wrong* assignments

Some more details about each step.

### `filter1`

This is an important filter, because out of the huge RNAcentral database it chooses only 16S sequences and filters out those that are badly classified or are not informative.
To pass this filter sequences have to satisfy the following predicate:

- the sequence ID is **not** in `blacklistedRNACentralIDs`
- the corresponding sequence is longer than `minimum16SLength`
- for each taxonomy association separately:
  + annotated as an `rRNA`
  + **not** one of those in `uninformativeTaxIDs`
  + a descendant of either *Archaea* or *Bacteria*
  + **not** a descendant of an *environmental* or *unclassified* taxon

You can lookup the mentioned constants in the [`filter1`](docs/src/main/scala/filter1.scala.md) code.

Only **~3.9%** of all RNAcentral sequences pass this filter.


### `filter2`

This step filters out assignments that are covered by bigger sequences. For example, if a sequence `S` has an assignment to taxon `A`, and a sequence `s` which is a subsequence of `S` has the same assignment, it gets discarded for `s`. If this was the only assignment for `s`, then the sequence gets discarded.

A match with a bigger sequence is always better, but if we won't discard those subsequence assignments they will be always implied, which doesn't add information for the analysis. So this filter allows to reduce the size of the database while leaving only most informative assignments.

After this step a BLAST database is generated from the sequences that passed the filter.

Around **72%** of the sequences from `filter1` pass `filter3`. Among these sequences there are also some with a reduced number of assignments.


### `filter3`

This step actually consists of two:

- Run MG7 pipeline using the reference database from `filter2` using its output sequences FASTA as input data. This will produce a table which matches each sequence the lowest common ancestor (LCA) of the assignments of any other (very) similar sequences.

- For each sequence we check the relation of its assignments with the corresponding LCA that we've got from MG7. If some assignment is too far away from the LCA in the taxonomic tree, it is discarded.  
  If a sequence has a single assignment, it is ignored, because it may represent a rare organism and it may be misplaced in the taxonomic tree.

After this step BLAST database is generated again.

Over **99.8%** of the sequences from `filter2` pass `filter2`, because it's mostly about reducing the number of assignments and there are not many sequences that get all assignments discarded.

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


## Usage

### Released data in S3

The output of each release is stored in an S3 folder of the following format:

```
s3://resources.ohnosequences.com/<organization>/<artifact_name>/<release_version>/
```

Where parameters values can be found in [`build.sbt`](build.sbt). They may change, but at the moment this address looks like `s3://resources.ohnosequences.com/era7bio/db.rna16s/0.9.0/`.

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
