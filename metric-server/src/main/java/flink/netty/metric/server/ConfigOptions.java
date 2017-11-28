package flink.netty.metric.server;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ConfigOptions {

	final double SLA_MAX_DEFAULT = 0.0;
	public final double SLA_MAX_LATENCY;

	public final boolean MITIGATE;

	final int INTERVAL_DEFAULT = 1000;
	public final int INTERVAL;

	final String JOB_DESC_DEFAULT = "SocketWordCountParallelism";
	public final String JOB_DESC;

	final int PORT_DEFAULT = 9888;
	public final int PORT;

	public final String JOB_COMMAND_DEFAULT = " -n examples/streaming/SocketWordCountParallelism2.jar --port 9001 --sleep 30000 --para 3 --parareduce 4 --ip loadgen112 --node 172.16.0.114 --sleep2 0 --timewindow 0 --timeout 100 --path test";

	public ConfigOptions(final String[] args) throws ParseException {
		Options options = new Options();
		initOptions(options);
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);
		if (cmd.hasOption("h")) {
			new HelpFormatter().printHelp("java -jar metric-server-run.jar -p <server_port> -i <interval>", options,
					true);
			System.exit(0);
		}
		this.SLA_MAX_LATENCY = Double.parseDouble(cmd.getOptionValue("s", String.valueOf(SLA_MAX_DEFAULT)));
		this.INTERVAL = Integer.parseInt(cmd.getOptionValue("i", String.valueOf(INTERVAL_DEFAULT)));
		this.JOB_DESC = cmd.getOptionValue("j", JOB_DESC_DEFAULT);
		this.PORT = Integer.parseInt(cmd.getOptionValue("p", String.valueOf(PORT_DEFAULT)));
		if (cmd.hasOption("mitigate")) {
			MITIGATE = true;
		} else {
			MITIGATE = false;
		}
	}

	public static void initOptions(Options options) {
		options.addOption(ArgumentParserOptions.SLA_MAX_LATENCY_OPT);
		options.addOption(ArgumentParserOptions.MITIGATE_OPT);
		options.addOption(ArgumentParserOptions.INTERVAL_OPT);
		options.addOption(ArgumentParserOptions.JOB_OPT);
		options.addOption(ArgumentParserOptions.PORT_OPT);
		options.addOption(ArgumentParserOptions.HELP_OPT);
	}

}
