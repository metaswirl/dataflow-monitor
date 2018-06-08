export class Operator {
    name: string;
    tasks: Array<Task>
    //cy equals tasks index
}

export class Task {
    name: string;
    cx: number;
    cy: number;
    input: Array<string>;
    output: Array<string>
}

export class Metric {
    taskId: string;
    metricId: string;
    values: Array<Value>;
    resolution: number;
}

export class Value {
    time: Date;
    value: number;
}

export class Cardinality {
    source: point;
    target: point
}

class point {
    cx: number;
    cy: number
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
    name: string;
    id: string;
    options: Lineoptions;
    data: Array<Value>
}

export class LinePlotValue {
    x: number;
    y: number
}
export class Lineoptions{
    color: string;
}

