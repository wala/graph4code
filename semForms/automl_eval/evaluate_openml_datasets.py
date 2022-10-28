# 'openml_rawds_heads'
from os import listdir
from os.path import isfile, join
import pandas as pd
from elasticsearch import Elasticsearch
import os, json, ast, time
from sklearn.datasets import fetch_openml
import pickle
from sys import argv
import hashlib
import pandas
import sys
from glob import glob
import numpy as nps
import json,traceback

import openml

from try_specific_run import try_dataset

import faiss

COMPUTE_FAISS_EMBED = False
seed = 123456


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


def print_neighbors(distances, neighbors, keys, queries, exps, col2counts, total_num_tables):
    matched = False
    neighbours_scores = {}
    for idx, col in enumerate(neighbors):
        # print('-----------------------------------')
        # print('table name:' + str(queries))
        smallest_dist = 1000
        smallest_neighbor = -1
        for idx2, neighbor in enumerate(col):
            if distances[idx][idx2] < smallest_dist:
                smallest_neighbor = neighbor
            smallest_dist = min(smallest_dist, distances[idx][idx2])
            # print('possible neighbor:' + str(keys[neighbor]) + " distance:" + str(distances[idx][idx2]))
            neighbours_scores[str(keys[neighbor])] = str(distances[idx][idx2])
        if smallest_dist < 1.5:
            qs = set(queries)
            ks = set(keys[smallest_neighbor])
            # collect up unique-ish columns
            uniquecols = 0

            for c in qs.intersection(ks):
                assert c.lower() in col2counts, c + str(col2counts)
                # print('matched:' + c + ' ' + str(col2counts[c.lower()] / total_num_tables))
                if (col2counts[c.lower()] / total_num_tables) < .1:
                    uniquecols += 1

            if uniquecols > 2 and min(len(ks), len(qs)) > 4:
                # print('we have a match for:' + str(queries) + ' match: ' + str(keys[smallest_neighbor]))
                matched = True
    return matched, neighbours_scores



def do_match(all_open_ml_cols, all_exprs_cols, model):
    index = faiss.IndexFlatL2(512)  # build the index
    index.add(np.array(model(all_exprs_cols)))
    openml_embeddings = model(all_open_ml_cols)
    k = 6
    D, N = index.search(np.array(openml_embeddings), k)
    m = print_neighbors(D, N, all_exprs_cols, all_open_ml_cols, exp_list, col2counts, len(all_exps))
    if m == False:
        no_matches.append(csv)



def get_ES_matches(matches_file_name = './matched_ES_data_openml.json'):
    if os.path.exists(matches_file_name):
        print('ES matches are already computed!')
        return json.load(open(matches_file_name))

    # Set parameters for ElasticSearch
    es = Elasticsearch("http://localhost:9200", basic_auth=("elastic", os.environ['ES_PASSWORD']))
    onlyfiles = [join(openml_dir, f) for f in listdir(openml_dir) if isfile(join(openml_dir, f))]
    print(f'Found {len(onlyfiles)} OpenML files!!')

    jsonData, datasets_with_matches = collect_matches(es, onlyfiles, sample=False)
    with open(matches_file_name, 'w') as file:
        json.dump(jsonData, file, indent=4)
    return json.load(open(matches_file_name))
    ######################################
    
    
def create_command(output_file, name, target_name, task_type, seed, expressions):
    command_list = []
    command_list.append(
        ['', name, target_name, str(expressions),
         'plain', task_type, seed, output_file + '_plain.out']
    )
    command_list.append(
        ['', name, target_name, str(expressions),
         'expr', task_type, seed, output_file + '_expr.out']
    )
    command_list.append(
        ['', name, target_name, str(expressions),
         'both', task_type, seed, output_file + '_both.out']
    )
    return command_list

