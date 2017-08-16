package flink.netty.metric.server;

import java.util.HashMap;

public class BackpressureData {

	private HashMap<String, Double> BufferPoolUsageMap;
	
	public BackpressureData() {
		this.BufferPoolUsageMap = new HashMap<String, Double>();
	}

	public HashMap<String, Double> getBufferPoolUsageMap() {
		return BufferPoolUsageMap;
	}
	
	public void updateBufferPoolUsageMap(String key, Double value) {
		BufferPoolUsageMap.put(key, value);
	}
	
	/*
	 * Example Lines:
		loadgen113.Sink: Unnamed.1.inPoolUsage|0.0
		loadgen113.Flat Map.1.inPoolUsage|1.0
		loadgen113.Flat Map.1.outPoolUsage|0.21428572
		loadgen113.Sink: Unnamed.1.outPoolUsage|0.0

	 */
	public void updateBufferPoolUsageByLine(String metricData) {
	
			if (metricData.contains("PoolUsage")) {
				String[] splitByPipe = metricData.split("\\|");
				if (splitByPipe.length == 3 ) {
					String key = splitByPipe[1].replaceAll("PoolUsage", "");
					Double value = Double.valueOf(splitByPipe[2]);
					updateBufferPoolUsageMap(key, value);
				}
			}
		

	}
}
