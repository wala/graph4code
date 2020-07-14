#!/bin/bash
QUADS_LOC=$1
wget https://archive.org/download/graph4codev1/ai_stackexchange_triples.nq.bz2 --directory-prefix=$QUADS_LOC
