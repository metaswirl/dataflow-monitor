import {getDataFromMetrics, getTopology, initMetricForTasks} from "./RestInterface";
import {Cardinality, Task} from "./datastructure";
import d3 = require("d3");
import {colorScaleLines} from "./LinePlot";
import {drawNode, updateNode, updateNodes} from "./node";


let margin = {top: 10, right: 20, bottom: 60, left: 20};
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
//Variables for Debug
let xScale = d3.scaleLinear();
let xLabel = d3.scaleOrdinal();
let yScales = [];
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
let maschineColor = d3.scaleLinear();
maschineColor.domain([0, 5]);
maschineColor.range(["green", "orange"]);
let loadColor = colorScaleLines;

getTopology.done(function (result) {
    result.reverse();

    // xAxis prepare
    xScale.domain([0, result.length - 1]);
    xScale.range([0, canvas.width]);

    let labels = [];
    let labelRange = [];
    result.forEach(function (item, i) {
        labels.push(item.name);
        labelRange.push(xScale(i));
    });
    xLabel.domain(labels);
    xLabel.range(labelRange);

    //various y-Axis
    result.forEach(function (item) {
        let yScale = d3.scaleLinear();
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
            let textElem: any = this;
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
    let cardinality = getLinks(result);

    //Draw the Links
    graphSvg
        .append("g")
        .attr("class", "links")
        .selectAll(".link")
        .data(cardinality)
        .enter().append("path")
        .attr("class", "link")
        .attr("d", function (d: Cardinality) {
            let sx = xScale(d.source.cx), sy = yScales[d.source.cx](d.source.cy),
                tx = xScale(d.target.cx), ty = yScales[d.target.cx](d.target.cy),
                dr = 0;
            return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
        });

    //Prepare Data as Tasklist
    let taskList:Array<Task> = createTaskList(result);

    //Draw the Nodes
    graphSvg
        .append("g")
        .attr("class", "nodes")
        .selectAll(".node")
        .data(taskList)
        .enter().append("circle")
        .attr("r", 5)
        .attr("class", "node")
        .attr("cx", function (d: Task) {
            return xScale(d.cx)
        })
        .attr("cy", function (d: Task) {
            return yScales[d.cx](d.cy)
        })
        .style("fill", function (d: Task) {
            return loadColor(d.name)
        });
    //Draw Node Overlay
    graphSvg
        .append("g")
        .attr("class", "overlays")
        .selectAll("overlay")
        .data(taskList)
        .enter().append(function (d: Task) {
        let obj = d3.select(this);
        return drawNode(obj, xScale(d.cx), yScales[d.cx](d.cy), d);
        });
    //Init Metrics for in and out - Queue
    let initList:Array<string> = getInitList(taskList);
    initMetricForTasks("buffers.inputQueueLength", initList, 1).done(function () {
        setInterval(function () {
            updateInputQueue(initList)
        },1000);
    });
    initMetricForTasks("buffers.outputQueueLength", initList, 1).done(function () {
        setInterval(function () {
            updateOutputQueue(initList)
        },1000);
    });

    //Set Timeout for updates on Nodes

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
function updateInputQueue(data:Array<string>) {
    let inputValList:Array<object> = [];
    data.forEach(function (item) {
        getDataFromMetrics("buffers.inputQueueLength", item, Date.now()-1200).done(function (result) {
            let point = result.values[0];
            let inputVal = {
                taskId: item + "_" + "inQueue",
                value: point[1]
            };
            inputValList.push(inputVal);
            if (inputValList.length == data.length){
                updateNode(inputValList, true)
            }
        });
    });
}
function updateOutputQueue(data:Array<string>) {
    let inputValList:Array<object> = [];
    data.forEach(function (item) {
        getDataFromMetrics("buffers.outputQueueLength", item, Date.now()-1200).done(function (result) {
            let point = result.values[0];
            let inputVal = {
                taskId: item + "_" + "outQueue",
                value: point[1]
            };
            inputValList.push(inputVal);
            if (inputValList.length == data.length){
                updateNode(inputValList, false)
            }
        });
    });
}

function getInitList(data:Array<Task>) {
    let initList:Array<string> = [];
    data.forEach(function (item) {
        initList.push(item.name);
    });
    return initList;
}

function createTaskList(input) {
    let listOfTasks: Array<Task> = [];
    input.forEach(function (item, i) {
        item.tasks.forEach(function (t, j) {
            let task = {
                name: t.id,
                cx: i,
                cy: j
            };
            listOfTasks.push(task)
        })
    });
    return listOfTasks;
}

function getLinks(dataset) {
    let links: Array<object> = [];
    for (let i = 1; i < dataset.length; i++) {
        dataset[i].tasks.forEach(function (task, j) {
            task.input.forEach(function (input, k) {
                let link = {
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
            })
        })
    }
    return links;
}

