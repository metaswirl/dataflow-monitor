package berlin.bbdc.inet.mera.monitor.topology

import berlin.bbdc.inet.mera.monitor.model.CommType.CommType
import berlin.bbdc.inet.mera.monitor.model.CommType
import berlin.bbdc.inet.mera.monitor.model.Model
import berlin.bbdc.inet.mera.monitor.model.ModelBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

class TopologyServer(hostname: String, port: Integer, cli: AbstractFlinkClient) {
  // So far there is no way to update the model. In the future we would like to update the model,
  // but this would require further changes to the ModelFileWriter etc.
  val windowSize = ConfigFactory.load.getInt("model.windowSize")

  def buildModels(): Map[String, Model] = {
    var models = new mutable.HashMap[String, Model]
    cli.establishConnection()
    //get list of all running jobs. for each job build a model
    for (jid <- cli.getJobs().jobsRunning) {
      val job = cli.getJob(jid)
      val modelBuilder = new ModelBuilder
      //iterate over vertices and for each add a new operator to the model
      job.vertices foreach (v => {
        modelBuilder.addSuccessor(v.name, v.parallelism, TopologyServer.findCommTypeById(v.id, job.plan),
          v.name contains "loadshedder", Some(getHostMappings(jid, v.id)))
      })
      models += (jid -> modelBuilder.createModel(windowSize))
    }

    if (models.isEmpty) {
      throw new IllegalStateException("No models detected in Flink JobManager")
    }

    models.toMap
  }

  private def getHostMappings(jobId : String, vertexId : String) : Map[Int, String] =
    cli.getFullVertex(jobId, vertexId).subtasks.map(x => x.subtask -> x.host).toMap
}

object TopologyServer {

  def findCommTypeById(id: String, plan: Plan): CommType = {
    plan.nodes.foreach(n => {
      if (n.inputs != null) {
        n.inputs.foreach(i => {
          if (i.id.equals(id)) {
            i.shipStrategy match {
              case "HASH" | "RANGE" =>
                return CommType.POINTWISE
              case "REBALANCE" | "FORWARD" =>
                return CommType.ALL_TO_ALL
              case t =>
                throw new IllegalStateException("Unknown CommType " + t)
            }
          }
        })
      }
    })
    CommType.UNCONNECTED
  }
}



@JsonIgnoreProperties(ignoreUnknown = true)
case class AllJobs(@JsonProperty("jobs-running") jobsRunning: List[String])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Job(jid: String,
               name: String,
               vertices: List[Vertex],
               plan: Plan)

@JsonIgnoreProperties(ignoreUnknown = true)
case class Vertex(id: String,
                  name: String,
                  parallelism: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
case class FullVertex(id: String,
                  name: String,
                  parallelism: Int,
                  subtasks: List[SubTask])

@JsonIgnoreProperties(ignoreUnknown = true)
case class SubTask(subtask: Int,
                   host: String)

@JsonIgnoreProperties(ignoreUnknown = true)
case class Plan(nodes: List[Node])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Node(id: String,
                description: String,
                inputs: List[Input])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Input(id: String,
                 @JsonProperty("ship_strategy") shipStrategy: String)
