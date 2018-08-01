"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var datastructure_1 = require("./datastructure");
var constants_1 = require("./constants");
var longGraph_1 = require("./longGraph");
var d3 = require("d3");
function drawNodeLink(obj, link, level) {
    var svg = obj;
    var g = svg.append("g");
    var percentToLength = d3.scaleLinear()
        .range([constants_1.arcRadius.outer, constants_1.sendRecieveIndicator])
        .domain([0, 100]);
    var outputStream = svg.append("g");
    outputStream.append("path")
        .attr("class", "outStreamFull")
        .datum(link)
        .attr("id", function (d) {
        return d.source.id + "outputStreamFull" + d.target.id;
    })
        .attr("d", function (d) {
        var mT = calcFilling(d, false);
        return "M" + longGraph_1.xScale(d.source.cx) + ","
            + longGraph_1.yScales[d.source.cx](d.source.cy)
            + "A" + 0 + "," + 0 + " 0 0,1 "
            + mT.x + ","
            + mT.y;
    });
    outputStream.append("path")
        .attr("class", "outStreamLink")
        .datum(link)
        .attr("id", function (d) {
        return d.source.id + "outputSteamLink" + d.target.id;
    })
        .attr("d", function (d) {
        var mT = calcFilling(d, false, percentToLength(60));
        return "M" + longGraph_1.xScale(d.source.cx) + ","
            + longGraph_1.yScales[d.source.cx](d.source.cy)
            + "A" + 0 + "," + 0 + " 0 0,1 "
            + mT.x + ","
            + mT.y;
    });
    var inputStreamMax = svg.append("g");
    inputStreamMax.append("path")
        .attr("class", "inStreamFull")
        .datum(link.reverse())
        .attr("id", function (d) {
        return d.source.id + "inputStreamFull" + d.target.id;
    })
        .attr("d", function (d) {
        var mT = calcFilling(d, true);
        return "M" + longGraph_1.xScale(d.source.cx) + ","
            + longGraph_1.yScales[d.source.cx](d.source.cy) + "A" + 0 + "," + 0 + " 0 0,1 "
            + mT.x + ","
            + mT.y;
    });
    inputStreamMax.append("path")
        .attr("class", "inStreamLink")
        .datum(link)
        .attr("id", function (d) {
        return d.source.id + "inputStreamLink" + d.target.id;
    })
        .attr("d", function (d) {
        var mT = calcFilling(d, true, percentToLength(5));
        return "M" + longGraph_1.xScale(d.source.cx) + ","
            + longGraph_1.yScales[d.source.cx](d.source.cy) + "A" + 0 + "," + 0 + " 0 0,1 "
            + mT.x + ","
            + mT.y;
    });
    return g.node();
}
exports.drawNodeLink = drawNodeLink;
//Helper Functions
function calcFilling(link, reverse, level) {
    var alpha = Math.atan((longGraph_1.yScales[link.target.cx](link.target.cy) - longGraph_1.yScales[link.source.cx](link.source.cy)) / (longGraph_1.xScale(link.target.cx) - longGraph_1.xScale(link.source.cx)));
    var mX = longGraph_1.xScale(link.source.cx);
    var mY = longGraph_1.yScales[link.source.cx](link.source.cy);
    if (reverse) {
        if (level != null) {
            mX -= (level) * Math.cos(alpha);
            mY -= (level) * Math.sin(alpha);
        }
        else {
            mX -= (constants_1.sendRecieveIndicator) * Math.cos(alpha);
            mY -= (constants_1.sendRecieveIndicator) * Math.sin(alpha);
        }
    }
    else {
        if (level != null) {
            mX += (level) * Math.cos(alpha);
            mY += (level) * Math.sin(alpha);
        }
        else {
            mX += (constants_1.sendRecieveIndicator) * Math.cos(alpha);
            mY += (constants_1.sendRecieveIndicator) * Math.sin(alpha);
        }
    }
    var value = new datastructure_1.Value(mX, mY);
    return value;
}
