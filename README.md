# Usage

For now we only support a single node setup

Requirements:
- Flink 1.4.2
- Scala >=2.11 (tested for 2.11)
- sbt

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
        metrics.reporter.monitor.class: berlin.bbdc.inet.flinkPlugin.FlinkMetricPusher
        EOF 

## Running the monitor

We assume that Flink is co-located with the monitor and its jobmanager is reachable through _localhost_.

    cd flink-monitor/
    sbt run 
