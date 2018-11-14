# db.rna16s

[![](https://travis-ci.org/ohnosequences/db.rna16s.svg)](https://travis-ci.org/ohnosequences/db.rna16s)
[![](http://github-release-version.herokuapp.com/github/ohnosequences/db.rna16s/release.svg)](https://github.com/ohnosequences/db.rna16s/releases/latest)
[![](https://img.shields.io/badge/license-AGPLv3-blue.svg)](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-%28agpl-3.0%29)
[![](https://img.shields.io/badge/license-ODbL-brightgreen.svg)](https://opendatacommons.org/licenses/odbl/)

`db.rna16s` is a curated database of 16S sequences, obtained directly form [db.rnacentral][db.rnacentral]. This package contains code to filter the data from RNACentral releases, as well as pointers to the location of the data.

For each supported version of [db.rnacentral][db.rnacentral], a single FASTA is available, containing a subset of the RNACentral sequences that are identified as 16S.

# How to access the data

## Versions

All the data in `db.rna16s` is versioned following the RNACentral releases number scheme.

Each of these versions is encoded as an object that extends the sealed class [`Version`](src/main/scala/data.scala#L15-L31).

The `Set` [`Version.all`](src/main/scala/data.scala#L17) contains all the releases supported and maintained through `db.rna16s`.

## Files

The module [`db.rna16s.data`](src/main/scala/data.scala#L33-L78) contains the pointers to the S3 objects where the actual data is stored. The path of the S3 object corresponding to the FASTA file can be accessed evaluating the following function over a `Version` object:

```scala
sequences : Version => S3Object
```

The path to the S3 object returned by that function look something like the following:

```
s3://resources.ohnosequences.com/ohnosequences/db/rna16s/<version>/rna16s.fa
```

## License

- The *code* which generates the database is licensed under the **[AGPLv3]** license
- The *database* itself is made available under the **[ODbLv1]** license.
- The database *contents* are available under their respective licenses. As far as we can tell all data included in *db.rna16s* could be considered **free** for any use; do note that sequences and annotations coming from SILVA, which has a restrictive license, are excluded from *db.rna16s*.

See the [open data commons FAQ](http://opendatacommons.org/faq/licenses/#db-versus-contents) for more on this distinction between database and contents.

[RNAcentral]: https://rnacentral.org
[RNAcentral data sources]: https://rnacentral.org/expert-databases
[MG7]: https://github.com/ohnosequences/mg7
[AGPLv3]: https://www.gnu.org/licenses/agpl-3.0.en.html
[ODbLv1]: http://opendatacommons.org/licenses/odbl/1.0/
[db.rnacentral]: https://github.com/ohnosequences/db.rnacentral
