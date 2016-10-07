
```scala
package ohnosequences.db.rna16s

import ohnosequences.fastarious.fasta._

package object test {

  type ID    = String
  type Taxon = String
  type Fasta = FASTA.Value

  // TODO: move it to db.rnacentral
  implicit class IteratorOps[V](val iterator: Iterator[V]) extends AnyVal {
```

Similar to the Stream's .groupBy, but assuming that groups are contiguous. Another difference is that it returns the key corresponding to each group.

```scala
    // NOTE: The original iterator should be discarded after calling this method
    def contiguousGroupBy[K](getKey: V => K): Iterator[(K, Seq[V])] = new Iterator[(K, Seq[V])] {
```

The definition is very straightforward: we keep the `rest` of values and on each `.next()` call bite off the longest prefix with the same key
Buffered iterator allows to look ahead without removing the next element

```scala
      private val rest: BufferedIterator[V] = iterator.buffered

      // NOTE: this is so simple, because of the contiguous grouping assumpltion
      def hasNext: Boolean = rest.hasNext

      def next(): (K, Seq[V]) = {
        val key = getKey(rest.head)

        key -> groupOf(key)
      }

      @annotation.tailrec
      private def groupOf_rec(key: K, acc: Seq[V]): Seq[V] = {
        if ( rest.hasNext && getKey(rest.head) == key )
          groupOf_rec(key, rest.next() +: acc)
        else acc
      }

      private def groupOf(key: K): Seq[V] = groupOf_rec(key, Seq())
    }
  }

}

```




[main/scala/package.scala]: ../../main/scala/package.scala.md
[main/scala/release.scala]: ../../main/scala/release.scala.md
[test/scala/clusterSequences.scala]: clusterSequences.scala.md
[test/scala/compats.scala]: compats.scala.md
[test/scala/dropInconsistentAssignments.scala]: dropInconsistentAssignments.scala.md
[test/scala/dropRedundantAssignments.scala]: dropRedundantAssignments.scala.md
[test/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[test/scala/package.scala]: package.scala.md
[test/scala/pick16SCandidates.scala]: pick16SCandidates.scala.md
[test/scala/releaseData.scala]: releaseData.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md