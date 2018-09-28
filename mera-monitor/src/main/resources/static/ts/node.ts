import d3 = require("d3");
import {getTaskByName, QueueElement, Task} from "./datastructure";
import {arcRadius, inOutPoolResolution} from "./constants";
import {xScale, yScalePerMaschine} from "./longGraph";

let arcOut = d3.arc()
    .innerRadius(arcRadius.inner)
    .outerRadius(arcRadius.outer)
    .startAngle(1 * Math.PI);
let arcIn = d3.arc()
    .innerRadius(arcRadius.inner)
    .outerRadius(arcRadius.outer)
    .startAngle(1 * Math.PI);

export function drawNode(point, d:Task) {
    let g = point.append("g")
                 .attr("transform", "translate(" + xScale(d.cx) + "," + yScalePerMaschine.get(getTaskByName(d.id).address)(getTaskByName(d.id).cy) + ")");
    g.append("path")
        .datum({endAngle: 0 * Math.PI})
        .style("stroke", "black")
        .style("fill", "white")
        .attr("d", arcOut)
        .attr("class", "outQueueOutline");
    g.append("path")
        .datum({endAngle: 0 * Math.PI})
        .style("fill", "white")
        .style("stroke", "black")
        .attr("d", arcIn)
        .attr("class", "outQueue")
        .attr("id", encodeURIComponent(d.id + "-" + "outQueue"));
    g.append("path")
        .datum({endAngle: 2 * Math.PI})
        .style("stroke", "black")
        .style("fill", "white")
        .attr("d", arcOut)
        .attr("class", "inQueueOutline");
    g.append("path")
        .datum({endAngle: 2 * Math.PI})
        .style("fill", "gray")
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
            .each(function (d) {
                if (d.endAngle == Math.PI){
                }
                else{
                    d3.select(this)
                        .style("fill", function (d) {
                            return d.color;
                        })
                        .transition()
                        .duration(inOutPoolResolution * 1000)
                        .styleTween("fill", arcTweenColor)
                        .attrTween("d", arcInTween);
                }
            })
    }
    else {
        d3.selectAll(".outQueue")
            .data(nodes)
            .style("fill", function (d) {
                return d.color;
            })
            .transition()
            .duration(inOutPoolResolution * 1000)
            .styleTween("fill", arcTweenColor)
            .attrTween("d", arcOutTween);
    }

}
function arcTweenColor(d) {
    let t = this._current;
    if(t){
        let interp = d3.interpolateRgb(t.color, d.color);
        return interp
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
