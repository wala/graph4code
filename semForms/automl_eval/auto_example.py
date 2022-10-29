from pandas import DataFrame
from pandas import to_datetime
from pandas import read_csv
from pandas import unique
from pandas.api.types import infer_dtype
from sys import argv
from sys import stdout
from sklearn.metrics import f1_score
from sklearn.metrics import mean_squared_error
from sklearn.metrics import r2_score
from sklearn.metrics import roc_auc_score
from sklearn.model_selection import train_test_split, TimeSeriesSplit, GridSearchCV
from sklearn.preprocessing import LabelEncoder
from sklearn.preprocessing import OrdinalEncoder
from numpy import nan, inf
import numpy
from traceback import format_exc
from importlib import import_module
import json
import pandas
import traceback, sys

import math

from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer
from sklearn.linear_model import LogisticRegression, LinearRegression
from sklearn import metrics
from scipy.stats import pearsonr

SEED=33


# Import for an AutoML solution (if desired)
from automl import automl_estimator
# -------------------------------------------------------------------


def wrapper_func(expname, code):
    f = eval(code)

    def df_func(df):
        res = f(df)
        if isinstance(res, pandas.Series):
            df[expname] = res
        return df

    return df_func

expressions_with_good_corr = []

def prune_correlated(df):
    cor_matrix = df.corr().abs()
    print('correlation matrix:')
    print(cor_matrix)
    upper_tri = cor_matrix.where(numpy.triu(numpy.ones(cor_matrix.shape), k=1).astype(bool))
    to_drop = [c for c in upper_tri.columns if any(upper_tri[c] > 0.9) and c.startswith('expr_')]
    print("dropping correlated " + str(to_drop))
    return df.drop(to_drop, axis=1)

def handle_transforms(how, noncorr_expr_columns, Y, X, name):
    transforms = []
    l = noncorr_expr_columns
    assert len(l) > 0
    correlation_with_target = {}
    target = "'" + Y.columns.tolist()[0] + "'"

    for exp in l:
        try:
            print('Dataset columns (X):',  X.columns.tolist())
            print('Dataset columns (Y):',  Y.columns.tolist())
            expr_code = exp['code']
            if target in expr_code:
                print('expression code is about target')
                continue

            new_exp_val = FunctionTransformer(func=wrapper_func(exp['expr'], expr_code)).fit(X).transform(X)
            ret_df = prune_correlated(new_exp_val)
            if exp['expr'] not in ret_df.columns:
                continue

            corrtest = pearsonr(new_exp_val[exp['expr']], Y[Y.columns.values.tolist()[0]])
            print('correlation_with_target: ', corrtest)
            print(f'Found corr = {corrtest[0]} with significance {corrtest[1]} ')
            if 'nan' in str(corrtest).lower():
                print('Bad correlation for ', expr_code,', Skip!!')
                continue

            correlation_with_target[exp['expr']] = corrtest

            # capture if correlation is less than some significance level
            if corrtest[1] < .05:
                expressions_with_good_corr.append(
                    {
                        'dataset_name': name,
                        'expression_code': str(expr_code),
                        'dataset_cols': X.columns.tolist(),
                        'expr_corr_w_target': corrtest[0],
                        'significance': corrtest[1]
                    }
                )
            transforms.append((exp['expr'], FunctionTransformer(func=wrapper_func(exp['expr'], expr_code))))
            # TODO: what about expr codes that don't work
            # TODO: filter by the ones with good correlation?
        except:
            traceback.print_exc(file=sys.stdout)
            print('Could not add transform: ', exp['code'])
    if how == "expr":
        def dropf(df):
            to_drop = [c for c in df.columns if not c.startswith("expr_")]
            newdf = df.drop(to_drop, axis=1)
            if len(newdf.columns) == 1:
                print('reshaping')
                newdf = newdf[[newdf.columns[0]]]
            return newdf

        
        drop_plain = FunctionTransformer(func=dropf)
        transforms.append(('drop plain', drop_plain))

    return transforms, correlation_with_target

