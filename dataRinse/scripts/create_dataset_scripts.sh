#!/bin/bash

SCRIPTS_DIR=$1
find $SCRIPTS_DIR -name *.py |xargs grep 'read_csv(' > /tmp/find_csv_uses.txt

# CSV datasets as a pattern to find zip files; e.g. ../data/*.zip
DATASET_DIR=$2

# output gets written out as CSV to a set of scripts in a file called csv2scripts.json
python python_process_csv.py /tmp/find_csv_uses.txt $DATASET_DIR



