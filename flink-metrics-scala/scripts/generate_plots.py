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
    metrics = pd.read_csv("/tmp/" + folder + "/metrics.csv", sep=";")
    metrics.key = metrics.key.str.replace("pyramid.taskmanager.[^.]*.Scala SocketTextStreamWordCount Example.", "")
    new_metrics = metrics.pivot("time", "key", "value").fillna(0)
    del(metrics)
    return new_metrics

def plot_by_key(ax, metrics, key, roll=1):
    cols = [c for c in metrics.columns if key in c]
    metrics[cols].rolling(roll, center=False).mean().plot(ax=ax)
    ax.legend(bbox_to_anchor=(1,1))
    f.subplots_adjust(right=0.8)
    f.suptitle(key)

def plot_by_key_by_op(ax, metrics, key, op, roll=1):
    cols = [c for c in metrics.columns if key in c and op in c]
    metrics[cols].rolling(roll, center=False).mean().plot(ax=ax)
    ax.legend(bbox_to_anchor=(1,1))
    f.subplots_adjust(right=0.8)
    f.suptitle("{}-{}".format(key, op))

folder = sorted(filter(lambda x: "mera" in x, os.listdir("/tmp")))[-1]
print("loading metrics from " + folder)
metrics = load_metrics(folder)

i = 1
f = plt.figure(figsize=(20,10))
for k in ["numRecordsInPerSecond", "numRecordsOutPerSecond", "buffers.outPoolUsage", "buffers.inPoolUsage"]:
    ax = f.add_subplot(220 + i)
    plot_by_key(ax, metrics, k, 20)
    i += 1

fname = "/tmp/{}/plot.png".format(folder)
print("saving to " + fname)
plt.savefig(fname)
plt.show()

#f = plt.figure(figsize=(20,10))
#for k in ["numRecordsInPerSecond", "numRecordsOutPerSecond", "buffers.outPoolUsage", "buffers.inPoolUsage"]:
#    ax = f.add_subplot(111)
#    plot_by_key_by_op(ax, metrics, k, "Filter", 10)
#    plt.savefig("/tmp/{}/{}_{}.png".format(folder, k, "Filter"))
