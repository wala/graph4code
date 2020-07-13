#!/bin/bash

echo "-------Cloning Graph4Code Github Repo-----"
git clone https://github.com/wala/graph4code.git
cd graph4code/scripts
chmod +x *.sh

echo "-------Downloading Graph4Code Files-----"
QUADS_LOC=~/graph4code_quads/
JENA_DB_LOC=~/graph4code_db/

mkdir $QUADS_LOC
./download_graph.sh $QUADS_LOC


echo "-------Build Graph4Code JENA DB-----"
mkdir $JENA_DB_LOC
./build_graph.sh $QUADS_LOC $JENA_DB_LOC

echo "-------Launch FUSEKI over Graph4Code JENA DB-----"
./serve_graph.sh $JENA_DB_LOC

