import logging
import requests
import json
from os.path import join as pjoin
import os
import time
import argparse

logging.basicConfig(filename="bottleneck_manager.log",
                    filemode='a',
                    format="%(asctime)-15s %(levelname)s %(message)s",
                    level=logging.DEBUG)

K_DURATION = 'duration'
K_BN = 'bottlenecks'
K_BN_START = 'start'
K_BN_NODE = 'node'
K_BN_DELAY = 'delay'

os.environ["NO_PROXY"] = "localhost,127.0.0.1,172.16.0.112,172.16.0.113,172.16.0.114,172.16.0.115,172.16.0.116,loadgen112,loadgen113,loadgen114,loadgen115,loadgen116,adenin,thymin,guanin,cytosin,desoxyribose,{}".format(os.environ.get("NO_PROXY", ""))
os.environ["no_proxy"] = "localhost,127.0.0.1,172.16.0.112,172.16.0.113,172.16.0.114,172.16.0.115,172.16.0.116,loadgen112,loadgen113,loadgen114,loadgen115,loadgen116,adenin,thymin,guanin,cytosin,desoxyribose,{}".format(os.environ.get("no_proxy", ""))

class BottleneckException(Exception):
    pass

class BottleneckManager:
    state_dir = None
    bottlenecks = None

    def __init__(self, state_dir, bottlenecks):
        self.state_dir = state_dir
        self.bottlenecks = bottlenecks

    def validate_bottlenecks(self, bn_addresses):
        deletes = []

        for key, address in bn_addresses.items():
            logging.debug("Checking bottleneck address {}->{}".format(key, address))
            reachable = False
            try:
                ret = requests.get("http://" + address)
                logging.debug(ret.text)
                try:
                    js = ret.json()
                except json.decoder.JSONDecodeError:
                    logging.warning("could not decode " + ret.text)

                if ret.ok and "delay" in js:
                    reachable = True
            except requests.exceptions.ConnectionError:
                logging.warning("connection error")


            if not reachable:
                logging.warn("could not access {} at {}".format(key, address))
                deletes.append(key)

        for key in deletes:
            del bn_addresses[key]

        return bn_addresses

    def read_bottlenecks(self):
        bn_addresses = {}
        for fil in os.listdir(self.state_dir):
            if fil.startswith("bottleneck-"):
                bn_id = fil[11:]
                fpath = pjoin(self.state_dir, fil)
                with open(fpath, 'r') as f:
                    bn_addresses[bn_id] = f.read().strip()
                    logging.debug("Found bottleneck file {} with id {} containing address {}".format(
                        fpath, bn_id, bn_addresses[bn_id]))
        return bn_addresses

    def set_bottleneck(self, address, delay):
        ret = 0
        retry = 0
        url = "http://{}/".format(address)
        payload=json.dumps({'delay':delay})
        r = requests.post(url, data=payload)

        if (r.status_code != 200):
            raise BottleneckException("Could not post {} to {}. status code {}".format(
                payload, url, str(r.status_code)))

    def run(self, start):
        bottlenecks = self.bottlenecks
        bn_addresses = self.read_bottlenecks()
        bn_addresses = self.validate_bottlenecks(bn_addresses)

        bns_in_job = set([b[K_BN_NODE] for b in bottlenecks])
        bns_parsed = set([k for k in bn_addresses])
        if len(bns_in_job - bns_parsed) > 0:
            logging.debug("bottlenecks in config " + str(bns_in_job))
            logging.debug("bottlenecks as can be learned from files " + str(bns_parsed))
            logging.debug("difference " + str(bns_in_job - bns_parsed))
            raise BottleneckException("Not all bottlenecks could be registered. ")

        for bottleneck in sorted(bottlenecks, key=lambda x: x[K_BN_START]):
            diff = start + bottleneck[K_BN_START] - time.time()
            if diff > 0:
                logging.info("waiting for {} seconds".format(diff))
                time.sleep(diff)
            logging.info("setting bottleneck {} to a delay of {}".format(
                bottleneck[K_BN_NODE], bottleneck[K_BN_DELAY]))
            self.set_bottleneck(bn_addresses[bottleneck[K_BN_NODE]],
                                        bottleneck[K_BN_DELAY])

# modified from: https://stackoverflow.com/questions/11415570/directory-path-types-with-argparse
def readable_dir(prospective_dir):
    if not os.path.isdir(prospective_dir):
        raise Exception("{0} is not a valid path for a dir".format(prospective_dir))
    if not os.access(prospective_dir, os.R_OK):
        raise Exception("{0} is not a readable dir".format(prospective_dir))

    return prospective_dir

def readable_file(prospective_file):
    if not os.path.isfile(prospective_file):
        raise argparse.ArgumentTypeError("{0} is not a valid path for a file".format(prospective_file))
    if not os.access(prospective_file, os.R_OK):
        raise argparse.ArgumentTypeError("{0} is not a readable file".format(prospective_file))

    return prospective_file

def parse_args():
    parser = argparse.ArgumentParser(prog='PROG')
    parser.add_argument('-s', '--state_dir', help='path to store state',
                        required=True, type=readable_dir)
    parser.add_argument('-e', '--experiment', help='path to experiment.json',
                        required=True, type=readable_file)
    result = parser.parse_args()
    return result

def main():
    logging.info("started")
    result = parse_args()
    start = time.time()
    with open(result.experiment, 'r') as f:
        exp_dict = json.load(f)

    remaining_warmup = start + exp_dict['warmup'] - time.time()

    if remaining_warmup > 0:
        logging.info("sleeping warmup time")
        time.sleep(remaining_warmup)

    bm = BottleneckManager(result.state_dir, exp_dict[K_BN])
    bm.run(start)
    rest = exp_dict[K_DURATION] + start - time.time()

    if rest > 0:
       logging.info("Waiting for {} seconds before ending".format(rest))
       time.sleep(rest)

if __name__ == "__main__":
    main()
