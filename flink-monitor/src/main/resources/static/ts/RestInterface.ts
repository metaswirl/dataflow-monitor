//Helper for Class RestInterface

import {MetricListObject, MetricPostObject} from "./datastructure";
import $ = require("jquery");


const pathToOperators: string = "http://127.0.0.1:12345/data/operators";
const pathToMetrics: string = "http://127.0.0.1:12345/data/metrics";
const pathToTopology: string = "http://127.0.0.1:12345/data/topology";

let initMetrics: Array<MetricListObject> = [];

export let getOperators = $.getJSON(pathToOperators);
export let getMetrics = $.getJSON(pathToMetrics);
export let getTopology = $.getJSON(pathToTopology);

export function initMetricForTasks(metric: any, taskIds: Array<string>, resolutionTime: number) {
    let postObj = new MetricPostObject(metric, taskIds, resolutionTime);
    let listPost = new MetricListObject(postObj);
    listPost.since = Date.now();
    if(resolutionTime > 5000){
        setInitMetrics(listPost);
    }
    return $.post("http://127.0.0.1:12345/data/metrics/tasks/init", JSON.stringify(postObj))
}

export function getInitMetrics() {
    return initMetrics;
}

// ToDo: consider removing a metric after init
export function setInitMetrics(value: MetricListObject) {
    initMetrics.push(value);
}

export function updateInitMetrics(value: Array<MetricListObject>) {
    initMetrics = value;
}

//TODO: REWRITE TO DIFFER BETWEEN OPERATOR OR TASK
export function getDataFromMetrics(metricId: string, taskId: string, since: number) {

    let encodedURI = "http://127.0.0.1:12345/data/metrics/task?metricId=" + encodeURIComponent(metricId) + "&taskId=" + encodeURIComponent(taskId) + "&since=" + since;
    return $.getJSON(encodedURI);
}

