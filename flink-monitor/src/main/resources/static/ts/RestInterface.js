//Helper for Class RestInterface
import { Operators } from "./datastructure";
let pathToOperators = "http://localhost:12345/data/Operators";
let pathToMetrics = "http://localhost:12345/data/metrics";
let operators = new Operators();
let metricNames;
let getOperators = $.getJSON(pathToOperators)
    .done(function (result) {
    operators.setSimpleGraph(result);
});
let getMetrics = $.getJSON(pathToMetrics)
    .done(function (result) {
    metricNames = result;
});
$.when(getOperators, getMetrics).done(function () {
    console.log(operators);
    console.log(metricNames);
});
//# sourceMappingURL=RestInterface.js.map