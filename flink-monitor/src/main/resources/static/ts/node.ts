import d3 = require("d3");
import $ =require("jquery");
import {Task} from "./datastructure";


export function drawNode(point, posx:number , posy:number, d:Task) {
    let arc = d3.arc()
        .innerRadius(6)
        .outerRadius(12)
        .startAngle(0 * Math.PI);
    let arc2 = d3.arc()
        .innerRadius(6)
        .outerRadius(12)
        .startAngle(1 * Math.PI);


    let svg = point,
        g = svg.append("g")
            .attr("transform", "translate(" + posx + "," + posy + ")");

    let outQueue = g.append("path")
        .datum({endAngle: 1 * Math.PI})
        .style("fill", "white")
        .style("stroke", "black")
        .attr("d", arc)
        .attr("class", d.name + "_" + "outQueue");

    let inQueue = g.append("path")
        .datum({endAngle: 2 * Math.PI})
        .style("fill", "white")
        .style("stroke", "black")
        .attr("d", arc2)
        .attr("class", d.name + "_" + "inQueue");

    //let selectivity = g.append("text")
    //    .attr("dy", "-0.8em")
    //    .style("text-anchor", "middle")
    //    .attr("class", "selectivity")
    //   .text("5");

    //let taskId = g.append("text")
    //    .attr("dy", "1.6em")
    //    .style("text-anchor", "middle")
    //    .attr("class", "taskId")
    //    .text(".1");
    return g.node();

}

