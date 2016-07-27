# 16S RNA Reference Database

[![](https://travis-ci.org/ohnosequences/db.rna16s.svg?branch=master)](https://travis-ci.org/ohnosequences/db.rna16s)
[![](https://img.shields.io/codacy/62caae6ae58f48dca6633f2f88ed8898.svg)](https://www.codacy.com/app/era7/db.rna16s)
[![](http://github-release-version.herokuapp.com/github/ohnosequences/db.rna16s/release.svg)](https://github.com/ohnosequences/db.rna16s/releases/latest)
[![](https://img.shields.io/badge/license-AGPLv3-blue.svg)](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-%28agpl-3.0%29)
[![](https://img.shields.io/badge/contact-gitter_chat-dd1054.svg)](https://gitter.im/ohnosequences/db.rna16s)

This is a reference database of 16S sequences based on the data from [RNAcentral release 5](http://blog.rnacentral.org/2016/03/rnacentral-release-5.html).

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


## Usage

### Released data in S3

The output of each release is stored in an S3 folder of the following format:

```
s3://resources.ohnosequences.com/<organization>/<artifact_name>/<release_version>/
```

Where parameters values can be found in [`build.sbt`](build.sbt). They may change, but at the moment this address looks like `s3://resources.ohnosequences.com/era7bio/db.rna16s/0.9.0/`.

Inside of this folder there are various subfolders, such as `pick16SCandidates/`, `dropRedundantAssignments/`, etc. Each of them has the same structure:

```shell
filterName/
├── blastdb/                       # BLAST DB files
│   └── era7bio.db.rna16s.fasta.*
├── output/
│   ├── filterName.csv                # assignments table
│   └── filterName.fasta              # corresponding FASTA
└── summary/
    └── filterName.csv                # summary of accepted/rejected taxas
```

This schema may change with time, so it's always better to retrieve these paths from the code of the library.


#### Output tables format

* *Assignments table* `output/filterName.csv` has 2 column format:
  - Extended sequence ID: `<RNAcentral_ID>|lcl|<rna16s_DB_name>`
  - List of assigned taxonomic IDs (separated with `;`)

* *Summary table* `summary/finterN.csv` is similar but has 3 columns:
  - Extended sequence ID
  - Accepted taxonomic IDs list
  - Rejected taxonomic IDs list

All produced tables have CSV format (comma separated values) with Unix line endings (`\n`).


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


## Maintenance

Here is the sequence of bundles you have to launch to repeat the whole DB generation process.

* First of all you need to publish an fat-jar artifact with `sbt publish`
* Then launch `sbt test:console` and run commands in it
* For each bundle you can choose EC2 instance type in [`src/test/scala/runBundles.scala`](src/test/scala/runBundles.scala)

To make it a bit shorter I assume that you first do `import era7bio.db._` in the `test:console`

1. `pick16SCandidates`
    - Recommended EC2 instance type: `r3.x2large`, it has 60GB RAM
    - Approximate run time: several hours
    - Command: `test.rna16s.launch(rna16s.compats.pick16SCandidates, your_aws_user)`.  
      It returns you the instance ID. You have to terminate it **manually**.

2. `dropRedundantAssignments`
    - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
    - Approximate run time: 10-20 minutes
    - Command: `test.rna16s.launch(rna16s.compats.dropRedundantAssignmentsAndGenerate, your_aws_user)`.  
      It returns you the instance ID. You have to terminate it **manually**.

3. MG7 + `dropInconsistentAssignments`

   * 3.1. First you need to run all the steps of the MG7 pipeline (one after another, not all at once):

      ```scala
      > rna16s.referenceDBPipeline.split.deploy(your_aws_user)
      > rna16s.referenceDBPipeline.blastLoquat.deploy(your_aws_user)
      > rna16s.referenceDBPipeline.assignLoquat.deploy(your_aws_user)
      > rna16s.referenceDBPipeline.mergeLoquat.deploy(your_aws_user)
      ```

      Each loquat will offer you to subscribe to the notifications and tell you when it finished.

   * 3.2. Then run `dropInconsistentAssignments`:
      - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
      - Approximate run time: 10-20 minutes
      - Command: `test.rna16s.launch(rna16s.compats.filter3AndGenerate, your_aws_user)`.  
        It returns you the instance ID. You have to terminate it **manually**.
