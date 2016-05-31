package era7bio.db

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


  implicit class MapOp[K, V](val m: Map[K, Iterable[V]]) extends AnyVal {

    /* From Map[K, Seq[V]] to Map[V, Seq[K]] */
    def trans: Map[V, Seq[K]] =
      m.foldLeft(Map[V, Seq[K]]()) { case (acc, (k, vs)) =>

        vs.foldLeft(acc) { (accc, v) =>

          val ks = accc.get(v).getOrElse( Seq() )
          accc.updated(v, k +: ks)
        }
      }
  }

}
