## Machine Requirements

 We run graph4code on an Intel VM with 64 cores, 512GB of RAM and 2TB of disk.
 
## Loading Graph4Code<a name="loading"></a>

This script downloads and loads Graph4Code quad files in Apache Jena. The following scripts are tested on Linux Ubuntu with Java (openjdk version "1.8.0_252") and Ruby "ruby 2.3.1p112" installed.

The main script (shown below) perform the following steps:
- Clone this repository
- Download Graph4Code ([download_graph.sh](https://github.com/wala/graph4code/blob/master/scripts/download_graph.sh))
- Build Jena DB using the downloaded files ([build_graph.sh](https://github.com/wala/graph4code/blob/master/scripts/build_graph.sh))
- Start Jena Fuseki for querying the loaded graph ([serve_graph.sh](https://github.com/wala/graph4code/blob/master/scripts/serve_graph.sh)). 


```
#!/bin/bash

#-------Setup----------#
export SOURCE_DIR=$PWD
export QUADS_LOC=$SOURCE_DIR/graph4code_quads/
export JENA_DB_LOC=$SOURCE_DIR/graph4code_db/
export JENA_LOC=$SOURCE_DIR/apache-jena-3.16.0
export FUSEKI_LOC=$SOURCE_DIR/apache-jena-fuseki-3.16.0
echo "SOURCE_DIR: $SOURCE_DIR"
echo "JENA_LOC: $JENA_LOC"
echo "QUADS_LOC: $QUADS_LOC"
echo "JENA_DB_LOC: $JENA_DB_LOC"
echo "Main Directory: $source_dir"
git clone https://github.com/wala/graph4code.git
mkdir $QUADS_LOC
mkdir $JENA_DB_LOC
wget http://mirror.metrocast.net/apache/jena/binaries/apache-jena-fuseki-3.16.0.tar.gz
tar -xzf apache-jena-fuseki-3.16.0.tar.gz

wget http://mirror.metrocast.net/apache/jena/binaries/apache-jena-3.16.0.tar.gz
tar -xzf apache-jena-3.16.0.tar.gz
#-------END OF Setup----------#

echo "-------Downloading Graph4Code Files-----"
cd $SOURCE_DIR/graph4code/scripts
chmod +x *.sh
./download_graph.sh $QUADS_LOC


echo "-------Build Graph4Code JENA DB-----"
cd $SOURCE_DIR/graph4code/scripts
./build_graph.sh $JENA_LOC $QUADS_LOC $JENA_DB_LOC

echo "-------Launch FUSEKI over Graph4Code JENA DB: log available at $FUSEKI_LOC/log-----"
cd $SOURCE_DIR/graph4code/scripts
./serve_graph.sh $FUSEKI_LOC $JENA_DB_LOC


echo "Testing sample query"
cd $FUSEKI_LOC/bin/
./s-query --service http://localhost:3030/graph_v1_0/query 'SELECT * { graph ?g {?s ?p ?o . }}  limit 10'
```

## Docker
Alternatively, we also provide a docker file for creating a docker image with the graph database ready to use. The docker file is available here https://github.com/wala/graph4code/blob/master/scripts/dockerfile.
