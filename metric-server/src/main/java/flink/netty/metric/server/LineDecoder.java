package flink.netty.metric.server;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class LineDecoder extends ByteToMessageDecoder {

	@Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) { // (2)
        String received = msg.toString(io.netty.util.CharsetUtil.US_ASCII);
       
		if (!received.contains("\n")) {
            return;
        }
        
        out.add(msg.readBytes(received.indexOf("\n") + 1));
    }

}
