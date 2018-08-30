import {getDataFromMetrics, getInitMetrics, updateInitMetrics} from "./RestInterface";
import {Options, SeriesObject} from "./highcharts";
import {LineOptions, LinePlotData, Metric, MetricListObject, Value} from "./datastructure";
import Highcharts = require("./highcharts");
import $ = require("jquery");
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
    lang:{
        noData: "Please Init Metric"
    },
    noData: {
        style: {
            fontWeight: 'bold',
            fontSize: '15px',
            color: '#303030'
        }
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
        enabled: true
    },
    exporting: {
        enabled: true
    }
};

let LinePlot = Highcharts.chart('linePlot', ChartOptions);
//Todo: getInitMetrics & forEach taskId initPlot & set Refresh in resolution Interval

setInterval(function () {
    let listOfInitMetrics: Array<MetricListObject> = getInitMetrics();

    listOfInitMetrics.forEach(function (metricListObject: MetricListObject) {
        metricListObject.taskIds.forEach(function (task) {
            let selMetric = new Metric(task, metricListObject.metricId, metricListObject.resolution);
            let lastCall;
            if (LinePlot.get(selMetric.taskId + "_" + selMetric.metricId) != undefined) {
                let dataPerTask = LinePlot.get(selMetric.taskId + "_" + selMetric.metricId) as SeriesObject;
                let dataIndex = dataPerTask.data.length - 1;
                if (dataIndex >= 0) {
                    lastCall = dataPerTask.data[dataIndex].x;
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
        let options = new LineOptions(colorScaleLines(selectedMetric.taskId).toString());
        let line = new LinePlotData(selectedMetric.taskId, selectedMetric.taskId + "_" + selectedMetric.metricId, options);
        result.values.forEach(function (point) {
            let value = new Value(point[0],point[1]);
            line.data.push(value);
        });
        if (LinePlot.get(line.id) == undefined) {
            LinePlot.addSeries(line, false);
            let plotHeading:string = $(".highcharts-title")[0].childNodes[0].textContent;
            if(plotHeading == 'Selected Metric'){
                $(".highcharts-title")[0].childNodes[0].textContent = selectedMetric.metricId;
            }
            else if(plotHeading.indexOf(selectedMetric.metricId) != -1){}
            else{
                $(".highcharts-title")[0].childNodes[0].textContent = plotHeading + " & " +  selectedMetric.metricId;
            }
        }
        else {
            let series: any = LinePlot.get(line.id);
            line.data.forEach(function (point) {
               if(point != null){
                   if (series.data.length >= 20) {
                       series.addPoint(point, true, true, false);
                   }
                   else {
                       series.addPoint(point, true, false, false);
                   }
               }
            });
        }
    })
}
