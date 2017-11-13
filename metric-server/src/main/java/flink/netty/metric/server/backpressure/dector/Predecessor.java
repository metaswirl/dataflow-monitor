package flink.netty.metric.server.backpressure.dector;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "ship_strategy", "side" })
public class Predecessor {
	@JsonProperty("id")
	private Integer id;
	@JsonProperty("ship_strategy")
	private String shipStrategy;
	@JsonProperty("side")
	private String side;
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
	
	@JsonProperty("ship_strategy")
	public String getShipStrategy() {
		return shipStrategy;
	}

	@JsonProperty("ship_strategy")
	public void setShipStrategy(String shipStrategy) {
		this.shipStrategy = shipStrategy;
	}

	@JsonProperty("side")
	public String getSide() {
		return side;
	}

	@JsonProperty("side")
	public void setSide(String side) {
		this.side = side;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperty(String name, Object value) {
		System.out.println(name + value.toString());
		this.additionalProperties.put(name, value);
	}
}
