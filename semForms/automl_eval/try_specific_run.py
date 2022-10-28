import json
from os.path import exists
from pandas import read_csv
from sys import argv
import numpy
import random
from functools import singledispatch
from sklearn import datasets
import numpy as np
import pandas as pd

def get_field(target, df):
    if target in df.columns:
        return target
    for col in df.columns:
        if target == col.lower():
            return col
    return None

@singledispatch
def to_serializable(val):
    """Used by default."""
    return str(val)

@to_serializable.register(numpy.float32)
def ts_float32(val):
    """Used if *val* is an instance of numpy.float32."""
    return numpy.float64(val)


from auto_example import analyze

def try_dataset(argv, make_auto, compute_corr_only):
    mode = argv[4]
    problem_type = argv[5]
    SEED = int(argv[6])
    random.seed(SEED)
    numpy.random.seed(SEED)
    csv = argv[1]
    df = read_csv(csv, index_col=None)
    column = get_field(argv[2], df)

    columns = eval(argv[3]) #list of expression, the code snippets, lambda expression

    print('csv:' + csv)
    print('mode:' + mode)
    print('column:' + column) #target?
    print('problem_type:' + problem_type)
    print('seed:' + str(SEED))
    print('non corr expr columns' + str(columns))
    if len(argv) > 8:
        pipeline_file = argv[8]
        print('pipeline_file:' + pipeline_file)

    if mode != "plain":
        if len(argv) > 8:
            ret = analyze(df, mode, csv, column, problem_type, SEED,
                          non_correlated_expr_columns=columns, pipeline_file=pipeline_file, make_auto=make_auto,compute_corr_only=compute_corr_only)
        else:
            ret = analyze(df, mode, csv, column, problem_type, SEED,
                          non_correlated_expr_columns=columns, make_auto=make_auto, compute_corr_only=compute_corr_only)
    else:
        if len(argv) > 8:
            ret = analyze(df, mode, csv, column, problem_type, SEED, pipeline_file=pipeline_file
                          , make_auto=make_auto, compute_corr_only=compute_corr_only)
        else:
            ret = analyze(df, mode, csv, column, problem_type, SEED, make_auto=make_auto, compute_corr_only=compute_corr_only)

    with open(argv[7], "w") as f:
        json.dump(ret, f, indent=4, default=to_serializable)
