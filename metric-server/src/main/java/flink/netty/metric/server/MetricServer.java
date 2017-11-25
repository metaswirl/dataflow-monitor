package flink.netty.metric.server;

import java.io.FileWriter;
import java.io.IOException;

import flink.netty.metric.server.backpressure.dector.BackpressureDetector;
import io.netty.bootstrap.ServerBootstrap;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Discards any incoming data.
 */
public class MetricServer {

	private int port;
	public static FileWriter fw;
	public static FileWriter fwMeter;
	public static  BackpressureData backpressureData = new BackpressureData();
	public static boolean mitigate = true;
	public MetricServer(int port) {
		this.port = port;
	}
	
	public static synchronized void writeLineToFile(String line) {
		if (line.contains("#")) {
			if(line.contains("numBytesOutPerSecond") || line.contains("numRecordsOutPerSecond")) {
				try {
					//won't become bottle neck, because "write" is buffered, so not forced to write every line immediately. 
					fwMeter.write(sanitizeLine(line.replaceAll("#", System.currentTimeMillis() + "")));
				} catch (IOException e) {
					System.out.println("Error writing line to File: " + e.getMessage());
				}
			} else {
				try {
					fw.write(sanitizeLine(line.replaceAll("#", System.currentTimeMillis() + "")));
				} catch (IOException e) {
					System.out.println("Error writing line to File: " + e.getMessage());
				}
			}

		}
	}

	public static String sanitizeLine(String str) {
		String result = str;
		result = result.replace("PoolUsage", "");
		result = result.replace("loadgen112", "Ma");
		result = result.replace("loadgen113", "Sl1");
		result = result.replace("loadgen114", "Sl2");
		result = result.replace("loadgen115", "Sl3");
		result = result.replace("Unnamed", "");
		result = result.replace("Source: Socket Stream", "Source");
		result = result.replace("Flat Map", "Map");
		return result;
	}

	public void run() throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup(); 
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap(); 
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class) 
					.childHandler(new ChannelInitializer<SocketChannel>() { 
						@Override
						public void initChannel(SocketChannel ch) throws Exception {
							ch.pipeline().addLast(new LineDecoder(), new MetricServerHandler());
						}
					}).option(ChannelOption.SO_BACKLOG, 128) 
					.childOption(ChannelOption.SO_KEEPALIVE, true); 

			// Bind and start to accept incoming connections.
			ChannelFuture f = b.bind(port).sync(); 

			// Wait until the server socket is closed.
			// In this example, this does not happen, but you can do that to
			// gracefully
			// shut down your server.
			f.channel().closeFuture().sync();
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}
	void init(String[] args) {

	}
	public static void main(String[] args) throws Exception {
		
		ConfigOptions configOptions = new ConfigOptions(args);
		int port = configOptions.PORT;
		mitigate = configOptions.MITIGATE;
		try {
			fw = new FileWriter("metrics.csv", true);
			fwMeter = new FileWriter("meter-metrics.csv", true);
		} catch (Exception e) {
			System.out.println("Error while setting up FileWriter.");
		}
		System.out.println("BackpressuerDector started.");
		new Thread(new BackpressureDetector(configOptions)).start();
		System.out.println("FlinkNettyMetricServer started.");
		new MetricServer(port).run();
	}
}