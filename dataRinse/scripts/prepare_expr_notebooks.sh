DIR=`dirname "$0"`
DIR=`realpath "$DIR"`

dataset=$1
shift

scripts=
for s in "$@"; do
    py_tmp=/tmp/`basename "$s"`.json
    python $DIR/encode_for_wrapper.py "$s" $py_tmp
    curl -X POST -d @$py_tmp localhost:4567/analyze_code > /tmp/`basename $s .py`.json
    bzip2 /tmp/`basename $s .py`.json

    scripts="$scripts /tmp/"`basename $s .py`".json.bz2 file://$s"
done

echo $scripts

ROOT=$DIR/..
ROOT=`realpath $ROOT`
java -cp $ROOT/code_breaker_py3/target/CodeBreaker_py3-0.0.1-SNAPSHOT.jar util.ExpressionGenerator $dataset $scripts

