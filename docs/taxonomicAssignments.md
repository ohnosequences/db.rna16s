# Taxonomic Assignments

These are a function `RNAID => Set[TaxonID]`

For the standard taxon set we have a predicate (based on the NCBI taxonomy tree) telling you whether a `TaxonID` is standard in this sense.

The mapping result is a `Set[RNAID]` (plus other info) and we can simply filter them by assignment *before* any further analysis.

``` scala
// traverse the taxonomy tree etc
val isStandard: TaxonID => Boolean
// we have this from the Entry info
val assignments: RNAID => Set[TaxonID]

val keepStandard: Set[RNAID] => Set[(RNAID, Set[TaxonID])] =
  ids map { id => (id, assignments(id) filter isStandard) }
```

The standard taxon set can (and should) be defined at the level of the NCBI taxonomy API. We can have there predicates/sets of taxa like classified bacteria, uncultured, etc.