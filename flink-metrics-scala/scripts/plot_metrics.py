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
import networkx as nx
import pydot
import shutil
import subprocess

figsize=(20, 10)
# https://stackoverflow.com/questions/8389636/creating-over-20-unique-legend-colors-using-matplotlib
cmap="jet"
dpi=900
plot_format="pdf"
debug=False

pjoin = os.path.join

fname_inf_nodes = "plot_inf_nodes." + plot_format
fname_inf_nodes_bar = "plot_inf_nodes_bar." + plot_format
fname_inf_edges = "plot_inf_edges." + plot_format
fname_raw_metrics = "plot_raw_metrics." + plot_format
fname_raw_metrics_bar = "plot_raw_metrics_bar." + plot_format
fname_graph = "graph." + plot_format


def plot_inferred_metrics_nodes(folder):
    print("- plotting inferred metrics for vertices")

    data = pd.read_csv(folder + "/inferred_metrics_nodes.csv", sep=";")
    data.time = pd.to_datetime(data.time, unit='ms')
    data = data.set_index('time')

    keys = set(data.columns) - set(["task", "time", "capacity"])

    data = data.replace("Infinity", 100)
    data.selectivity = pd.to_numeric(data.selectivity)

    first = True
    count = 0
    fig, axes = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
    fig2, axes2 = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
    fig.suptitle("inferred vertex metrics")
    fig2.suptitle("inferred vertex metrics")
    first_label_list = None

    for el in keys:
        axes[count].set_title(el)
        axes2[count].set_title(el)
        #ax = fig.add_subplot(10 + 100 * len(keys) + count, sharex=True)
        nd = data.pivot(columns="task", values=el)
        if debug:
            print()
            print(el)
            print(nd.describe())
        res = nd.plot(ax=axes[count], legend=False, colormap=cmap)

        bar_data = pd.concat({"min":nd.min(), "q0.25":nd.quantile(0.25), "mean":nd.mean(), "q0.75":nd.quantile(0.75), "max":nd.max()}, axis=1)
        bar_data.plot.bar(ax=axes2[count])

        lines, labels = res.get_legend_handles_labels()
        label_list = list(labels)
        if first:
            first = False
            first_label_list = label_list
            fig.legend( lines, labels, loc = 'lower center' , ncol=3 )
        else:
            pass
            #if not all([i[0] == i[1] for i in zip(label_list, first_label_list)]):
            #    comparison = "\n".join(["{}\t\tVS\t\t{}".format(i[0], i[1]) for i in zip(label_list, first_label_list)])
            #    raise Exception("Different labels for different keys. Discovered on key '{}'\n{}".format(el, comparison))
        count += 1
        fig.savefig(pjoin(folder, fname_inf_nodes), bbox_inches='tight', format=plot_format, dpi=dpi)
        fig2.savefig(pjoin(folder, fname_inf_nodes_bar), bbox_inches='tight', format=plot_format, dpi=dpi)

def plot_inferred_metrics_edges(folder):
    print("- plotting inferred metrics for edges")

    data = pd.read_csv(folder + "/inferred_metrics_edges.csv", sep=";")
    data.time = pd.to_datetime(data.time, unit='ms')
    data = data.set_index('time')

    keys = set(data.columns) - set(["time", "source", "target"])

    #data = data.replace("Infinity", 10**9)

    first = True
    count = 0
    first_label_list = None
    fig, axes = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
    fig.suptitle("inferred edge metrics")
    for el in keys:
        axes[count].set_title(el)
        #ax = fig.add_subplot(10 + 100 * len(keys) + count, sharex=True)
        nd = pd.pivot_table(data, index="time", columns=["source", "target"], values=el)
        if debug:
            print()
            print(el)
            print(nd.describe())

        res = nd.plot(ax=axes[count], legend=False, colormap=cmap)
        lines, labels = res.get_legend_handles_labels()
        label_list = list(labels)
        if first:
            first = False
            first_label_list = label_list
            fig.legend( lines, labels, loc = 'lower center' , ncol=3)
        else:
            if not all([i[0] == i[1] for i in zip(label_list, first_label_list)]):
                comparison = "\n".join(["{}\t\tVS\t\t{}".format(i[0], i[1]) for i in zip(label_list, first_label_list)])
                raise Exception("Different labels for different keys. Discovered on key '{}'\n{}".format(el, comparison))
        count += 1
    plt.savefig(pjoin(folder, fname_inf_edges), bbox_inches='tight', format=plot_format, dpi=dpi)

