package berlin.bbdc.inet.mera.usecases.ThreeStageWordCount

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class TestLineGenerator extends Specification with Mockito {
  "LineGenerator" should {
    "have 'Aaberg' as its second word" in {
      val lg: LineGenerator = new LineGenerator()
      lg.words(1) must_== "Aaberg"
    }
    "have 'zZt' as its last word" in {
      val lg: LineGenerator = new LineGenerator()
      lg.words(411078) must_== "zZt"
    }
    "generate 20 words" in {
      val lg: LineGenerator = new LineGenerator()
      val line: String = lg.generate()
      line.split(" ").length must_== 20
    }
  }
}
