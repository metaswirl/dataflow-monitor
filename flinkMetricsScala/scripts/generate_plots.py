#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2018 Niklas Semmler <niklas@inet.tu-berlin.de>
#
# Distributed under terms of the MIT license.

import pandas as pd
import os
import matplotlib.pyplot as plt

def load_metrics(folder):
    metrics = pd.read_csv("/tmp/" + folder + "/metrics.csv")
    metrics.key = metrics.key.str.replace("pyramid.taskmanager.[^.]*.Scala SocketTextStreamWordCount Example.", "")
    return metrics.pivot("time", "key", "value").fillna(0)

def plot_by_key(metrics, key, roll=1):
    cols = [c for c in metrics.columns if key in c]
    f = plt.figure(figsize=(20,10))
    ax = f.add_subplot(111)
    metrics[cols].rolling(roll, center=False).mean().plot(ax=ax)
    ax.legend(bbox_to_anchor=(1,1))
    f.subplots_adjust(right=0.8)
    f.suptitle(key)

def plot_by_key_by_op(metrics, key, op, roll=1):
    cols = [c for c in metrics.columns if key in c and op in c]
    f = plt.figure(figsize=(20,10))
    ax = f.add_subplot(111)
    metrics[cols].rolling(roll, center=False).mean().plot(ax=ax)
    ax.legend(bbox_to_anchor=(1,1))
    f.subplots_adjust(right=0.8)
    f.suptitle("{}-{}".format(key, op))

folder = sorted(filter(lambda x: "mera" in x, os.listdir("/tmp")))[-1]
metrics = load_metrics(folder)

for k in ["numRecordsInPerSecond", "numRecordsOutPerSecond", "buffers.outPoolUsage", "buffers.inPoolUsage"]:
    plot_by_key(metrics, k, 10)
    plt.savefig("/tmp/{}/{}.png".format(folder, k))
    plot_by_key_by_op(metrics, k, "Filter", 10)
    plt.savefig("/tmp/{}/{}_{}.png".format(folder, k, "Filter"))
