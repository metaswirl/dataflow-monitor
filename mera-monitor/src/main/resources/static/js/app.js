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
    var Lineoptions = /** @class */ (function () {
        function Lineoptions() {
        }
        return Lineoptions;
    }());
    exports.Lineoptions = Lineoptions;
});
//Helper for Class RestInterface
define("RestInterface", ["require", "exports", "datastructure", "jquery"], function (require, exports, datastructure_1, $) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    var pathToOperators = "http://127.0.0.1:12345/data/operators";
    var pathToMetrics = "http://127.0.0.1:12345/data/metrics";
    var pathToTopology = "http://127.0.0.1:12345/data/topology";
    var pathToOptimize = "http://127.0.0.1:12345/optimize";
    var pathToInit = "http://127.0.0.1:12345/data/metrics/tasks/init";
    var isOptimized = false;
    var initMetrics = [];
    exports.getOperators = $.getJSON(pathToOperators);
    exports.getMetrics = $.getJSON(pathToMetrics);
    exports.getTopology = $.getJSON(pathToTopology);
    function initMetricForTasks(metric, taskIds, resolutionTime) {
        var postObj = new datastructure_1.MetricPostObject(metric, taskIds, resolutionTime);
        var listPost = new datastructure_1.MetricListObject(postObj);
        listPost.since = Date.now();
        if (resolutionTime >= 5) {
            setInitMetrics(listPost);
        }
        return $.post(pathToInit, JSON.stringify(postObj));
    }
    exports.initMetricForTasks = initMetricForTasks;
    function optimizeLoad() {
        var postObj = {
            isOptimized: isOptimized
        };
        if (isOptimized) {
            isOptimized = false;
        }
        else {
            isOptimized = true;
        }
        return $.post(pathToOptimize, JSON.stringify(postObj));
    }
    exports.optimizeLoad = optimizeLoad;
    function getIsOptimized() {
        return isOptimized;
    }
    exports.getIsOptimized = getIsOptimized;
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
    // ToDo: REWRITE TO DIFFER BETWEEN OPERATOR OR TASK
    function getDataFromMetrics(metricId, taskId, since) {
        var encodedURI = "http://127.0.0.1:12345/data/metrics/task?metricId="
            + encodeURIComponent(metricId)
            + "&taskId="
            + encodeURIComponent(taskId)
            + "&since="
            + since;
        return $.getJSON(encodedURI);
    }
    exports.getDataFromMetrics = getDataFromMetrics;
});
define("LinePlot", ["require", "exports", "RestInterface", "datastructure", "./highcharts", "jquery", "d3"], function (require, exports, RestInterface_1, datastructure_2, Highcharts, $, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.colorScaleLines = d3.scaleOrdinal(d3["schemeCategory20c"]);
    var ChartOptions = {
        chart: {
            type: 'spline',
            height: $(".linePlot").height(),
            animation: {
                duration: 0
            },
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
                var selMetric = new datastructure_2.Metric();
                var lastCall;
                selMetric.taskId = task;
                selMetric.metricId = metricListObject.metricId;
                selMetric.resolution = metricListObject.resolution;
                if (LinePlot.get(selMetric.taskId + "_" + selMetric.metricId) != undefined) {
                    var dataPerTask = LinePlot.get(selMetric.taskId + "_" + selMetric.metricId).data;
                    var dataIndex = dataPerTask.length - 1;
                    if (dataIndex >= 0) {
                        lastCall = dataPerTask[dataIndex].x;
                        metricListObject.since = lastCall;
                    }
                }
                setSeries(selMetric, metricListObject.since);
                //LinePlot.redraw();
            });
        });
        RestInterface_1.updateInitMetrics(listOfInitMetrics);
    }, 5000);
    function setSeries(selectedMetric, since) {
        RestInterface_1.getDataFromMetrics(selectedMetric.metricId, selectedMetric.taskId, since).done(function (result) {
            var line = new datastructure_2.LinePlotData();
            line.id = selectedMetric.taskId + "_" + selectedMetric.metricId;
            line.name = selectedMetric.taskId;
            line.data = [];
            var options = new datastructure_2.Lineoptions();
            options.color = exports.colorScaleLines(line.id.split("_", 1)[0]).toString();
            line.options = options;
            result.values.forEach(function (point) {
                var value = new datastructure_2.LinePlotValue();
                value.x = point[0];
                value.y = point[1];
                line.data.push(value);
            });
            if (LinePlot.get(line.id) == undefined) {
                LinePlot.addSeries(line, false);
            }
            else {
                var series_1 = LinePlot.get(line.id);
                line.data.forEach(function (point) {
                    if (point != null) {
                        if (series_1.data.length >= 20) {
                            series_1.addPoint(point, true, true, false);
                            //series.update(series.options)
                        }
                        else {
                            series_1.addPoint(point, true, false, false);
                            //series.update(series.options)
                        }
                    }
                });
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
    $("#optimizeBtn").on("click", function () {
        var isoptimizedLoad = RestInterface_2.optimizeLoad();
        isoptimizedLoad.done(function () {
            if (RestInterface_2.getIsOptimized()) {
                $("#optimizeBtn").addClass("isOptimized");
            }
            else {
                $("#optimizeBtn").removeClass("isOptimized");
            }
        });
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
define("node", ["require", "exports", "d3"], function (require, exports, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    var arcOut = d3.arc()
        .innerRadius(6)
        .outerRadius(12)
        .startAngle(1 * Math.PI);
    var arcIn = d3.arc()
        .innerRadius(6)
        .outerRadius(12)
        .startAngle(1 * Math.PI);
    var colorScaleBuffer = d3.scaleLinear()
        .domain([0, 1.1])
        .range([d3.rgb(74, 255, 71), d3.rgb(255, 71, 71)]);
    function drawNode(point, posx, posy, d) {
        var svg = point, g = svg.append("g")
            .attr("transform", "translate(" + posx + "," + posy + ")");
        var outQueueOutline = g.append("path")
            .datum({ endAngle: 0 * Math.PI })
            .style("stroke", "black")
            .style("fill", "white")
            .attr("d", arcOut)
            .attr("class", "outQueueOutline");
        var outQueue = g.append("path")
            .datum({ endAngle: 0 * Math.PI })
            .style("fill", "white")
            .style("stroke", "black")
            .attr("d", arcIn)
            .attr("class", "outQueue")
            .attr("id", encodeURIComponent(d.name + "-" + "outQueue"));
        var inQueueOutline = g.append("path")
            .datum({ endAngle: 2 * Math.PI })
            .style("stroke", "black")
            .style("fill", "white")
            .attr("d", arcOut)
            .attr("class", "inQueueOutline");
        var inQueue = g.append("path")
            .datum({ endAngle: 2 * Math.PI })
            .style("fill", "white")
            .style("stroke", "black")
            .attr("d", arcOut)
            .attr("class", "inQueue")
            .attr("id", encodeURIComponent(d.name + "-" + "inQueue"));
        return g.node();
    }
    exports.drawNode = drawNode;
    function updateNode(nodes, isInput) {
        if (isInput) {
            d3.selectAll(".inQueue")
                .data(nodes)
                .datum(function (d) {
                return { endAngle: (1 + d.value) * Math.PI, color: colorScaleBuffer(d.value) };
            })
                .style("fill", function (d) {
                return d.color;
            })
                .transition()
                .duration(500)
                .attrTween("d", arcInTween);
        }
        else {
            d3.selectAll(".outQueue")
                .data(nodes)
                .datum(function (d) {
                return { endAngle: (1 - d.value) * Math.PI, color: colorScaleBuffer(d.value) };
            })
                .style("fill", function (d) {
                return d.color;
            })
                .transition()
                .duration(500)
                .attrTween("d", arcOutTween);
        }
    }
    exports.updateNode = updateNode;
    function arcInTween(d) {
        var interp = d3.interpolate(this._current, d);
        this._current = d;
        return function (t) {
            var tmp = interp(t);
            return arcIn(tmp);
        };
    }
    function arcOutTween(d) {
        var interp = d3.interpolate(this._current, d);
        this._current = d;
        return function (t) {
            var tmp = interp(t);
            return arcOut(tmp);
        };
    }
});
define("longGraph", ["require", "exports", "RestInterface", "d3", "LinePlot", "node"], function (require, exports, RestInterface_3, d3, LinePlot_2, node_1) {
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
    var loadColor = LinePlot_2.colorScaleLines;
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
            .style("fill", function (d) {
            return loadColor(d.name);
        });
        //Draw Node Overlay
        graphSvg
            .append("g")
            .attr("class", "overlays")
            .selectAll("overlay")
            .data(taskList)
            .enter().append(function (d) {
            var obj = d3.select(this);
            return node_1.drawNode(obj, xScale(d.cx), yScales[d.cx](d.cy), d);
        });
        //Init Metrics for in and out - Queue
        var initList = getInitList(taskList);
        RestInterface_3.initMetricForTasks("buffers.inPoolUsage", initList, 1).done(function () {
            setInterval(function () {
                updateInputQueue(initList);
            }, 1000);
        });
        RestInterface_3.initMetricForTasks("buffers.outPoolUsage", initList, 1).done(function () {
            setInterval(function () {
                updateOutputQueue(initList);
            }, 1000);
        });
    });
    // Helper Functions
    function updateInputQueue(data) {
        var inputValList = [];
        data.forEach(function (item) {
            RestInterface_3.getDataFromMetrics("buffers.inPoolUsage", item, Date.now() - 1200).done(function (result) {
                var point = result.values[0];
                var inputVal = {
                    taskId: item + "_" + "inQueue",
                    value: point[1]
                };
                inputValList.push(inputVal);
                if (inputValList.length == data.length) {
                    node_1.updateNode(inputValList, true);
                }
            });
        });
    }
    function updateOutputQueue(data) {
        var inputValList = [];
        data.forEach(function (item) {
            RestInterface_3.getDataFromMetrics("buffers.outPoolUsage", item, Date.now() - 1200).done(function (result) {
                var point = result.values[0];
                var inputVal = {
                    taskId: item + "_" + "outQueue",
                    value: point[1]
                };
                inputValList.push(inputVal);
                if (inputValList.length == data.length) {
                    node_1.updateNode(inputValList, false);
                }
            });
        });
    }
    function getInitList(data) {
        var initList = [];
        data.forEach(function (item) {
            initList.push(item.name);
        });
        return initList;
    }
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