def build_automl_commands(ES_matches, commands_file_name='./commands_automl.pkl'):
    if os.path.exists(commands_file_name):
        print('commands_automl are already computed!')
        with open(commands_file_name, 'rb') as handle:
            command_list = pickle.load(handle)
            return command_list
    command_list = []
    if not COMPUTE_FAISS_EMBED:
        all_exps = ES_matches
        for csv in all_exps:
            all_exprs_cols = []
            if len(all_exps[csv]['matched_cols']) == 0:
                continue
            expressions = []
            expression_hashes_found = []
            name = csv.split('/')[-1].split('.csv')[0]
            if name not in dataset_metadata:
                print('Could not find metadata for dataset: ', name)
                
                name = csv.replace('.zip_', '.zip/').split('/')[-1].replace('.csv', '.plain.csv')
                print('Try full name: ', name)
                if name not in dataset_metadata:

                    continue
            metadata = dataset_metadata[name]
            for col in all_exps[csv]['matched_cols']:
                for e in all_exps[csv]['matched_cols'][col]:
                    col, exp = (col, e)
                    hash_code = hashlib.md5(exp['code'].encode('utf-8')).hexdigest()
                    if hash_code in expression_hashes_found:
                        print('md5 hash: duplicate expression?')
                        continue
                    expression_hashes_found.append(hash_code)
                    expressions.append(
                        {
                            'expr': f'expr_{len(expressions)}', #exp['expr_name'],
                            'code': exp['code']
                        }
                    )
            if len(expressions) == 0:
                print('Skip!! -- did not find any matching expressions!!')
            for target_name, task_type in zip(metadata['target_name'], metadata['task_type']):
                output_file = 'automl_out/' + f'{name}_{target_name}_{task_type}'
                name = ''.join([openml_dir, csv])  # TEMPORARY

                command_list.extend(
                    create_command(output_file, name,
                                   target_name, task_type, seed, expressions))

    else:
        import tensorflow_hub as hub
        model = hub.load(module_url)
        all_exps = ES_matches
        col2counts = {}
        for csv in all_exps:
            cols = all_exps[csv]['columns_list']
            for c in cols:
                cl = c.lower()
                if cl not in col2counts:
                    col2counts[cl] = 0
                col2counts[cl] += 1

        no_matches = []
        no_matches_search = 0

        for csv in all_exps:
            all_open_ml_cols = []
            all_exprs_cols = []
            exp_list = []

            open_ml_cols = [csv]
            open_ml_cols.extend(all_exps[csv]['columns_list'])
            exp_cols = []
            cols = csv + ' ' + ' '.join(all_exps[csv]['columns_list'])
            all_open_ml_cols.append(cols)
            if len(all_exps[csv]['matched_cols']) == 0:
                no_matches.append(csv)
                no_matches_search += 1
                continue
            matched_dataset_to_details = {}
            for col in all_exps[csv]['matched_cols']:
                for e in all_exps[csv]['matched_cols'][col]:
                    exp_list.append(e)
                    tables = ' '.join(e['csvfiles'])
                    fields = ' '.join(e['fields'])
                    all_exprs_cols.append(tables + ' ' + fields)
                    matched_dataset_to_details[tables + ' ' + fields] = (col, e)
                    exp_cols.append(e['csvfiles'] + e['fields'])

            index = faiss.IndexFlatL2(512)  # build the index
            index.add(np.array(model(all_exprs_cols)))
            # all_open_ml_cols: all colns in current OpenML dataset
            # all_exprs_cols: matched ES dataset
            print('*'*20)
     
            name = csv.split('/')[-1].split('.csv')[0] #we have examples like nasa_numeric.csv, mushroom.csv_cols.json, cars.csv_cols.json
            print(f'OpenML dataset: {csv} -- {name}')

            if name not in dataset_metadata:
                print('Could not find metadata for dataset: ', name)
                continue
            metadata = dataset_metadata[name]
            if not metadata['target_name']:
                print('Target column is unknown, skipping ', name)
                continue
            problem_type = metadata['task_type']
            problem_type = 'regression' if problem_type == 'regression' else 'classification'
           
            print('Possible matches to select from: ')
            for ex in all_exprs_cols:
                print('\t', ex)
            openml_embeddings = model(all_open_ml_cols)
            k = 6
            D, N = index.search(np.array(openml_embeddings), k)
            try:
                m, neighbours_scores = print_neighbors(D, N, exp_cols, open_ml_cols, exp_list, col2counts, len(all_exps))
            except:
                print('Skip print_neighbors assertion failed!!')
                continue
            print('Top matches: ')
            expressions = []
            expression_hashes_found = []
            for possible_neighb, score in neighbours_scores.items():
                print(f'{possible_neighb}:{score}')
                key_to_search = ' '.join(eval(possible_neighb))
                if key_to_search not in matched_dataset_to_details:
                    print(f'Skip {key_to_search}: could not find its metadata!!') #TODO
                    continue
                col, exp = matched_dataset_to_details[key_to_search]
                hash_code = hashlib.md5(exp['code'].encode('utf-8')).hexdigest()
                if hash_code in expression_hashes_found:
                    print('md5 hash: duplicate expression?')
                    continue
                expression_hashes_found.append(hash_code)
                expressions.append(
                    {
                        'expr': f'expr_{len(expressions)}', #exp['expr_name'],
                        'code': exp['code']
                    }
                )
            if len(expressions) == 0:
                print('Skip!! -- did not find any matching expressions!!')
            if 'classification' in metadata['task_type']: #TODO: binary vs. multiclass
                metadata['task_type'] = 'classification'
 
            output_file = 'automl_out/'+csv.split('/')[-1]
            name = ''.join([openml_dir, csv]) #TEMPORARY
     # )
            command_list.extend(create_command(output_file, name, metadata['target_name'][0], metadata['task_type'], seed, expressions))
            print('added command: ', command_list[-1])
            if m == False:
                no_matches.append(csv)
    with open(commands_file_name, 'wb') as handle:
        pickle.dump(command_list, handle, protocol=pickle.HIGHEST_PROTOCOL)
    return command_list

