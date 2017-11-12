package flink.netty.metric.server.backpressure.dector;

public class TypeIDTuple {
	public int nodeID;
	public String nodeType;
	
	public void TypeIDTuple (int nodeID, String nodeType) {
		this.nodeID = nodeID;
		this.nodeType = nodeType;
	}
}
