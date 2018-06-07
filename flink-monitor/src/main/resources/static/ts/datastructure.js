export class Operator {
}
export class Operators {
    setSimpleGraph(data) {
        let simpleGraph = [];
        data.forEach(function (value) {
            let scopedata = new Operator();
            scopedata.name = value;
            simpleGraph.push(scopedata);
        });
        this.listOfOperators = simpleGraph;
    }
}
export class Task {
}
export class Metric {
}
export class Value {
}
//# sourceMappingURL=datastructure.js.map