# Use any AutoML approach - ideally one that can deal with issues in the input data through preprocessing
def make_automl(model_type, seed):
     # Defaul Metric
     metric_name = 'roc_auc'
     # Just select one ML model 
     estimator   = 'LGBMClassifierEstimator' 
     # If it is a regression problem, set metric accordingly
     if model_type == 'regression':
        metric_name     = 'neg_mean_squared_error'
        estimator       = 'LGBMRegressorEstimator' 
     # Fix model type to only type classifcation (regardless of binary/multi-class)
     if model_type == 'binary_classification':
          model_type = 'classification'
     # Fix model type to only type classifcation (regardless of binary/multi-class)
     # and set appropriate metric
     if model_type == 'multiclass_classification':
          metric_name = 'f1_weighted'
          model_type = 'classification'
     print("Calling AutoML with model type: " + str(model_type))  
     print("Random seed: " + str(seed))

     # Initialize AutoML 
     # Setting some basic parameters specific to automl method (parameter names might be different)
     automl = automl_estimator(learning_type=model_type, scorer_for_ranking=metric_name, random_state=seed)
     
     return ('est', automl)

def make_manual(model_type, seed):
    if model_type == 'regression':
       return ('est', LinearRegression())
    elif model_type == 'classification':
       return ('est', LogisticRegression())


def analyze(in_df, how, name, target, model_type, SEED, non_correlated_expr_columns=None, make_auto=True,
            compute_corr_only=False):
    
    if not compute_corr_only:
        print('compute_corr_only: False, skip automl')
        
    print("begun analyzing " + str(how) + " " + str(name) + " for " + str(target) + " as " + str(model_type) + " using random seed: " + str(SEED))
    ret = {"csv": name,
           "how": how,
           "target": target,
           "type": model_type,
           "seed": SEED}

    if len(in_df) > 100000:
        in_df = in_df.sample(n=100000, axis=0)

    mixed_dtypes = [x for x in in_df.columns if infer_dtype(in_df[x]).startswith("mixed")]
    in_df.drop(mixed_dtypes, axis=1)

    Y = in_df[target].to_frame()
    X = in_df.drop([target], axis=1)
    correlation_with_target = {}
    try:
        if model_type == "classification" or model_type == "binary_classification" or model_type == "multiclass_classification":
            X_train, X_test, Y_train, Y_test = train_test_split(X, Y, stratify=Y, random_state=SEED)
        elif model_type == 'regression':
            try:
                bin_labels_5 = ['0', '1', '2', '3', '4']
                y_labels = pandas.qcut(Y, q=[0, .2, .4, .6, .8, 1], labels=bin_labels_5, duplicates='drop')
                X_train, X_test, Y_train, Y_test = train_test_split(X, Y, stratify=y_labels, random_state=SEED)
            except:
                X_train, X_test, Y_train, Y_test = train_test_split(X, Y, random_state=SEED)
        if make_auto:
            ma = make_automl(model_type, SEED)
        else:
            ma = make_manual(model_type, SEED)

        if how != "plain":
            print("transforming")
            transforms, correlation_with_target = handle_transforms(how, non_correlated_expr_columns, Y, X, name)
            transforms.append(ma)
            print(transforms)
            pipeline = Pipeline(transforms)
        else:
            pipeline = Pipeline([ma])

        ret['correlation_with_target'] = correlation_with_target
        if not compute_corr_only:
            pipeline.fit(X_train, Y_train)
            print('columns in X_train: ', X_train.columns.values.tolist())
            print('columns in Y_train: ', Y_train.columns.values.tolist())

            if make_auto:
                Y_predicted = pipeline.predict(X_test)
                if model_type == 'regression':
                    ret['r^2'] = metrics.r2_score(Y_test, Y_predicted)
                    ret['mse'] = metrics.mean_squared_error(Y_test, Y_predicted)
                    print("r2 xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                    print(ret['r^2'])
                else:
                    try:
                        Y_predict_proba = pipeline.predict_proba(X)[:, 1]
                        ret['roc_auc_score'] = metrics.roc_auc_score(Y_test, Y_predict_proba)
                        print("auc xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                        print(ret['roc_auc_score'])
                    except:
                        pass
                    ret['balanced_accuracy']  = metrics.balanced_accuracy_score(Y_test, Y_predicted)
                    ret['precision_recall_F'] = metrics.precision_recall_fscore_support(Y_test, Y_predicted)

            print("succeeded " + name)
        else:
            print('Return: compute corr only is enabled!')

    except:
        msg = str(format_exc())
        ret['correlation_with_target'] = correlation_with_target
        print("failed " + name + ": " + msg)
        ret['error'] = msg
    return ret

def write_expressions_file():
    with open('expressions_with_good_corr.json', 'w') as f:
        json.dump(expressions_with_good_corr, f, indent=4)


if __name__ == "__main__":
    csv = argv[1]
    in_df = read_csv(csv)
    target = argv[2]
    type = argv[3]
    ret = analyze(in_df, csv, target, type)
    print(DataFrame([ret]))
