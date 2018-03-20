package berlin.bbdc.inet.mera.server.topology

import berlin.bbdc.inet.mera.server.model.{CommType, ModelBuilder}

class TopologyServer() {
  // So far there is no way to update the model. In the future we would like to update the model,
  // but this would require further changes to the ModelFileWriter etc.

  def createModelBuilder(): ModelBuilder = {
    val modelBuilder = new ModelBuilder()
    modelBuilder.addSuccessor("Source: Socket Stream", 1, CommType.RecordByRecord)
    modelBuilder.addSuccessor("Flat Map", 3, CommType.RecordByRecord)
    modelBuilder.addSuccessor("Filter", 2, CommType.RecordByRecord)
    modelBuilder.addSuccessor("Map", 2, CommType.Grouped)
    modelBuilder.addSuccessor("aggregation", 2, CommType.RecordByRecord)
    modelBuilder.addSuccessor("bottleneck", 1, CommType.RecordByRecord)
    modelBuilder.addSuccessor("Sink: Unnamed", 2, CommType.RecordByRecord)
    return modelBuilder
  }
}
