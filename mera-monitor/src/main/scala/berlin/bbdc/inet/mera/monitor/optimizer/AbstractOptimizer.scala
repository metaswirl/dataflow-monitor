package berlin.bbdc.inet.mera.monitor.optimizer

import berlin.bbdc.inet.mera.monitor.model.Model

/** General format for the optimizer
  */
abstract class AbstractOptimizer {
  /** Checks whether the preconditions of the particular optimizer are met
   */
  def canOptimize(model: Model): Boolean

  /** Executes a optimization on the current snapshot metrics
    */
  def optimize(): Unit
}
