#!/usr/bin/env python

import subprocess
import argparse
import os
import shutil
import time
import sys

from os.path import join as pjoin

# Extensions:
#  TODO Health check, is mera alive? Has the optimizer been started?
#  TODO Does the bottleneck work
#  TODO Does mera monitor generate metrics
#
#  TODO Extract general metric from mera-monitor. E.g. max average queue
#  saturation over the last time window of x seconds

def start_flink(flink_dir):
    return subprocess.check_call([pjoin(flink_dir, "bin", "start-cluster.sh")])

def stop_flink(flink_dir):
    return subprocess.check_call([pjoin(flink_dir, "bin", "stop-cluster.sh")])

def run_flink(flink_dir, job_jar):
    return subprocess.Popen([pjoin(flink_dir, "bin", "flink"), "run",
                                  job_jar])

def cancel_flink_job(flink_dir):
    res = subprocess.check_output([pjoin(flink_dir, "bin", "flink"), "list"])
    job_id = res.split(b"\n")[2].split(b":")[3].strip().lstrip()
    return subprocess.check_call([pjoin(flink_dir, "bin", "flink"), "cancel", job_id])

def main():
    parser = argparse.ArgumentParser(prog='PROG')
    parser.add_argument('--libJar', help='path to flink-reporter jar file',
                        required=True)
    parser.add_argument('--jobJar', help='path to Flink job jar file',
                        required=True)
    parser.add_argument('--monitorJar', help='path to mera monitor jar file',
                        required=True)
    parser.add_argument('--flinkDir', help='path to Flink directory',
                        required=True)
    parser.add_argument('--timeout', help='seconds after which the job is cancelled',
                        default=100)
    result = parser.parse_args()
    if not os.path.exists(result.libJar):
        raise Exception("library jar path is incorrect")
    if not os.path.exists(result.jobJar):
        raise Exception("job jar path is incorrect")
    if not os.path.exists(result.monitorJar):
        raise Exception("monitor jar path is incorrect")
    if not os.path.exists(result.flinkDir):
        raise Exception("flink directory path is incorrect")

    shutil.copy(result.libJar, pjoin(result.flinkDir, "lib"))
    start_flink(result.flinkDir)
    p_job = run_flink(result.flinkDir, result.jobJar)
    if result.timeout > 0:
        time.sleep(result.timeout)
        if p_job.poll():
            raise Exception("Job ended prematurely")
    else:
        p_job.wait()

    cancel_flink_job(result.flinkDir)

    stop_flink(result.flinkDir)
    print("Waiting for job to finish")
    p_job.wait()
    print("Everything is working")
    sys.exit(0)

if __name__ == "__main__":
    main()
