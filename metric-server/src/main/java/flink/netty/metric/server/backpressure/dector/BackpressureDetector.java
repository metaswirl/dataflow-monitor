package flink.netty.metric.server.backpressure.dector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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
	private int timeWindow = 8;
	private ArrayList<Boolean> backpressureList = new ArrayList<Boolean>();
	private double backpressureRatio = -1;
	private Node backpressureNode = null;
	private double slaMaxLatency = 1000.0;
	private int reScaleAttempds = 0;
	private Node slowLinkNode = null;
	/**
	 * BufferPoolUsage over 50 % -> Backpressure
	 * 
	 * @param id
	 */
	private void markBackpressureInExectuionPlan(String id, Double bufferPoolUsage) {
		// example id: "loadgen113.Flat Map.1.in"
		// example id: "loadgen113.Flat Map.1.out"
		String idSplitbyPoint[] = id.split("\\.");
		String machine = idSplitbyPoint[0];
		String type = id.split("\\.")[1];
		String inOrOut = id.split("\\.")[3];
		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getType().contains(type)) {
				if (inOrOut.equals("in")) {
					node.updateTaskAndInputBufferUsage(machine, bufferPoolUsage);
				} else {
					node.updateTaskAndOutputBufferUsage(machine, bufferPoolUsage);
				}
			}
		}
	}
	
	private void markLatencyInExectuionPlan(String id, Double bufferPoolUsage) {
		// example id: "Ma.taskmanager.a4e452faf111a732a7fed998bcb296b9.SocketWordCountParallelism.Keyed Reduce.0.latency"
		String type = id.split("\\.")[4];
		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getType().contains(type)) {
				node.updateTaskAndLatency(id, bufferPoolUsage);
				return;
			}
		}
		System.out.println("type not found: " + type);
	}

	public void detectBackpressureInExecutionGraph() {
		detectSlowTask();
		//detectSlowLink();
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

	public void updateBackpressureRatio(Node node) {
		for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
			if (entry.getValue().inputBufferPoolusage >= 0.99) {

				if (slowTaskID.equals(node.getId().toString() + entry.getKey())) {
					if (backpressureList.size() >= timeWindow) {
						backpressureList.remove(0);
					}
					backpressureList.add(true);

				}
				int countTrue = 0;

				for (Boolean b : backpressureList) {
					if (b) {
						countTrue++;
					}
				}
				backpressureRatio = countTrue / (double) backpressureList.size();
				return;

			} else {
				if (slowTaskID.equals(node.getId().toString() + entry.getKey())) {
					if (backpressureList.size() >= timeWindow) {
						backpressureList.remove(0);
					}
					backpressureList.add(false);

					int countTrue = 0;
					for (Boolean b : backpressureList) {
						if (b) {
							countTrue++;
						}
					}
					backpressureRatio = countTrue / (double) backpressureList.size();

				}
				return;
			}
		}
	}

	public void detectSlowTask() {
		for (Node node : flinkExecutionPlan.getNodes()) {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().inputBufferPoolusage >= 0.99 && entry.getValue().outputBufferPoolusage <= 0.30) {

					try {

						if (slowTaskID.equals("-1")) {
							slowTaskID = node.getId().toString() + entry.getKey();
						}
						if (backpressureNode == null) {
							backpressureNode = node;
						}
						int affectedNodes = checkBackpressureExpansion(node.getId()) + 1; //including itself.
						fw.write(System.currentTimeMillis() + ";slowTask;" + node.getType() + ";" + entry.getKey() + ";"
								+ affectedNodes + ";" + backpressureRatio + "\n");
						fw.flush();
						System.out.print("*");
						return;
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					if (slowTaskID.equals(node.getId().toString() + entry.getKey())) {

						slowTaskID = "-1";
						// fw.write(System.currentTimeMillis() + ";slowTask;" +
						// node.getType() + ";" + entry.getKey()
						// + ";" + checkBackpressureExpansion(node.getId()) +
						// ";" + backpressureRatio + "\n");
						// fw.flush();

					}
				}
			}
		}
	}
	public void detectSlowLink() {
		for (Node node : flinkExecutionPlan.getNodes()) {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage > 0.99) {
					int succesor = findSuccessor(node.getId());
					if(succesor == -1 ) {
						if (!node.getId().equals(slowTaskID)) {
							System.out.print("l");
							slowLinkNode = node;
						}

					} else {
						for (Map.Entry<String, Tuple>  sucEn : findNodebyID(succesor).getTaskAndBufferUsage().entrySet()) {
							if(sucEn.getValue().inputBufferPoolusage < 0.3) {
								if (!node.getId().equals(slowTaskID)) {
									System.out.print("l");
									slowLinkNode = node;
								}
							}
						}
					}
				} 
			}
			slowLinkNode = null;
		}
		
	}

	public int checkBackpressureExpansion(int nodeID) {
		Node node = findNodebyID(nodeID);
		int countAffectNodes = 0;
		if (node.getPredecessors() != null && !node.getPredecessors().isEmpty()) {
			for (Predecessor prepredecessor : node.getPredecessors()) {
				countAffectNodes += checkBackpressureExpansion(prepredecessor.getId());
			}
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage >= 0.99) {
					countAffectNodes++;
				}
			}
			return countAffectNodes;
		} else {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage >= 0.99) {
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
	
	private double maxPipeLatency () {
		double maxPipeLatency = 0;
		for (Node node : flinkExecutionPlan.getNodes()) {
			maxPipeLatency += node.getMaxLatency();
		}
		return maxPipeLatency;
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
			flinkExecutionPlan = mapper.readValue(new File("exeplan.json"), FlinkExecutionPlan.class);
			while (true) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					System.out.println("BackpressureDector: Error count not sleep.");
				}
				for (Entry<String, Double> entry : MetricServer.backpressureData.getBufferPoolUsageMap().entrySet()) {
					markBackpressureInExectuionPlan(entry.getKey(), entry.getValue());
				}
				for (Entry<String, Long> entry : MetricServer.backpressureData.getLatencyMapTime().entrySet()) {
					long currTime = System.currentTimeMillis();
					if(currTime - entry.getValue() > 2000 ) {
						MetricServer.backpressureData.getLatencyMap().remove(entry.getKey());
					}
				}
				for (Entry<String, Double> entry : MetricServer.backpressureData.getLatencyMap().entrySet()) {
					markLatencyInExectuionPlan(entry.getKey(), entry.getValue());
				}
				if (backpressureNode != null) {
					updateBackpressureRatio(backpressureNode);
				}
				fwLatency.write(System.currentTimeMillis() + " " + maxPipeLatency() + "\n");
				fwLatency.flush();
				if(backpressureNode != null && maxPipeLatency() > slaMaxLatency && reScaleAttempds == 0 && MetricServer.mitigate) {
					reScaleAttempds++;
					System.out.println("SLA violation! Latency is  " + maxPipeLatency() + "Attempding to rescale. Time: " + System.currentTimeMillis() );
					Runtime.getRuntime().exec("python3 cancelJob2.py canceled 30000");
					System.out.println("Rescaled.");
				}
				
				detectBackpressureInExecutionGraph();
				if (slowTaskID.equals("-1")) {
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
