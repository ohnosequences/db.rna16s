
```scala
package era7bio.db.test

import era7bio.db._
import org.scalatest.FunSuite
import better.files._


class Dbrna16sTest extends FunSuite {

  test("Process some sample files") {

    rna16sDB.generateBundle.processSources(
      file"source.table.sample.tsv",
      file"output.table.sample.tsv".clear()
    )(file"source.sample.fasta",
      file"output.sample.fasta".clear()
    )
  }
}

```




[main/scala/rna16s.scala]: ../../main/scala/rna16s.scala.md
[test/scala/compats.scala]: compats.scala.md
[test/scala/Dbrna16s.scala]: Dbrna16s.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md