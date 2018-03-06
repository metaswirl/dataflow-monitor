package de.tuberlin.inet.mera.model

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}

@JsonIgnoreProperties(ignoreUnknown = true)
case class Jobs(var timestamp: Long = 0,
                @JsonProperty("jobs-running") jobsRunning: List[String],
                @JsonProperty("jobs-finished") jobsFinished: List[String],
                @JsonProperty("jobs-cancelled") jobsCancelled: List[String],
                @JsonProperty("jobs-failed") jobsFailed: List[String]) {

}
