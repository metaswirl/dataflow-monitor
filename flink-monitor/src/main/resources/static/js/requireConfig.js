require.config({
    basePath: "ts/js",
    paths: {
        require: "frameworks/require/require",
        jquery: "frameworks/jquery/jquery-3.3.1",
        highcharts: "frameworks/highcharts/highcharts",
        d3: "frameworks/d3/d3"
    },
    shim: {
        highcharts: {
            exports: "Highcharts",
            deps: ["jquery"]
        },
        d3: {
            exports: "d3",
            deps: ["jquery"]
        }
    } // end Shim Configuration
});
require(["LinePlot", "interfaceLoads", "longGraph"]);