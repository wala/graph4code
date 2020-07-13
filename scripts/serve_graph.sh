#!/bin/bash
FUSEKI_LOC=$1
JENA_DB_LOC=$2
cd $FUSEKI_LOC
mkdir logs/
java -Xmx100g -jar fuseki-server.jar --port 3030 --tdb2 --loc $JENA_DB_LOC /graph_v1_0 >> logs/serve_graph_g1_v1_0.log 2>&1 & 