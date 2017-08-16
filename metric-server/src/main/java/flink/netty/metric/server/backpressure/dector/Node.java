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
	
	public boolean getBackpressure () {
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
