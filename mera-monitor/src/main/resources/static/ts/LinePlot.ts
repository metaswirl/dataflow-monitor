import {getDataFromMetrics, initMetricForTasks} from "./RestInterface";
import * as Highcharts from "./highcharts";
import {Options, SeriesObject} from "./highcharts";
import {LineOptions, LinePlotData, Metric, MetricListObject, MetricPostObject, Value} from "./datastructure";
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
let LinePlot2 = Highcharts.chart('linePlot2', ChartOptions);
let LinePlot3 = Highcharts.chart('linePlot3', ChartOptions);
//Todo: getInitMetrics & forEach taskId initPlot & set Refresh in resolution Interval

//Hardcode für Metric bottleneckDelay, latency und dropRate every 15s
let metricDelay = "numBytesOut";
let metricLatency = "numRecordsOut";
let metricDropRate ="numRecordsIn";
ChartOptions.title.text = metricDelay;
let linePlotDelay = Highcharts.chart('linePlot', ChartOptions);
ChartOptions.title.text = metricLatency;
let linePlotLatency = Highcharts.chart('linePlot2', ChartOptions);
ChartOptions.title.text = metricDropRate;
let linePlotDropRate = Highcharts.chart('linePlot3', ChartOptions);
let now = Date.now();
let plotDelay:MetricListObject = new MetricListObject(new MetricPostObject(metricDelay,["Map.0","Map.1"],15));
let plotLatency:MetricListObject = new MetricListObject(new MetricPostObject(metricLatency,["Map.0","Map.1"],15));
let plotDropRate:MetricListObject = new MetricListObject(new MetricPostObject(metricDropRate,["Map.0","Map.1"],15));
initMetricForTasks(plotDelay.metricId,plotDelay.taskIds,plotDelay.resolution);
initMetricForTasks(plotLatency.metricId,plotLatency.taskIds,plotLatency.resolution);
initMetricForTasks(plotDropRate.metricId,plotDropRate.taskIds,plotDropRate.resolution);
plotDelay.since = now;
plotLatency.since = now;
plotDropRate.since = now;
let listOfHardCodeMetrics = [plotDropRate, plotLatency, plotDelay];

setInterval(function () {
    let listOfInitMetrics: Array<MetricListObject> = listOfHardCodeMetrics;
    listOfInitMetrics.forEach(function (metricListObject: MetricListObject) {
        metricListObject.taskIds.forEach(function (task) {
            let selMetric = new Metric(task, metricListObject.metricId, metricListObject.resolution);
            let lastCall;
            let plot;
            switch (metricListObject.metricId) {
                case metricDelay:
                    plot = linePlotDelay;
                    break;

                case metricLatency:
                    plot = linePlotLatency;
                    break;

                case metricDropRate:
                    plot = linePlotDropRate;
                    break;

                default:
                    plot = linePlotDelay;
            }
            if (plot.get(selMetric.taskId + "_" + selMetric.metricId) != undefined) {
                let dataPerTask = plot.get(selMetric.taskId + "_" + selMetric.metricId) as SeriesObject;
                let dataIndex = dataPerTask.data.length - 1;
                if (dataIndex >= 0) {
                    lastCall = dataPerTask.data[dataIndex].x;
                    metricListObject.since = lastCall;
                }
            }
            setStaticSeries(selMetric, metricListObject.since, plot);
            //LinePlot.redraw();
        })
    });
}, 5000);


//Hardcode End

/*
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
*/
function setStaticSeries(selectedMetric:Metric, since:number, plot) {
    getDataFromMetrics(selectedMetric.metricId, selectedMetric.taskId, since).done(function (result) {
        let options = new LineOptions(colorScaleLines(selectedMetric.taskId).toString());
        let line = new LinePlotData(selectedMetric.taskId, selectedMetric.taskId + "_" + selectedMetric.metricId, options);
        result.values.forEach(function (point) {
            let value = new Value(point[0],point[1]);
            line.data.push(value);
        });
        console.log(result);
        if (plot.get(line.id) == undefined) {
            plot.addSeries(line, false);

        }
        else {
            let series: any = plot.get(line.id);
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
