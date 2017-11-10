package flink.netty.metric.server.backpressure.dector;

public class Tuple {
	  public double inputBufferPoolusage; 
	  public double outputBufferPoolusage; 
	  public Tuple(double inputBufferPoolusage, double outputBufferPoolusage) { 
	    this.inputBufferPoolusage = inputBufferPoolusage; 
	    this.outputBufferPoolusage = outputBufferPoolusage; 
	  } 
	  
	  public double getInputBufferPoolusage() {
		  return inputBufferPoolusage;
	  }
	  public double getOutputBufferPoolusage() {
		  return outputBufferPoolusage;
	  }
	  public void setInputBufferPoolusage(double update) {
		  this.inputBufferPoolusage = update;
	  }
	  public void setOutputBufferPoolusage(double update) {
		  this.outputBufferPoolusage = update;
	  }
}
