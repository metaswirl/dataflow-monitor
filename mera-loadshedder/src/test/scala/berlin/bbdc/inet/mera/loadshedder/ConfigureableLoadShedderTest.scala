package berlin.bbdc.inet.mera.loadshedder

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.apache.flink.util.Collector
import java.lang.Thread

class ConfigurableLoadShedderTest extends Specification with Mockito {

  "Loadshedder" should {
    "record count of seen records" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int]()

      cls.flatMap(0, mockCollector)
      cls.flatMap(0, mockCollector)

      cls.count must_== 2
    }
    "reset should work if dropRate==0" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int]()
      cls.flatMap(0, mockCollector)
      cls.flatMap(0, mockCollector)
      cls.reset()

      cls.count must_== 0
      cls.numberOfRecords must_== 2
      cls.dropCount must_== 0
      cls.dropRatio must_== 0
    }
    "reset should work if dropRate>0" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int]()
      cls.flatMap(0, mockCollector)
      cls.flatMap(0, mockCollector)
      cls.setDropRate(1)
      cls.reset()

      cls.count must_== 0
      cls.numberOfRecords must_== 2
      cls.dropCount must_== 0
      cls.dropRatio must_== 2
    }
    "drop 1 item" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int]()

      // make the loadshedder assume the arrival rate is 2 item per second
      cls.flatMap(0, mockCollector)
      cls.flatMap(0, mockCollector)
      cls.setDropRate(1)
      cls.reset()

      cls.flatMap(1, mockCollector)
      cls.flatMap(2, mockCollector)
      cls.flatMap(3, mockCollector)

      cls.dropRatio must_== 2
      there was one(mockCollector).collect(1)
      there was no(mockCollector).collect(2)
      there was one(mockCollector).collect(3)
    }
    "drop 0 item" in {
      val mockCollector = mock[Collector[Int]]
      val cls = new ConfigurableLoadShedder[Int](0)

      cls.flatMap(1, mockCollector)

      there was one(mockCollector).collect(1)
    }
    /*
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
    */
  }
}

