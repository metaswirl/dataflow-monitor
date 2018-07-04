import {getDataFromMetrics, getInitMetrics, updateInitMetrics} from "./RestInterface";
import {Options} from "./highcharts";
import {Lineoptions, LinePlotData, LinePlotValue, Metric, MetricListObject, Value} from "./datastructure";
import Highcharts = require("./highcharts");
import $  = require("jquery");
import d3 = require("d3");

export let colorScaleLines = d3.scaleOrdinal(d3["schemeCategory20c"]);
let ChartOptions:Options = {
    chart: {
        type: 'spline',
        height: $(".linePlot").height(),
        animation: {
            duration: 0
        }, // don't animate in old IE
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
            let selMetric: Metric = new Metric();
            let lastCall;
            selMetric.taskId = task;
            selMetric.metricId = metricListObject.metricId;
            selMetric.resolution = metricListObject.resolution;
            if (LinePlot.get(selMetric.taskId + "_" + selMetric.metricId) != undefined) {
                let dataPerTask = LinePlot.get(selMetric.taskId + "_" + selMetric.metricId).data;
                let dataIndex = dataPerTask.length - 1;
                if (dataIndex >= 0) {
                    lastCall = dataPerTask[dataIndex].x;
                    metricListObject.since = lastCall;
                }
            }
            setSeries(selMetric, metricListObject.since);
            //LinePlot.redraw();
        })
    });
    updateInitMetrics(listOfInitMetrics);
}, 5000);





export function setSeries(selectedMetric: Metric, since: number) {
    getDataFromMetrics(selectedMetric.metricId, selectedMetric.taskId, since).done(function (result) {
        console.log(result);
        let line = new LinePlotData();
        line.id = selectedMetric.taskId + "_" + selectedMetric.metricId;
        line.name = selectedMetric.taskId;
        line.data = [];
        let options = new Lineoptions();
        options.color = colorScaleLines(line.id.split("_",1)[0]).toString();
        line.options = options ;
        result.values.forEach(function (point) {
            let value = new LinePlotValue();
            value.x = point[0];
            value.y = point[1];
            line.data.push(value);
        });
        if (LinePlot.get(line.id) == undefined) {
            LinePlot.addSeries(line, false)
        }
        else {
            let series: any = LinePlot.get(line.id);
            line.data.forEach(function (point) {
               if(point != null){
                   if (series.data.length >= 20) {
                       series.addPoint(point, true, true, false);
                       //series.update(series.options)
                   }
                   else {
                       series.addPoint(point, true, false, false);
                       //series.update(series.options)
                   }
               }
            });
        }
    })

}
