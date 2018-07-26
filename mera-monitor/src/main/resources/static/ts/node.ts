import d3 = require("d3");
import {QueueElement, Task} from "./datastructure";
import {arcRadius, inOutPoolResolution} from "./constants";

let arcOut = d3.arc()
    .innerRadius(arcRadius.inner)
    .outerRadius(arcRadius.outer)
    .startAngle(1 * Math.PI);
let arcIn = d3.arc()
    .innerRadius(arcRadius.inner)
    .outerRadius(arcRadius.outer)
    .startAngle(1 * Math.PI);

export function drawNode(point, posx:number , posy:number, d:Task) {
    let svg = point,
        g = svg.append("g")
            .attr("transform", "translate(" + posx + "," + posy + ")");

    let outQueueOutline = g.append("path")
        .datum({endAngle: 0 * Math.PI})
        .style("stroke", "black")
        .style("fill", "white")
        .attr("d", arcOut)
        .attr("class", "outQueueOutline");
    let outQueue = g.append("path")
        .datum({endAngle: 0 * Math.PI})
        .style("fill", "white")
        .style("stroke", "black")
        .attr("d", arcIn)
        .attr("class", "outQueue")
        .attr("id", encodeURIComponent(d.id + "-" + "outQueue"));

    let inQueueOutline = g.append("path")
        .datum({endAngle: 2 * Math.PI})
        .style("stroke", "black")
        .style("fill", "white")
        .attr("d", arcOut)
        .attr("class", "inQueueOutline");
    let inQueue = g.append("path")
        .datum({endAngle: 2 * Math.PI})
        .style("fill", "white")
        .style("stroke", "black")
        .attr("d", arcOut)
        .attr("class", "inQueue")
        .attr("id", encodeURIComponent(d.id + "-" + "inQueue"));
    return g.node();

}
export function updateNode(nodes:Array<QueueElement>, isInput:boolean) {
    if(isInput){
        d3.selectAll(".inQueue")
            .data(nodes)
            .datum(function (d) {
                return d
            })
            .style("fill", function (d) {
                return d.color;
            })
            .transition()
            .duration(inOutPoolResolution * 1000)
            .attrTween("d", arcInTween);
    }
    else {
        d3.selectAll(".outQueue")
            .data(nodes)
            .datum(function (d) {
                return d
            })
            .style("fill", function (d) {
                return d.color;
            })
            .transition()
            .duration(inOutPoolResolution * 1000)
            .attrTween("d", arcOutTween);
    }

}
function arcInTween(d) {
    let interp = d3.interpolate(this._current, d);
    this._current = d;
    return function (t) {
        let tmp = interp(t);
        return arcIn(tmp);
    }
}
function arcOutTween(d) {
    let interp = d3.interpolate(this._current, d);
    this._current = d;
    return function (t) {
        let tmp = interp(t);
        return arcOut(tmp);
    }
}
