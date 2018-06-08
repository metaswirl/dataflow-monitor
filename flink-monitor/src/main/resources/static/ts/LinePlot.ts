import {getDataFromMetrics, getInitMetrics, updateInitMetrics} from "./RestInterface";
import {Options} from "./highcharts";
import {LinePlotData, LinePlotValue, Metric, MetricListObject, Value} from "./datastructure";
import Highcharts = require("./highcharts");
import $  = require("jquery");

let ChartOptions:Options = {
    chart: {
        type: 'spline',
        height: $(".linePlot").height(),
        animation: true, // don't animate in old IE
        marginRight: 10,
        events: {
        }
    },
    title: {
        text: 'Selected Metric'
    },
    xAxis: {
        type: 'datetime',
        tickPixelInterval: 150
    },
    yAxis: {
        title: {
            text: 'Value'
        },
        plotLines: [{
            value: 0,
            width: 1,
            color: '#800610'
        }]
    },
    tooltip: {
        formatter: function () {
            return '<b>' + this.series.name + '</b><br/>' +
                Highcharts.dateFormat('%Y-%m-%d %H:%M:%S', this.x) + '<br/>' +
                Highcharts.numberFormat(this.y, 2);
        }
    },
    legend: {
        enabled: false
    },
    exporting: {
        enabled: false
    }
};

let LinePlot = Highcharts.chart('linePlot', ChartOptions);
//Todo: getInitMetrics & forEach taskId initPlot & set Refresh in resolution Interval

setInterval(function () {
    let listOfInitMetrics: Array<MetricListObject> = getInitMetrics();

    listOfInitMetrics.forEach(function (metricListObject: MetricListObject) {
        metricListObject.taskIds.forEach(function (task) {
            let selmetric: Metric = new Metric();
            let lastCall;
            selmetric.taskId = task;
            selmetric.metricId = metricListObject.metricId;
            selmetric.resolution = metricListObject.resolution;
            if (LinePlot.get(selmetric.taskId) != undefined) {
                let dataPerTask = LinePlot.get(selmetric.taskId).data;
                let dataIndex = dataPerTask.length - 1;
                if (dataIndex >= 0) {
                    lastCall = dataPerTask[dataIndex].x;
                    metricListObject.since = lastCall;
                }
            }
            setSeries(selmetric, metricListObject.since);
        })
    });
    updateInitMetrics(listOfInitMetrics);
}, 5000);


console.log(LinePlot);


export function setSeries(selectedMetric: Metric, since: number) {
    getDataFromMetrics(selectedMetric.metricId, selectedMetric.taskId, since).done(function (result) {
        let line = new LinePlotData();
        line.id = selectedMetric.taskId;
        line.name = selectedMetric.metricId;
        line.data = [];
        result.forEach(function (point) {
            let value = new LinePlotValue();
            value.x = point[0];
            value.y = point[1];
            line.data.push(value);
        });
        if (LinePlot.get(selectedMetric.taskId) == undefined) {
            LinePlot.addSeries(line, true)
        }
        else {
            let series: any = LinePlot.get(line.id);
            line.data.forEach(function (point) {
                if (series.data.length >= 20) {
                    series.addPoint(point, false, true)
                }
                else {
                    series.addPoint(point, false, false)
                }
            });
            LinePlot.redraw()
        }
    })

}
