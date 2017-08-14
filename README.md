# Build Metric Jar
	cd flinkMetrics/
	mvn install


# Build Metric Server:
	cd metric-server/
	mvn install

# Start Server
	java -jar target/metric-server-run.jar

Server will run on Port 9888
Server will write data to file: metrics.csv