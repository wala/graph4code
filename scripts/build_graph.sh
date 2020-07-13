#!/bin/bash
QUADS_LOC=$1
JENA_DB_LOC=$2

wget http://mirror.metrocast.net/apache/jena/binaries/apache-jena-3.16.0.tar.gz
tar -xzf apache-jena-3.16.0.tar.gz
cd apache-jena-3.16.0
sudo sysctl -w vm.max_map_count=500000

cd apache-jena-3.16.0
mkdir logs
cd bin
chmod +x ./tdb2.tdbloader
./tdb2.tdbloader --loader=parallel --loc=$JENA_DB_LOC $QUADS_LOC/*.nq.bz2 > ../logs/load_graph_v1.log 2>&1 

