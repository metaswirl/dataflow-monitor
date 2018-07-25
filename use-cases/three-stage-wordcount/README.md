# Three Stage WordCount 

This jobs shows the power of load-shedding on a three stage wordcount.

You can set the bottleneck by sending a number to the port 22333 of the bottleneck operator. E.g. by sending:

    echo "100" | nc localhost 22333
