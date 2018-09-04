#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2018 Niklas Semmler <niklas@inet.tu-berlin.de>
#
# Distributed under terms of the MIT license.

from matplotlib import pyplot as plt
from collections import OrderedDict
import pandas as pd
import argparse
from os.path import join as pjoin

"""
Create timeseries plots 
"""

def shorten_key(key):
    parts = key.split('.')
    return ".".join(parts[4:])

def define_columns(key):
    columns = []
    for c in summary.columns:
        if key in c:
            columns.append(c)
    return columns


parser = argparse.ArgumentParser(prog='PROG')
parser.add_argument('-i', '--input-dir', help='input dir',
                    required=True, dest="input")
#parser.add_argument('-o', '--output-dir', help='output dir',
#                    required=True, dest="output")
result = parser.parse_args()
output_dir = result.input
plot = False

def save(name):
    fpath = pjoin(output_dir, name)
    print("Saving " + fpath)
    plt.savefig(fpath)

metrics = pd.read_csv(result.input + "/metrics.csv", sep=';')
metrics.time = pd.to_datetime(metrics.time, unit='ms')
metrics.key = metrics.key.apply(shorten_key)
data = metrics. \
    set_index('time'). \
    groupby('key'). \
    resample('5S'). \
    mean(). \
    reset_index()

conditions = OrderedDict([
    ('output rate of sources', data.key.str.contains('Source') & \
        data.key.str.contains('numRecordsOut') & \
        data.key.str.contains('PerSecond')
     ),
    ('input rate of sink', data.key.str.contains('Sink') & \
        data.key.str.contains('numRecordsIn') & \
        data.key.str.contains('PerSecond')
     ),
    ('bottleneck delay', data.key.str.contains('bottleneckDelay')),
    ('backlog', data.key.str.contains('backlog')),
    ('latency', data.key.str.contains('latency'))
])
figsize=(20, 10)
fig, axes = plt.subplots(len(conditions), 1, sharex=True, figsize=figsize)
for c, ax in zip(conditions, axes):
    data[conditions[c]][['time', 'value']].groupby('time').sum().plot(ax=ax, title=c)
save("timeseries1.png")

conditions = OrderedDict([
    ('dropRate', data.key.str.contains('dropRate')),
    ('input queue saturation', data.key.str.contains('inPoolUsage')),
    ('output queue saturation', data.key.str.contains('outPoolUsage')),
    ('backlog', data.key.str.contains('backlog'))
])
figsize=(20, 10)
fig, axes = plt.subplots(len(conditions), 1, sharex=True, figsize=figsize)
for c, ax in zip(conditions, axes):
    data[conditions[c]][['time', 'value']].groupby('time').sum().plot(ax=ax, title=c)
save("timeseries2.png")

newd = data.pivot(index='time', columns='key', values='value')
summary = newd.groupby('bottleneck.0.bottleneckDelay').mean()
columns = define_columns("outPoolUsage")
summary[columns].plot(kind='bar')
save("bar-outPoolUsage.png")

columns = define_columns("inPoolUsage")
summary[columns].plot(kind='bar')
save("bar-inPoolUsage.png")

columns = define_columns("latency")
summary[columns].plot(kind='bar')
save("bar-latency.png")

columns = define_columns("RecordsOutPerSecond")
summary[columns].plot(kind='bar')
save("bar-RecordsOutPerSecond.png")

columns = define_columns("RecordsInPerSecond")
summary[columns].plot(kind='bar')
save("bar-RecordsInPerSecond.png")

columns = define_columns("latency")
summary[columns].head()
summary['SUM_LATENCY'] = 0
for c in columns:
    summary['SUM_LATENCY'] += summary[c]

summary['SUM_LATENCY'].plot(kind='bar', legend=False)
save('bar-agg-latency.png')
