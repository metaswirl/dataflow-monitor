package berlin.bbdc.inet.jobs.template

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class TestConfigurableBottleneckMap extends Specification with Mockito {

  "Bottleneck" should {
    "wait 10 ms" in {
      val ls = new ConfigurableBottleneckMap[Int]()
      ls.setDelay(100)
      val start = System.currentTimeMillis()
      ls.map(10)
      val end = System.currentTimeMillis()
      end-start must be_>=(10L)
    }
    "wait 100 ms" in {
      val ls = new ConfigurableBottleneckMap[Int]()
      ls.setDelay(100)
      val start = System.currentTimeMillis()
      ls.map(10)
      val end = System.currentTimeMillis()
      end-start must be_>=(100L)
    }
    "wait 300 ms" in {
      val ls = new ConfigurableBottleneckMap[Int]()
      ls.setDelay(300)
      val start = System.currentTimeMillis()
      ls.map(10)
      val end = System.currentTimeMillis()
      end-start must be_>=(300L)
    }
    "wait 500 ms" in {
      val ls = new ConfigurableBottleneckMap[Int]()
      ls.setDelay(500)
      val start = System.currentTimeMillis()
      ls.map(10)
      val end = System.currentTimeMillis()
      end-start must be_>=(500L)
    }
  }

}
