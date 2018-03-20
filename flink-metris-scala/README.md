# MERA Monitor

So far only supports Flink deployment on localhost.

## Prerequisites

- Flink 1.4
- Scala 2.11
- SBT
- Python 3

## Installation of Flink Plugin

Compile library

    > sbt assembly

Copy to library folder in flink installation 

    > cp target/scala-2.11/mera-assembly-0.2.jar ${FLINK_DIR}/lib/
    
Add lines to config file

    > echo "metrics.reporters: mera_plugin
    metrics.reporter.mera_plugin.class: berlin.bbdc.inet.mera.flinkPlugin.FlinkMetricPusher
    metrics.reporter.mera_plugin.interval: 100 MILLISECONDS" >> ${FLINK_DIR}/conf/flink-conf.yaml

## Starting MERA

    > sbt "run-main berlin.bbdc.inet.mera.server.Starter" 
    
Metrics will be stored in /tmp/mera_<X>, where <X> is the current time in milliseconds.

You can generate some plots with the script:

    > python scripts/generate_plots.py
    
However its capabilities are so far pretty limited.
