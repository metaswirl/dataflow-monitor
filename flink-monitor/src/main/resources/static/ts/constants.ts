export const enum sides{
    left = "inQueue",
    right = "outQueue",
}
export const inOutPoolResolution = 2;
export const nodeRadius = 7.5;
export const sendRecieveIndicator = 50;

const nodeBorder = 1;
export const arcRadius = {
    inner: nodeRadius + nodeBorder,
    outer: (nodeRadius + nodeBorder) * 2
};
