package era7bio.db

import scala.collection._

case object collectionUtils {

  implicit class StreamOp[T](val s: Stream[T]) extends AnyVal {

    /* Similar to .groupBy, by lazier: computes only one group at a time
       assuming that groups are not mixed */
    def group[K](key: T => K): Stream[(K, Stream[T])] = {
      if (s.isEmpty) Stream()
      else {
        val k = key(s.head)
        val (prefix, suffix) = s.span { key(_) == k }
        (k -> prefix) #:: suffix.group(key)
      }
    }
  }


  implicit class MapOp[K, V](val m: collection.Map[K, Iterable[V]]) extends AnyVal {

    /* From Map[K, Seq[V]] to Map[V, Seq[K]],
       applying given function (`identity` by default)
    */
    def trans[FK, FV](f: ((K, V)) => (FK, FV)): Map[FV, Seq[FK]] =
      m.foldLeft(Map[FV, Seq[FK]]()) { case (acc, (k, vs)) =>

        vs.foldLeft(acc) { (accc, v) =>

          val (fk, fv) = f(k -> v)
          val fks = accc.get(fv).getOrElse( Seq() )

          accc.updated(fv, fk +: fks)
        }
      }

    def trans: Map[V, Seq[K]] = trans(identity[(K, V)])
  }

}
