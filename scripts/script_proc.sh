#!/bin/bash

DIR=`dirname $0`
FN=`basename "$1"`
OUT="/tmp/logs/$FN.log.bz2"

mkdir -p /tmp/logs

(echo bash $DIR/ulimitit.sh 60 java "-DquadFile=$4" -cp $DIR/../jars/codebreaker.jar util.RunTurtleSingleAnalysis "$1" "$2" "$3"; bash $DIR/ulimitit.sh 60 java "-DquadFile=$4" -cp $DIR/../jars/codebreaker.jar util.RunTurtleSingleAnalysis "$1" "$2" "$3") 2>&1 | bzip2 - > "$OUT"

