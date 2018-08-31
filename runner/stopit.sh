#! /bin/sh
ansible-playbook \
stopit.yml \
-i hosts \
-e '@config/system.json' \
-e '@config/experiment.json' \
-e '@config/env.json' \
-e '@config/runtime.json' \
-e "experiment"="$DATE" \
-e "run"=$i \
-e "flink_commit_id"="661ba399397cce608b0263d8edd202a4726e4622" \
-e "mera_commit_id"="fubar"
