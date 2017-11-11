package flink.netty.metric.server;


import io.netty.buffer.ByteBuf;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * Handles a server-side channel.
 */
public class MetricServerHandler extends ChannelInboundHandlerAdapter { 

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
    	ByteBuf in = (ByteBuf) msg;
    	
        try {
        	String received = in.toString(io.netty.util.CharsetUtil.US_ASCII);
        	MetricServer.writeLineToFile(received);
        	MetricServer.backpressureData.updateBufferPoolUsageByLine(received);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { 
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }
}
