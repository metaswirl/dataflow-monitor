package berlin.bbdc.inet.mera.server.webservice

import berlin.bbdc.inet.mera.server.model.Model

class WebService(model : Model) {
  // This should implement a web server to access metrics and topology
  // TODO: Kajetan: Integrate web server
  // TODO: Kajetan: Add JSON support

  def entryPoint(url : String) : String = {
    if (url.contains("/operators")) return "fubar"
    ""
  }

  /* Calls to webserver defined in the following

     URL
      GET /data/operators
     Returns
      ["Source", "Map", ..., "Sink"]
     Description
      Operators should be sorted.

     URL
      GET /data/tasksOfOperator/{OperatorID}
     Returns
      [ {"id":"Map.0", "input":["Source.0", "Source.1"], "output":["FlatMap.0"]},
        {"id":"Map.1" ...},
        ...
      ]

     URL
      GET /data/metrics
     Returns
      [ "metric1", "metric2", ... ]


     URL
      GET /data/initMetric/{MetricID}&resolution=[in seconds]
     Description
      - Create buffer to store values of metric in the given resolution. Average to reach resolution.
      - No resolution below a single second

     URL
      GET /data/metric/{MetricID}
     Returns
      { "Map.0": "values":[ (time1, value1), (time2, value2), ... ], "late":[ (timeX, valueX), ... ],
        "Map.1": ...,
         ...
      }
     Description
      - If not initialized (above), return error.
      - Time is formatted as seconds since epoch

     URL
      GET /static/index.html
      GET /static/stuff.js
      GET /static/etc
     Description
      return static content
   */

}
