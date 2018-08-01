[![build status](https://gitlab.inet.tu-berlin.de/flink/mera-monitor/badges/master/build.svg)](https://gitlab.inet.tu-berlin.de/flink/mera-monitor/pipelines)
[![coverage report](https://gitlab.inet.tu-berlin.de/flink/mera-monitor/badges/master/coverage.svg)](https://flink.gitlab.inet.tu-berlin.de/mera-monitor)

# Usage

For now we only support a single node setup

Requirements:
- Flink 1.4.2
- Scala >=2.11 (tested for 2.11)
- sbt
- gurobi (for the optimizer only)

## Building 

Build everything with:

    sbt clean assembly

Run the monitor with

    sbt monitor/run

Or use the short-hand

    sbt "monitor/runMain berlin.bbdc.inet.mera.monitor.Starter"

## Configuring Flink

Before using the monitor, your version of Flink first has to be instrumented.

1. Execute the following commands

        cd flink-reporter
        sbt assembly

2. Copy the resulting jar to the `lib/` folder of your Flink deployment. 

        cp target/scala-2.11/flink-reporter-assembly-0.1.jar $FLINK_PATH/lib

3. Add the following entries to your flink-config:

        cat <<EOF >> $FLINK_PATH/conf/flink-conf.yaml
        metrics.reporters: monitor
        metrics.reporter.monitor.class: berlin.bbdc.inet.mera.reporter.FlinkMetricPusher
        metrics.reporter.monitor.interval: 500 MILLISECONDS
        EOF 

## Running the monitor

We assume that Flink is co-located with the monitor and its jobmanager is reachable through _localhost_.

As a prerequisite of the optimizer Gurobi has to be installed.

Get it here:

- Download: http://www.gurobi.com/downloads/gurobi-optimizer
- As an Academic get your license here: http://www.gurobi.com/academia/for-universities
- Activate your license with `grbgetkey`
- Follow the guide here: http://www.gurobi.com/documentation/8.0/quickstart_linux/software_installation_guid.html

        - GUROBI_HOME should point to your <installdir>.
        - PATH should be extended to include <installdir>/bin.
        - LD_LIBRARY_PATH should be extended to include <installdir>/lib.

Then you can start the monitor either via

    cd flink-monitor/
    sbt run 

Or 

    sbt assembly
    java -cp "target/scala-2.11/mera-assembly-0.2.jar:lib/gurobi.jar" berlin.bbdc.inet.mera.server.Starter

## Running the WebUI

After running the "sbt run" simply go to 
    
    http://localhost:12345
   
To see a running LinePlot a first Metric has to be initialized. 

