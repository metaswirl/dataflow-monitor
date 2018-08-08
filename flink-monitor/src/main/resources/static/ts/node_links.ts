import {Cardinality, Value} from "./datastructure";
import {arcRadius, sendRecieveIndicator} from "./constants";
import {xScale, yScales} from "./longGraph";
import d3 = require("d3");


export function drawNodeLink(obj, link:Cardinality, level?:number) {
    let svg = obj;
    let g = svg.append("g");
    let percentToLength = d3.scaleLinear()
        .range([arcRadius.outer, sendRecieveIndicator])
        .domain([0,100]);

    let outputStream = svg.append("g");
        outputStream.append("path")
            .attr("class", "outStreamFull")
            .datum(link)
            .attr("id", function (d: Cardinality) {
                return d.source.id + "outputStreamFull" + d.target.id
            })
            .attr("d", function (d:Cardinality) {
                let mT = calcFilling(d, false);
                return "M" + xScale(d.source.cx) + ","
                    + yScales[d.source.cx](d.source.cy)
                    + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y;
            });
        outputStream.append("path")
            .attr("class", "outStreamLink")
            .datum(link)
            .attr("id", function (d: Cardinality) {
              return d.source.id + "outputSteamLink" + d.target.id
            })
            .attr("d", function (d:Cardinality) {
                let mT = calcFilling(d, false, percentToLength(60));
                return "M" + xScale(d.source.cx) + ","
                    + yScales[d.source.cx](d.source.cy)
                    + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y;
            });

    let inputStreamMax = svg.append("g");
        inputStreamMax.append("path")
            .attr("class", "inStreamFull")
            .datum(link.reverse())
            .attr("id", function (d: Cardinality) {
                return d.source.id + "inputStreamFull" + d.target.id
            })
            .attr("d", function (d: Cardinality) {
                let mT = calcFilling(d, true);
                return "M" + xScale(d.source.cx) + ","
                    + yScales[d.source.cx](d.source.cy) + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y
            });
        inputStreamMax.append("path")
            .attr("class", "inStreamLink")
            .datum(link)
            .attr("id", function (d: Cardinality) {
                return d.source.id + "inputStreamLink" + d.target.id
            })
            .attr("d", function (d: Cardinality) {
                let mT = calcFilling(d, true, percentToLength(5));
                return "M" + xScale(d.source.cx) + ","
                    + yScales[d.source.cx](d.source.cy) + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y
            });

    return g.node();

}

export function updateNodeLink(link:Cardinality, ) {

}

//Helper Functions
function calcFilling(link:Cardinality, reverse:Boolean, level?:number):Value {
    let alpha = Math.atan((yScales[link.target.cx](link.target.cy) - yScales[link.source.cx](link.source.cy)) / (xScale(link.target.cx) - xScale(link.source.cx)));
    let mX = xScale(link.source.cx);
    let mY = yScales[link.source.cx](link.source.cy);
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