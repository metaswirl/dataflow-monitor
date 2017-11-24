package flink.netty.metric.server.backpressure.dector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import flink.netty.metric.server.MetricServer;

public class BackpressureDetector implements Runnable {

	FileWriter fw;
	FileWriter fwLatency;
	private FlinkExecutionPlan flinkExecutionPlan;
	private String slowTaskID = "-1";
	public static final int timeWindow = 8;
	// is zero for testing
	private double slaMaxLatency = 0.0;
	private boolean reScaleAttempted = false;
	private Map<String, Node> nodeAndTypes;
	private Map<Integer, Node> nodeIDtoNodeMap;
	private static final double SLOWLINK_OUT_BP_USAGE = 0.99;
	private static final double SLOWLINK_FREE_OF_PRESSURE = 0.99;
	private static final double OUTPUT_BACKPRESSURE_THRESHOLD = 0.99;
	private static final double SLOWTASK_INPUT_BP_THRESHOLD = 0.99;
	private static final double SLOWTASK_OUTPUT_BP_THRESHOLD = 0.30;
	private static final double MBA_THRESHOLD = 0.5;
	// Does not account for different pipelines.
	private double maxPipeLineDelay = 0;
	private String jobDescription = "SocketWordCountParallelism";
	private String jobCommand = " -n examples/streaming/SocketWordCountParallelism2.jar --port 9001 --sleep 30000 --para 3 --parareduce 4 --ip loadgen112 --node 172.16.0.114 --sleep2 0 --timewindow 0 --timeout 100 --path test";
	private int intervalCheck = 100;

	/**
	 * BufferPoolUsage over 50 % -> Backpressure
	 * 
	 * @param id
	 */
	private void markBackpressureInExectuionPlan() {
		// example id: "loadgen113.Flat Map.1.in"
		// example id: "loadgen113.Flat Map.1.out"
		for (Entry<String, Double> entry : MetricServer.backpressureData.getBufferPoolUsageMap().entrySet()) {
			String id = entry.getKey();
			Double bufferPoolUsage = entry.getValue();
			String idSplitbyPoint[] = id.split("\\.");
			String machine = idSplitbyPoint[0];
			String type = id.split("\\.")[1];
			String inOrOut = id.split("\\.")[3];
			Node node = nodeAndTypes.get(type);

			if (node.getType().contains(type)) {
				if (inOrOut.equals("in")) {
					node.updateTaskAndInputBufferUsage(machine, bufferPoolUsage);
				} else {
					node.updateTaskAndOutputBufferUsage(machine, bufferPoolUsage);
				}
			} else {
				System.out.println("Type not found");
			}

		}
	}

	private void markLatencyInExectuionPlan() {
		// example id:
		// "Ma.taskmanager.a4e452faf111a732a7fed998bcb296b9.SocketWordCountParallelism.Keyed
		// Reduce.0.latency"
		for (Entry<String, Double> entry : MetricServer.backpressureData.getLatencyMap().entrySet()) {
			String id = entry.getKey();
			Double bufferPoolUsage = entry.getValue();
			String type = id.split("\\.")[4];
			Node node = nodeAndTypes.get(type);

			if (node.getType().contains(type)) {
				node.updateTaskAndLatency(id, bufferPoolUsage);
				return;
			}

		}

		// System.out.println("type not found: " );
	}

	public Task detectBackpressureInExecutionGraph(Task task) {
		SlowTask slowtask = null;
		if (task != null && task instanceof SlowTask) {
			slowtask = detectSlowTask((SlowTask) task);
		} else {
			slowtask = detectSlowTask(null);
		}
		if (slowtask != null) {
			return slowtask;
		}
		SlowLink slowlink = null;
		if (task != null && task instanceof SlowLink) {
			slowlink = detectSlowLink((SlowLink) task);
		} else {
			slowlink = detectSlowLink(null);
		}
		return slowlink;
	}

	public SlowTask detectSlowTask(SlowTask task) {
		for (Node node : flinkExecutionPlan.getNodes()) {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				// slow task found
				if ((entry.getValue().inputBufferPoolusage >= SLOWTASK_INPUT_BP_THRESHOLD
						|| entry.getValue().calculateInputBMA() > MBA_THRESHOLD)
						&& entry.getValue().outputBufferPoolusage <= SLOWTASK_OUTPUT_BP_THRESHOLD) {

					try {
						// we only consider one slow task
						// if (slowTaskID.equals("-1")) {
						// slowTaskID = node.getId().toString() +
						// entry.getKey();
						// }
						// if (slowOperatorNode == null) {
						// slowOperatorNode = node;
						// }
						if (task == null) {

							int affectedNodes = checkBackpressureExpansion(node.getId()) + 1;
							fw.write(System.currentTimeMillis() + ";slowTask;" + node.getType() + ";" + entry.getKey()
									+ ";" + affectedNodes + ";" + entry.getValue().calculateOutputBMA() + "\n");
							fw.flush();
							System.out.print("*");
							return new SlowTask(node, entry.getKey());
						} else {
							return task;
						}

					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					// is this our previous slow task?
					isThisOurPreviousSlowTask(node, entry.getKey(), task);

				}
			}
		}
		return null;
	}

