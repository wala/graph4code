#!/bin/bash
QUADS_LOC=$1
wget https://archive.org/download/graph4codev1/ai_stackexchange_triples.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/github_1_fixed.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/github_2_fixed.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/github_3_fixed.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/github_4_fixed.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/datascience_stackexchange_triples.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/docstrings_triples.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/math_stackexchange_triples.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stackoverflow_triples_1.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stackoverflow_triples_2.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stackoverflow_triples_3.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stackoverflow_triples_4.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stackoverflow_triples_5.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stackoverflow_triples_6.nq.bz2 --directory-prefix=$QUADS_LOC
wget https://archive.org/download/graph4codev1/stats_stackexchange_triples.nq.bz2 --directory-prefix=$QUADS_LOC