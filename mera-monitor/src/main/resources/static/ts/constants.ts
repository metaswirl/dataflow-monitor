export const enum sides{
    left = "inQueue",
    right = "outQueue",
}
export const inOutPoolResolution = 20;
export const nodeRadius = 7.5;
export const sendRecieveIndicator = 60;

const nodeBorder = 1;
export const arcRadius = {
    inner: nodeRadius + nodeBorder,
    outer: (nodeRadius + nodeBorder) * 2
};
