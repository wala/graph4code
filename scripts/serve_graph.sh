#!/bin/bash

JENA_DB_LOC=$1
mkdir logs/
wget http://mirror.metrocast.net/apache/jena/binaries/apache-jena-fuseki-3.16.0.tar.gz
tar -xzf apache-jena-fuseki-3.16.0.tar.gz

java -Xmx100g -jar fuseki-server.jar --port 3030 --tdb2 --loc $JENA_DB_LOC /graph_v1_0 >> logs/serve_graph_g1_v1_0.log 2>&1 