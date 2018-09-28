import {CardinalityByString, getTaskByName, Value} from "./datastructure";
import {arcRadius, sendRecieveIndicator} from "./constants";
import {xScale, yScalePerMaschine} from "./longGraph";
import d3 = require("d3");

let percentToLength = d3.scaleLinear()
    .range([arcRadius.outer, sendRecieveIndicator])
    .domain([0,100]);
export function drawNodeLink(obj, link:CardinalityByString, level?:number) {
    let svg = obj;
    let g = svg.append("g");
    let outputStream = svg.append("g");
        outputStream.append("path")
            .attr("class", "inFractionFull")
            .datum(link)
            .attr("id", function (d: CardinalityByString) {
                return normalizeString(d.target + "inputFractionFull" + d.source)
            })
            .attr("d", function (d:CardinalityByString) {
                return line(d, false, percentToLength(100))
            });
        outputStream.append("path")
            .attr("class", "inFractionLink")
            .datum(link)
            .attr("id", function (d: CardinalityByString) {
              return normalizeString(d.target + "inputFractionLink" + d.source)
            })
            .attr("d", function (d:CardinalityByString) {
                return line(d, false, percentToLength(getRandomInt(100)))
            });

    let inputStreamMax = svg.append("g");
        inputStreamMax.append("path")
            .attr("class", "outFractionFull")
            .datum(link.reverse())
            .attr("id", function (d: CardinalityByString) {
                return normalizeString(d.source + "outputFractionFull" + d.target)
            })
            .attr("d", function (d: CardinalityByString) {
               return line(d, true, percentToLength(100))
            });
        inputStreamMax.append("path")
            .attr("class", "outFractionLink")
            .datum(link)
            .attr("id", function (d: CardinalityByString) {
                return normalizeString(d.source + "outputFractionLink" + d.target)
            })
            .attr("d", (d) => {
                return line(d, true, percentToLength(getRandomInt(100)))
            });

    return g.node();

}
export function updateNodeLink(updateNodeList:Array<CardinalityByString>) {
    updateNodeList.forEach(function (item) {
        let inLink = d3.select("#" + normalizeString(item.target + "inputFractionLink" + item.source));
            inLink
                .datum(item)
                .transition()
                .duration(1000)
                .attrTween("d", pathTween(line(item, false, percentToLength(getRandomInt(100))) ,4));

        let outLink = d3.select("#" + normalizeString(item.target + "outputFractionLink" + item.source));
            outLink
                .datum(item)
                .transition()
                .duration(1000)
                .attrTween("d", pathTween(line(item.reverse(), true, percentToLength(getRandomInt(100))) ,4));
        })
}

//Helper Functions
function calcFilling(link:CardinalityByString, reverse:Boolean, level?:number):Value {
    let alpha = Math.atan((yScalePerMaschine.get(getTaskByName(link.target).address)(getTaskByName(link.target).cy) - yScalePerMaschine.get(getTaskByName(link.source).address)(getTaskByName(link.source).cy)) / (xScale(getTaskByName(link.target).cx) - xScale(getTaskByName(link.source).cx)));
    let mX = xScale(getTaskByName(link.source).cx);
    let mY = yScalePerMaschine.get(getTaskByName(link.source).address)(getTaskByName(link.source).cy);
    if (reverse){
        if(level != null){
            mX -= (level) * Math.cos(alpha);
            mY -= (level) * Math.sin(alpha);
        }
        else{
            mX -= (sendRecieveIndicator) * Math.cos(alpha);
            mY -= (sendRecieveIndicator) * Math.sin(alpha);
        }
    }
    else{
        if(level != null){
            mX += (level) * Math.cos(alpha);
            mY += (level) * Math.sin(alpha);
        }
        else{
            mX += (sendRecieveIndicator) * Math.cos(alpha);
            mY += (sendRecieveIndicator) * Math.sin(alpha);
        }
    }
    let value = new Value(mX, mY);
    return value
}
function getRandomInt(max) {
    return Math.floor(Math.random() * Math.floor(max));
}

function line(d, output:Boolean, level:number) {
    let mT = calcFilling(d, output , level);
    return "M" + xScale(getTaskByName(d.source).cx) + ","
        + yScalePerMaschine.get(getTaskByName(d.source).address)(getTaskByName(d.source).cy)
        + "A" + 0 + "," + 0 + " 0 0,1 "
        + mT.x + ","
        + mT.y
}
function pathTween(d1, precision) {
    return function() {
        let path0 = this,
            path1 = path0.cloneNode(),
            n0 = path0.getTotalLength(),
            n1 = (path1.setAttribute("d", d1), path1).getTotalLength();

        // Uniform sampling of distance based on specified precision.
        let distances = [0], i = 0, dt = precision / Math.max(n0, n1);
        while ((i += dt) < 1) distances.push(i);
        distances.push(1);

        // Compute point-interpolators at each distance.
        let points = distances.map(function(t) {
            let p0 = path0.getPointAtLength(t * n0),
                p1 = path1.getPointAtLength(t * n1);
            return d3.interpolate([p0.x, p0.y], [p1.x, p1.y]);
        });

        return function(t) {
            return t < 1 ? "M" + points.map(function(p) { return p(t); }).join("L") : d1;
        };
    };
}
function normalizeString(input:string):string {
    let output = input.replace(/[^a-zA-Z0-9]/gm, "");
    return output;
}