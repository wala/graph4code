#!/bin/bash
JENA_LOC=$1
QUADS_LOC=$2
JENA_DB_LOC=$2

cd $JENA_LOC
#sudo sysctl -w vm.max_map_count=500000
#mkdir logs
cd bin
chmod +x ./tdb2.tdbloader
#./tdb2.tdbloader --loader=parallel --loc=$JENA_DB_LOC $QUADS_LOC/*.nq.bz2 > ../logs/load_graph_v1.log 2>&1 
./tdb2.tdbloader --loader=parallel --loc=$JENA_DB_LOC $QUADS_LOC/*.nq.bz2 

