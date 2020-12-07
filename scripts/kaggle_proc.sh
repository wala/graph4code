#!/bin/bash

DIR=`dirname $0`
DIR=`realpath $DIR`

datadir=$1
outdir=$2

if test $# = 4; then
    part=$3
    parts=$4
else
    part=0
    parts=1
fi

rm -f $outdir/kaggle.$part.nq

n=0
for ds in $datadir; do
    for repo in `ls $ds`; do
	pushd $ds/$repo
	for f in `find * -name '*.py'`; do
	    if test `expr $n % $parts` = $part; then
		$DIR/script_proc.sh $ds/$repo/$f $repo $f "$outdir/kaggle.$part.nq"
	    fi;
	    n=`expr $n + 1`
	done
	popd
    done
done
