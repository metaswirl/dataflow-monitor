package flink.netty.metric.server.backpressure.dector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;

public class MitigateThread implements Runnable {
	
	private String jobDescription;
	private String jobCommand;
	
	public MitigateThread (String jobDescription, String jobCommand) {
		this.jobDescription = jobDescription;
		this.jobCommand = jobCommand;
	}

	private String obtainJobID(String jobDescription) throws IOException, InterruptedException {
		String jobID = null;
		Process process = Runtime.getRuntime().exec("./bin/flink list -r");
		process.waitFor();
		BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;
		while ((line = input.readLine()) != null) {
			// exampleLine:
			// 12.11.2017 21:14:48 : 2513e7cf8e301e1c60e741cc02ce167d :
			// SocketWordCountParallelism (RUNNING)
			if (line.contains(jobDescription)) {
				String splitLine[] = line.split(":");
				if (splitLine.length > 3) {
					jobID = splitLine[3].trim();
					return jobID;
				} else {
					System.out.println("Flink list -r: missmatch. Didn't find job: " + jobDescription);
				}

			}
			System.out.println(line);
		}
		System.out.println("No Job found with description: " + jobDescription);
		return null;
	}

	private String cancelJobWithSavepoint(String jobID) throws ExecutionException, IOException, InterruptedException {
		String cancelJobCommand = "./bin/flink cancel -s /tmp/ " + jobID;
		Process process = Runtime.getRuntime().exec(cancelJobCommand);
		process.waitFor();
		BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null;
		String savePointPath = null;
		while ((line = input.readLine()) != null) {
			System.out.println(line);

			// exampleLine:
			// Cancelled job b7577b03070629cbfa677b6c507194b9. Savepoint stored
			// in /tmp/savepoint-82a54b372b0f.
			if (line.contains("Savepoint stored in")) {
				String splitLine[] = line.split(" ");
				if (splitLine.length > 6) {
					savePointPath = splitLine[6].trim();
					// remove dot at the end of string
					savePointPath = savePointPath.substring(0, savePointPath.length() - 1);
				}
			}
		}
		return savePointPath;
	}

	@Override
	public void run() {

		try {
			String jobID = obtainJobID(jobDescription);
			String savePoint = cancelJobWithSavepoint(jobID);
			String restartCommand = "./bin/flink run -s " + savePoint + jobCommand;
			System.out.println("###############Metric-Command-Restart-JobCommand###############");
			System.out.println(restartCommand);
			System.out.println("###############################################################");
			Process process = Runtime.getRuntime().exec(restartCommand);
			BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			while ((line = input.readLine()) != null) {
				System.out.println(line);
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

	}

}
