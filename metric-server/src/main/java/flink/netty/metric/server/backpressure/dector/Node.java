package flink.netty.metric.server.backpressure.dector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "type", "pact", "contents", "parallelism", "predecessors" })
public class Node {

	private boolean backpressure = false;

	public void setBackpressure(boolean value) {
		this.backpressure = value;
	}

	public boolean getBackpressure() {
		return backpressure;
	}

	@JsonProperty("id")
	private Integer id;
	@JsonProperty("type")
	private String type;
	@JsonProperty("pact")
	private String pact;
	@JsonProperty("contents")
	private String contents;
	@JsonProperty("parallelism")
	private Integer parallelism;
	@JsonProperty("predecessors")
	private List<Predecessor> predecessors = null;
	@JsonIgnore
	private Map<String, Object> additionalProperties = new HashMap<String, Object>();

	private Map<String, Tuple> tasksAndBufferUsage = new HashMap<String, Tuple>();

	private Map<String, Double> taskAndLatency = new HashMap<String, Double>();
	private Map<String, Long> taskAndLatencyTime = new HashMap<String, Long>();

	public Map<String, Double> getTaskAndLatency() {
		return taskAndLatency;
	}

	public double getMaxLatency() {
		long currTime = System.currentTimeMillis();
		for (Map.Entry<String, Long> entry : taskAndLatencyTime.entrySet()) {
			// delete latency keys if they haven't been updated for 2 seconds
			// (we don't know when the restart is finished)
			if (currTime - entry.getValue() > 2000) {
				taskAndLatency.remove(entry.getKey());
			}
		}
		double max = 0;
		for (double d : taskAndLatency.values()) {
			if (d > max) {
				max = d;
			}
		}
		return max;
	}

	public void updateTaskAndLatency(String key, double value) {
		taskAndLatency.put(key, value);
		taskAndLatencyTime.put(key, System.currentTimeMillis());
	}

	public Map<String, Tuple> getTaskAndBufferUsage() {
		return tasksAndBufferUsage;
	}

	public void updateTaskAndBufferUsage(String key, double inputBufferValue, double outputBufferValue) {
		if (tasksAndBufferUsage.containsKey(key)) {
			tasksAndBufferUsage.get(key).setInputBufferPoolusage(inputBufferValue);
			tasksAndBufferUsage.get(key).setOutputBufferPoolusage(outputBufferValue);
		} else {
			Tuple tuple = new Tuple(inputBufferValue, outputBufferValue);
			tasksAndBufferUsage.put(key, tuple);
		}
	}

	public void updateTaskAndInputBufferUsage(String key, double inputBufferValue) {
		if (tasksAndBufferUsage.containsKey(key)) {
			tasksAndBufferUsage.get(key).setInputBufferPoolusage(inputBufferValue);
		} else {
			Tuple tuple = new Tuple(inputBufferValue, -1);
			tasksAndBufferUsage.put(key, tuple);
		}

	}

	public void updateTaskAndOutputBufferUsage(String key, double outputBufferValue) {
		if (tasksAndBufferUsage.containsKey(key)) {
			tasksAndBufferUsage.get(key).setOutputBufferPoolusage(outputBufferValue);
		} else {
			Tuple tuple = new Tuple(-1, outputBufferValue);
			tasksAndBufferUsage.put(key, tuple);
		}

	}

	@JsonProperty("id")
	public Integer getId() {
		return id;
	}

	@JsonProperty("id")
	public void setId(Integer id) {
		this.id = id;
	}

	@JsonProperty("type")
	public String getType() {
		return type;
	}

	@JsonProperty("type")
	public void setType(String type) {
		this.type = type;
	}

	@JsonProperty("pact")
	public String getPact() {
		return pact;
	}

	@JsonProperty("pact")
	public void setPact(String pact) {
		this.pact = pact;
	}

	@JsonProperty("contents")
	public String getContents() {
		return contents;
	}

	@JsonProperty("contents")
	public void setContents(String contents) {
		this.contents = contents;
	}

	@JsonProperty("parallelism")
	public Integer getParallelism() {
		return parallelism;
	}

	@JsonProperty("parallelism")
	public void setParallelism(Integer parallelism) {
		this.parallelism = parallelism;
	}

	@JsonProperty("predecessors")
	public List<Predecessor> getPredecessors() {
		return predecessors;
	}

	@JsonProperty("predecessors")
	public void setPredecessors(List<Predecessor> predecessors) {
		this.predecessors = predecessors;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

}