def analyze_results(dir_name):
    onlyfiles = [join(dir_name, f) for f in listdir(dir_name) if isfile(join(dir_name, f))]
    print(f'Found {len(onlyfiles)} OpenML files!!')
    dataset_expr_to_results = {}
    dataset_plain_to_results = {}
    for file in onlyfiles:
        print(f'--------------{file}------------')
        data = json.load(open(file))
        print(type(data))

        if data['type'] == 'classification':
            metric = 'mean_per_class_error'
        else:
            metric = 'mse'
        if data['how'] == 'expressions':
            dataset_expr_to_results[data['csv']] = {
                'metric':
                    {
                        'name': metric,
                        'value': data[metric] if metric in data else 'failed'
                    }
            }
        else:
            dataset_plain_to_results[data['csv']] = {
                'metric':{
                    'name': metric,
                    'value': data[metric] if metric in data else 'failed'
                }
            }
    alldict = [dataset_plain_to_results, dataset_expr_to_results]
    allkeys = set().union(*alldict)
    for key in allkeys:
        out = f"{key} ({dataset_plain_to_results[key]['metric']['name']}): " \
              f"plain:{dataset_plain_to_results[key]['metric']['value']}, " \
              f"expr: {dataset_expr_to_results[key]['metric']['value']}"
        if 'failed' not in out:
            out = '**'+out
        print(out)


#-----------OpenML---------#
# Directories and filenames need to be set appropriately
openml_dir = './openml_rawds_heads/'
dataset_metadata = json.load(open('../../data/dataset_metadata.json'))
ES_matches = get_ES_matches(matches_file_name='./matched_ES_data_openml.json')
command_list = build_automl_commands(ES_matches, commands_file_name='./commands.pkl')

print(f'Found {len(command_list)//2} datasets')
topk = 3
nCount = 0
for command in command_list:
    try:
        '''
        trying dataset:  [
        '', 
        '/data/openml_rawds/nasa_numeric.csv', 
        'act_effort', 
        '[{\'expr\': \'expr_1\', \'code\': "lambda df: (df[ \'turn\' ] == 3)"}]', 
        'plain', 
        'regression',
         123456, 
         'automl_out/nasa_numeric.csv_plain.out']
        '''
        os.makedirs('automl_out_' +str(seed), exist_ok=True)
        # Set random seed
        seed = 7777777
        command[-2] = seed     
        print('task_type: ', command[5])
        print('Number of expressions: ', len(command[3]))
        print('trying dataset: ', command)
        nCount = nCount+1
        continue
        try_dataset(command, make_auto=False, compute_corr_only=False)
        time.sleep(60)
        from autoexample import write_expressions_file
        write_expressions_file()
    except:
        traceback.print_exc(file=sys.stdout)
        print('Failed dataset: ', command)
