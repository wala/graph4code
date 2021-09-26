#!/bin/bash
# This code is best run on multiple machines, splitting
# $1 so each machine collects the requisite libs
# Ensure to have elasticsearch started on the machine with:
# $ELASTIC_SEARCH_HOME/bin/elasticsearch
#
export TOP_MODULES_TO_INSPECT=$1
export OUTPUT_PATH=$2
export ANACONDA_HOME=$3


for f in `cat $TOP_MODULES_TO_INSPECT`; do bash `pwd`/ulimitit.sh 300 bash ./inspect_module.sh $f $OUTPUT_PATH $ANACONDA_HOME < /dev/null; done
