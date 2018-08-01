"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.inOutPoolResolution = 2;
exports.nodeRadius = 7.5;
exports.sendRecieveIndicator = 60;
var nodeBorder = 1;
exports.arcRadius = {
    inner: exports.nodeRadius + nodeBorder,
    outer: (exports.nodeRadius + nodeBorder) * 2
};
