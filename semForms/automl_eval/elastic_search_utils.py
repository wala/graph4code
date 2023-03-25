
import pandas as pd
import random
from elasticsearch import Elasticsearch
import os, json, ast

def collect_matches(es, dataset):
    jsonData = {}
    # datasets_with_matches = {} #key is num of matches
    print(dataset)
    dataset_name = dataset.split('/')[-1]
    df = pd.read_csv(dataset)
    column_names = df.keys().values.tolist()
    # columns_list = df.columns.tolist()
    # num_cols = len(columns_list)
    matched_cols = {}
    # my_list = list(df)
    # print('type(columns_list): ', type(columns_list))
    # print('type(columns_list[0]): ', type(columns_list[0]))
    dataset_cols = []
    for col in column_names:
        try: #" '1007_s_at': 1" column_names get returned from pd in a strange format  [" '1007_s_at': 1", " '121_at': 2", " '1405_i_at': 3", " '1552256_a_at': 4"]
            new_val = col.strip()
            if ':' in new_val:
                col = new_val.split(':')[0].replace("'", '')
                print('newCol:', col)
            # res = ast.literal_eval(new_val)
            # if type(res) == dict:
            #     col = res.keys()[0]
            #     print('newCol:', col)
        except:
            # print(new_val, ' conversion failed!!')
            pass
        dataset_cols.append(col)
        digits = sum(c.isdigit() for c in col)
        if 'Unnamed' in col or col.lower() in ['unnamed', 'id', 'time', 'class']:
            print('Skip coln: ', col)
            continue
        if col.lower().startswith('attr') or col.lower().startswith('var'):
            print('Skip coln: ', col)
            continue
        if digits >= 3:
            print('Skip coln: ', col)
            continue
        res = es.search(index='expressions100k', body=get_query(col, 1))
        # print(col)
        matched_entries = []
        if len(res['hits']['hits']) > 0:
            print(f'======={dataset}: {col} =========== ')
            for res_entry in res['hits']['hits']:
                file = ''
                code = ''
                #drop it if no "code" is available, but always pick up code -- code is what we apply
                if 'code' not in res_entry['_source']:
                    continue
                code = res_entry['_source']['code']
                if col.lower() not in code:
                    continue
                if col not in res_entry['_source']['fields']:
                    continue
                if 'source_file' in res_entry['_source']:
                    file = res_entry['_source']['source_file']

                matched_entry = {
                    'source_file':  file,
                    'code': code,
                    'expr_name': res_entry['_source']['expr_name'],
                    'fields': res_entry['_source']['fields'],
                    'csvfiles': res_entry['_source']['csvfiles'],
                    'json_file': res_entry['_source']['json_file'],
                }
                matched_entries.append(matched_entry)
        if len(matched_entries)>0:
            matched_cols[col] = matched_entries
    if len(matched_cols) > 0:
        jsonData[dataset_name] = {}
        jsonData[dataset_name]['path'] = dataset
        jsonData[dataset_name]['num_cols'] = len(dataset_cols)
        jsonData[dataset_name]['columns_list'] = dataset_cols
        jsonData[dataset_name]['num_matched_cols'] = len(matched_cols)
        jsonData[dataset_name]['matched_cols'] = matched_cols

        # if str(len(matched_cols)) not in datasets_with_matches:
        #     datasets_with_matches[str(len(matched_cols))] = []
        # datasets_with_matches[str(len(matched_cols))].append(len(matched_cols))
    return jsonData#, datasets_with_matches

def get_query(c, number_of_matches = None):
    should_clauses = []
    should_clauses.append({"match": {"fields": c}})
    query = {
        "from": 0, "size": 10,
        "query": {
            "bool": {
                "must": [],
                "should": []
            }
        }
    }
    query['query']['bool']['should'] = should_clauses
    query['query']['bool']['must'] = should_clauses
    if not number_of_matches:
        query['query']['bool']["minimum_should_match"] = len(should_clauses) - 1
    else:
        query['query']['bool']["minimum_should_match"] = 1
    print(query)
    return query