def plot_raw_metrics(folder, keys):
    print("- plotting raw metrics")

    if os.stat(folder + "/metrics.csv").st_size == 0:
        print("{}/metric.csv empty. skipping".format(folder))
        return
    metrics = pd.read_csv(folder + "/metrics.csv", sep=";")
    metrics.key = metrics.key.str.replace("pyramid.taskmanager.[^.]*.Scala SocketTextStreamWordCount Example.", "")
    metrics.time = pd.to_datetime(metrics.time, unit='ms')
    metrics = metrics.pivot("time", "key", "value").fillna(0)

    def plot_by_key(ax, ax2, metrics, key, roll=1):
        ax.set_title(key)
        ax2.set_title(key)
        cols = {c:re.sub("." + key, "", c) for c in metrics.columns if c.endswith(key)}
        metrics = metrics.rename(columns=cols)
        nd = metrics[list(cols.values())].rolling(roll, center=False).mean()

        bar_data = pd.concat({"min":nd.min(), "q0.25":nd.quantile(0.25), "mean":nd.mean(), "q0.75":nd.quantile(0.75), "max":nd.max()}, axis=1)
        bar_data.plot.bar(ax=ax2)

        if debug:
            print()
            print(key)
            print(nd.describe())
        return nd.plot(ax=ax, legend=False, colormap=cmap)

    fig, axes = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
    fig2, axes2 = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
    fig.suptitle("raw metrics")
    fig2.suptitle("raw metrics")
    first = True
    first_label_list = None
    i = 0
    for k in keys:
        res = plot_by_key(axes[i], axes2[i], metrics, k, 20)
        lines, labels = res.get_legend_handles_labels()
        label_list = list(labels)
        if first:
            first = False
            first_label_list = label_list
            fig.legend( lines, labels, loc = 'lower center' , ncol=3 )
        else:
            if not all([i[0] == i[1] for i in zip(label_list, first_label_list)]):
                comparison = "\n".join(["{}\t\tVS\t\t{}".format(i[0], i[1]) for i in zip(label_list, first_label_list)])
                raise Exception("Different labels for different keys. Discovered on key '{}'\n{}".format(el, comparison))

        i += 1
    fig.savefig(pjoin(folder, fname_raw_metrics), bbox_inches='tight', format=plot_format, dpi=dpi)
    fig2.savefig(pjoin(folder, fname_raw_metrics_bar), bbox_inches='tight', format=plot_format, dpi=dpi)


def draw_graph(folder):
    print("- drawing graph")
    def add_edge(row):
        x = [y.replace(":", " -") for y in list(row)]
        g.add_edge(x[0], x[1])

    edge_data = pd.read_csv(folder + "/graph.csv", sep=";")
    g = nx.DiGraph()
    edge_data.apply(add_edge, 1)
    #nx.draw_networkx(g)
    gpd = nx.nx_pydot.to_pydot(g)
    gpd.write_pdf(pjoin(folder, fname_graph))


def main(folder=None, viewer=True):
    if not folder:
        folder = "/tmp/" + sorted(filter(lambda x: "mera" in x, os.listdir("/tmp")))[-1]

    print("Storing plots in '{}'".format(folder))

    draw_graph(folder)
    plot_inferred_metrics_edges(folder)
    plot_inferred_metrics_nodes(folder)
    plot_raw_metrics(folder, ["numRecordsInPerSecond", "numRecordsOutPerSecond",
        "buffers.outPoolUsage", "buffers.inPoolUsage", "numRecordsOut",
        "numRecordsIn"])

    pdfunite = shutil.which("pdfunite")
    if pdfunite:
        print("- creating summary")
        fname_summary = pjoin("summary.pdf")
        cmd = [pdfunite, fname_graph, fname_inf_nodes, fname_inf_nodes_bar,
                fname_inf_edges, fname_raw_metrics, fname_raw_metrics_bar,
                fname_summary]
        cmd = [pjoin(folder, c) for c in cmd]
        subprocess.check_output(cmd)
    if viewer:
        subprocess.Popen(["zathura", pjoin(folder, "summary.pdf")])



if __name__ == "__main__":
    folder = None
    if (len(sys.argv) > 1):
        if os.path.exists(sys.argv[1]) and os.path.isdir(sys.argv[1]):
            folder = sys.argv[1]
        else:
            print("{} <folder>".format(sys.argv[0]))
            sys.exit(1)
    main(folder=folder)


