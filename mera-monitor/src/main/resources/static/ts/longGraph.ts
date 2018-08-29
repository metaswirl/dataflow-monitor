import {getDataFromMetrics, getTopology, initMetricForTasks} from "./RestInterface";
import {
    Cardinality,
    CardinalityByString,
    getTaskByName,
    getXValue,
    QueueElement,
    setTaskByName,
    Task
} from "./datastructure";
import {colorScaleLines} from "./LinePlot";
import {updateNode} from "./node";
import {inOutPoolResolution, nodeRadius, sides} from "./constants";
import d3 = require("d3");


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
let maxNumberOfTPE:Array<object> = [];
let sumOfTPE:number;
export let xScale = d3.scaleLinear();
let xLabel = d3.scaleOrdinal();
export let yScales = [];
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
let nodeColor = colorScaleLines;

getTopology.done(function (result) {
    result.reverse();
    let hierachy = getHierachy(result);
    sumOfTPE = maxNumberOfTPE.reduce((a,b) => a + b.maxNumber, 0);
    let taskSpace = canvas.height/sumOfTPE;
    let canvasStart:number = 0;

    let yScalePerMaschine = d3.map();
    hierachy.forEach(function (machine) {
        let min = canvasStart;
        let max = canvasStart + (taskSpace * maxNumberOfTPE.find(host => host.machineId === machine.key).maxNumber);
        let yScale = d3.scaleLinear()
                .domain([0, maxNumberOfTPE.find(host => host.machineId === machine.key).maxNumber])
                .range([min, max]);
       yScalePerMaschine.set(machine.key, yScale);
       canvasStart = max;
       console.log(canvasStart);
    });
    


    // xAxis prepare
    xScale.domain([0, result.length -1]);
    xScale.range([50, canvas.width]);

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
                return "translate(" + textElem.getBBox().width * 0.43 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
            else if (i == yScales.length - 1) {
                return "translate(" + textElem.getBBox().width * -0.43 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
            else {
                return "translate(" + 0 + "," + textElem.getBBox().height * 0.5 + ")rotate(0)";
            }
        });

    //Prepare Cardinality List
    let cardinality = getLinks(result);
    let cardinalityByName = getLinksByName(result);
    //Draw Machine Divider
    let dividers = graphSvg
        .append("g")
        .attr("class", "dividers")
        .selectAll("divider")
        .data(maxNumberOfTPE);
        dividers
            .enter()
            .append("path")
            .attr("class","divider")
            .attr("d", function(d){
                let yScale = yScalePerMaschine.get(d.machineId);
                let sx = xScale(0), sy = yScale(d.maxNumber - 1),
                    tx = xScale(0.3), ty = yScale(d.maxNumber - 1),
                    dr = 0;
                return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
            });
        dividers
            .enter()
            .append("text")
            .attr("x", function (d) {
                return xScale(0);
            })
            .attr("y", function (d) {
                let yScale = yScalePerMaschine.get(d.machineId);
                return yScale(d.maxNumber -1) - 10;
            })
            .text(function (d) {
                return d.machineId
            });
    //Draw the Links
    graphSvg
        .append("g")
        .attr("class", "links")
        .selectAll(".link")
        .data(cardinalityByName)
        .enter().append("path")
        .attr("class", "link")
        .attr("d", function (d: CardinalityByString) {
            let ySource = yScalePerMaschine.get(getTaskByName(d.source).address);
            let yTarget = yScalePerMaschine.get(getTaskByName(d.target).address);
            let sx = xScale(getTaskByName(d.source).cx), sy = ySource(getTaskByName(d.source).cy),
                tx = xScale(getTaskByName(d.target).cx), ty = yTarget(getTaskByName(d.target).cy),
                dr = 0;
            return "M" + sx + "," + sy + "A" + dr + "," + dr + " 0 0,1 " + tx + "," + ty;
        });

    //Draw the LineOverlay
    /*graphSvg
        .append("g")
        .attr("class", "lineOverlays")
        .selectAll("lineOverlays")
        .data(cardinality)
        .enter().append(function (d: Cardinality) {
        let obj = d3.select(this);
        return drawNodeLink(obj, d)
    });*/


    //Prepare Data as Tasklist
    let taskList:Array<Task> = createTaskList(result);

    //Draw the Nodes
    graphSvg
        .append("g")
        .attr("class", "nodes")
        .selectAll(".node")
        .data(taskList)
        .enter().append("circle")
        .attr("r", nodeRadius)
        .attr("class", "node")
        .attr("cx", function (d: Task) {
            return xScale(getTaskByName(d.id).cx)
        })
        .attr("cy", function (d: Task) {
            let yScale = yScalePerMaschine.get(getTaskByName(d.id).address);
            return yScale(getTaskByName(d.id).cy)
        })
        .style("fill", function (d: Task) {
            return nodeColor(d.id) as string
        });
    //Draw Node Overlay
    /*graphSvg
        .append("g")
        .attr("class", "nodeOverlays")
        .selectAll("nodeOverlays")
        .data(taskList)
        .enter().append(function (d: Task) {
        let obj = d3.select(this);
        return drawNode(obj, xScale(d.cx), yScales[d.cx](d.cy), d);
        });*/
    //Init Metrics for in and out - Queue
    let initList:Array<string> = getInitList(taskList);
    initMetricForTasks("buffers.inPoolUsage", initList, inOutPoolResolution).done(function () {
        setInterval(function () {
            updateInputQueue(initList)
        },(inOutPoolResolution * 1000));
    });
    initMetricForTasks("buffers.outPoolUsage", initList, inOutPoolResolution).done(function () {
        setInterval(function () {
            updateOutputQueue(initList)
        },(inOutPoolResolution * 1000));
    });
});

