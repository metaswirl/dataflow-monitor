import d3 = require("d3");
import {sides} from "./constants";

export class Operator {
    name: string;
    tasks: Array<Task>
    //cy equals tasks index
}
let machineCount = d3.map();
let xValues = d3.map();
let taskMap = d3.map();

export function getTaskByName(key:string) {
    if(taskMap.has(key)){
        return taskMap.get(key)
    }
    else{
        console.log("Task not in Map")
    }
}
export function setTaskByName(value:Task){
    if(taskMap.has(value.id)){
        console.log("Already in Map")
    }
    else{
        taskMap.set(value.id, value);
    }
}

export function getXValue(key:string) {
    if(xValues.has(key)){
        return xValues.get(key)
    }
    else{
        xValues.set(key, xValues.size());
        return xValues.get(key)
    }
}

export function getMachineCount(key:string) {
    if(machineCount.has(key)){
        return machineCount.get(key)
    }
    else{
        machineCount.set(key, (machineCount.size()));
        return machineCount.get(key)
    }
}

let colorScaleBuffer =  d3.scaleLinear()
    .domain([0, 1.1])
    .range([d3.rgb(74, 255, 71), d3.rgb(255, 71, 71)]);

export class Task {
    constructor(id:string, cx:number, cy:number, operator?:string, address?:string, input?:Array<string>){
     this.id = id;
     this.cx = cx;
     this.cy = cy;
     this.operator = operator;
     this.address = address;
     this.input = input;
    }
    id: string;
    cx: number;
    cy: number;
    address?: string;
    operator?:string;
    input?: Array<string>;
    output?: Array<string>
}
export class Metric {
    constructor(taskid:string, metricId:string, resolution:number){
        this.taskId = taskid;
        this.metricId = metricId;
        this.resolution = resolution;
    }
    taskId: string;
    metricId: string;
    resolution: number;
}

export class Cardinality {
    constructor(source:Task, target:Task){
        this.source = source;
        this.target = target;
    }
    reverse():Cardinality{
        let target = this.source;
        this.source = this.target;
        this.target = target;
        return this
    }
    source: Task;
    target: Task
}

export class MetricPostObject {
    metricId: string;
    taskIds: Array<string>;
    resolution: number;

    constructor(mId: string, tId: Array<string>, res: number) {
        this.resolution = res;
        this.taskIds = tId;
        this.metricId = mId;
    }
}

export class MetricListObject extends MetricPostObject {
    since: number;

    constructor(metricPostObject: MetricPostObject) {
        super(metricPostObject.metricId, metricPostObject.taskIds, metricPostObject.resolution)
    }
}

export class LinePlotData {
    constructor(name:string, id:string, options:LineOptions){
        this.name = name;
        this.id = id;
        this.options = options
    }
    name: string;
    id: string;
    options: LineOptions;
    data: Array<Value> = [];
}

export class Value {
    constructor(x:number, y:number){
        this.x = x;
        this.y = y;
    }
    x: number;
    y: number
}
export class LineOptions{
    constructor(color:string){
        this.color = color
    }
    color: string;
}
export class QueueElement {
    constructor(side:sides, value:number, taskId:string) {
        switch (side) {
            case sides.right:
                this.value = value;
                this.endAngle = (1 - value) * Math.PI;
                this.color = colorScaleBuffer(value);
                this.id = taskId + "_" + "outQueue";
                break;
            case sides.left:
                this.value = value;
                this.endAngle = (1 + value) * Math.PI;
                this.color = colorScaleBuffer(value);
                this.id = taskId + "_" + "inQueue";
                break;
        }
    }
    id:string;
    endAngle: number;
    value:number;
    color:string;
}

