from os import listdir
from os.path import isfile, join
import pandas as pd
# from elasticsearch import Elasticsearch
import os, json, ast
from sklearn.datasets import fetch_openml

import faiss
# import tensorflow_hub as hub
import pandas
import sys
from glob import glob
import numpy as np
import json
from check_intersec_openml_code import collect_matches
import openml
# from timeout import timeout
from multiprocessing import Pool, cpu_count, TimeoutError

# @timeout(60)
def fetch_dataset(name):
    try:
        X = fetch_openml(name)
        return X, name
    except:
        return None, None

def gather_datasets_metadata(output_file='./data/dataset_metadata.json'):
    if os.path.exists(output_file):
        dataset_metadata = json.load(open(output_file))
    else:
        dataset_metadata = {}
    datasets = openml.datasets.list_datasets()
    list_datasets_names = []
    for id, dataset in datasets.items():
        name = dataset['name']
        list_datasets_names.append(name)
    # list_datasets_names = list_datasets_names[:10]
    num_cores = cpu_count()
    pool = Pool(num_cores, maxtasksperchild=1)
    it = pool.imap_unordered(fetch_dataset, list_datasets_names)
    dataset_to_target_names = {}
    for eps in range(len(list_datasets_names)):
        print("Processing dataset file: {} (out of {})".format(eps, len(list_datasets_names)))
        try:
            X, name = it.next(60)
            if X:
                dataset_to_target_names[name] = X['target_names']
        except:
            print("Time out exception in processing dataset #", eps)

    for id, dataset in datasets.items():
        name = dataset['name']
        print(f'-----------Name: {name}------------')
        if name in dataset_metadata:
            continue
        task_type = None
        if 'NumberOfClasses' not in dataset or dataset['NumberOfClasses'] == 0:
            task_type = 'regression'
        elif dataset['NumberOfClasses'] == 2:
            task_type = 'binary_classification'
        else:
            task_type = 'multiclass_classification'
        # try:
        #     X = fetch_dataset(name) #fetch_openml(name)
        # except:
        #     print('Could not fetch ', name, ': skipping!!')
        #     continue
        # if X is None:
        #     print('Could not find dataset: ', name)
        #     continue
        # target = X['target_names']
        if name not in dataset_to_target_names:
            continue

        dataset_metadata[name] = {
            'id': dataset['did'],
            'status': dataset['status'],
            'task_type': task_type,
            'num_classes': dataset['NumberOfClasses'] if 'NumberOfClasses' in dataset else -1,
            'target_name': dataset_to_target_names[name] #target
        }
        if len(dataset_metadata) %10 == 0:
            with open('dataset_metadata.json', 'w') as file:
                json.dump(dataset_metadata, file, indent=4)

    with open(output_file, 'w') as file:
        json.dump(dataset_metadata, file, indent=4)
    return dataset_metadata


