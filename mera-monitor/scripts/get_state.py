#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2018 Niklas Semmler <niklas@inet.tu-berlin.de>
#
# Distributed under terms of the MIT license.

""" <program> 14:00:30 14:02:00
    -> graph annotated with state averaged across this time interval
""" 

import networkx as nx
from networkx.readwrite import json_graph
import pandas as pd
import re
import sys
from datetime import datetime as dt

def main(folder, begin, end):
    def read_data(folder, name):
        df = pd.read_csv("{folder}/inferred_metrics_{name}.csv".format(**locals()), sep=";")
        df.time = pd.to_datetime(df.time, unit='ms')
        return df.set_index('time')
    def select_data(df):
        b = begin(df.index[0])
        e = end(df.index[0])
        return df[(df.index > b) & (df.index < e)]
    def add_edge(row):
        x = [y.replace(":", " -") for y in list(row)]
        g.add_edge(x[0], x[1])

    nodes = select_data(read_data(folder, "nodes"))
    edges = select_data(read_data(folder, "edges"))

    # The selectivity at the source can become infinite, as it only produces items
    nodes.selectivity = pd.to_numeric(nodes.selectivity.replace("Infinity", 1.0))
    nodes.capacity = pd.to_numeric(nodes.capacity.replace("Infinity", float("infinity")))

    tasks = nodes.task.unique()

    nodes_res = nodes.groupby("task").mean()
    edges_res = edges.groupby(["source", "target"]).mean()

    links = list(edges_res.index)

    g = nx.DiGraph()
    for task in tasks:
        g.add_node(task, **dict(nodes_res.loc[task]))
    for link in links:
        g.add_edge(link[0], link[1], **dict(edges_res.loc[link]))

    global js_g
    js_g = nx.node_link_data(g)
    print(js_g)

    # Assumption: both share the same time
    # Assumption: Dataset does not cross multiple days

    # TODO: How do we select time?

def format_time(time_str: str):
    h, m, s = [int(x) for x in time_str.split(":")]
    return lambda x: dt(year=x.year, month=x.month, day=x.day, hour=h, minute=m, second=s)

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("{sys.argv[0]} <folder> <begin> <end>".format(**locals()))
        sys.exit(1)

    input_format = re.compile("[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}")
    folder= sys.argv[1]
    begin = sys.argv[2]
    end = sys.argv[3]

    if not (input_format.match(begin) and input_format.match(begin)):
        print("Please format your input as 'XX:XX:XX'")
        sys.exit(1)
    main(folder, format_time(begin), format_time(end))

