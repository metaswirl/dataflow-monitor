export const enum sides{
    left = "inQueue",
    right = "outQueue",
}
export const inOutPoolResolution = 2;
export const nodeRadius = 5;
export const arcRadius = {
    inner: nodeRadius + 1,
    outer: this.inner * 2
};