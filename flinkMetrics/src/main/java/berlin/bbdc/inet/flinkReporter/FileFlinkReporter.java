package berlin.bbdc.inet.flinkReporter;

import com.codahale.metrics.Reporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.graphite.*;
import com.esotericsoftware.minlog.Log;

import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.metrics.CharacterFilter;
import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dgu on 10.02.17.
 */
public class FileFlinkReporter implements MetricReporter, Scheduled, Reporter, CharacterFilter {

	private static final Logger LOG = LoggerFactory.getLogger(FileFlinkReporter.class);

	protected final MetricRegistry registry;
	protected CsvReporter reporter;
	protected Graphite graphite;
	protected GraphiteReporter gReporter;

	private final Map<String, Gauge<?>> gauges = new HashMap<>();
	private final Map<String, Counter> counters = new HashMap<>();
	private final Map<String, Histogram> histograms = new HashMap<>();
	private final Map<String, Meter> meters = new HashMap<>();
	private FileWriter fwGauge;
	private FileWriter fwHistogram;
	private FileWriter fwCounters;
	private FileWriter fwMeters;
	private Socket metricServerSocket = null;
	private DataOutputStream outputStream = null;

	private enum Protocol {
		TCP, UDP
	}

	public void restartConnection() {
		LOG.info("Trying to reconnect with MetricServer.");
		try {
			try {
				metricServerSocket.close();
				outputStream.close();
			} catch (Exception e) {
				LOG.info("Failed to close connections: " + e.getMessage());
			}
			metricServerSocket = new Socket("loadgen112", 9888);
			outputStream = new DataOutputStream(metricServerSocket.getOutputStream());
		} catch (UnknownHostException e) {
			LOG.info("Failed to reconnect with MetricServer: " + e.getMessage());
		} catch (IOException e) {
			LOG.info("Failed to reconnect with MetricServer: " + e.getMessage());
		}
	}

	/***
	 * Constructor
	 */
	public FileFlinkReporter() {
		// Path gaugePath = FileSystems.getDefault().getPath("/tmp/metrics/",
		// "Gauge.csv");
		// Path histoPath = FileSystems.getDefault().getPath("/tmp/metrics/",
		// "Histogram.csv");
		// Path CounterPath = FileSystems.getDefault().getPath("/tmp/metrics/",
		// "Counters.csv");
		// Path meterPath = FileSystems.getDefault().getPath("/tmp/metrics/",
		// "Meters.csv");
		// try {
		// Files.deleteIfExists(gaugePath);
		// Files.deleteIfExists(histoPath);
		// Files.deleteIfExists(CounterPath);
		// Files.deleteIfExists(meterPath);
		// } catch (IOException e1) {
		// LOG.info("Metrics: Error attempting to delete old metric files.");
		// e1.printStackTrace();
		// }
		// fwGauge = null;
		// fwHistogram = null;
		// fwCounters = null;
		// fwMeters = null;
		try {
			fwGauge = new FileWriter("/tmp/metrics/Gauge.csv", true);
			fwHistogram = new FileWriter("/tmp/metrics/Histogram.csv", true);
			fwCounters = new FileWriter("/tmp/metrics/Counters.csv", true);
			fwMeters = new FileWriter("/tmp/metrics/Meters.csv", true);
		} catch (IOException e) {
			LOG.info("Metrics: Error while creating files.");
			e.printStackTrace();
		}
		registry = new MetricRegistry();

		try {

			System.out.println("Trying to connect to MetricServer: loadgen112:9888");
			metricServerSocket = new Socket("loadgen112", 9888);
		} catch (Exception e) {
			LOG.info("Exception: " + e.getMessage());
			LOG.info("Using default source IP:Port loadgen112:9888");

		}
		try {
			if (metricServerSocket == null) {
				metricServerSocket = new Socket("loadgen112", 9888);
			}

			outputStream = new DataOutputStream(metricServerSocket.getOutputStream());
			LOG.info("Connected to MetricsServer");
		} catch (Exception e) {
			LOG.info("Failed to connect to default server loadgen112:9091");
		}
		try {
			metricServerSocket.setKeepAlive(true);
			metricServerSocket.setReuseAddress(true);
			metricServerSocket.setSoTimeout(0);
		} catch (Exception e) {
			LOG.info("Failed to set KeepAlive for socket.");
		}
	}

	@Override
	/**
	 * Start reporter
	 */
	public void open(MetricConfig metricConfig) {
		// String filePath = metricConfig.getString("path", "/tmp/metrics.db");
		LOG.info("INET - MetricReporter started");
	}

	@Override
	public void close() {
		LOG.warn("INET - Tearing down Reporter.");
		meters.clear();
		gauges.clear();
		histograms.clear();
		counters.clear();
		// try {
		// fwGauge.close();
		// fwHistogram.close();
		// fwCounters.close();
		// fwMeters.close();
		// } catch (IOException e) {
		// Log.info("Metrics: Error closing files.");
		// e.printStackTrace();
		// }
		try {
			outputStream.writeBytes("disconnect \n");
			outputStream.close();
		} catch (IOException e) {
			Log.info("Metrics: Closing outPutStream failed: " + e.getMessage());
		}
	}

