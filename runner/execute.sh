#! /bin/sh
if [ $# -lt 1 ]; then
  echo "$0 NUMBER-OF-RUNS"
  exit 1
fi
if [ $1 -lt 1 ]; then
  echo "should at least run once"
  exit 1
fi
DATE="$(date +"%Y-%m-%d_%H:%M:%S" )" 
echo "experiment folder start time: $DATE"

for i in $(seq 1 $1); do
  ansible-playbook \
  experiment.yml \
  -i hosts \
  -e '@config/system.json' \
  -e '@config/experiment.json' \
  -e '@config/env.json' \
  -e '@config/runtime.json' \
  -e "experiment"="$DATE" \
  -e "run"=$i \
  -e "flink_commit_id"="661ba399397cce608b0263d8edd202a4726e4622" \
  -e "mera_commit_id"="fubar"
done
