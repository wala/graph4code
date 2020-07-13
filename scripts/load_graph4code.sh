#!/bin/bash

#echo "-------Cloning Graph4Code Github Repo-----"
#git clone https://github.com/wala/graph4code.git
#cd graph4code/scripts
#chmod +x *.sh

echo "-------Downloading Graph4Code Files-----"
SOURCE_DIR=$PWD
QUADS_LOC=$SOURCE_DIR/graph4code_quads/
JENA_DB_LOC=$SOURCE_DIR/graph4code_db/

#-------Setup----------#
echo "Main Directory: $source_dir"
git clone https://github.com/wala/graph4code.git
mkdir $QUADS_LOC
mkdir $JENA_DB_LOC
wget http://mirror.metrocast.net/apache/jena/binaries/apache-jena-fuseki-3.16.0.tar.gz
tar -xzf apache-jena-fuseki-3.16.0.tar.gz

wget http://mirror.metrocast.net/apache/jena/binaries/apache-jena-3.16.0.tar.gz
tar -xzf apache-jena-3.16.0.tar.gz
JENA_LOC=$SOURCE_DIR/apache-jena-fuseki-3.16.0
FUSEKI_LOC=$SOURCE_DIR/apache-jena-fuseki-3.16.0
#-------END OF Setup----------#

cd $SOURCE_DIR/graph4code/scripts
./download_graph.sh $QUADS_LOC


echo "-------Build Graph4Code JENA DB-----"
./build_graph.sh $JENA_LOC $QUADS_LOC $JENA_DB_LOC

echo "-------Launch FUSEKI over Graph4Code JENA DB-----"
./serve_graph.sh $FUSEKI_LOC $JENA_DB_LOC