// Helper Functions
function updateInputQueue(data:Array<string>) {
    let queueElements:Array<QueueElement> = [];
    data.forEach(function (item) {
        getDataFromMetrics("buffers.inPoolUsage", item, Date.now() - (inOutPoolResolution + 200)).done(function (result) {
            if(result.values.length != 0){
                let queueElement = new QueueElement(sides.left, result.values[0][1], item);
                queueElements.push(queueElement);
                if (queueElements.length == data.length){
                    updateNode(queueElements, true)
                }
            }
        });
    });
}
function updateOutputQueue(data:Array<string>) {
    let queueElements:Array<QueueElement> = [];
    data.forEach(function (item) {
        getDataFromMetrics("buffers.outPoolUsage", item, Date.now() - (inOutPoolResolution + 200)).done(function (result) {
            if(result.values.length != 0){
                let queueElement = new QueueElement(sides.right, result.values[0][1], item);
                queueElements.push(queueElement);
                if (queueElements.length == data.length){
                    updateNode(queueElements, false)
                }
            }
        });
    });
}

function getInitList(data:Array<Task>):Array<string> {
    let initList:Array<string> = [];
    data.forEach(function (item) {
        initList.push(item.id);
    });
    return initList;
}

function createTaskList(input):Array<Task> {
    let listOfTasks:Array<Task> = [];
    input.forEach(function (item, i) {
        item.tasks.forEach(function (t, j) {
            let task = new Task(t.id, i, j);
            listOfTasks.push(task)
        })
    });
    return listOfTasks;
}

function getLinks(dataset):Array<Cardinality> {
    let links: Array<Cardinality> = [];
    for (let i = 1; i < dataset.length; i++) {
        dataset[i].tasks.forEach(function (task, j) {
            task.input.forEach(function (input, k) {
                let target = new Task(input, i-1, k, dataset[i-1].name);
                let source = new Task(task.id, i, j, dataset[i].name, task.host);
                let cardinality = new Cardinality(source, target);
                links.push(cardinality);
            })
        })
    }
    return links;
}
function getLinksByName(dataset):Array<CardinalityByString> {
    let links:Array<CardinalityByString> = [];
    for (let i = 1; i < dataset.length; i++) {
        dataset[i].tasks.forEach(function (task) {
            task.input.forEach(function (input) {
                let source = input;
                let target = task.id;
                let cardinality = new CardinalityByString(source, target);
                links.push(cardinality);
            })
        })
    }
    return links
}
function getHierachy(dataset:Array<object>) {
    let listToOrder:Array<Task> = [];

    dataset.forEach(function (operator) {
        operator.tasks.forEach(function (task, i) {
            let listTask = new Task(task.id, getXValue(operator.name), undefined, operator.name, task.host, task.input);
            listToOrder.push(listTask);
        })
    });
    let parallelismList = d3.nest()
        .key(function (d:Task) {
            return d.address
        })
        .key(function (d:Task) {
            return d.operator
        })
        .rollup(function (v) {
            return v.length
        })
        .entries(listToOrder);
    console.log(parallelismList);

        parallelismList.forEach(function (machine) {
            let machineId = machine.key;
            let maxNumber = Math.max.apply(Math, machine.values.map(function(o) { return o.value; }));
            let object = {
                machineId: machineId,
                maxNumber: maxNumber
            };
            maxNumberOfTPE.push(object);
        });

    let entries = d3.nest()
        .key(function (d:Task) {
            return d.address
        })
        .key(function (d:Task) {
            return d.operator
        })
        .entries(listToOrder);

    entries.forEach(function (entry) {
        entry.values.forEach(function (operator) {
            operator.values.forEach(function (task:Task, k) {
                let mapTask = new Task(task.id, task.cx, (k), operator.key, entry.key, task.input);
                setTaskByName(mapTask);
            })
        })
    });
return entries;
}

