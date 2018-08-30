import {CardinalityByString, getTaskByName, Value} from "./datastructure";
import {arcRadius, sendRecieveIndicator} from "./constants";
import {xScale, yScalePerMaschine} from "./longGraph";
import d3 = require("d3");


export function drawNodeLink(obj, link:CardinalityByString, level?:number) {
    let svg = obj;
    let g = svg.append("g");
    let percentToLength = d3.scaleLinear()
        .range([arcRadius.outer, sendRecieveIndicator])
        .domain([0,100]);

    let outputStream = svg.append("g");
        outputStream.append("path")
            .attr("class", "inStreamFull")
            .datum(link)
            .attr("id", function (d: CardinalityByString) {
                return d.target + "inputStreamFull" + d.source
            })
            .attr("d", function (d:CardinalityByString) {
                let mT = calcFilling(d, false);
                return "M" + xScale(getTaskByName(d.source).cx) + ","
                    + yScalePerMaschine.get(getTaskByName(d.source).address)(getTaskByName(d.source).cy)
                    + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y;
            });
        outputStream.append("path")
            .attr("class", "inStreamLink")
            .datum(link)
            .attr("id", function (d: CardinalityByString) {
              return d.target + "inputSteamLink" + d.source
            })
            .attr("d", function (d:CardinalityByString) {
                let mT = calcFilling(d, false, percentToLength(60));
                return "M" + xScale(getTaskByName(d.source).cx) + ","
                    + yScalePerMaschine.get(getTaskByName(d.source).address)(getTaskByName(d.source).cy)
                    + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y;
            });

    let inputStreamMax = svg.append("g");
        inputStreamMax.append("path")
            .attr("class", "outStreamFull")
            .datum(link.reverse())
            .attr("id", function (d: CardinalityByString) {
                return d.source + "outputStreamFull" + d.target
            })
            .attr("d", function (d: CardinalityByString) {
                let mT = calcFilling(d, true);
                return "M" + xScale(getTaskByName(d.source).cx) + ","
                    + yScalePerMaschine.get(getTaskByName(d.source).address)(getTaskByName(d.source).cy)
                    + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y
            });
        inputStreamMax.append("path")
            .attr("class", "outStreamLink")
            .datum(link)
            .attr("id", function (d: CardinalityByString) {
                return d.source + "outputStreamLink" + d.target
            })
            .attr("d", function (d: CardinalityByString) {
                let mT = calcFilling(d, true, percentToLength(5));
                return "M" + xScale(getTaskByName(d.source).cx) + ","
                    + yScalePerMaschine.get(getTaskByName(d.source).address)(getTaskByName(d.source).cy)
                    + "A" + 0 + "," + 0 + " 0 0,1 "
                    + mT.x + ","
                    + mT.y
            });

    return g.node();

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