#! /bin/sh
#
if [ -z ${FLINK_DIR+x} ]; then
    echo "\$FLINK_DIR must be set!"
    exit 1
fi
if [ -z ${MERA_DIR+x} ]; then
    echo "\$MERA_DIR must be set!"
    exit 1
fi

$FLINK_DIR/bin/stop-cluster.sh
sleep 3
$FLINK_DIR/bin/start-cluster.sh

CMD_FLINK="-c $FLINK_DIR bin/flink run $MERA_DIR/use-cases/three-stage-wordcount/target/scala-2.11/ThreeStageWordCount-assembly-0.1.jar"
CMD_MERA="-c $MERA_DIR/flink-monitor sbt"
CMD_README="less $MERA_DIR/use-cases/three-stage-wordcount/README.md"

tmux new-session $CMD_FLINK \; split-window -v \; split-window -h $CMD_README \; select-pane -t 0 \;  split-window -h $CMD_MERA \; resize-pane -t 0 -y 40 \;
