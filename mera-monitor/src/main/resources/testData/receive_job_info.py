#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2018 Niklas Semmler <niklas@inet.tu-berlin.de>
#
# Distributed under terms of the MIT license.
import requests
import json

host="http://localhost:8081"

r = requests.get(host + "/jobs")
jobs = json.loads(r.text)

if len(jobs['jobs-running']) < 1:
    raise Exception("No job running")
elif len(jobs['jobs-running']) > 1:
    print("Taking the first job: " + jid)

with open('twitter_jobs.json', 'w') as f:
    f.write(json.dumps(jobs, indent=2))

jid = jobs['jobs-running'][0]


r = requests.get(host + "/jobs/" + jid)
job = json.loads(r.text)
with open('twitter_job_{}.json'.format(jid), 'w') as f:
    f.write(json.dumps(job, indent=2))

for v in job['vertices']:
    vid = v['id']
    r = requests.get(host + "/jobs/" + jid + "/vertices/" + vid)
    operator_info = json.loads(r.text)
    name = operator_info['name'].split(':')[0].lower().replace(' ', '_')
    with open('twitter_job_{}_vertex_{}.json'.format(jid, vid), 'w') as f:
        f.write(json.dumps(operator_info, indent=2))

    for subtask in operator_info['subtasks']:
        print(subtask['subtask'], subtask['host'])
