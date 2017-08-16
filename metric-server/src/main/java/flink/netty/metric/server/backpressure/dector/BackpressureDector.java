package flink.netty.metric.server.backpressure.dector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import flink.netty.metric.server.MetricServer;

public class BackpressureDector implements Runnable {

	private FlinkExecutionPlan flinkExecutionPlan;
	private boolean backpressureDetected = false;
	private List<Node> currentSuggestionForIncrementList = new ArrayList<Node>();

	/**
	 * loadgen113.Sink: Unnamed.1.inPoolUsage
	 * 
	 * @param id
	 */
	private void markBackpressureInExectuionPlan(String id) {
		String type = id.split("\\.")[1];
		for (Node node : flinkExecutionPlan.getNodes()) {
			if (node.getType().contains(type)) {
				node.setBackpressure(true);
				backpressureDetected = true;
			}
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
			if(!currentSuggestionForIncrementList.contains(node) ) {
				System.out.println("Suggestion Increment: " + node.getType() + " current Parallelism: " + node.getParallelism());
			}
			
		}
		
		currentSuggestionForIncrementList = nodesWithBackpressure;
	}

	@Override
	public void run() {
		ObjectMapper mapper = new ObjectMapper();

		try {
			flinkExecutionPlan = mapper.readValue(new File("exeplan.json"), FlinkExecutionPlan.class);
			while (true) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					System.out.println("BackpressureDector: Error count not sleep.");
				}
				for (Entry<String, Double> entry : MetricServer.backpressureData.getBufferPoolUsageMap().entrySet()) {
					// BufferPoolUsage over 50 % -> Backpressure
					if (entry.getValue() > 0.5) {
						markBackpressureInExectuionPlan(entry.getKey());
					}
				}
				if (backpressureDetected) {
					generateParallelismIncremtSuggestion();
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