	private void isThisOurPreviousSlowTask(Node node, String entryKey, SlowTask slowtask) {
		if (slowtask != null && slowtask.equals(new SlowTask(node, entryKey))) {
			boolean noPredecessorHasBackpressure = true;
			// has some of its predecessors still backpressure?
			for (Predecessor predecessor : node.getPredecessors()) {
				Node currPredecessor = nodeIDtoNodeMap.get(predecessor.getId());
				for (Map.Entry<String, Tuple> preEntry : currPredecessor.getTaskAndBufferUsage().entrySet()) {
					if (preEntry.getValue().outputBufferPoolusage >= SLOWLINK_OUT_BP_USAGE) {
						noPredecessorHasBackpressure = false;
					}
				}
			}
			if (noPredecessorHasBackpressure) {
				slowtask = null;
			}
		}
	}

	public SlowLink detectSlowLink(SlowLink slowlink) {

		double maxPipeLatency = 0;
		for (Node node : flinkExecutionPlan.getNodes()) {
			// compute max pipeline delay
			maxPipeLatency += node.getMaxLatency();
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage > SLOWLINK_OUT_BP_USAGE
						|| entry.getValue().calculateOutputBMA() > MBA_THRESHOLD) {
					Node successorNode = node.getSuccessor();
					if (successorNode == null) {
						if (slowlink == null || !slowlink.getNode().getId().equals(node.getId())) {
							// System.out.print("l" + node.getType());
							// slowLinkID = entry.getKey();
							// slowLinkNode = node;
							return new SlowLink(node, entry.getKey());
						}
					} else {
						// is successor our backpressure node?
						if (true) {
							// declaration can't be outside loop, because we
							// individually check each node.
							boolean foundSuccesorWithBackpressure = false;
							// are all nodes in front of this suspected slow
							// link
							// free of backpressure?
							for (Map.Entry<String, Tuple> sucEn : successorNode.getTaskAndBufferUsage().entrySet()) {
								if (sucEn.getValue().inputBufferPoolusage > SLOWLINK_FREE_OF_PRESSURE) {
									foundSuccesorWithBackpressure = true;
								}
							}
							if (!foundSuccesorWithBackpressure) {
								// System.out.print("x" + node.getType());
								// slowLinkID = entry.getKey();
								// slowLinkNode = node;
								return new SlowLink(node, entry.getKey());
							}
						}

					}
				}
			}
		}
		maxPipeLineDelay = maxPipeLatency;
		return null;

	}

	/**
	 * Counts how many tasks are effected by a slow task/link
	 * 
	 * @param nodeID
	 * @return number of effected tasks by backpressure
	 */
	public int checkBackpressureExpansion(int nodeID) {
		Node node = nodeIDtoNodeMap.get(nodeID);
		int countAffectNodes = 0;
		if (node.getPredecessors() != null && !node.getPredecessors().isEmpty()) {
			for (Predecessor prepredecessor : node.getPredecessors()) {
				countAffectNodes += checkBackpressureExpansion(prepredecessor.getId());
			}
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage >= OUTPUT_BACKPRESSURE_THRESHOLD) {
					countAffectNodes++;
				}
			}
			return countAffectNodes;
		} else {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage >= OUTPUT_BACKPRESSURE_THRESHOLD) {
					countAffectNodes++;
				}
			}
			return countAffectNodes;
		}

	}

	private void mitigateOrNot(Task task) {
		// we can only scale UP
		if(task != null ) {

			if (task instanceof SlowTask && maxPipeLineDelay > slaMaxLatency && !reScaleAttempted) {
				Pattern pattern = detectPattern(task.getNode());
				reScaleAttempted = true;
				userSystemOutput(pattern, "Slow Task", task);

				if (pattern.equals(Pattern.NtoMkeyed)) {
					// we don't know how long rescaling actually takes, so it is
					// only allowed to do it once.

					if (MetricServer.mitigate) {
						System.out.println("SLA violation! Latency is  " + maxPipeLineDelay
								+ "Attempding to rescale. Time: " + System.currentTimeMillis());
						new Thread(new MitigateThread(jobDescription, jobCommand)).start();
					}

				}
			}
			if (task instanceof SlowLink && maxPipeLineDelay > slaMaxLatency && !reScaleAttempted) {
				Pattern pattern = detectPattern(task.getNode());
				reScaleAttempted = true;
				userSystemOutput(pattern, "Slow Link", task);

			}
		}

	}

	private void userSystemOutput(Pattern pattern, String slowTaskoLink, Task task) {
		System.out.println("###########################################################");
		System.out.println("Pattern: " + pattern);
		System.out.println("Operator: " + task.getNode().getType());
		System.out.println("Cause: " + slowTaskoLink);
		if (slowTaskoLink.contains("Link")) {
			System.out.println("Location: " + task.getId());
		} else {
			System.out.println("Location: " + task.getId());
		}
		System.out.print("Recomended Mitigation Techniques: ");
		for (MitigationTechnique mt : getMitigationTechnique(pattern, true)) {
			System.out.print(mt + " ");
		}
		System.out.println();
		System.out.println("Timestamp: " + System.currentTimeMillis());
		System.out.println("###########################################################");
	}

	private void removeOldLatencies() {
		for (Entry<String, Long> entry : MetricServer.backpressureData.getLatencyMapTime().entrySet()) {
			long currTime = System.currentTimeMillis();
			// remove old keys (there might be keys not used anymore
			// after a job restart)
			if (currTime - entry.getValue() > 2000) {
				MetricServer.backpressureData.getLatencyMap().remove(entry.getKey());
			}
		}
	}

	private void init() {

		nodeAndTypes = new HashMap<String, Node>();
		for (Node node : flinkExecutionPlan.getNodes()) {
			nodeAndTypes.put(node.getType(), node);
		}

		nodeIDtoNodeMap = new HashMap<Integer, Node>();
		for (Node node : flinkExecutionPlan.getNodes()) {
			nodeIDtoNodeMap.put(node.getId(), node);
		}

		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getPredecessors() != null && !node.getPredecessors().isEmpty()) {
				for (Predecessor predecessor : node.getPredecessors()) {
					Node predecessorNode = nodeIDtoNodeMap.get(predecessor.getId());
					predecessorNode.setSuccessor(node);
				}
			}
		}

	}

	private Pattern detectPattern(Node node) {
		int parallelismNode = node.getParallelism();
		int parallelismPredecessor;
		List<Predecessor> predecessorList = node.getPredecessors();
		if (predecessorList != null && !predecessorList.isEmpty()) {
			// at this point we don't know how to handle multiple predecessors.
			// What is the pattern if two operators forward data to another
			// operator?
			Predecessor firstPredecessor = predecessorList.get(0);
			Node predecessorNode = nodeIDtoNodeMap.get(firstPredecessor.getId());
			parallelismPredecessor = predecessorNode.getParallelism();
			// we need to use additonalProperties, because ship_strategey
			// (underscore is not the problem) is some how not recognized by
			// Jackson.
			if (firstPredecessor.getAdditionalProperties().get("ship_strategy").toString().equals("HASH")) {
				return Pattern.NtoMkeyed;
			}

			if (parallelismNode > parallelismPredecessor) {
				// M smaller N
				return Pattern.MbiggerN;
			} else {
				return Pattern.NbiggerOrEqualM;
			}
		}
		System.out.println("Pattern couldn't be identified. ");
		return null;
	}

	private List<MitigationTechnique> getMitigationTechnique(Pattern pattern, boolean slowNodeLink) {
		if (slowNodeLink) {
			if (pattern.equals(Pattern.NtoMkeyed)) {
				List<MitigationTechnique> result = new ArrayList<MitigationTechnique>();
				result.add(MitigationTechnique.PARTITIONING);
				result.add(MitigationTechnique.SCALING);
				return result;
			} else if (pattern.equals(Pattern.MbiggerN)) {
				List<MitigationTechnique> result = new ArrayList<MitigationTechnique>();
				result.add(MitigationTechnique.BALANCING);
				result.add(MitigationTechnique.SCALING);
				return result;
			} else {
				List<MitigationTechnique> result = new ArrayList<MitigationTechnique>();
				result.add(MitigationTechnique.SCALING);
				return result;
			}
		} else {
			// We don't know about Data Skew.
			return null;
		}

	}

	@Override
	public void run() {
		ObjectMapper mapper = new ObjectMapper();
		try {
			fw = new FileWriter("backpressure.csv", true);
			fwLatency = new FileWriter("oplatency.csv", true);
			// read executionPlan from a json file
			// best would be to use Flinks API to get it, but who has time for
			// that?^^
			flinkExecutionPlan = mapper.readValue(new File("exeplan.json"), FlinkExecutionPlan.class);
			init();
			Task slowTaskorLink = null;
			while (true) {
				try {
					// sleepy time
					// we wouldn't need this if we used a signal from the
					// FileFlinkReporter telling us when it is finished sending
					// data.
					Thread.sleep(intervalCheck);
				} catch (InterruptedException e) {
					System.out.println("BackpressureDector: Error count not sleep.");
				}
				// Update Backpressure in ExecutionPlan
				markBackpressureInExectuionPlan();
				// Update how long latency keys are not updated
				// we wouldn't need this, if we would use the the
				// notifyOfRemovedMetric() method from the file Flink reporter
				removeOldLatencies();
				// update operator latencies in the ExecutionPlan
				markLatencyInExectuionPlan();

				// only update Backpressure ratio if we have a slow node or link
				// (slow link is still missing)
				// we should do this for all nodes input and output buffer

				// latency signal
				fwLatency.write(System.currentTimeMillis() + " " + maxPipeLineDelay + "\n");
				fwLatency.flush();
				slowTaskorLink = detectBackpressureInExecutionGraph(slowTaskorLink);

				mitigateOrNot(slowTaskorLink);
				if (slowTaskID.equals("-1")) {
					// signal
					fw.write(System.currentTimeMillis() + ";NoBackpressure;" + "0;" + "0;" + "0;0" + "\n");
					// generateParallelismIncremtSuggestion();
					fw.flush();
				} else {

				}

			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
