package flink.netty.metric.server;

import java.util.concurrent.ConcurrentHashMap;

public class BackpressureData {

	private ConcurrentHashMap<String, Double> BufferPoolUsageMap;
	private ConcurrentHashMap<String, Double> LatencyMap;
	private ConcurrentHashMap<String, Long> LatencyMapTime;
	
	public ConcurrentHashMap<String, Long> getLatencyMapTime() {
		return LatencyMapTime;
	}

	public ConcurrentHashMap<String, Double> getLatencyMap() {
		return LatencyMap;
	}

	public BackpressureData() {
		this.BufferPoolUsageMap = new ConcurrentHashMap<String, Double>();
		this.LatencyMap = new ConcurrentHashMap<String, Double>();
		this.LatencyMapTime = new ConcurrentHashMap<String, Long>();
	}

	public ConcurrentHashMap<String, Double> getBufferPoolUsageMap() {
		return BufferPoolUsageMap;
	}
	
	public void updateBufferPoolUsageMap(String key, Double value) {
		BufferPoolUsageMap.put(key, value);
	}
	
	/*
	 * Example Lines:
		#|loadgen113.Sink: Unnamed.1.inPoolUsage|0.0
		#|loadgen113.Flat Map.1.inPoolUsage|1.0
		#|loadgen113.Flat Map.1.outPoolUsage|0.21428572
		#|loadgen113.Sink: Unnamed.1.outPoolUsage|0.0
		
		#|Ma.taskmanager.a4e452faf111a732a7fed998bcb296b9.SocketWordCountParallelism.Keyed Reduce.0.latency|{LatencySourceDescriptor{vertexID=1, subtaskIndex=-1}={p99=6518.0, p50=316.0, min=62.0, max=6518.0, p95=6518.0, mean=1049.3846153846155}}
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
			
			if (metricData.contains("latency") &&  metricData.contains("p99")) {
				String[] splitByPipe = metricData.split("\\|");
				if (splitByPipe.length == 3 ) {
					String value = splitByPipe[2].split("\\}\\=\\{")[1].replaceAll("\\}\\}", "").replaceAll("p99=", "").replaceAll("p50=", "").replaceAll("min=", "").replaceAll("max=", "").replaceAll("p95=", "").replaceAll("mean=", "").replaceAll(" ", "");
					double max = Double.valueOf(value.split(",")[3]);
					String key = splitByPipe[1];
				
					LatencyMap.put(key, max);
					LatencyMapTime.put(key, System.currentTimeMillis());
				}
			}

	}
}
