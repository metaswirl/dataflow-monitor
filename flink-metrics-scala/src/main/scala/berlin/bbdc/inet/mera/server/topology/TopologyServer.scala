package berlin.bbdc.inet.mera.server.topology

import berlin.bbdc.inet.mera.server.model.{CommType, ModelBuilder}

class TopologyServer() {
  // So far there is no way to update the model. In the future we would like to update the model,
  // but this would require further changes to the ModelFileWriter etc.

  def createModelBuilder(): ModelBuilder = {
    val modelBuilder = new ModelBuilder()
    modelBuilder.addSuccessor("Source: Socket Stream", 1, CommType.Ungrouped)
    modelBuilder.addSuccessor("Flat Map", 3, CommType.Ungrouped)
    modelBuilder.addSuccessor("Filter", 2, CommType.Ungrouped)
    modelBuilder.addSuccessor("Map", 2, CommType.Grouped)
    modelBuilder.addSuccessor("aggregation", 2, CommType.Ungrouped)
    modelBuilder.addSuccessor("bottleneck", 1, CommType.Ungrouped)
    modelBuilder.addSuccessor("Sink: Unnamed", 2, CommType.Ungrouped)
    return modelBuilder
  }
}
