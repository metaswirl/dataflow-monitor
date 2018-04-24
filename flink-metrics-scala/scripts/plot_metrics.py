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
fname_raw_metrics_diff = "plot_raw_metrics_diff." + plot_format
fname_raw_metrics_drop_rates = "plot_raw_drop_rates." + plot_format
fname_target = "plot_raw_target." + plot_format
fname_graph = "graph." + plot_format

inf_replacement = -20000.0

def load_csv(fname):      
    data = pd.read_csv(fname, sep=";")     
    data.time = pd.to_datetime(data.time, unit='ms')
    return data.set_index('time')


def plot_inferred_metrics_nodes(folder, opti_start):
    print("- plotting inferred metrics for vertices")

    data = load_csv(folder + "/inferred_metrics_nodes.csv")
    data = data[~data.task.str.contains("loadshedder")]
    data.capacity = pd.to_numeric(data.capacity.replace("Infinity", inf_replacement))

    keys = set(data.columns) - set(["task", "time"])

    # the selectivity at the source can become infinite, as it only produces items
    data.selectivity = pd.to_numeric(data.selectivity)

    first = True
    count = 0
    fig, axes = plt.subplots(len(keys)+1, 1, sharex=True, figsize=figsize)
    fig2, axes2 = plt.subplots(len(keys)+1, 1, sharex=True, figsize=figsize)
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
            print(nd.head())
        res = nd.plot(ax=axes[count], legend=False, colormap=cmap)
        axes[count].axvline(opti_start)

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

    cap = data.pivot(columns="task", values="capacity")
    inRate = data.pivot(columns="task", values="inputRate")
    #for k in [k for k, v in dict(cap.max()).items() if v == inf_replacement]:
    #    del cap[k]
    #    del inRate[k]
    diff = (cap-inRate)
    diff.plot(ax=axes[count], legend=False, colormap=cmap, title="capacity-inputRate")
    axes[count].axvline(opti_start)

    fig.savefig(pjoin(folder, fname_inf_nodes), bbox_inches='tight', format=plot_format, dpi=dpi)
    fig2.savefig(pjoin(folder, fname_inf_nodes_bar), bbox_inches='tight', format=plot_format, dpi=dpi)

def plot_inferred_metrics_edges(folder, opti_start):
    print("- plotting inferred metrics for edges")

    data = load_csv(folder + "/inferred_metrics_edges.csv")
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
        axes[count].axvline(opti_start)
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

def plot_raw_metrics(folder, keys, opti_start):
    print("- plotting raw metrics")

    if os.stat(folder + "/metrics.csv").st_size == 0:
        print("{}/metric.csv empty. skipping".format(folder))
        return
    # only long format in use: can't use load_csv
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

    def plot_raw_metrics_raw():
        print("\t- plot general raw metrics")
        fig, axes = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
        fig2, axes2 = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
        fig.suptitle("raw metrics")
        fig2.suptitle("raw metrics barchart")
        first = True
        first_label_list = None
        i = 0
        #metrics = metrics[[c for c in metrics.columns if "loadshedder1.1" in c]]
        for k in keys:
            res = plot_by_key(axes[i], axes2[i], metrics, k, 20)
            axes[i].axvline(opti_start)
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

    def plot_raw_metrics_diff():
        print("\t- plot diff metrics")
        keys = [c.strip(".numRecordsInPerSecond") for c in metrics.columns if "loadshedder" in c and c.endswith(".numRecordsInPerSecond")]
        fig, axes = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
        fig.suptitle("inputRate - outputRate")
        for i, k in enumerate(keys):
            res = metrics[k + ".numRecordsInPerSecond"] - metrics[k + ".numRecordsOutPerSecond"]
            res.plot(ax = axes[i], title=k)
            axes[i].axvline(opti_start)
        fig.savefig(pjoin(folder, fname_raw_metrics_diff), bbox_inches='tight', format=plot_format, dpi=dpi)

    def plot_drop_rates():
        print("\t- plot drop counts")
        keys = [c for c in metrics.columns if c.endswith(".dropCount")]
        fig, axes = plt.subplots(len(keys), 1, sharex=True, figsize=figsize)
        fig.suptitle("drop rate")
        for i, k in enumerate(keys):
            metrics[k].plot(ax=axes[i], title=k, legend=False)
            axes[i].axvline(opti_start)
        fig.savefig(pjoin(folder, fname_raw_metrics_drop_rates), bbox_inches='tight', format=plot_format, dpi=dpi)

    def plot_target_metrics():
        print("\t- plot target metrics")
        fig, axes = plt.subplots(5, 1, sharex=True, figsize=figsize)
        fig.suptitle("target metrics")
        keys = [c for c in metrics.columns if "Sink" in c and c.endswith(".numRecordsInPerSecond")]
        metrics[keys].sum(axis=1).plot(ax=axes[0], title="cumulative input rate of sinks")
        axes[0].axvline(opti_start)

        not_keys = ["Sink", "Source", "loadshedder"]
        keys = [c for c in metrics.columns if c.endswith(".outPoolUsage") and all([not nk in c for nk in not_keys])]
        metrics[keys].mean(axis=1).plot(ax=axes[1], title="average output queue")
        axes[1].axvline(opti_start)

        keys = [c for c in metrics.columns if c.endswith(".latency")]
        metrics[keys].sum(axis=1).plot(ax=axes[2], title="latency", legend=False)
        axes[2].axvline(opti_start)

        keys = [c for c in metrics.columns if "Source" in c and c.endswith(".numRecordsOutPerSecond")]
        metrics[keys].plot(ax=axes[3], title="output rate of source", legend=False)
        axes[3].axvline(opti_start)

        keys = [c for c in metrics.columns if "Source" in c and c.endswith(".backlog")]
        metrics[keys].plot(ax=axes[4], title="backlog at source", legend=False)
        axes[4].axvline(opti_start)

        fig.savefig(pjoin(folder, fname_target), bbox_inches='tight', format=plot_format, dpi=dpi)
        
    plot_raw_metrics_raw()
    plot_raw_metrics_diff()
    plot_target_metrics()
    plot_drop_rates()

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

    try:
        with open(pjoin(folder, "optimization_start.csv"), 'r') as f:
            opti_start = pd.to_datetime(int(f.read().strip()), unit='ms')
    except:
        opti_start = 0

    plots_fnames = []
    draw_graph(folder)
    plots_fnames.append(fname_graph)

    plot_inferred_metrics_nodes(folder, opti_start)
    plots_fnames.append(fname_inf_nodes)
    plot_inferred_metrics_edges(folder, opti_start)
    plots_fnames.append(fname_inf_edges)
    plot_raw_metrics(folder, ["numRecordsInPerSecond", "numRecordsOutPerSecond",
        "buffers.outPoolUsage", "buffers.inPoolUsage", "numRecordsOut",
        "numRecordsIn"], opti_start)
    plots_fnames += [fname_raw_metrics, fname_target, fname_raw_metrics_drop_rates,
            fname_raw_metrics_diff, fname_raw_metrics_bar]

    pdfunite = shutil.which("pdfunite")
    if pdfunite:
        print("- creating summary")
        fname_summary = pjoin("summary.pdf")
        cmd = [pdfunite] + plots_fnames + [fname_summary]
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


