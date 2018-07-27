package berlin.bbdc.inet.mera.monitor.webserver

import berlin.bbdc.inet.mera.monitor.model.Model
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class MetricContainerSpec extends Specification with Mockito with BeforeAll {


  val model: Model = mock[Model]

  def beforeAll(): Unit = {

  }
}
