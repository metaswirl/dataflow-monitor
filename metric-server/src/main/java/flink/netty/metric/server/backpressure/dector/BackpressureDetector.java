package flink.netty.metric.server.backpressure.dector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

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
	private Node slowOperatorNode = null;
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
	private double mBAThreshold = 0.5;
	// Does not account for different pipelines.
	private double maxPipeLineDelay = 0;
	private String jobDescription = "SocketWordCountParallelism";
	private String jobCommand = " -n examples/streaming/SocketWordCountParallelism2.jar --port 9001 --sleep 30000 --para 3 --parareduce 3 --ip loadgen112 --node 172.16.0.114 --sleep2 0 --timewindow 0 --timeout 100 --path test";

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

	public void detectSlowTask() {
		for (Node node : flinkExecutionPlan.getNodes()) {
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				// slow task found
				if ((entry.getValue().inputBufferPoolusage >= slowTaskinputBPThreshold || entry.getValue().calculateInputBMA() > mBAThreshold)
						&& entry.getValue().outputBufferPoolusage <= slowTaskoutputBPThreshold ) {

					try {
						// we only consider one slow task
						if (slowTaskID.equals("-1")) {
							slowTaskID = node.getId().toString() + entry.getKey();
						}
						if (slowOperatorNode == null) {
							slowOperatorNode = node;
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
				for (Map.Entry<String, Tuple> preEntry : currPredecessor.getTaskAndBufferUsage().entrySet()) {
					if (preEntry.getValue().outputBufferPoolusage >= slowLinkoutBufferUsageThreshold) {
						noPredecessorHasBackpressure = false;
					}
				}
			}
			if (noPredecessorHasBackpressure) {
				slowTaskID = "-1";
				slowOperatorNode = null;
			}
		}
	}

	public void detectSlowLink() {

		double maxPipeLatency = 0;
		for (Node node : flinkExecutionPlan.getNodes()) {
			// compute max pipeline delay
			maxPipeLatency += node.getMaxLatency();
			for (Map.Entry<String, Tuple> entry : node.getTaskAndBufferUsage().entrySet()) {
				if (entry.getValue().outputBufferPoolusage > slowLinkoutBufferUsageThreshold || entry.getValue().calculateOutputBMA() > mBAThreshold) {
					Node successorNode = node.getSuccessor();
					if (successorNode == null) {
						if (!node.getId().equals(slowTaskID)) {

							// System.out.print("l" + node.getType());
							slowLinkNode = node;
						}

					} else {
						// is successor our backpressure node?
						if (!successorNode.equals(slowOperatorNode)) {
							// declaration can't be outside loop, because we
							// individually check each node.
							boolean foundSuccesorWithBackpressure = false;
							// are all nodes in front of this suspected slow
							// link
							// free of backpressure?
							for (Map.Entry<String, Tuple> sucEn : successorNode.getTaskAndBufferUsage().entrySet()) {
								if (sucEn.getValue().inputBufferPoolusage > slowLinkFreeOfBPThreashold) {
									foundSuccesorWithBackpressure = true;
								}
							}
							if (!foundSuccesorWithBackpressure) {
								// System.out.print("x" + node.getType());
								slowLinkNode = node;
							}
						}

					}
				}
			}
			slowLinkNode = null;
		}
		maxPipeLineDelay = maxPipeLatency;

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

	private void scaleUP() {
		try {
			String jobID = obtainJobID(jobDescription);
			String savePoint = cancelJobWithSavepoint(jobID);
			String restartCommand = "./bin/flink run -s " + savePoint + jobCommand;
			System.out.println("###############Metric-Command-Restart-JobCommand###############");
			System.out.println(restartCommand);
			System.out.println("###############################################################");
			Process process = Runtime.getRuntime().exec(restartCommand);
			BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			while ((line = input.readLine()) != null) {
				System.out.println(line);
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

	private String obtainJobID(String jobDescription) throws IOException, InterruptedException {
		String jobID = null;
		Process process = Runtime.getRuntime().exec("./bin/flink list -r");
		process.waitFor();
		BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;
		while ((line = input.readLine()) != null) {
			// exampleLine:
			// 12.11.2017 21:14:48 : 2513e7cf8e301e1c60e741cc02ce167d :
			// SocketWordCountParallelism (RUNNING)
			if (line.contains(jobDescription)) {
				String splitLine[] = line.split(":");
				if (splitLine.length > 3) {
					jobID = splitLine[3].trim();
					return jobID;
				} else {
					System.out.println("Flink list -r: missmatch. Didn't find job: " + jobDescription);
				}

			}
			System.out.println(line);
		}
		System.out.println("No Job found with description: " + jobDescription);
		return null;
	}

	private String cancelJobWithSavepoint(String jobID) throws ExecutionException, IOException, InterruptedException {
		String cancelJobCommand = "./bin/flink cancel -s /tmp/ " + jobID;
		Process process = Runtime.getRuntime().exec(cancelJobCommand);
		process.waitFor();
		BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;
		String savePointPath = null;
		while ((line = input.readLine()) != null) {
			System.out.println(line);

			// exampleLine:
			// Cancelled job b7577b03070629cbfa677b6c507194b9. Savepoint stored
			// in /tmp/savepoint-82a54b372b0f.
			if (line.contains("Savepoint stored in")) {
				String splitLine[] = line.split(" ");
				if (splitLine.length > 6) {
					savePointPath = splitLine[6].trim();
					// remove dot at the end of string
					savePointPath = savePointPath.substring(0, savePointPath.length() - 1);
				}
			}
		}
		return savePointPath;
	}

	private void mitigateOrNot() {
		// we can only scale UP
		if (slowOperatorNode != null && maxPipeLineDelay > slaMaxLatency && !reScaleAttempted
				&& MetricServer.mitigate) {
			Pattern pattern = detectPattern(slowOperatorNode);
			System.out.println("Pattern detected: " +  pattern);
			if(pattern.equals(Pattern.NtoMkeyed)) {
				// we don't know how long rescaling actually takes, so it is
				// only allowed to do it once.
				reScaleAttempted = true;
				System.out.println("SLA violation! Latency is  " + maxPipeLineDelay + "Attempding to rescale. Time: "
						+ System.currentTimeMillis());
				scaleUP();
				System.out.println("Rescaled.");
			}
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
			//we need to use additonalProperties, because ship_strategey (underscore is not the problem) is some how not recognized by Jackson.
			if (firstPredecessor.getAdditionalProperties().get("ship_strategy").toString().equals("HASH")) {
				return Pattern.NtoMkeyed;
			}
			if (parallelismNode < parallelismPredecessor) {
				return Pattern.MbiggerN;
			} else {
				return Pattern.NsmallerOrEqualM;
			}
		}
		System.out.println("Pattern couldn't be identified. ");
		return null;
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
				fwLatency.write(System.currentTimeMillis() + " " + maxPipeLineDelay + "\n");
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
