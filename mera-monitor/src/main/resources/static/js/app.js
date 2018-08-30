define("constants", ["require", "exports"], function (require, exports) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.inOutPoolResolution = 2;
    exports.nodeRadius = 7.5;
    exports.sendRecieveIndicator = 60;
    const nodeBorder = 1;
    exports.arcRadius = {
        inner: exports.nodeRadius + nodeBorder,
        outer: (exports.nodeRadius + nodeBorder) * 2
    };
});
define("datastructure", ["require", "exports", "d3"], function (require, exports, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    class Operator {
    }
    exports.Operator = Operator;
    let machineCount = d3.map();
    let xValues = d3.map();
    let taskMap = d3.map();
    function getTaskByName(key) {
        if (taskMap.has(key)) {
            return taskMap.get(key);
        }
        else {
            console.log("Task not in Map");
        }
    }
    exports.getTaskByName = getTaskByName;
    function setTaskByName(value) {
        if (taskMap.has(value.id)) {
            console.log("Already in Map");
        }
        else {
            taskMap.set(value.id, value);
        }
    }
    exports.setTaskByName = setTaskByName;
    function getXValue(key) {
        if (xValues.has(key)) {
            return xValues.get(key);
        }
        else {
            xValues.set(key, xValues.size());
            return xValues.get(key);
        }
    }
    exports.getXValue = getXValue;
    function getMachineCount(key) {
        if (machineCount.has(key)) {
            return machineCount.get(key);
        }
        else {
            machineCount.set(key, (machineCount.size()));
            return machineCount.get(key);
        }
    }
    exports.getMachineCount = getMachineCount;
    let colorScaleBuffer = d3.scaleLinear()
        .domain([0, 1.1])
        .range([d3.rgb(74, 255, 71), d3.rgb(255, 71, 71)]);
    class Task {
        constructor(id, cx, cy, operator, address, input) {
            this.id = id;
            this.cx = cx;
            this.cy = cy;
            this.operator = operator;
            this.address = address;
            this.input = input;
        }
    }
    exports.Task = Task;
    class Metric {
        constructor(taskid, metricId, resolution) {
            this.taskId = taskid;
            this.metricId = metricId;
            this.resolution = resolution;
        }
    }
    exports.Metric = Metric;
    class Cardinality {
        constructor(source, target) {
            this.source = source;
            this.target = target;
        }
        reverse() {
            let target = this.source;
            this.source = this.target;
            this.target = target;
            return this;
        }
    }
    exports.Cardinality = Cardinality;
    class CardinalityByString {
        constructor(source, target) {
            this.source = source;
            this.target = target;
        }
        reverse() {
            let target = this.source;
            this.source = this.target;
            this.target = target;
            return this;
        }
    }
    exports.CardinalityByString = CardinalityByString;
    class MetricPostObject {
        constructor(mId, tId, res) {
            this.resolution = res;
            this.taskIds = tId;
            this.metricId = mId;
        }
    }
    exports.MetricPostObject = MetricPostObject;
    class MetricListObject extends MetricPostObject {
        constructor(metricPostObject) {
            super(metricPostObject.metricId, metricPostObject.taskIds, metricPostObject.resolution);
        }
    }
    exports.MetricListObject = MetricListObject;
    class LinePlotData {
        constructor(name, id, options) {
            this.data = [];
            this.name = name;
            this.id = id;
            this.options = options;
        }
    }
    exports.LinePlotData = LinePlotData;
    class Value {
        constructor(x, y) {
            this.x = x;
            this.y = y;
        }
    }
    exports.Value = Value;
    class LineOptions {
        constructor(color) {
            this.color = color;
        }
    }
    exports.LineOptions = LineOptions;
    class QueueElement {
        constructor(side, value, taskId) {
            switch (side) {
                case "outQueue" /* right */:
                    this.value = value;
                    this.endAngle = (1 - value) * Math.PI;
                    this.color = colorScaleBuffer(value);
                    this.id = taskId + "_" + "outQueue";
                    break;
                case "inQueue" /* left */:
                    this.value = value;
                    this.endAngle = (1 + value) * Math.PI;
                    this.color = colorScaleBuffer(value);
                    this.id = taskId + "_" + "inQueue";
                    break;
            }
        }
    }
    exports.QueueElement = QueueElement;
});
//Helper for Class RestInterface
define("RestInterface", ["require", "exports", "datastructure", "jquery"], function (require, exports, datastructure_1, $) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    const pathToOperators = "http://127.0.0.1:12345/data/operators";
    const pathToMetrics = "http://127.0.0.1:12345/data/metrics";
    const pathToTopology = "http://127.0.0.1:12345/data/topology";
    const pathToOptimize = "http://127.0.0.1:12345/optimize";
    const pathToInit = "http://127.0.0.1:12345/data/metrics/tasks/init";
    let isOptimized = false;
    let initMetrics = [];
    //export let getOperators = $.getJSON(pathToOperators);
    exports.getMetrics = $.getJSON(pathToMetrics);
    exports.getTopology = $.getJSON(pathToTopology);
    function initMetricForTasks(metric, taskIds, resolutionTime) {
        let postObj = new datastructure_1.MetricPostObject(metric, taskIds, resolutionTime);
        let listPost = new datastructure_1.MetricListObject(postObj);
        listPost.since = Date.now();
        if (resolutionTime >= 5) {
            setInitMetrics(listPost);
        }
        return $.post(pathToInit, JSON.stringify(postObj));
    }
    exports.initMetricForTasks = initMetricForTasks;
    function optimizeLoad() {
        let postObj = {
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
    function getOptimizeStatus() {
        $.get(pathToOptimize).done(function (result) {
            isOptimized = result.running;
            if (isOptimized) {
                $("#optimizeBtn").addClass("isOptimized");
            }
            else {
                $("#optimizeBtn").removeClass("isOptimized");
            }
        });
    }
    exports.getOptimizeStatus = getOptimizeStatus;
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
        let encodedURI = "http://127.0.0.1:12345/data/metrics/task?metricId="
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
    let ChartOptions = {
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
        lang: {
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
        let listOfInitMetrics = RestInterface_1.getInitMetrics();
        listOfInitMetrics.forEach(function (metricListObject) {
            metricListObject.taskIds.forEach(function (task) {
                let selMetric = new datastructure_2.Metric(task, metricListObject.metricId, metricListObject.resolution);
                let lastCall;
                if (LinePlot.get(selMetric.taskId + "_" + selMetric.metricId) != undefined) {
                    let dataPerTask = LinePlot.get(selMetric.taskId + "_" + selMetric.metricId);
                    let dataIndex = dataPerTask.data.length - 1;
                    if (dataIndex >= 0) {
                        lastCall = dataPerTask.data[dataIndex].x;
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
            let options = new datastructure_2.LineOptions(exports.colorScaleLines(selectedMetric.taskId).toString());
            let line = new datastructure_2.LinePlotData(selectedMetric.taskId, selectedMetric.taskId + "_" + selectedMetric.metricId, options);
            result.values.forEach(function (point) {
                let value = new datastructure_2.Value(point[0], point[1]);
                line.data.push(value);
            });
            if (LinePlot.get(line.id) == undefined) {
                LinePlot.addSeries(line, false);
                let plotHeading = $(".highcharts-title")[0].childNodes[0].textContent;
                if (plotHeading == 'Selected Metric') {
                    $(".highcharts-title")[0].childNodes[0].textContent = selectedMetric.metricId;
                }
                else if (plotHeading.indexOf(selectedMetric.metricId) != -1) { }
                else {
                    $(".highcharts-title")[0].childNodes[0].textContent = plotHeading + " & " + selectedMetric.metricId;
                }
            }
            else {
                let series = LinePlot.get(line.id);
                line.data.forEach(function (point) {
                    if (point != null) {
                        if (series.data.length >= 20) {
                            series.addPoint(point, true, true, false);
                        }
                        else {
                            series.addPoint(point, true, false, false);
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
        if ($("#taskoroperator").val() == "byOperator") {
            let optionsByOperator = [];
            result.forEach(function (item) {
                optionsByOperator.push(item.name);
            });
            setOptions(optionsByOperator, "Ids");
        }
        else {
            let optionsByTask = [];
            result.forEach(function (item) {
                item.tasks.forEach(function (ite) {
                    optionsByTask.push(ite.id);
                });
            });
            setOptions(optionsByTask, "Ids");
        }
    });
    window.onload = function () {
        RestInterface_2.getOptimizeStatus();
    };
    $("#taskoroperator").on("change", function () {
        RestInterface_2.getTopology.done(function (result) {
            $("#Ids").empty();
            console.log(result);
            if ($("#taskoroperator").val() == "byOperator") {
                let optionsByOperator = [];
                result.forEach(function (item) {
                    optionsByOperator.push(item.name);
                });
                setOptions(optionsByOperator, "Ids");
            }
            else {
                let optionsByTask = [];
                result.forEach(function (item) {
                    item.tasks.forEach(function (ite) {
                        optionsByTask.push(ite.id);
                    });
                });
                setOptions(optionsByTask, "Ids");
            }
        });
    });
    $("#initButton").on("click", function () {
        initMetricOnAction();
    });
    $("#optimizeBtn").on("click", function () {
        let isoptimizedLoad = RestInterface_2.optimizeLoad();
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
        let metric = $("#metrics").val().toString();
        let Ids = $("#Ids").val();
        let resolutionString = $("input[name=resolutionselect]:checked").val();
        let resolution = parseInt(resolutionString);
        let post = RestInterface_2.initMetricForTasks(metric, Ids, resolution);
        post.done(function () {
            let metrics = RestInterface_2.getInitMetrics();
            metrics.forEach(function (metric) {
                metric.taskIds.forEach(function (task) {
                    let selmetric = new datastructure_3.Metric(task, metric.metricId, metric.resolution);
                    LinePlot_1.setSeries(selmetric, Date.now());
                });
            });
        });
    }
    function setOptions(arrayOfOptions, parentElementTag) {
        let metricSelector = document.getElementById(parentElementTag);
        arrayOfOptions.forEach(function (value) {
            let el = document.createElement("option");
            el.textContent = value;
            el.value = value;
            metricSelector.appendChild(el);
        });
    }
});
define("node", ["require", "exports", "d3", "constants"], function (require, exports, d3, constants_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    let arcOut = d3.arc()
        .innerRadius(constants_1.arcRadius.inner)
        .outerRadius(constants_1.arcRadius.outer)
        .startAngle(1 * Math.PI);
    let arcIn = d3.arc()
        .innerRadius(constants_1.arcRadius.inner)
        .outerRadius(constants_1.arcRadius.outer)
        .startAngle(1 * Math.PI);
    function drawNode(point, posx, posy, d) {
        let svg = point, g = svg.append("g")
            .attr("transform", "translate(" + posx + "," + posy + ")");
        let outQueueOutline = g.append("path")
            .datum({ endAngle: 0 * Math.PI })
            .style("stroke", "black")
            .style("fill", "white")
            .attr("d", arcOut)
            .attr("class", "outQueueOutline");
        let outQueue = g.append("path")
            .datum({ endAngle: 0 * Math.PI })
            .style("fill", "white")
            .style("stroke", "black")
            .attr("d", arcIn)
            .attr("class", "outQueue")
            .attr("id", encodeURIComponent(d.id + "-" + "outQueue"));
        let inQueueOutline = g.append("path")
            .datum({ endAngle: 2 * Math.PI })
            .style("stroke", "black")
            .style("fill", "white")
            .attr("d", arcOut)
            .attr("class", "inQueueOutline");
        let inQueue = g.append("path")
            .datum({ endAngle: 2 * Math.PI })
            .style("fill", "white")
            .style("stroke", "black")
            .attr("d", arcOut)
            .attr("class", "inQueue")
            .attr("id", encodeURIComponent(d.id + "-" + "inQueue"));
        return g.node();
    }
    exports.drawNode = drawNode;
    function updateNode(nodes, isInput) {
        if (isInput) {
            d3.selectAll(".inQueue")
                .data(nodes)
                .datum(function (d) {
                return d;
            })
                .style("fill", function (d) {
                return d.color;
            })
                .transition()
                .duration(constants_1.inOutPoolResolution * 1000)
                .attrTween("d", arcInTween);
        }
        else {
            d3.selectAll(".outQueue")
                .data(nodes)
                .datum(function (d) {
                return d;
            })
                .style("fill", function (d) {
                return d.color;
            })
                .transition()
                .duration(constants_1.inOutPoolResolution * 1000)
                .attrTween("d", arcOutTween);
        }
    }
    exports.updateNode = updateNode;
    function arcInTween(d) {
        let interp = d3.interpolate(this._current, d);
        this._current = d;
        return function (t) {
            let tmp = interp(t);
            return arcIn(tmp);
        };
    }
    function arcOutTween(d) {
        let interp = d3.interpolate(this._current, d);
        this._current = d;
        return function (t) {
            let tmp = interp(t);
            return arcOut(tmp);
        };
    }
});
define("node_links", ["require", "exports", "datastructure", "constants", "longGraph", "d3"], function (require, exports, datastructure_4, constants_2, longGraph_1, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    function drawNodeLink(obj, link, level) {
        let svg = obj;
        let g = svg.append("g");
        let percentToLength = d3.scaleLinear()
            .range([constants_2.arcRadius.outer, constants_2.sendRecieveIndicator])
            .domain([0, 100]);
        let outputStream = svg.append("g");
        outputStream.append("path")
            .attr("class", "inStreamFull")
            .datum(link)
            .attr("id", function (d) {
            return d.target + "inputStreamFull" + d.source;
        })
            .attr("d", function (d) {
            let mT = calcFilling(d, false);
            return "M" + longGraph_1.xScale(datastructure_4.getTaskByName(d.source).cx) + ","
                + longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(d.source).address)(datastructure_4.getTaskByName(d.source).cy)
                + "A" + 0 + "," + 0 + " 0 0,1 "
                + mT.x + ","
                + mT.y;
        });
        outputStream.append("path")
            .attr("class", "inStreamLink")
            .datum(link)
            .attr("id", function (d) {
            return d.target + "inputSteamLink" + d.source;
        })
            .attr("d", function (d) {
            let mT = calcFilling(d, false, percentToLength(60));
            return "M" + longGraph_1.xScale(datastructure_4.getTaskByName(d.source).cx) + ","
                + longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(d.source).address)(datastructure_4.getTaskByName(d.source).cy)
                + "A" + 0 + "," + 0 + " 0 0,1 "
                + mT.x + ","
                + mT.y;
        });
        let inputStreamMax = svg.append("g");
        inputStreamMax.append("path")
            .attr("class", "outStreamFull")
            .datum(link.reverse())
            .attr("id", function (d) {
            return d.source + "outputStreamFull" + d.target;
        })
            .attr("d", function (d) {
            let mT = calcFilling(d, true);
            return "M" + longGraph_1.xScale(datastructure_4.getTaskByName(d.source).cx) + ","
                + longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(d.source).address)(datastructure_4.getTaskByName(d.source).cy)
                + "A" + 0 + "," + 0 + " 0 0,1 "
                + mT.x + ","
                + mT.y;
        });
        inputStreamMax.append("path")
            .attr("class", "outStreamLink")
            .datum(link)
            .attr("id", function (d) {
            return d.source + "outputStreamLink" + d.target;
        })
            .attr("d", function (d) {
            let mT = calcFilling(d, true, percentToLength(5));
            return "M" + longGraph_1.xScale(datastructure_4.getTaskByName(d.source).cx) + ","
                + longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(d.source).address)(datastructure_4.getTaskByName(d.source).cy)
                + "A" + 0 + "," + 0 + " 0 0,1 "
                + mT.x + ","
                + mT.y;
        });
        return g.node();
    }
    exports.drawNodeLink = drawNodeLink;
    //Helper Functions
    function calcFilling(link, reverse, level) {
        let alpha = Math.atan((longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(link.target).address)(datastructure_4.getTaskByName(link.target).cy) - longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(link.source).address)(datastructure_4.getTaskByName(link.source).cy)) / (longGraph_1.xScale(datastructure_4.getTaskByName(link.target).cx) - longGraph_1.xScale(datastructure_4.getTaskByName(link.source).cx)));
        let mX = longGraph_1.xScale(datastructure_4.getTaskByName(link.source).cx);
        let mY = longGraph_1.yScalePerMaschine.get(datastructure_4.getTaskByName(link.source).address)(datastructure_4.getTaskByName(link.source).cy);
        if (reverse) {
            if (level != null) {
                mX -= (level) * Math.cos(alpha);
                mY -= (level) * Math.sin(alpha);
            }
            else {
                mX -= (constants_2.sendRecieveIndicator) * Math.cos(alpha);
                mY -= (constants_2.sendRecieveIndicator) * Math.sin(alpha);
            }
        }
        else {
            if (level != null) {
                mX += (level) * Math.cos(alpha);
                mY += (level) * Math.sin(alpha);
            }
            else {
                mX += (constants_2.sendRecieveIndicator) * Math.cos(alpha);
                mY += (constants_2.sendRecieveIndicator) * Math.sin(alpha);
            }
        }
        let value = new datastructure_4.Value(mX, mY);
        return value;
    }
});
define("longGraph", ["require", "exports", "RestInterface", "datastructure", "LinePlot", "node", "constants", "node_links", "d3"], function (require, exports, RestInterface_3, datastructure_5, LinePlot_2, node_1, constants_3, node_links_1, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    let margin = { top: 20, right: 20, bottom: 60, left: 20 };
    let longGraph = {
        width: $(".longGraph").width(),
        height: $(".longGraph").height()
    };
    let shortGraph = {
        width: $(".shortGraph").width(),
        height: $(".shortGraph").height()
    };
    let canvas = {
        width: longGraph.width - margin.right - margin.left,
        height: longGraph.height - margin.top - margin.bottom
    };
    let shortGraphCanvas = {
        width: shortGraph.width - margin.right - margin.left,
        height: shortGraph.height - margin.top - margin.bottom
    };
    let maxNumberOfTPE = [];
    let sumOfTPE;
    exports.xScale = d3.scaleLinear();
    let xLabel = d3.scaleOrdinal();
    exports.yScales = [];
    exports.yScalePerMaschine = d3.map();
    let graphSvg = d3.select("#longGraph")
        .attr("width", longGraph.width)
        .attr("height", longGraph.height)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
    let shortGraphSvg = d3.select("#shortGraph")
        .attr("width", shortGraph.width)
        .attr("height", shortGraph.height)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
    //Color Axis
    let nodeColor = LinePlot_2.colorScaleLines;
    RestInterface_3.getTopology.done(function (result) {
        result.reverse();
        console.log(result);
        let hierachy = getHierachy(result);
        sumOfTPE = maxNumberOfTPE.reduce((a, b) => a + b.maxNumber, 0);
        let taskSpace = canvas.height / sumOfTPE;
        let canvasStart = 0;
        hierachy.forEach(function (machine) {
            let min = canvasStart;
            let max = canvasStart + (taskSpace * maxNumberOfTPE.find(host => host.machineId === machine.key).maxNumber);
            let yScale = d3.scaleLinear()
                .domain([0, maxNumberOfTPE.find(host => host.machineId === machine.key).maxNumber])
                .range([min, max]);
            exports.yScalePerMaschine.set(machine.key, yScale);
            canvasStart = max;
        });
        // xAxis prepare
        exports.xScale.domain([0, result.length - 1]);
        exports.xScale.range([0, canvas.width]);
        let labels = [];
        let labelRange = [];
        result.forEach(function (item, i) {
            labels.push(item.id);
            labelRange.push(exports.xScale(i));
        });
        xLabel.domain(labels);
        xLabel.range(labelRange);
        //various y-Axis
        result.forEach(function (item) {
            let yScale = d3.scaleLinear();
            yScale.domain([-1, item.tasks.length]);
            yScale.range([0, canvas.height]);
            exports.yScales.push(yScale);
        });
        //Draw X-Axis
        shortGraphSvg
            .append("g")
            .attr("class", "xAxis")
            .attr("transform", "translate(0, " + shortGraphCanvas.height + ")")
            .call(d3.axisBottom(xLabel));
        shortGraphSvg.selectAll(".xAxis text")
            .attr("transform", function (d, i) {
            let textElem = this;
            if (i == 0) {
                return "translate(" + textElem.getBBox().width * 0.43 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
            else if (i == exports.yScales.length - 1) {
                return "translate(" + textElem.getBBox().width * -0.43 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
            else {
                return "translate(" + 0 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
        });
        //Prepare Cardinality List
        let cardinalityByName = getLinksByName(result);
        //Draw the Links
        graphSvg
            .append("g")
            .attr("class", "links")
            .selectAll(".link")
            .data(cardinalityByName)
            .enter().append("path")
            .attr("class", "link")
            .attr("d", function (d) {
            let ySource = exports.yScalePerMaschine.get(datastructure_5.getTaskByName(d.source).address);
            let yTarget = exports.yScalePerMaschine.get(datastructure_5.getTaskByName(d.target).address);
            let sx = exports.xScale(datastructure_5.getTaskByName(d.source).cx), sy = ySource(datastructure_5.getTaskByName(d.source).cy), tx = exports.xScale(datastructure_5.getTaskByName(d.target).cx), ty = yTarget(datastructure_5.getTaskByName(d.target).cy), dr = 0;
            return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
        });
        //Draw Machine Divider
        let dividers = graphSvg
            .append("g")
            .attr("class", "dividers")
            .selectAll("divider")
            .data(maxNumberOfTPE);
        dividers
            .enter()
            .append("path")
            .attr("class", "divider")
            .attr("d", function (d) {
            let yScale = exports.yScalePerMaschine.get(d.machineId);
            let sx = exports.xScale(0), sy = yScale(d.maxNumber - 1), tx = exports.xScale(5), ty = yScale(d.maxNumber - 1), dr = 0;
            return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
        })
            .attr("stroke-dasharray", "5,10,5")
            .attr("transform", "translate(" + 0 + "," + taskSpace / 2 + ")");
        dividers
            .enter()
            .append("text")
            .attr("x", function () {
            return exports.xScale(0);
        })
            .attr("y", function (d) {
            let yScale = exports.yScalePerMaschine.get(d.machineId);
            return yScale(d.maxNumber - 1);
        })
            .text(function (d) {
            return d.machineId;
        });
        //Draw the LineOverlay
        graphSvg
            .append("g")
            .attr("class", "lineOverlays")
            .selectAll("lineOverlays")
            .data(cardinalityByName)
            .enter().append(function (d) {
            let obj = d3.select(this);
            return node_links_1.drawNodeLink(obj, d);
        });
        //Prepare Data as Tasklist
        let taskList = createTaskList(result);
        //Draw the Nodes
        graphSvg
            .append("g")
            .attr("class", "nodes")
            .selectAll(".node")
            .data(taskList)
            .enter().append("circle")
            .attr("r", constants_3.nodeRadius)
            .attr("class", "node")
            .attr("cx", function (d) {
            return exports.xScale(datastructure_5.getTaskByName(d.id).cx);
        })
            .attr("cy", function (d) {
            let yScale = exports.yScalePerMaschine.get(datastructure_5.getTaskByName(d.id).address);
            return yScale(datastructure_5.getTaskByName(d.id).cy);
        })
            .style("fill", function (d) {
            return nodeColor(d.id);
        });
        //Draw Node Overlay
        graphSvg
            .append("g")
            .attr("class", "nodeOverlays")
            .selectAll("nodeOverlays")
            .data(taskList)
            .enter().append(function (d) {
            let obj = d3.select(this);
            return node_1.drawNode(obj, exports.xScale(d.cx), exports.yScalePerMaschine.get(datastructure_5.getTaskByName(d.id).address)(datastructure_5.getTaskByName(d.id).cy), d);
        });
        //Init Metrics for in and out - Queue
        let initList = getInitList(taskList);
        RestInterface_3.initMetricForTasks("buffers.inPoolUsage", initList, constants_3.inOutPoolResolution).done(function () {
            setInterval(function () {
                updateInputQueue(initList);
            }, (constants_3.inOutPoolResolution * 1000));
        });
        RestInterface_3.initMetricForTasks("buffers.outPoolUsage", initList, constants_3.inOutPoolResolution).done(function () {
            setInterval(function () {
                updateOutputQueue(initList);
            }, (constants_3.inOutPoolResolution * 1000));
        });
    });
    // Helper Functions
    function updateInputQueue(data) {
        let queueElements = [];
        data.forEach(function (item) {
            RestInterface_3.getDataFromMetrics("buffers.inPoolUsage", item, Date.now() - (constants_3.inOutPoolResolution + 200)).done(function (result) {
                if (result.values.length != 0) {
                    let queueElement = new datastructure_5.QueueElement("inQueue" /* left */, result.values[0][1], item);
                    queueElements.push(queueElement);
                    if (queueElements.length == data.length) {
                        node_1.updateNode(queueElements, true);
                    }
                }
            });
        });
    }
    function updateOutputQueue(data) {
        let queueElements = [];
        data.forEach(function (item) {
            RestInterface_3.getDataFromMetrics("buffers.outPoolUsage", item, Date.now() - (constants_3.inOutPoolResolution + 200)).done(function (result) {
                if (result.values.length != 0) {
                    let queueElement = new datastructure_5.QueueElement("outQueue" /* right */, result.values[0][1], item);
                    queueElements.push(queueElement);
                    if (queueElements.length == data.length) {
                        node_1.updateNode(queueElements, false);
                    }
                }
            });
        });
    }
    function getInitList(data) {
        let initList = [];
        data.forEach(function (item) {
            initList.push(item.id);
        });
        return initList;
    }
    function createTaskList(input) {
        let listOfTasks = [];
        input.forEach(function (item, i) {
            item.tasks.forEach(function (t, j) {
                let task = new datastructure_5.Task(t.id, i, j);
                listOfTasks.push(task);
            });
        });
        return listOfTasks;
    }
    function getLinks(dataset) {
        let links = [];
        for (let i = 1; i < dataset.length; i++) {
            dataset[i].tasks.forEach(function (task, j) {
                task.input.forEach(function (input, k) {
                    let target = new datastructure_5.Task(input, i - 1, k, dataset[i - 1].name);
                    let source = new datastructure_5.Task(task.id, i, j, dataset[i].name, task.host);
                    let cardinality = new datastructure_5.Cardinality(source, target);
                    links.push(cardinality);
                });
            });
        }
        return links;
    }
    function getLinksByName(dataset) {
        let links = [];
        for (let i = 1; i < dataset.length; i++) {
            dataset[i].tasks.forEach(function (task) {
                task.input.forEach(function (input) {
                    let source = input;
                    let target = task.id;
                    let cardinality = new datastructure_5.CardinalityByString(source, target);
                    links.push(cardinality);
                });
            });
        }
        return links;
    }
    function getHierachy(dataset) {
        let listToOrder = [];
        dataset.forEach(function (operator) {
            operator.tasks.forEach(function (task, i) {
                let listTask = new datastructure_5.Task(task.id, datastructure_5.getXValue(operator.id), undefined, operator.id, task.address, task.input);
                listToOrder.push(listTask);
            });
        });
        let parallelismList = d3.nest()
            .key(function (d) {
            return d.address;
        })
            .key(function (d) {
            return d.operator;
        })
            .rollup(function (v) {
            return v.length;
        })
            .entries(listToOrder);
        console.log(parallelismList);
        parallelismList.forEach(function (machine) {
            let machineId = machine.key;
            let maxNumber = Math.max.apply(Math, machine.values.map(function (o) { return o.value; }));
            let object = {
                machineId: machineId,
                maxNumber: maxNumber
            };
            maxNumberOfTPE.push(object);
        });
        let entries = d3.nest()
            .key(function (d) {
            return d.address;
        })
            .key(function (d) {
            return d.operator;
        })
            .entries(listToOrder);
        entries.forEach(function (entry) {
            entry.values.forEach(function (operator) {
                operator.values.forEach(function (task, k) {
                    let mapTask = new datastructure_5.Task(task.id, task.cx, (k), operator.key, entry.key, task.input);
                    datastructure_5.setTaskByName(mapTask);
                });
            });
        });
        return entries;
    }
});
//# sourceMappingURL=app.js.map