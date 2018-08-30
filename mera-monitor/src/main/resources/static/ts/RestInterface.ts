//Helper for Class RestInterface

import {MetricListObject, MetricPostObject} from "./datastructure";
import $ = require("jquery");


const pathToOperators: string = "http://127.0.0.1:12345/data/operators";
const pathToMetrics: string = "http://127.0.0.1:12345/data/metrics";
const pathToTopology: string = "http://127.0.0.1:12345/data/topology";
const pathToOptimize:string = "http://127.0.0.1:12345/optimize";
const pathToInit:string = "http://127.0.0.1:12345/data/metrics/tasks/init";
let isOptimized:boolean = false;

let initMetrics: Array<MetricListObject> = [];

//export let getOperators = $.getJSON(pathToOperators);
export let getMetrics = $.getJSON(pathToMetrics);
export let getTopology = $.getJSON(pathToTopology);

export function initMetricForTasks(metric: any, taskIds: Array<string>, resolutionTime: number) {
    let postObj = new MetricPostObject(metric, taskIds, resolutionTime);
    let listPost = new MetricListObject(postObj);
    listPost.since = Date.now();
    if(resolutionTime >= 5){
        setInitMetrics(listPost);
    }
    return $.post(pathToInit, JSON.stringify(postObj))
}

export function optimizeLoad() {
    let postObj = {
        isOptimized: isOptimized
    };
    if(isOptimized){
        isOptimized = false
    }
    else{
        isOptimized = true
    }
    return $.post(pathToOptimize, JSON.stringify(postObj))
}
export function getOptimizeStatus() {
    $.get(pathToOptimize).done(function (result) {
        isOptimized = result.running;
        if(isOptimized){
            $("#optimizeBtn").addClass("isOptimized")
        }
        else{
            $("#optimizeBtn").removeClass("isOptimized")
        }
    })
}

export function getIsOptimized() {
    return isOptimized;
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

// ToDo: REWRITE TO DIFFER BETWEEN OPERATOR OR TASK
export function getDataFromMetrics(metricId: string, taskId: string, since: number) {
    let encodedURI = "http://127.0.0.1:12345/data/metrics/task?metricId="
        + encodeURIComponent(metricId)
        + "&taskId="
        + encodeURIComponent(taskId)
        + "&since="
        + since;
    return $.getJSON(encodedURI);
}

