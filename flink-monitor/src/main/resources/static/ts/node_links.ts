import {Cardinality, Value} from "./datastructure";
import {sendRecieveIndicator} from "./constants";
import {xScale, yScales} from "./longGraph";

export function drawNodeLink(obj, link:Cardinality, level?:number) {
    let alpha = Math.atan((link.target.cy - link.source.cy) / (link.target.cx - link.source.cx));
    let beta = Math.atan((link.target.cx - link.source.cx) / (link.target.cy - link.source.cy));
    let svg = obj;
    let g = svg.append("g");

    let outputStreamMax = g.append("path")
        .data(link)
        .attr("id", function (d: Cardinality) {
            return d.source.id + "inputStreamMax"
        })
        .attr("d", function (d) {
            let sx = new Value(xScale(d.source.cx),yScales[d.source.cx](d.target.cy));
            console.log(sx, d);
            return "M" + xScale(d.source.cx) + "," + yScales[d.source.cx](d.target.cy) + "A" + 0 + "," + 0 + " 0 0,1 " + calcFilling(alpha).x + "," + calcFilling(alpha).y;
        });

    let inputStreamMax = g.append("path")
        .data(link)
        .attr("id", function (d: Cardinality) {
            return d.target.id + "inputStreamMax"
        })
        .attr("d", function (d: Cardinality) {
            return "M" + xScale(d.source.cx) + "," + yScales[d.source.cx](d.target.cy) + "A" + 0 + "," + 0 + " 0 0,1 " + calcFilling(beta).x + "," + calcFilling(beta).y
        });

    return g.node();

}

//Helper Functions
function calcFilling(angle:number, level?:number):Value {
    let mX;
    let mY;
    if(level != null){
        mX = (sendRecieveIndicator * angle) / Math.sin(angle);
        mY = (sendRecieveIndicator * angle) / Math.cos(angle);
    }
    else{
        mX = (sendRecieveIndicator) / Math.sin(angle);
        mY = (sendRecieveIndicator) / Math.cos(angle);
    }
    let value = new Value(mX, mY);
    return value
}