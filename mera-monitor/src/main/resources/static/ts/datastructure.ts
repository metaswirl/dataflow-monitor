export class Operator {
    name: string;
    tasks: Array<Task>
    //cy equals tasks index
}

export class Task {
    constructor(id:string, cx:number, cy:number){
     this.id = id;
     this.cx = cx;
     this.cy = cy;
    }
    id: string;
    cx: number;
    cy: number;
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
    constructor(name:string, id:string, options:Lineoptions){
        this.name = name;
        this.id = id;
        this.options = options
    }
    name: string;
    id: string;
    options: Lineoptions;
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
export class Lineoptions{
    constructor(color:string){
        this.color = color
    }
    color: string;
}