	@Override
	public void notifyOfAddedMetric(Metric metric, String s, MetricGroup metricGroup) {
		// Metric full name - namespace
		// loadgen112.taskmanager.c7c9e536681dfa49a685712420394b75.SocketWordCountParallelism.Flat
		// Map.0.buffers.inPoolUsage-gauge:0.0
		// loadgen112.taskmanager.7641f1a77466868ea34277d05a3670e0.SocketWordCountParallelism.Keyed
		// Reduce.0.numRecordsOutPerSecond
		LOG.info("INET - notify: " + metricGroup.getMetricIdentifier(s, this));

		String fullName = metricGroup.getMetricIdentifier(s, this);
		if (fullName.split("\\.").length > 7) {
			String editedFullName = fullName.split("\\.")[0] + "." + fullName.split("\\.")[4] + "."
					+ fullName.split("\\.")[5] + "." + fullName.split("\\.")[7];
			fullName = editedFullName;
		}

		String key;
		if (metric instanceof Counter) {
			key = fullName + "-" + "counter";
			counters.put(key, (Counter) metric);
			LOG.info("INET - Counter - added: {} as {}", fullName, key);
			// registry.register(fullName, new FlinkCounterWrapper((Counter)
			// metric));
		} else if (metric instanceof Gauge) {
			key = fullName;
			gauges.put(key, (Gauge<?>) metric);
			LOG.info("INET - Gauge - added: {} as {}", fullName, key);
			// registry.register(fullName,
			// FlinkGaugeWrapper.fromGauge((Gauge<?>) metric));
		} else if (metric instanceof Histogram) {
			key = fullName + "-" + "histogram";
			histograms.put(key, (Histogram) metric);
			LOG.info("INET - Histogram - added: {} as {}", fullName, key);
		} else if (metric instanceof Meter) {
			key = fullName + "-" + "meter";
			// Meter meter = (Meter) metric;
			meters.put(key, (Meter) metric);
			LOG.info("INET - Meter - added: {} as {}", fullName, key);
		} else {
			LOG.warn("Cannot add metric of type {}. This indicates that the reporter "
					+ "does not support this metric type.", metric.getClass().getName());
		}
	}

	@Override
	public void notifyOfRemovedMetric(Metric metric, String s, MetricGroup metricGroup) {
		LOG.info("INET - Metrics - removed: {}", s);
		String fullName = metricGroup.getMetricIdentifier(s, this);
		if (metric instanceof Counter) {
			counters.remove(fullName);
		} else if (metric instanceof Gauge) {
			gauges.remove(fullName);
		} else if (metric instanceof Meter) {
			meters.remove(fullName);
		} else if (metric instanceof Histogram) {
			histograms.remove(fullName);
		}
	}

	// XXX: Is this even used?
	@Override
	/***
	 * replace null string in reporting
	 * 
	 * @s metric name
	 */
	public String filterCharacters(String s) {
		char[] chars = null;
		final int strLen = s.length();
		int pos = 0;

		for (int i = 0; i < strLen; i++) {
			final char c = s.charAt(i);
			switch (c) {
			case '.':
				if (chars == null) {
					chars = s.toCharArray();
				}
				chars[pos++] = '-';
				break;
			case '"':
				if (chars == null) {
					chars = s.toCharArray();
				}
				break;

			default:
				if (chars != null) {
					chars[pos] = c;
				}
				pos++;
			}
		}

		return chars == null ? s : new String(chars, 0, pos);
	}

	@Override
	/**
	 * Method from ScheduledReporter. The abstract base class for all scheduled
	 * reporters (i.e., reporters which process a registry's
	 * com.codahale.metrics
	 */
	public void report() {

		for (Map.Entry<String, Gauge<?>> entry : gauges.entrySet()) {
			try {
				// fwGauge.write(entry.getKey() + "|" +
				// entry.getValue().getValue().toString() + "\n");
				if (entry.getKey().contains("Pool") || entry.getKey().contains("latency")
						|| entry.getKey().contains("Length")) {

					try {
						outputStream.writeBytes(
								"#|" + entry.getKey() + "|" + entry.getValue().getValue().toString() + "\n");
					} catch (Exception e) {
						LOG.error("Failed to connection to MetricServer: " + e.getMessage());
						restartConnection();
					}
				}
			} catch (Exception e) {
				LOG.error("Failed to access gauge: " + e.getMessage());
			}
		}
		for (Map.Entry<String, Counter> entry : counters.entrySet()) {
			try {
				// fwCounters.write(entry.getKey() + ":" +
				// System.currentTimeMillis() + ":"
				// + String.valueOf(entry.getValue().getCount()) + "\n");
			} catch (Exception e) {
				LOG.error("Failed to access counter: " + entry.getKey());
			}
		}
		for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
			try {
				// fwHistogram.write(entry.getKey() + ":" +
				// System.currentTimeMillis() + ":"
				// + String.valueOf(entry.getValue().getCount()) + "\n");
			} catch (Exception e) {
				LOG.error("Failed to access histogram: " + entry.getKey());
			}
		}
		for (Map.Entry<String, Meter> entry : meters.entrySet()) {
			try {
				// fwCounters.write(entry.getKey() + ":" +
				// System.currentTimeMillis() + ":"
				// + String.valueOf(entry.getValue().getRate()) + "\n");
				if (entry.getKey().contains("numBytesOutPerSecond")
						|| entry.getKey().contains("numRecordsOutPerSecond")) {
					try {
						outputStream.writeBytes("#|" + entry.getKey() + "|" + entry.getValue().getRate() + "\n");
					} catch (Exception e) {
						LOG.error("Failed to connection to MetricServer: " + e.getMessage());
						restartConnection();
					}
				}
			} catch (Exception e) {
				LOG.error("Failed to access meter: " + entry.getKey());
			}
		}
	}

}
