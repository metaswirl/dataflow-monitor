package berlin.bbdc.inet.mera.loadshedder

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.apache.flink.util.Collector
import java.lang.Thread

class ConfigurableLoadShedderTest extends Specification with Mockito {

  "Loadshedder" should {
    "drop 1 item" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int](1)
      cls.flatMap(1, mockCollector)
      cls.flatMap(2, mockCollector)

      there was no(mockCollector).collect(1)
      there was one(mockCollector).collect(2)
    }
  }

  "Loadshedder" should {
    "drop 0 item" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int](0)
      cls.flatMap(1, mockCollector)

      there was one(mockCollector).collect(1)
    }
  }
  "Loadshedder" should {
    "drop 1 item every time window" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int](1)
      cls.flatMap(1, mockCollector)
      cls.flatMap(2, mockCollector)

      there was no(mockCollector).collect(1)
      there was one(mockCollector).collect(2)
      for (i <- 10 until 1008) { 
        cls.flatMap(i, mockCollector)
        there was one(mockCollector).collect(i)
      }

      Thread.sleep(1000)

      cls.flatMap(3, mockCollector)
      cls.flatMap(4, mockCollector)

      there was no(mockCollector).collect(3)
      there was one(mockCollector).collect(4)
    }
  }
}

