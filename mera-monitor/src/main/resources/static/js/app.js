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
            console.log("Task already in Map");
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
    let step = d3.scaleLinear()
        .domain([1, 8])
        .range([1.1, 0]);
    // @ts-ignore
    let colorScaleBuffer = d3.scaleLinear()
        .domain([1.1, step(2), step(3), step(4), step(5), step(6), step(7), 0])
        .range(['#d73027', '#f46d43', '#fdae61', '#fee08b', '#d9ef8b', '#a6d96a', '#66bd63', '#1a9850'])
        .interpolate(d3.interpolateHcl);
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
        constructor(source, target, inFraction, outFraction) {
            this.source = source;
            this.target = target;
            this.inFraction = inFraction;
            this.outFraction = outFraction;
        }
        reverse() {
            let target = this.source;
            this.source = this.target;
            this.target = target;
            let inFra = this.outFraction;
            this.outFraction = this.inFraction;
            this.inFraction = inFra;
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
    const pathToEdges = "http://127.0.0.1:12345/data/edges";
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
    function getDataFromEdges() {
        return $.getJSON(pathToEdges);
    }
    exports.getDataFromEdges = getDataFromEdges;
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
define("node", ["require", "exports", "d3", "datastructure", "constants", "longGraph"], function (require, exports, d3, datastructure_3, constants_1, longGraph_1) {
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
    function drawNode(point, d) {
        let g = point.append("g")
            .attr("transform", "translate(" + longGraph_1.xScale(d.cx) + "," + longGraph_1.yScalePerMaschine.get(datastructure_3.getTaskByName(d.id).address)(datastructure_3.getTaskByName(d.id).cy) + ")");
        g.append("path")
            .datum({ endAngle: 0 * Math.PI })
            .style("stroke", "black")
            .style("fill", "white")
            .attr("d", arcOut)
            .attr("class", "outQueueOutline");
        g.append("path")
            .datum({ endAngle: 0 * Math.PI })
            .style("fill", "white")
            .style("stroke", "black")
            .attr("d", arcIn)
            .attr("class", "outQueue")
            .attr("id", encodeURIComponent(d.id + "-" + "outQueue"));
        g.append("path")
            .datum({ endAngle: 2 * Math.PI })
            .style("stroke", "black")
            .style("fill", "white")
            .attr("d", arcOut)
            .attr("class", "inQueueOutline");
        g.append("path")
            .datum({ endAngle: 2 * Math.PI })
            .style("fill", "gray")
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
                .each(function (d) {
                if (d.endAngle == Math.PI) {
                }
                else {
                    d3.select(this)
                        .style("fill", function (d) {
                        return d.color;
                    })
                        .transition()
                        .duration(constants_1.inOutPoolResolution * 1000)
                        .styleTween("fill", arcTweenColor)
                        .attrTween("d", arcInTween);
                }
            });
        }
        else {
            d3.selectAll(".outQueue")
                .data(nodes)
                .style("fill", function (d) {
                return d.color;
            })
                .transition()
                .duration(constants_1.inOutPoolResolution * 1000)
                .styleTween("fill", arcTweenColor)
                .attrTween("d", arcOutTween);
        }
    }
    exports.updateNode = updateNode;
    function arcTweenColor(d) {
        let t = this._current;
        if (t) {
            let interp = d3.interpolateRgb(t.color, d.color);
            return interp;
        }
    }
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
define("longGraph", ["require", "exports", "RestInterface", "datastructure", "LinePlot", "node", "constants", "node_links", "d3"], function (require, exports, RestInterface_2, datastructure_4, LinePlot_1, node_1, constants_2, node_links_1, d3) {
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
    let nodeColor = LinePlot_1.colorScaleLines;
    //Set SVG Hierachy before Rest calls
    let links = graphSvg
        .append("g")
        .attr("class", "links");
    let linkOverlay = graphSvg
        .append("g")
        .attr("class", "lineOverlays");
    RestInterface_2.getTopology.done(function (result) {
        result.reverse();
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
        RestInterface_2.getDataFromEdges().done(function (result) {
            let cardinalityByRest = [];
            result.map(function (item) {
                let cardinaltyByString = new datastructure_4.CardinalityByString(item.src, item.dst, item.inFraction, item.outFraction);
                cardinalityByRest.push(cardinaltyByString);
            });
            //Draw the Links
            links
                .selectAll(".link")
                .data(cardinalityByRest)
                .enter().append("path")
                .attr("class", "link")
                .attr("d", function (d) {
                let ySource = exports.yScalePerMaschine.get(datastructure_4.getTaskByName(d.source).address);
                let yTarget = exports.yScalePerMaschine.get(datastructure_4.getTaskByName(d.target).address);
                let sx = exports.xScale(datastructure_4.getTaskByName(d.source).cx), sy = ySource(datastructure_4.getTaskByName(d.source).cy), tx = exports.xScale(datastructure_4.getTaskByName(d.target).cx), ty = yTarget(datastructure_4.getTaskByName(d.target).cy), dr = 0;
                return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
            });
            //Draw the LineOverlay
            linkOverlay
                .selectAll("lineOverlays")
                .data(cardinalityByRest)
                .enter().append(function (d) {
                let obj = d3.select(this);
                return node_links_1.drawNodeLink(obj, d);
            });
            node_links_1.updateNodeLink(cardinalityByRest);
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
        //Prepare Data as Tasklist
        let taskList = createTaskList(result);
        //Draw the Nodes
        graphSvg
            .append("g")
            .attr("class", "nodes")
            .selectAll(".node")
            .data(taskList)
            .enter().append("circle")
            .attr("r", constants_2.nodeRadius)
            .attr("class", "node")
            .attr("cx", function (d) {
            return exports.xScale(datastructure_4.getTaskByName(d.id).cx);
        })
            .attr("cy", function (d) {
            return exports.yScalePerMaschine.get(datastructure_4.getTaskByName(d.id).address)(datastructure_4.getTaskByName(d.id).cy);
        })
            .style("fill", function () {
            return "rgb(49, 130, 189)";
        });
        //Draw Node Overlay
        graphSvg
            .append("g")
            .attr("class", "nodeOverlays")
            .selectAll("nodeOverlays")
            .data(taskList)
            .enter().append(function (d) {
            return node_1.drawNode(d3.select(this), d);
        });
        //Init Metrics for in and out - Queue
        let initList = getInitList(taskList);
        RestInterface_2.initMetricForTasks("buffers.inPoolUsage", initList, constants_2.inOutPoolResolution).done(function () {
            setInterval(function () {
                updateInputQueue(initList);
            }, (constants_2.inOutPoolResolution * 1000));
        });
        RestInterface_2.initMetricForTasks("buffers.outPoolUsage", initList, constants_2.inOutPoolResolution).done(function () {
            setInterval(function () {
                updateOutputQueue(initList);
            }, (constants_2.inOutPoolResolution * 1000));
        });
    });
    // Helper Functions
    function updateInputQueue(data) {
        let queueElements = [];
        data.forEach(function (item) {
            RestInterface_2.getDataFromMetrics("buffers.inPoolUsage", item, Date.now() - (constants_2.inOutPoolResolution + 200)).done(function (result) {
                if (result.values.length != 0) {
                    let queueElement = new datastructure_4.QueueElement("inQueue" /* left */, result.values[0][1], item);
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
            RestInterface_2.getDataFromMetrics("buffers.outPoolUsage", item, Date.now() - (constants_2.inOutPoolResolution + 200)).done(function (result) {
                if (result.values.length != 0) {
                    let queueElement = new datastructure_4.QueueElement("outQueue" /* right */, result.values[0][1], item);
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
                let task = new datastructure_4.Task(t.id, i, j);
                listOfTasks.push(task);
            });
        });
        return listOfTasks;
    }
    function getHierachy(dataset) {
        let listToOrder = [];
        dataset.forEach(function (operator) {
            operator.tasks.forEach(function (task, i) {
                let listTask = new datastructure_4.Task(task.id, datastructure_4.getXValue(operator.id), undefined, operator.id, task.address, task.input);
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
                    let mapTask = new datastructure_4.Task(task.id, task.cx, (k), operator.key, entry.key, task.input);
                    datastructure_4.setTaskByName(mapTask);
                });
            });
        });
        return entries;
    }
});
define("node_links", ["require", "exports", "datastructure", "constants", "longGraph", "d3"], function (require, exports, datastructure_5, constants_3, longGraph_2, d3) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    let percentToLength = d3.scaleLinear()
        .range([constants_3.arcRadius.outer, constants_3.sendRecieveIndicator])
        .domain([0, 100]);
    function drawNodeLink(obj, link, level) {
        let svg = obj;
        let g = svg.append("g");
        let outputStream = svg.append("g");
        outputStream.append("path")
            .attr("class", "inFractionFull")
            .datum(link)
            .attr("id", function (d) {
            return normalizeString(d.target + "inputFractionFull" + d.source);
        })
            .attr("d", function (d) {
            return line(d, false, percentToLength(100));
        });
        outputStream.append("path")
            .attr("class", "inFractionLink")
            .datum(link)
            .attr("id", function (d) {
            return normalizeString(d.target + "inputFractionLink" + d.source);
        })
            .attr("d", function (d) {
            return line(d, false, percentToLength(getRandomInt(100)));
        });
        let inputStreamMax = svg.append("g");
        inputStreamMax.append("path")
            .attr("class", "outFractionFull")
            .datum(link.reverse())
            .attr("id", function (d) {
            return normalizeString(d.source + "outputFractionFull" + d.target);
        })
            .attr("d", function (d) {
            return line(d, true, percentToLength(100));
        });
        inputStreamMax.append("path")
            .attr("class", "outFractionLink")
            .datum(link)
            .attr("id", function (d) {
            return normalizeString(d.source + "outputFractionLink" + d.target);
        })
            .attr("d", (d) => {
            return line(d, true, percentToLength(getRandomInt(100)));
        });
        return g.node();
    }
    exports.drawNodeLink = drawNodeLink;
    function updateNodeLink(updateNodeList) {
        updateNodeList.forEach(function (item) {
            let inLink = d3.select("#" + normalizeString(item.target + "inputFractionLink" + item.source));
            inLink
                .datum(item)
                .transition()
                .duration(1000)
                .attrTween("d", pathTween(line(item, false, percentToLength(getRandomInt(100))), 4));
            let outLink = d3.select("#" + normalizeString(item.target + "outputFractionLink" + item.source));
            outLink
                .datum(item)
                .transition()
                .duration(1000)
                .attrTween("d", pathTween(line(item.reverse(), true, percentToLength(getRandomInt(100))), 4));
        });
    }
    exports.updateNodeLink = updateNodeLink;
    //Helper Functions
    function calcFilling(link, reverse, level) {
        let alpha = Math.atan((longGraph_2.yScalePerMaschine.get(datastructure_5.getTaskByName(link.target).address)(datastructure_5.getTaskByName(link.target).cy) - longGraph_2.yScalePerMaschine.get(datastructure_5.getTaskByName(link.source).address)(datastructure_5.getTaskByName(link.source).cy)) / (longGraph_2.xScale(datastructure_5.getTaskByName(link.target).cx) - longGraph_2.xScale(datastructure_5.getTaskByName(link.source).cx)));
        let mX = longGraph_2.xScale(datastructure_5.getTaskByName(link.source).cx);
        let mY = longGraph_2.yScalePerMaschine.get(datastructure_5.getTaskByName(link.source).address)(datastructure_5.getTaskByName(link.source).cy);
        if (reverse) {
            if (level != null) {
                mX -= (level) * Math.cos(alpha);
                mY -= (level) * Math.sin(alpha);
            }
            else {
                mX -= (constants_3.sendRecieveIndicator) * Math.cos(alpha);
                mY -= (constants_3.sendRecieveIndicator) * Math.sin(alpha);
            }
        }
        else {
            if (level != null) {
                mX += (level) * Math.cos(alpha);
                mY += (level) * Math.sin(alpha);
            }
            else {
                mX += (constants_3.sendRecieveIndicator) * Math.cos(alpha);
                mY += (constants_3.sendRecieveIndicator) * Math.sin(alpha);
            }
        }
        let value = new datastructure_5.Value(mX, mY);
        return value;
    }
    function getRandomInt(max) {
        return Math.floor(Math.random() * Math.floor(max));
    }
    function line(d, output, level) {
        let mT = calcFilling(d, output, level);
        return "M" + longGraph_2.xScale(datastructure_5.getTaskByName(d.source).cx) + ","
            + longGraph_2.yScalePerMaschine.get(datastructure_5.getTaskByName(d.source).address)(datastructure_5.getTaskByName(d.source).cy)
            + "A" + 0 + "," + 0 + " 0 0,1 "
            + mT.x + ","
            + mT.y;
    }
    function pathTween(d1, precision) {
        return function () {
            let path0 = this, path1 = path0.cloneNode(), n0 = path0.getTotalLength(), n1 = (path1.setAttribute("d", d1), path1).getTotalLength();
            // Uniform sampling of distance based on specified precision.
            let distances = [0], i = 0, dt = precision / Math.max(n0, n1);
            while ((i += dt) < 1)
                distances.push(i);
            distances.push(1);
            // Compute point-interpolators at each distance.
            let points = distances.map(function (t) {
                let p0 = path0.getPointAtLength(t * n0), p1 = path1.getPointAtLength(t * n1);
                return d3.interpolate([p0.x, p0.y], [p1.x, p1.y]);
            });
            return function (t) {
                return t < 1 ? "M" + points.map(function (p) { return p(t); }).join("L") : d1;
            };
        };
    }
    function normalizeString(input) {
        let output = input.replace(/[^a-zA-Z0-9]/gm, "");
        return output;
    }
});
define("interfaceLoads", ["require", "exports", "RestInterface", "datastructure", "LinePlot", "node_links"], function (require, exports, RestInterface_3, datastructure_6, LinePlot_2, node_links_2) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    RestInterface_3.getMetrics.done(function (result) {
        setOptions(result, "metrics");
    });
    RestInterface_3.getTopology.done(function (result) {
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
        RestInterface_3.getOptimizeStatus();
        setInterval(function () {
            RestInterface_3.getDataFromEdges().done(function (result) {
                let cardinalityByRest = [];
                result.map(function (item) {
                    let cardinaltyByString = new datastructure_6.CardinalityByString(item.src, item.dst, item.inFraction, item.outFraction);
                    cardinalityByRest.push(cardinaltyByString);
                });
                node_links_2.updateNodeLink(cardinalityByRest);
            });
        }, 5000);
    };
    $("#taskoroperator").on("change", function () {
        RestInterface_3.getTopology.done(function (result) {
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
        let isoptimizedLoad = RestInterface_3.optimizeLoad();
        isoptimizedLoad.done(function () {
            if (RestInterface_3.getIsOptimized()) {
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
        let post = RestInterface_3.initMetricForTasks(metric, Ids, resolution);
        post.done(function () {
            let metrics = RestInterface_3.getInitMetrics();
            metrics.forEach(function (metric) {
                metric.taskIds.forEach(function (task) {
                    let selmetric = new datastructure_6.Metric(task, metric.metricId, metric.resolution);
                    LinePlot_2.setSeries(selmetric, Date.now());
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
//# sourceMappingURL=app.js.map