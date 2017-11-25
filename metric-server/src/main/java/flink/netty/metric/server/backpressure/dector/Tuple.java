package flink.netty.metric.server.backpressure.dector;

import java.util.ArrayList;
import java.util.List;

public class Tuple {
	public double inputBufferPoolusage;
	public double outputBufferPoolusage;
	private List<Double> inputBPusageList;
	private List<Double> outBPusageList;

	public Tuple(double inputBufferPoolusage, double outputBufferPoolusage) {
		this.inputBufferPoolusage = inputBufferPoolusage;
		this.outputBufferPoolusage = outputBufferPoolusage;
		this.inputBPusageList = new ArrayList<Double>();
		this.outBPusageList = new ArrayList<Double>();
	}

	public double getInputBufferPoolusage() {
		return inputBufferPoolusage;
	}

	public double getOutputBufferPoolusage() {
		return outputBufferPoolusage;
	}

	public void setInputBufferPoolusage(double update) {
		this.inputBufferPoolusage = update;
		updateInputBPusageList(update);
	}

	public void setOutputBufferPoolusage(double update) {
		this.outputBufferPoolusage = update;
		updateOutputBPusageList(update);
	}
	
	private void updateInputBPusageList(double inputBPusage) {
		if (inputBPusageList.size() >= BackpressureDetector.TIME_WINDOW) {
			inputBPusageList.remove(0);
		}
		inputBPusageList.add(inputBPusage);
	}

	private void updateOutputBPusageList(double outputBPusage) {
		if (outBPusageList.size() >= BackpressureDetector.TIME_WINDOW) {
			outBPusageList.remove(0);
		}
		outBPusageList.add(outputBPusage);
	}
	public double calculateInputBMA() {
		int countTrue = 0;

		for (double b : inputBPusageList) {
			if (b > 0.99) {
				countTrue++;
			}
		}
		return countTrue / (double) inputBPusageList.size();
	}

	public double calculateOutputBMA() {
		int countTrue = 0;

		for (double b : outBPusageList) {
			if (b > 0.99) {
				countTrue++;
			}
		}
		return countTrue / (double) outBPusageList.size();
	}
}
