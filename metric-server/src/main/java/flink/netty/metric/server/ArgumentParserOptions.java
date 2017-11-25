package flink.netty.metric.server;

import org.apache.commons.cli.Option;

public class ArgumentParserOptions {
	public static final Option MITIGATE_OPT = Option.builder("mitigate").build();
	public static final Option HELP_OPT = Option.builder("h").build();
	public static final Option SLA_MAX_LATENCY_OPT = Option.builder("s").argName("SLA").desc("SLA Max Pipeline Delay (ms)")
			.hasArg().build();
	public static final Option INTERVAL_OPT = Option.builder("i").argName("Interval").desc("Backpressure Check Interval (ms)")
			.hasArg().build();
	public static final Option JOB_OPT = Option.builder("j").argName("Job").desc("Job description").hasArg().build();
	public static final Option PORT_OPT = Option.builder("p").argName("Port").desc("Metric Server Port").hasArg().build();
}
