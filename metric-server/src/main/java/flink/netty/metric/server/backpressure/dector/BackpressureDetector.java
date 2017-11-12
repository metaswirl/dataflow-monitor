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
	private List<Node> currentSuggestionForIncrementList = new ArrayList<Node>();
	private String slowTaskID = "-1";
	public static final int timeWindow = 8;
	private ArrayList<Boolean> backpressureList = new ArrayList<Boolean>();
	private Node backpressureNode = null;
	private double slaMaxLatency = 1000.0;
	private boolean reScaleAttempted = false;
	private Node slowLinkNode = null;
	private Map<String, Node> nodeAndTypes;
	private Map<Integer, Node> nodeIDtoNodeMap;
	private double slowLinkoutBufferUsageThreshold = 0.99;
	private double slowLinkFreeOfBPThreashold = 0.99;
	private double outputBPbackpressureThreshold = 0.99;
	private double slowTaskinputBPThreshold = 0.99;
	private double slowTaskoutputBPThreshold = 0.30;
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

	public void detectBackpressureInExecutionGraph() {
		detectSlowTask();
		detectSlowLink();
	}

	public int findSuccessor(int id) {
		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getPredecessors() != null && !node.getPredecessors().isEmpty()) {
				for (Predecessor predecessor : node.getPredecessors()) {
					if (predecessor.getId() == id) {
						return node.getId();
					}
				}
			}
		}
		return -1;
	}

	public void detectSlowTask() {
		for (Node node : flinkExecutionPlan.getNodes()) {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				// slow task found
				if (entry.getValue().inputBufferPoolusage >= slowTaskinputBPThreshold && entry.getValue().outputBufferPoolusage <= slowTaskoutputBPThreshold) {

					try {
						// we only consider one slow task
						if (slowTaskID.equals("-1")) {
							slowTaskID = node.getId().toString() + entry.getKey();
						}
						if (backpressureNode == null) {
							backpressureNode = node;
						}
						int affectedNodes = checkBackpressureExpansion(node.getId()) + 1; // including
																							// itself
						// signal: we should have an extra method for this

						fw.write(System.currentTimeMillis() + ";slowTask;" + node.getType() + ";" + entry.getKey() + ";"
								+ affectedNodes + ";" + entry.getValue().calculateOutputBMA() + "\n");
						fw.flush();
						System.out.print("*");
						return;
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					// is this our previous slow task?
					isThisOurPreviousSlowTask(node, entry.getKey());

				}
			}
		}
	}
	private void isThisOurPreviousSlowTask(Node node, String entryKey) {
		if (slowTaskID.equals(node.getId().toString() + entryKey)) {
			boolean noPredecessorHasBackpressure = true;
			// has some of its predecessors still backpressure?
			for (Predecessor predecessor : node.getPredecessors()) {
				Node currPredecessor = nodeIDtoNodeMap.get(predecessor.getId());
				for (Map.Entry<String, Tuple> preEntry : currPredecessor.getTaskAndBufferUsage()
						.entrySet()) {
					if (preEntry.getValue().outputBufferPoolusage >= slowLinkoutBufferUsageThreshold) {
						noPredecessorHasBackpressure = false;
					}
				}
			}
			if (noPredecessorHasBackpressure) {
				slowTaskID = "-1";
				backpressureNode = null;
			}
		}
	}

	public void detectSlowLink() {
		for (Node node : flinkExecutionPlan.getNodes()) {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage > slowLinkoutBufferUsageThreshold) {
					int succesor = findSuccessor(node.getId());
					if (succesor == -1) {
						if (!node.getId().equals(slowTaskID)) {

							//System.out.print("l" + node.getType());
							slowLinkNode = node;
						}

					} else {
						// is successor our backpressure node?
						if (!nodeIDtoNodeMap.get(succesor).equals(backpressureNode)) {
							//declaration can't be outside loop, beceause we individually check each node.
							boolean foundSuccesorWithBackpressure = false;
							// are all nodes in front of this suspected slow
							// link
							// free of backpressure?
							for (Map.Entry<String, Tuple> sucEn : nodeIDtoNodeMap.get(succesor).getTaskAndBufferUsage()
									.entrySet()) {
								if (sucEn.getValue().inputBufferPoolusage > slowLinkFreeOfBPThreashold) {
									foundSuccesorWithBackpressure = true;
								}
							}
							if (!foundSuccesorWithBackpressure) {
								//System.out.print("x" + node.getType());
								slowLinkNode = node;
								for (Map.Entry<String, Tuple> sucEn : nodeIDtoNodeMap.get(succesor).getTaskAndBufferUsage()
										.entrySet()) {
									System.out.println(sucEn.getValue().inputBufferPoolusage);
									if (sucEn.getValue().inputBufferPoolusage > slowLinkFreeOfBPThreashold) {
										System.out.println(sucEn.getValue().inputBufferPoolusage);
									}
								}
							}
						}

					}
				}
			}
			slowLinkNode = null;
		}

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
				if (entry.getValue().outputBufferPoolusage >= outputBPbackpressureThreshold) {
					countAffectNodes++;
				}
			}
			return countAffectNodes;
		} else {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage >= outputBPbackpressureThreshold) {
					countAffectNodes++;
				}
			}
			return countAffectNodes;
		}

	}

	public Node findNodebyID(int id) {
		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getId() == id) {
				return node;
			}

		}
		return null;
	}

	public boolean hasPredecessorBackpressure(Integer id) {

		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getId() == id) {
				if (node.getBackpressure()) {
					return true;
				}
			}
		}
		return false;
	}

	private void generateParallelismIncremtSuggestion() {
		List<Node> nodesWithBackpressure = new ArrayList<Node>();
		List<Node> nodesToIgnore = new ArrayList<Node>();
		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getBackpressure()) {
				nodesWithBackpressure.add(node);
			}
		}

		for (Node node : nodesWithBackpressure) {
			// remove Predecessors with backpressure from nodesWithBackpressure
			// list
			if (node.getPredecessors() != null) {
				for (Predecessor predecessor : node.getPredecessors()) {
					int predecessorID = predecessor.getId();
					if (hasPredecessorBackpressure(predecessorID)) {
						// find Node by ID and add to ignor list.
						for (Node ignoreNode : nodesWithBackpressure) {
							if (ignoreNode.getId() == predecessorID) {
								nodesToIgnore.add(ignoreNode);
							}
						}

					}
				}
			}

		}
		nodesWithBackpressure.removeAll(nodesToIgnore);
		for (Node node : nodesWithBackpressure) {
			if (!currentSuggestionForIncrementList.contains(node)) {
				System.out.println(
						"Suggestion Increment: " + node.getType() + " current Parallelism: " + node.getParallelism());
			}

		}

		currentSuggestionForIncrementList = nodesWithBackpressure;
	}

	private double maxPipeLatency() {
		double maxPipeLatency = 0;
		for (Node node : flinkExecutionPlan.getNodes()) {
			maxPipeLatency += node.getMaxLatency();
		}
		return maxPipeLatency;
	}

	private void mitigateOrNot() {
		try {
			// we can only scale UP
			if (backpressureNode != null && maxPipeLatency() > slaMaxLatency && !reScaleAttempted 
					&& MetricServer.mitigate) {
				// we don't know how long rescaling actually takes, so it is
				// only allowed to do it once.
				reScaleAttempted = true;
				System.out.println("SLA violation! Latency is  " + maxPipeLatency() + "Attempding to rescale. Time: "
						+ System.currentTimeMillis());
				Runtime.getRuntime().exec("python3 cancelJob2.py canceled 30000");
				System.out.println("Rescaled.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
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
					Node predecessorNode = nodeIDtoNodeMap.get(predecessor);
					predecessorNode.setSuccessor(node);
				}
			}
		}
		
	}

	@Override
	public void run() {
		ObjectMapper mapper = new ObjectMapper();
		for (int i = 0; i < timeWindow; i++) {
			backpressureList.add(false);
		}
		try {
			fw = new FileWriter("backpressure.csv", true);
			fwLatency = new FileWriter("oplatency.csv", true);
			// read executionPlan from a json file
			// best would be to use Flinks API to get it, but who has time for
			// that?^^
			flinkExecutionPlan = mapper.readValue(new File("exeplan.json"), FlinkExecutionPlan.class);
			init();
			while (true) {
				try {
					// sleepy time
					// we wouldn't need this if we used a signal from the
					// FileFlinkReporter telling us when it is finished sending
					// data.
					Thread.sleep(500);
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
				fwLatency.write(System.currentTimeMillis() + " " + maxPipeLatency() + "\n");
				fwLatency.flush();
				detectBackpressureInExecutionGraph();
				
				mitigateOrNot();
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
