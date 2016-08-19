# Manual 0.10.0 data review

## Blacklisted entries: filtered or not?

Here inside this folder there's the sequence and the BLAST result against the 0.10.0 database for each of the [blacklisted RNACentral entries](https://github.com/ohnosequences/db.rna16s/blob/26af3e73843bd09ba2b207669b06a829cee5f43c/src/test/scala/pick16SCandidates.scala#L66-L87) we had, which *do not* get excluded; here's the complete list for ease of reference:

```
"URS00008CD63B",  // claims to be Lactobacilus plantarum, it is an Enterococcus
"URS00008CCF2E",  // claims to be Candidatus Hepatobacter penaei, it is a Pseudomonas
"URS00007EE21F",  // claims to be Pseudomonas sp. NT 6-08, it is a Staph aureus
"URS00008C61AD",  // claims to be Yersinia pestis biovar Orientalis str. AS200901509, it is a Staph aureus
"URS00008E71FD",  // claims to be Staphylococcus sciuri, it is a Pseudomonas
"URS000089CEEE",  // claims to be Bacillus sp. W4(2008), it is a Pseudomonas
"URS0000974DB8",  // claims to be Pseudomonas sp. CL3.1, it is a Bacillus
"URS00008E9E3B",  // claims to be Pantoea sp. CR30, it is a Bacillus
"URS00008DEF63",  // claims to be Microbacterium oxydans, it is a (fragment of) Bacillus
"URS000082C8CF",  // claims to be Streptococcus pneumoniae, it is a Bacillus plus some chimeric sequence
"URS0000874571",  // claims to be Bordetella, it is a Pseudomonas aeruginosa
"URS00008A3994",  // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
"URS00008898AD",  // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
"URS0000215B45",  // claims to be Vibrio cholerae HC-02A1, it is an Enterococcus faecalis
"URS00008239BE",  // claims to be Mycobacterium abscessus, it is an Acinetobacter
"URS000074A9F2",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
"URS0000735DC4",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
"URS00005BB216",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
"URS0000590E49",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
"URS0000865688",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
"URS000085F838",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
"URS000074A9F2" // claims to be Prolinoborus fasciculus, it is an Acinetobacter
```

A summary for each of what

### URS00008CD63B

We have no really close BLAST results here, *but* we already have a *lot* of *Lactobacilus plantarum* in the db, and not a single hit with them. This would be a good way of filtering sequences too: if we have sequences with the same assignment and we don't get any hit, something's not OK.

### URS00008CCF2E

After looking at the BLAST results, I'm not sure why this was blacklisted before; all hits to Pseudomonas have ~92% of identity.

### URS00007EE21F

Filtered out.

### URS00008C61AD

Filtered out.

### URS00008E71FD

This one stays because of the 100% query coverage restriction, with which clearly we shouldn't be so restrictive here: we have a lot of hits with Pseudomonas 100% identity missing just 3 bases. While looking at the results I catched `URS00002065D8`, which should be filtered out too and is not, for the same reason.

### URS000089CEEE

Filtered out.

### URS0000974DB8

Filtered out.

### URS00008E9E3B

Again not being filtered because of query coverage; all good alignments missing ~8 bases at the end.

### URS00008DEF63

Filtered out.

### URS000082C8CF

This one stays, but it should be dropped just based on the number of `N`s: 38 of them in the middle of the sequence.

### URS0000874571

Filtered out.

### URS00008A3994

Filtered out.

### URS00008898AD

Filtered out.

### URS0000215B45

Filtered out.

### URS00008239BE

Filtered out.

### URS000074A9F2

Filtered out.

### URS0000735DC4

Filtered out.

### URS00005BB216

Filtered out.

### URS0000590E49

Filtered out.

### URS0000865688

Stays, again because of the query coverage restriction; best hit misses 7 bases.

### URS000085F838

Exactly the same as the previous one, but even worse: just 3 bases missing.

### URS000074A9F2

Filtered out.

## Conclusions

1. We should be less stringent with the query coverage restriction here, and take something like 99%, even 99.5% would help (if `qcovs` has that precision)
2. A good filter to add, maybe after this one, would be to exclude those which yield no hits when there are several sequences with the same assignment in the database.
3. Previous to all this, we should also drop sequences with a number of `N`s over a threshold. the `lots-of-Ns.grep` file is the result of `cat ohnosequences.db.rna16s.fasta | grep -C 20 'NNNNNNNNNN'`, which yields around 1000 sequences; this is a lower bound for the number of sequences with at least 10 contiguous `N`s.
