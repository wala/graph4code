from sklearn.datasets import fetch_openml
from auto_example import handle_transforms
from sklearn.ensemble import RandomForestRegressor, RandomForestClassifier
from sklearn.model_selection import cross_val_score
from sklearn import metrics
import statistics
import numpy
import base64
import json
import requests
import os
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer
import urllib
import logging
import nbformat
from nbconvert.exporters import PythonExporter
import requests
import json
from tqdm import tqdm
from sklearn.preprocessing import OneHotEncoder, LabelEncoder

# Download Python Files from Github
def get_python(nbhandle):
    # read source notebook
    # print('reading:' + nbhandle)
    f = urllib.request.urlopen(nbhandle)
    try:
        nb = nbformat.read(f, as_version=4)
        python_exporter = PythonExporter()
        code = python_exporter.from_notebook_node(nb)
        return code[0]
    except:
        # logging.exception("message")
        return None

#Search Github for related python files and notebooks for the given dataset 
def search_github(dataset_name, dataset_cols):
    buf = [dataset_name]
    for i in dataset_cols:
        buf.append(i)
    query = ' '.join(buf)
    print('query:' + query)
    rest_url = 'http://localhost:8001/api/github_search'
    payload = {
        "query": query,
    }
    ret = requests.get(rest_url, params=payload)
    data = json.loads(ret.content)
    urls = [d['URL'] for d in data]
    urls = [d['URL'].replace('github.com', 'raw.githubusercontent.com').replace('/blob/', '/') for d in data]
    return urls

#submit files to a program analysis script to extract possible transforms
def mine_code_for_expressions(urls): 
    expressions = []
    code2count = {}
    for url in tqdm(urls):
        code = get_python(url)
        if code is None:
            continue
        req = {'repo': url, 'source': code, 'indexName': 'expressions'}
        response = requests.post('http://localhost:4567/index', json=req)
        try:
            res = response.json()
        except:
            continue
        for r in res:
            if r['code'] not in code2count:
                code2count[r['code']] = 0
            code2count[r['code']] += 1

    codes = {k: v for k, v in sorted(code2count.items(), reverse=True, key=lambda item: item[1])[:10]}

    for idx, c in enumerate(codes):
        expressions.append({'expr_name': 'expr' + str(idx), 'code': c})
    # if len(expressions)==0:
    #     return mine_code_for_expressions_local_github(urls)
    return expressions


def pretty_print_transforms(transforms_suggested, expr_name_to_code):
    for (expr, transformer) in transforms_suggested:
        print(f'\t{expr_name_to_code[expr]}')
def encode_categorial(X):
    # do simple preprocessing
    lcategorical = []
    lnumeric = []
    cols = X.columns

    print(cols)
    for col in cols:
        if X[col].dtype == 'category':
            lcategorical.append(col)
        else:
            lnumeric.append(col)

    print("Categorical columns: " + str(lcategorical))
    print("Numeric columns: " + str(lnumeric))

    for name in lcategorical:
        label_encoder = LabelEncoder()
        X[name] = label_encoder.fit_transform(X[name])
    
    return X    



def github_read_file(username, repository_name, file_path, github_token=None):
    headers = {}
    if github_token:
        headers['Authorization'] = f"token {github_token}"
        
    url = f'https://api.github.ibm.com/repos/{username}/{repository_name}/contents/{file_path}'
    r = requests.get(url, headers=headers)
    r.raise_for_status()
    data = r.json()
    file_content = data['content']
    file_content_encoding = data.get('encoding')
    if file_content_encoding == 'base64':
        file_content = base64.b64decode(file_content).decode()

    return file_content