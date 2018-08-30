import {
    getInitMetrics,
    getIsOptimized,
    getMetrics,
    getTopology,
    initMetricForTasks,
    optimizeLoad
} from "./RestInterface";
import {Metric, MetricPostObject} from "./datastructure";
import {setSeries} from "./LinePlot";


getMetrics.done(function (result) {
    setOptions(result, "metrics");
});
getTopology.done(function (result) {
    $("#Ids").empty();
    if ($("#taskoroperator").val() == "byOperator") {
        let optionsByOperator: Array<string> = [];
        result.forEach(function (item) {
            optionsByOperator.push(item.name);
        });
        setOptions(optionsByOperator, "Ids")
    }
    else {
        let optionsByTask: Array<string> = [];
        result.forEach(function (item) {
            item.tasks.forEach(function (ite) {
                optionsByTask.push(ite.id);
            });
        });
        setOptions(optionsByTask, "Ids")
    }
});

$("#taskoroperator").on("change", function () {
    getTopology.done(function (result) {
        $("#Ids").empty();
        console.log(result);
        if ($("#taskoroperator").val() == "byOperator") {
            let optionsByOperator: Array<string> = [];
            result.forEach(function (item) {
                optionsByOperator.push(item.name);
            });
            setOptions(optionsByOperator, "Ids")
        }
        else {
            let optionsByTask: Array<string> = [];
            result.forEach(function (item) {
                item.tasks.forEach(function (ite) {
                    optionsByTask.push(ite.id);
                });
            });
            setOptions(optionsByTask, "Ids")
        }
    });
});
$("#initButton").on("click", function () {
    initMetricOnAction();
});
$("#optimizeBtn").on("click", function () {
    let isoptimizedLoad = optimizeLoad();
    isoptimizedLoad.done(function () {
        if(getIsOptimized()){
            $("#optimizeBtn").addClass("isOptimized")
        }
        else{
            $("#optimizeBtn").removeClass("isOptimized")
        }
    })
});

function initMetricOnAction() {
    let metric = $("#metrics").val().toString();
    let Ids: any = $("#Ids").val();
    let resolutionString: any = $("input[name=resolutionselect]:checked").val();
    let resolution = parseInt(resolutionString);
    let post = initMetricForTasks(metric, Ids, resolution);
    post.done(function () {
        let metrics: Array<MetricPostObject> = getInitMetrics();
        metrics.forEach(function (metric) {
            metric.taskIds.forEach(function (task: string) {
                let selmetric = new Metric(task, metric.metricId, metric.resolution);
                setSeries(selmetric, Date.now());
            })
        })
    })

}

function setOptions(arrayOfOptions: Array<string>, parentElementTag: string) {
    let metricSelector = document.getElementById(parentElementTag);
    arrayOfOptions.forEach(function (value) {
        let el = document.createElement("option");
        el.textContent = value;
        el.value = value;
        metricSelector.appendChild(el);
    });
}