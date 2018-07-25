#!/usr/bin/python3
import os
import shutil
import subprocess
import timeit
import argparse

MERA_PATH  = os.environ['MERA_PATH'] if 'MERA_PATH' in os.environ else '.'
JOB_PATH   = MERA_PATH + '/use-cases/three-stage-wordcount'
FLINK_PATH = os.environ['FLINK_PATH'] if 'FLINK_PATH' in os.environ else None

def header(name, text):
    print('\033[95m>>>\033[0m \033[1m' + name + ':\033[0m ' + text)

def cleanup(ask=True):
    header("cleanup", "removing old files")
    # remove old mera-commons from cache
    rm_commons = lambda: os.system("rm -rf ~/.ivy2/local/berlin.bbdc.inet/mera-commons_2.11")
    # remove old flink-loadshedder from cache
    rm_loadshedder = lambda: os.system("rm -rf ~/.ivy2/local/berlin.bbdc.inet/flink-loadshedder_2.11")

    if ask:
        join = input('Remove ~/.ivy2/local/berlin.bbdc.inet/mera-commons_2.11? [y/n]\n')
        if join.lower() == 'yes' or join.lower()=='y':
            rm_commons()
        join = input("Remove ~/.ivy2/local/berlin.bbdc.inet/flink-loadshedder_2.11? [y/n]\n")
        if join.lower() == 'yes' or join.lower()=='y':
            rm_loadshedder()
    else:
        rm_commons()
        rm_loadshedder()

def build(sequential=True, test=True):
    start = timeit.default_timer()
    # compile and publish mera-commons to cache
    cmd = ["sbt"]
    if not test:
        cmd.append("\"set test in assembly := {}\"")
    assembly_cmd = " ".join(cmd + ['clean', 'update', 'assembly'])
    assembly_and_publish_cmd = " ".join(cmd + ['clean', 'update', 'assembly', 'publishLocal'])

    header("mera-commmons", "assembly and publishLocal")
    pidCommons = subprocess.Popen(assembly_and_publish_cmd, shell=True, cwd='{}/mera-commons'.format(MERA_PATH))
    pidCommons.wait()

    # compile and publishLocal flink-loadshedder
    header("flink-loadshedder", "assembly and publishLocal")
    pidLoadshedder = subprocess.Popen(assembly_and_publish_cmd, shell=True, cwd='{}/flink-loadshedder'.format(MERA_PATH))
    sequential and pidLoadshedder.wait()

    # compile flink-monitor
    header("flink-monitor", "assembly")
    pidMonitor = subprocess.Popen(assembly_cmd, shell=True, cwd='{}/flink-monitor'.format(MERA_PATH))
    sequential and pidMonitor.wait()

    # compile flink-reporter
    header("flink-reporter", "assembly")
    pidReporter = subprocess.Popen(assembly_cmd, shell=True, cwd='{}/flink-reporter'.format(MERA_PATH))
    sequential and pidReporter.wait()
    not sequential and pidLoadshedder.wait()

    # compile ThreeStageWordCount with new flink-loadshedder
    header("ThreeStageWordCount", "assembly")
    pidJob = subprocess.Popen(assembly_cmd, shell=True, cwd=JOB_PATH)
    pidJob.wait()
    not sequential and pidMonitor.wait()
    not sequential and pidReporter.wait()

    stop = timeit.default_timer()
    print("Build completed in {}s".format(int(stop-start)))

def main():
    parser = argparse.ArgumentParser(prog='PROG')
    parser.add_argument('-f', '--force', help='delete old files',
                        action='store_true')
    parser.add_argument('-p', '--parallel', help='run build in parallel',
                        action='store_true')
    parser.add_argument('-t', '--test', help='execute tests while building',
                        action='store_true')
    result = parser.parse_args()
    cleanup(ask=not result.force)
    build(sequential=not result.parallel, test=result.test)
    
    header("Copy Flink dependency", "")
    if FLINK_PATH:
        print("Copying flink-reporter to flink_dir/lib")
        os.system('cp {}/flink-reporter/target/scala-2.11/flink-reporter-assembly-0.1.jar {}/lib'.format(MERA_PATH, FLINK_PATH))
    else:
        print("Please copy {}/flink-reporter/target/scala-2.11/flink-reporter-assembly-0.1.jar to your flink_dir/lib folder")

if __name__ == "__main__":
    main()
