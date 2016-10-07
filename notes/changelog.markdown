* #47: Reimplemented `pick16SCandidates` in a fixed-memory fashion
* #56: Modified final data filtering procedure:
  - now we run only split and blast steps from the MG7 pipeline
  - then in a new step we cluster sequences based on their BLAST-similarity
  - then we filter out inconsistent assignments based on the clusters and taxonomy (see code docs for details)
