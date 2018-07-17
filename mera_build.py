# Run with python3
import os
import shutil
import subprocess
import timeit

MERA_PATH  = '/home/kaichi/IdeaProjects/mera-monitor'
JOB_PATH   = MERA_PATH + '/use-cases/three-stage-wordcount'
FLINK_PATH = '/home/kaichi/Documents/flink-mera'

# remove old mera-commons from cache
Join = input('Remove ~/.ivy2/local/berlin.bbdc.inet/mera-commons_2.11? [y/n]\n')
if Join.lower() == 'yes' or Join.lower()=='y':
	os.system("rm -rf ~/.ivy2/local/berlin.bbdc.inet/mera-commons_2.11")


# remove old flink-loadshedder from cache
Join = input("Remove ~/.ivy2/local/berlin.bbdc.inet/flink-loadshedder_2.11? [y/n]\n")
if Join.lower() == 'yes' or Join.lower()=='y':
	os.system("rm -rf ~/.ivy2/local/berlin.bbdc.inet/flink-loadshedder_2.11")

start = timeit.default_timer()
# compile and publish mera-commons to cache
print("mera-commmons: assembly and publishLocal")
os.system("(cd {}/mera-commons; sbt clean assembly publishLocal)".format(MERA_PATH))

# compile and publishLocal flink-loadshedder
print("flink-loadshedder: assembly and publishLocal")
pidLoadshedder = subprocess.Popen(['sbt', 'clean', 'update', 'assembly', 'publishLocal'], cwd='{}/flink-loadshedder'.format(MERA_PATH))

# compile flink-monitor
print("flink-monitor: assembly")
pidMonitor = subprocess.Popen(['sbt', 'clean', 'update', 'assembly'], cwd='{}/flink-monitor'.format(MERA_PATH))

# compile flink-reporter
print("flink-reporter: assembly")
pidReporter = subprocess.Popen(['sbt', 'clean', 'update', 'assembly'], cwd='{}/flink-reporter'.format(MERA_PATH))

pidLoadshedder.wait()
# compile ThreeStageWordCount with new flink-loadshedder
print("ThreeStageWordCount: assembly")
pidJob = subprocess.Popen(['sbt', 'clean', 'update', 'assembly'], cwd=JOB_PATH)

pidReporter.wait()
print("Copy flink-reporter to flink-mera/lib")
os.system('cp {}/flink-reporter/target/scala-2.11/flink-reporter-assembly-0.1.jar {}/lib'.format(MERA_PATH, FLINK_PATH))

pidJob.wait()
pidMonitor.wait()

stop = timeit.default_timer()
print("Build completed in {}s".format(int(stop-start)))
