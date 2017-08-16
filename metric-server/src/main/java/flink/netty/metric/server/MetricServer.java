package flink.netty.metric.server;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import flink.netty.metric.server.backpressure.dector.BackpressureDector;
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
	public static  BackpressureData backpressureData = new BackpressureData();
	public MetricServer(int port) {
		this.port = port;
	}
	
	public static synchronized void writeLineToFile(String line) {
		if (line.contains("#")) {
			try {
				fw.write(sanitizeLine(line.replaceAll("#", System.currentTimeMillis() + "")));
			} catch (IOException e) {
				System.out.println("Error writing line to File: " + e.getMessage());
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
		EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap(); // (2)
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class) // (3)
					.childHandler(new ChannelInitializer<SocketChannel>() { // (4)
						@Override
						public void initChannel(SocketChannel ch) throws Exception {
							ch.pipeline().addLast(new LineDecoder(), new MetricServerHandler());
						}
					}).option(ChannelOption.SO_BACKLOG, 128) // (5)
					.childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

			// Bind and start to accept incoming connections.
			ChannelFuture f = b.bind(port).sync(); // (7)

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

	public static void main(String[] args) throws Exception {
		int port;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		} else {
			port = 9888;
		}
		try {
			fw = new FileWriter("metrics.csv", true);
		} catch (Exception e) {
			System.out.println("Error while setting up FileWriter.");
		}
		System.out.println("BackpressuerDector started.");
		new Thread(new BackpressureDector()).start();
		System.out.println("FlinkNettyMetricServer started.");
		new MetricServer(port).run();
	}
}