#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2018 Niklas Semmler <niklas@inet.tu-berlin.de>
#
# Distributed under terms of the MIT license.

import pandas as pd
import matplotlib.pyplot as plt
import os
import sys
import re

def plot_inferred_metrics_nodes(folder):
    data = pd.read_csv(folder + "/inferred_metrics_nodes.csv", sep=";")
    data.time = pd.to_datetime(data.time, unit='ms')
    data = data.set_index('time')

    keys = set(data.columns) - set(["task", "time", "selectivity", "capacity"])

    #data = data.replace("Infinity", 10**9)

    first = True
    count = 0
    fig, axes = plt.subplots(len(keys), 1, sharex=True)
    label_set = None

    for el in keys:
        axes[count].set_title(el)
        #ax = fig.add_subplot(10 + 100 * len(keys) + count, sharex=True)
        nd = data.pivot(columns="task", values=el)
        res = nd.plot(ax=axes[count], legend=False)
        lines, labels = res.get_legend_handles_labels()
        if first:
            first = False
            label_set = set(labels)
            fig.legend( lines, labels, loc = 'lower center' , ncol=5 )
        else:
            assert len(set.union(set(labels),label_set) - set.intersection(set(labels),label_set)) == 0
        count += 1


def plot_inferred_metrics_edges(folder):
    data = pd.read_csv(folder + "/inferred_metrics_edges.csv", sep=";")
    data.time = pd.to_datetime(data.time, unit='ms')
    data = data.set_index('time')

    keys = set(data.columns) - set(["time", "source", "target"])

    #data = data.replace("Infinity", 10**9)

    first = True
    count = 0
    label_set = None
    fig, axes = plt.subplots(len(keys), 1, sharex=True)
    for el in keys:
        axes[count].set_title(el)
        #ax = fig.add_subplot(10 + 100 * len(keys) + count, sharex=True)
        nd = pd.pivot_table(data, index="time", columns=["source", "target"], values=el)
        res = nd.plot(ax=axes[count], legend=False)
        lines, labels = res.get_legend_handles_labels()
        if first:
            first = False
            label_set = set(labels)
            fig.legend( lines, labels, loc = 'lower center' , ncol=4)
        else:
            assert len(set.union(set(labels),label_set) - set.intersection(set(labels),label_set)) == 0
        count += 1

def plot_raw_metrics(folder, keys):
    metrics = pd.read_csv(folder + "/metrics.csv", sep=";")
    metrics.key = metrics.key.str.replace("pyramid.taskmanager.[^.]*.Scala SocketTextStreamWordCount Example.", "")
    metrics.time = pd.to_datetime(metrics.time, unit='ms')
    metrics = metrics.pivot("time", "key", "value").fillna(0)

    def plot_by_key(ax, metrics, key, roll=1):
        ax.set_title(key)
        cols = {c:re.sub("." + key, "", c) for c in metrics.columns if c.endswith(key)}
        metrics = metrics.rename(columns=cols)
        return metrics[list(cols.values())].rolling(roll, center=False).mean().plot(ax=ax, legend=False)

    fig, axes = plt.subplots(len(keys), 1, sharex=True)
    first = True
    label_set = None
    i = 0
    for k in keys:
        res = plot_by_key(axes[i], metrics, k, 20)
        lines, labels = res.get_legend_handles_labels()
        if first:
            first = False
            label_set = set(labels)
            fig.legend( lines, labels, loc = 'lower center' , ncol=3 )
        else:
            assert len(set.union(set(labels),label_set) - set.intersection(set(labels),label_set)) == 0

        i += 1

folder = "/tmp/" + sorted(filter(lambda x: "mera" in x, os.listdir("/tmp")))[-1]

plot_inferred_metrics_edges(folder)
plot_inferred_metrics_nodes(folder)
plot_raw_metrics(folder, ["numRecordsInPerSecond", "numRecordsOutPerSecond",
    "buffers.outPoolUsage", "buffers.inPoolUsage", "numRecordsOut",
    "numRecordsIn"])

plt.show()
