var __extends = (this && this.__extends) || (function () {
    var extendStatics = Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
        function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
define("datastructure", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    var Operator = /** @class */ (function () {
        function Operator() {
        }
        return Operator;
    }());
    exports.Operator = Operator;
    var Task = /** @class */ (function () {
        function Task() {
        }
        return Task;
    }());
    exports.Task = Task;
    var Metric = /** @class */ (function () {
        function Metric() {
        }
        return Metric;
    }());
    exports.Metric = Metric;
    var Value = /** @class */ (function () {
        function Value() {
        }
        return Value;
    }());
    exports.Value = Value;
    var Cardinality = /** @class */ (function () {
        function Cardinality() {
        }
        return Cardinality;
    }());
    exports.Cardinality = Cardinality;
    var point = /** @class */ (function () {
        function point() {
        }
        return point;
    }());
    var MetricPostObject = /** @class */ (function () {
        function MetricPostObject(mId, tId, res) {
            this.resolution = res;
            this.taskIds = tId;
            this.metricId = mId;
        }
        return MetricPostObject;
    }());
    exports.MetricPostObject = MetricPostObject;
    var MetricListObject = /** @class */ (function (_super) {
        __extends(MetricListObject, _super);
        function MetricListObject(metricPostObject) {
            return _super.call(this, metricPostObject.metricId, metricPostObject.taskIds, metricPostObject.resolution) || this;
        }
        return MetricListObject;
    }(MetricPostObject));
    exports.MetricListObject = MetricListObject;
    var LinePlotData = /** @class */ (function () {
        function LinePlotData() {
        }
        return LinePlotData;
    }());
    exports.LinePlotData = LinePlotData;
    var LinePlotValue = /** @class */ (function () {
        function LinePlotValue() {
        }
        return LinePlotValue;
    }());
    exports.LinePlotValue = LinePlotValue;
});
//Helper for Class RestInterface
define("RestInterface", ["require", "exports", "datastructure", "jquery"], function (require, exports, datastructure_1, $) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    var pathToOperators = "http://127.0.0.1:12345/data/operators";
    var pathToMetrics = "http://127.0.0.1:12345/data/metrics";
    var pathToTopology = "http://127.0.0.1:12345/data/topology";
    var initMetrics = [];
    exports.getOperators = $.getJSON(pathToOperators);
    exports.getMetrics = $.getJSON(pathToMetrics);
    exports.getTopology = $.getJSON(pathToTopology);
    function initMetricForTasks(metric, taskIds, resolutionTime) {
        var postObj = new datastructure_1.MetricPostObject(metric, taskIds, resolutionTime);
        var listPost = new datastructure_1.MetricListObject(postObj);
        listPost.since = Date.now();
        setInitMetrics(listPost);
        return $.post("http://127.0.0.1:12345/data/metrics/tasks/init", JSON.stringify(postObj));
    }
    exports.initMetricForTasks = initMetricForTasks;
    function getInitMetrics() {
        return initMetrics;
    }
    exports.getInitMetrics = getInitMetrics;
    // ToDo: consider removing a metric after init
    function setInitMetrics(value) {
        initMetrics.push(value);
    }
    exports.setInitMetrics = setInitMetrics;
    function updateInitMetrics(value) {
        initMetrics = value;
    }
    exports.updateInitMetrics = updateInitMetrics;
    //TODO: REWRITE TO DIFFER BETWEEN OPERATOR OR TASK
    function getDataFromMetrics(metricId, taskId, since) {
        var encodedURI = "http://127.0.0.1:12345/data/metrics/task?metricId=" + encodeURIComponent(metricId) + "&taskId=" + encodeURIComponent(taskId) + "&since=" + since;
        return $.getJSON(encodedURI);
    }
    exports.getDataFromMetrics = getDataFromMetrics;
});
define("LinePlot", ["require", "exports", "RestInterface", "datastructure", "./highcharts", "jquery"], function (require, exports, RestInterface_1, datastructure_2, Highcharts, $) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    var ChartOptions = {
        chart: {
            type: 'spline',
            height: $(".linePlot").height(),
            animation: true,
            marginRight: 10,
            events: {}
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
    var LinePlot = Highcharts.chart('linePlot', ChartOptions);
    //Todo: getInitMetrics & forEach taskId initPlot & set Refresh in resolution Interval
    setInterval(function () {
        var listOfInitMetrics = RestInterface_1.getInitMetrics();
        listOfInitMetrics.forEach(function (metricListObject) {
            metricListObject.taskIds.forEach(function (task) {
                var selmetric = new datastructure_2.Metric();
                var lastCall;
                selmetric.taskId = task;
                selmetric.metricId = metricListObject.metricId;
                selmetric.resolution = metricListObject.resolution;
                if (LinePlot.get(selmetric.taskId) != undefined) {
                    var dataPerTask = LinePlot.get(selmetric.taskId).data;
                    var dataIndex = dataPerTask.length - 1;
                    if (dataIndex >= 0) {
                        lastCall = dataPerTask[dataIndex].x;
                        metricListObject.since = lastCall;
                    }
                }
                setSeries(selmetric, metricListObject.since);
            });
        });
        RestInterface_1.updateInitMetrics(listOfInitMetrics);
    }, 5000);
    console.log(LinePlot);
    function setSeries(selectedMetric, since) {
        RestInterface_1.getDataFromMetrics(selectedMetric.metricId, selectedMetric.taskId, since).done(function (result) {
            var line = new datastructure_2.LinePlotData();
            line.id = selectedMetric.taskId;
            line.name = selectedMetric.metricId;
            line.data = [];
            result.forEach(function (point) {
                var value = new datastructure_2.LinePlotValue();
                value.x = point[0];
                value.y = point[1];
                line.data.push(value);
            });
            if (LinePlot.get(selectedMetric.taskId) == undefined) {
                LinePlot.addSeries(line, true);
            }
            else {
                var series_1 = LinePlot.get(line.id);
                line.data.forEach(function (point) {
                    if (series_1.data.length >= 20) {
                        series_1.addPoint(point, false, true);
                    }
                    else {
                        series_1.addPoint(point, false, false);
                    }
                });
                LinePlot.redraw();
            }
        });
    }
    exports.setSeries = setSeries;
});
define("interfaceLoads", ["require", "exports", "RestInterface", "datastructure", "LinePlot"], function (require, exports, RestInterface_2, datastructure_3, LinePlot_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    RestInterface_2.getMetrics.done(function (result) {
        setOptions(result, "metrics");
    });
    RestInterface_2.getTopology.done(function (result) {
        $("#Ids").empty();
        console.log(result);
        if ($("#taskoroperator").val() == "byOperator") {
            var optionsByOperator_1 = [];
            result.forEach(function (item) {
                optionsByOperator_1.push(item.name);
            });
            setOptions(optionsByOperator_1, "Ids");
        }
        else {
            var optionsByTask_1 = [];
            result.forEach(function (item) {
                item.tasks.forEach(function (ite) {
                    optionsByTask_1.push(ite.id);
                });
            });
            setOptions(optionsByTask_1, "Ids");
        }
    });
    $("#taskoroperator").on("change", function () {
        RestInterface_2.getTopology.done(function (result) {
            $("#Ids").empty();
            console.log(result);
            if ($("#taskoroperator").val() == "byOperator") {
                var optionsByOperator_2 = [];
                result.forEach(function (item) {
                    optionsByOperator_2.push(item.name);
                });
                setOptions(optionsByOperator_2, "Ids");
            }
            else {
                var optionsByTask_2 = [];
                result.forEach(function (item) {
                    item.tasks.forEach(function (ite) {
                        optionsByTask_2.push(ite.id);
                    });
                });
                setOptions(optionsByTask_2, "Ids");
            }
        });
    });
    $("#initButton").on("click", function () {
        initMetricOnAction();
    });
    function initMetricOnAction() {
        var metric = $("#metrics").val().toString();
        var Ids = $("#Ids").val();
        var resolutionString = $("input[name=resolutionselect]:checked").val();
        var resolution = parseInt(resolutionString);
        var post = RestInterface_2.initMetricForTasks(metric, Ids, resolution);
        post.done(function () {
            var metrics = RestInterface_2.getInitMetrics();
            metrics.forEach(function (metric) {
                metric.taskIds.forEach(function (task) {
                    var selmetric = new datastructure_3.Metric();
                    selmetric.taskId = task;
                    selmetric.metricId = metric.metricId;
                    selmetric.resolution = metric.resolution;
                    LinePlot_1.setSeries(selmetric, Date.now());
                });
            });
        });
    }
    function setOptions(arrayOfOptions, parentElementTag) {
        var metricSelector = document.getElementById(parentElementTag);
        arrayOfOptions.forEach(function (value) {
            var el = document.createElement("option");
            el.textContent = value;
            el.value = value;
            metricSelector.appendChild(el);
        });
    }
});
define("longGraph", ["require", "exports", "RestInterface", "d3"], function (require, exports, RestInterface_3, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    var margin = { top: 10, right: 20, bottom: 60, left: 20 };
    var longGraph = {
        width: $(".longGraph").width(),
        height: $(".longGraph").height()
    };
    var shortGraph = {
        width: $(".shortGraph").width(),
        height: $(".shortGraph").height()
    };
    var canvas = {
        width: longGraph.width - margin.right - margin.left,
        height: longGraph.height - margin.top - margin.bottom
    };
    var shortGraphCanvas = {
        width: shortGraph.width - margin.right - margin.left,
        height: shortGraph.height - margin.top - margin.bottom
    };
    //Variables for Debug
    var xScale = d3.scaleLinear();
    var xLabel = d3.scaleOrdinal();
    var yScales = [];
    var updateColorInterval;
    var graphDataset;
    var graphSvg = d3.select("#longGraph")
        .attr("width", longGraph.width)
        .attr("height", longGraph.height)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
    var shortGraphSvg = d3.select("#shortGraph")
        .attr("width", shortGraph.width)
        .attr("height", shortGraph.height)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
    //Color Axis
    var maschineColor = d3.scaleLinear();
    maschineColor.domain([0, 5]);
    maschineColor.range(["green", "orange"]);
    var loadColor = d3.scaleLinear();
    loadColor.domain([0, 20]);
    loadColor.range(["green", "red"]);
    RestInterface_3.getTopology.done(function (result) {
        result.reverse();
        // xAxis prepare
        xScale.domain([0, result.length - 1]);
        xScale.range([0, canvas.width]);
        var labels = [];
        var labelRange = [];
        result.forEach(function (item, i) {
            labels.push(item.name);
            labelRange.push(xScale(i));
        });
        xLabel.domain(labels);
        xLabel.range(labelRange);
        //various y-Axis
        result.forEach(function (item) {
            var yScale = d3.scaleLinear();
            yScale.domain([-1, item.tasks.length]);
            yScale.range([0, canvas.height]);
            yScales.push(yScale);
        });
        //Draw X-Axis
        shortGraphSvg
            .append("g")
            .attr("class", "xAxis")
            .attr("transform", "translate(0, " + shortGraphCanvas.height + ")")
            .call(d3.axisBottom(xLabel));
        shortGraphSvg.selectAll(".xAxis text")
            .attr("transform", function (d, i) {
            var textElem = this;
            if (i == 0) {
                return "translate(" + textElem.getBBox().width * 0.4 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
            else if (i == yScales.length - 1) {
                return "translate(" + textElem.getBBox().width * -0.4 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
            else {
                return "translate(" + 0 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
        });
        //Prepare Cardinality List
        var cardinality = getLinks(result);
        console.log(cardinality);
        //Draw the Links
        graphSvg
            .append("g")
            .attr("class", "links")
            .selectAll(".link")
            .data(cardinality)
            .enter().append("path")
            .attr("class", "link")
            .attr("d", function (d) {
            var sx = xScale(d.source.cx), sy = yScales[d.source.cx](d.source.cy), tx = xScale(d.target.cx), ty = yScales[d.target.cx](d.target.cy), dr = 0;
            return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
        });
        //Prepare Data as Tasklist
        var taskList = createTaskList(result);
        //Draw the Nodes
        graphSvg
            .append("g")
            .attr("class", "nodes")
            .selectAll(".node")
            .data(taskList)
            .enter().append("circle")
            .attr("r", 5)
            .attr("class", "node")
            .attr("cx", function (d) {
            return xScale(d.cx);
        })
            .attr("cy", function (d) {
            return yScales[d.cx](d.cy);
        })
            .style("fill", function () {
            return loadColor(0);
        })
            .append("text")
            .text(function (d) {
            return d.name;
        })
            .attr("cy", function () {
            return 5;
        })
            .attr("cx", function (d) {
            return xScale(d.cx);
        })
            .attr("text", function (d) {
            return d.name;
        })
            .style("text-anchor", "end");
        //Todo: Do we want to have color coded Maschine implicators in the Graph ?
        //Draw connected Maschines
        /*graphSvg
            .append("g")
            .attr("class", "maschines")
            .selectAll(".maschine")
            .data(graphDataset)
            .enter().append("circle")
            .attr("class", function (d) {
                return "maschine" + d.maschine
            })
            .attr("r", 10)
            .attr("cx", function (d) {
                return xScale(d.cx)
            })
            .attr("cy", function (d) {
                return yScales[d.cx](d.cy)
            })
            .style("stroke", function (d) {
                return maschineColor(d.maschine)
            })
            .style("fill", function (d) {
                return maschineColor(d.maschine)
            });
        */
    });
    // Helper Functions
    //ToDo: Fix color Index for Load on Nodes (maybe add a scale around node for Viewing Data)
    /*function reloadNodeColor() {
        let graph = d3.selectAll(".node");
        graph.each(function (item) {
            let d3itm = d3.select(this);
            if (item.name == "Reduce") {
                d3itm.style("fill", function () {
                    try {
                        let data = LinePlot.get("Sl2-Keyed_Reduce-2-inputQueueLength").data;
                        return loadColor(data[data.length - 1].y)
                    }
                    catch (e) {
                        return loadColor(0)
                    }
                })
            }
        })
    }*/
    function createTaskList(input) {
        var listOfTasks = [];
        input.forEach(function (item, i) {
            item.tasks.forEach(function (t, j) {
                var task = {
                    name: t.id,
                    cx: i,
                    cy: j
                };
                listOfTasks.push(task);
            });
        });
        return listOfTasks;
    }
    function getLinks(dataset) {
        var links = [];
        var _loop_1 = function (i) {
            dataset[i].tasks.forEach(function (task, j) {
                task.input.forEach(function (input, k) {
                    var link = {
                        source: {
                            id: input,
                            cx: i - 1,
                            cy: k
                        },
                        target: {
                            id: task.id,
                            cx: i,
                            cy: j
                        }
                    };
                    links.push(link);
                });
            });
        };
        for (var i = 1; i < dataset.length; i++) {
            _loop_1(i);
        }
        return links;
    }
});
//# sourceMappingURL=app.js.map