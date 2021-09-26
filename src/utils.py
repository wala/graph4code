import json, sys, os, xmltodict
from os.path import join
import shutil
from elasticsearch import Elasticsearch
from bs4 import BeautifulSoup
import pickle
import subprocess, traceback
from os import path
from rdflib import URIRef, Literal, ConjunctiveGraph
from create_docstrings_graph import get_func_documentation, add_edge, get_new_func_klass_uri
from rdflib.namespace import RDF
import string, random
from torch.multiprocessing import Pool, cpu_count
from threading import Lock, Thread



es = None
DEBUG = False
lock = Lock()
list_of_ES_conn = list()

elastic_search_setting = {
    "settings": {
        "analysis": {
            "analyzer": {
                "my_custom_analyzer": {
                    "type": "custom",
                    "tokenizer": "whitespace",
                    "filter": ["my_delimiter","lowercase"]
                }
            },
            "filter": {
                "my_delimiter": {
                    "type": "word_delimiter",
                    "generate_word_parts": True,
                    "split_on_case_change": True
                }
            }
        }

    },
    "mappings": {
        "properties": {
            "content": {"type": "text",
                        "analyzer": "my_custom_analyzer",
                        "search_analyzer": "my_custom_analyzer"}
        }
    }
}

prefixes = {}
prefixes['rdf'] = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'
prefixes['rdfs'] = 'http://www.w3.org/2000/01/rdf-schema#'
prefixes['schema'] = 'http://schema.org/'
prefixes['sioc'] = 'http://rdfs.org/sioc/ns#'
prefixes['py'] = 'http://purl.org/twc/graph4code/python/'
prefixes['skos'] = 'http://www.w3.org/2004/02/skos/core#'
prefixes['sio'] = 'http://semanticscience.org/resource/'
prefixes['graph4code'] = 'http://purl.org/twc/graph4code/ontology/'
prefixes['owl'] = 'http://www.w3.org/2002/07/owl#'
prefixes['prov'] = 'http://www.w3.org/ns/prov#'
prefixes['dcat'] = 'http://www.w3.org/ns/dcat#'
prefixes['dcterms'] = 'http://purl.org/dc/terms/'
prefixes["stackoverflow3"] = "https://stackoverflow.com/questions/"
prefixes["npstackoverflow3"] = "http://purl.org/twc/graph4code/so/nanopub/"
prefixes["stats_stackexchange"] = "https://stats.stackexchange.com/questions/"
prefixes["npstats_stackexchange"] = "http://purl.org/twc/graph4code/stats_se/nanopub/"
prefixes["datascience_stackexchange"] = "https://datascience.stackexchange.com/questions/"
prefixes["npdatascience_stackexchange"] = "http://purl.org/twc/graph4code/datascience_se/nanopub/"
prefixes["math_stackexchange"] = "https://math.stackexchange.com/questions/"
prefixes["npmath_stackexchange"] = "http://purl.org/twc/graph4code/math_se/nanopub/"
prefixes["ai_stackexchange"] = "https://ai.stackexchange.com/questions/"
prefixes["npai_stackexchange"] = "http://purl.org/twc/graph4code/ai_se/nanopub/"

class FuncDetails:
    def __init__(self, functions, lib_name, file_name, graph_main_prefix, index_name, stack_output_dir, split_further=True):
        self.functions = functions
        self.lib_name = lib_name
        self.file_name = file_name

        self.num_failed_writes = 0
        self.number_of_found_klass_searches = 0
        self.number_of_found_method_searches = 0
        self.number_of_found_function_searches = 0
        self.num_failed_writes = 0
        self.escaped_filed= 0
        self.total_num_triples = 0

        self.graph_main_prefix = graph_main_prefix
        # self.es = es #Elasticsearch([{'host': 'localhost', 'port': 9200}]) #not pickable
        self.index_name = index_name
        self.stack_output_dir = stack_output_dir
        self.split_further = split_further
def remove_keys(row):
    new = {}
    for key,val in row.items():
        new[key.replace('@','')] = val
    return new

def clean(x):
    return x #.replace('\n','').replace('\r','').replace('\\','').replace('"','')

def read_stackoverflow_posts(stackoverflow_dir, load_posts_if_exists, pickled_files_out):
    posts = {}
    postsVotes = {}
    question_answers = {}
    if load_posts_if_exists and path.exists(pickled_files_out + "/posts.pickle"):
        print('Loading posts and postsVotes from disk')
        posts = pickle.load(open(pickled_files_out + "/posts.pickle", "rb"))
        print(f'len(posts) = {len(posts)}')
        postsVotes = pickle.load(open(pickled_files_out + "/postsVotes.pickle", "rb"))
        print(f'len(postsVotes) = {len(postsVotes)}')
        question_answers = pickle.load(open(pickled_files_out + "/question_answers.pickle", "rb"))
        print(f'len(question_answers) = {len(question_answers)}')
    else:
        print("Step 1: Extracting stackoverflow posts and other relevant information")
        file = join(stackoverflow_dir, 'Posts.xml')
        index = 0
        num_questions = 0
        num_answers = 0
        num_non_python_questions = 0
        num_posts_with_errors = 0
        for i, line in enumerate(open(file)):
            line = line.strip()
            try:
                if line.startswith("<row"):
                    index += 1
                    el = xmltodict.parse(line)['row']
                    el = remove_keys(el)
                    Id = clean((el.get('Id', '')))
                    Body = clean(el.get('Body', ''))
                    Title = clean(el.get('Title', ''))
                    PostTypeId = clean(el.get('PostTypeId', ''))
                    AcceptedAnswerId = clean(el.get('AcceptedAnswerId', ''))
                    ParentId = clean((el.get('ParentId', '')))
                    Tags = clean(el.get('Tags', ''))

                    if PostTypeId == '1':
                        num_questions += 1
                    elif PostTypeId == '2':
                        num_answers += 1
                    posts[Id] = (Id, PostTypeId, ParentId, AcceptedAnswerId, Title, Body, Tags)
                    if PostTypeId == '1' and Id not in question_answers:  # question
                        question_answers[Id] = set([])
                        continue
                    if PostTypeId == '2' and ParentId not in question_answers:  # answer
                        question_answers[ParentId] = set([])
                    question_answers[ParentId].add(Id)
                    # if index > 1000: ##debug
                    #     break
            except Exception as e:
                num_posts_with_errors += 1
                print(str(e))
        print(f'Extracted {len(posts)} out of {index} posts: num_questions: {num_questions}, '
              f'num_answers: {num_answers}, num_non_python_posts: {num_non_python_questions}')
        print(f'num_posts_with_errors: {num_posts_with_errors}')
        file = join(stackoverflow_dir, 'Votes.xml')
        for i, line in enumerate(open(file)):
            line = line.strip()
            try:
                if line.startswith("<row"):
                    el = xmltodict.parse(line)['row']
                    el = remove_keys(el)
                    PostId = el['PostId']
                    VoteTypeId = clean(el.get('VoteTypeId', ''))
                    if VoteTypeId == '1' or VoteTypeId == '2':
                        if PostId not in postsVotes:
                            postsVotes[PostId] = 1
                        else:
                            postsVotes[PostId] = postsVotes[PostId] + 1
            except Exception as e:
                traceback.print_exc(file=sys.stdout)
                print(e)

        for key, value in posts.items():
            qvotes = str(postsVotes[key]) if key in postsVotes else ''
            posts[key] = posts[key] + (qvotes,)
        ##save posts, votes
        # TODO: skip the above if these files are already there!
        try:
            print('Saving posts and votes')
            pickle.dump(posts, open(pickled_files_out + "/posts.pickle", "wb"))
            pickle.dump(postsVotes, open(pickled_files_out + "/postsVotes.pickle", "wb"))
            pickle.dump(question_answers, open(pickled_files_out + "/question_answers.pickle", "wb"))
        except:
            print('Failed to save posts/votes')
            traceback.print_exc(file=sys.stdout)
    return posts, postsVotes, question_answers

def build_elastic_search_index(index_name, es, posts, postsVotes, question_answers, delete_index):
    num_answers_with_less_votes = 0
    num_answers_not_found_in_posts = 0 #should be zero
    num_questions_not_found_in_posts = 0
    number_of_indexed_questions = 0
    number_of_indexed_answers = 0

    if delete_index:
        index = 1
        print("Step 2: Ingesting the collected posts in ES index")
        for key, value in question_answers.items():
            answers = []
            if key not in posts:
                num_questions_not_found_in_posts += 1
                continue
            for ansId in value:
                # ##TODO: filter answers based on votes
                # if ansId not in postsVotes:
                #     continue
                # if ansId in postsVotes and postsVotes[ansId] < 1:
                #     # print(f'answer #{ansId} for question {key} does not have enough votes')
                #     num_answers_with_less_votes += 1
                #     continue
                # print(f'answr #{ansId} for question {key} has enough votes {postsVotes[ansId]}')
                if ansId in posts:
                    answers.append(posts[ansId])
                else:
                    num_answers_not_found_in_posts += 1
            question = posts[key]
            # TODO: focus on questions, marked answers or answers with votes > 1
            qId, PostTypeId, ParentId, AcceptedAnswerId, Title, Body, Tags, qvotes = question
            docContent = Title + " " + Body + " " + Tags
            if DEBUG and 'tensorflow' not in docContent:
                continue
            answerCodes = []
            for answer in answers:
                # _, _, _, answerTitle, answerBody, _ = answer
                aId, aPostTypeId, aParentId, aAcceptedAnswerId, answerTitle, answerBody, aTags, avotes = answer
                docContent += answerBody
                soup = BeautifulSoup(answerBody, "html.parser")
                # codes = [p.get_text() for p in soup.find_all("code", text=True)]
                codes = [p.get_text() for p in soup.find_all("code", text=True) if '\n' in p.get_text()]
                answerCodes.append(codes)

            # codes has to be filtered for min length or having new line!
            doc = {'title': str(Title), 'content': docContent, 'codes': answerCodes, 'question_id:': key,
                   'question_votes:': qvotes,
                   'question_text:': Body, 'tags': Tags, 'answers': answers}
            res = es.index(index=index_name, id=index, body=doc)
            number_of_indexed_questions += 1
            number_of_indexed_answers += len(answers)
            index += 1

        print(f'num_answers_with_less_votes: {num_answers_with_less_votes}')
        print(f'num_questions_not_found_in_posts: {num_questions_not_found_in_posts}')
        print(f'num_answers_not_found_in_posts: {num_answers_not_found_in_posts}')
        print(f'number_of_indexed_questions: {number_of_indexed_questions}')
        print(f'number_of_indexed_answers: {number_of_indexed_answers}')
        # print('Total number of posts with votes > 1 extracted from Stackoverflow = ', len(posts))



def get_pure_class_or_function_query(c, key_terms=None):
    should_clauses = set([])
    # must_clauses = []
    # for term in c.split('.'):
    #     should_clauses.append({"match": {"content": term}})
    # key_term = c.split('.')[-1]
    # must_clauses.append({"match":{"content" : key_term}})
    # if key_terms:
    #     must_clauses.append({"match":{"content": key_terms}})
    # query = {
    #     "from": 0, "size": 1000,
    #     "query": {
    #     "bool" : {
    #         "must" : [],
    #         "should" : []
    #     }
    # }
    # }
    # query['query']['bool']['should'] = should_clauses
    # query['query']['bool']['must'] = must_clauses
    # query['query']['bool']["minimum_should_match"] = len(should_clauses) - 1
    if key_terms:
        for term in key_terms.split('.'):
            should_clauses.add(term)

    for term in c.split('.'):
        should_clauses.add(term)

    query = {
        'from': 0, 'size': 5000,
        "query": {
            "multi_match": {
                "query": ' '.join(should_clauses),
                "type": "most_fields",
                "fields": ["content"],
                "operator": "AND"
            }
        }
    }

    if DEBUG:
        print(f'------------\nElastic search query used for {c} and {key_terms} is {query}\n------------')
    return query

def get_class_function_query(func, c):
    return get_pure_class_or_function_query(c, func)

def filter_results(res, qualified_name, es):
    must_clauses = []
    arr = qualified_name.split('.')
    must_clauses.append(arr[-1])
    if len(arr)>1:
        must_clauses.append(arr[0])

    must_2_match = {}
    for must in must_clauses:
        token_parts = es.indices.analyze(index="dummy_idx", body={
            "field": "content",
            "text": must
        })
        tokens = []
        for s in token_parts['tokens']:
            tokens.append(s['token'])
        str_to_match = ' '.join(tokens).lower()
        must_2_match[must] = str_to_match

    if DEBUG:
        print(f'token matches for {qualified_name}: {must_2_match}')

    num_bad_matches = 0
    for doc in res['hits']['hits']:
        num_must_matches = 0
        for must in must_clauses:
            if must.lower() in str(doc["_source"]["content"]).lower():
                num_must_matches += 1
            else:
                str_to_match =  must_2_match[must]
                if str_to_match in str(doc["_source"]["content"]).lower():
                    num_must_matches += 1
        if num_must_matches == len(must_clauses):
            doc['good_match'] = 'True'
        else:
            if DEBUG:
                print(f'bad match for {qualified_name}: {doc["_source"]["title"]}')
            doc['good_match'] = 'False'
            num_bad_matches += 1

    if DEBUG:
        print(f"Number of bad matches for {qualified_name} is {num_bad_matches} out of {len(res['hits']['hits'])}")

    return res

def num_lines(fname):
    with open(fname) as f:
        for i, l in enumerate(f):
            pass
    return i + 1

def create_doc_graph(function_details:FuncDetails):
    klass_searches = {}
    all_functions_searches = {}
    global list_of_ES_conn
    import time
    lock.acquire()
    while len(list_of_ES_conn) == 0:
        print('No more available ES ports ... waiting')
        time.sleep(2)
    es = list_of_ES_conn.pop(0)
    print(f'Current # of available ports: {len(list_of_ES_conn)}')
    lock.release()

    # es = Elasticsearch([{'host': 'localhost', 'port': 9200}])
    all_functions = []

    if function_details.split_further:
        for function_dic in function_details.functions:
            all_functions.append(function_dic)
    else:
        all_functions.append(function_details.functions)

    for function_dic in all_functions:
        # if 'module' not in function_dic:
        #     function_details.escaped_filed += 1
        #     continue
        doc_details = get_func_documentation(function_dic)
        # print('------')
        # print(function_dic)
        # print('------')
        # # print(doc_details)
        # attrs = vars(doc_details)
        # # {'kids': 0, 'name': 'Dog', 'color': 'Spotted', 'age': 10, 'legs': 2, 'smell': 'Alot'}
        # # now dump this in some way or another
        # print(', '.join("%s: %s" % item for item in attrs.items()))
        # print('------')
        graph_uri = URIRef(prefixes[function_details.graph_main_prefix])
        try:
            g = ConjunctiveGraph()
            if doc_details.type == 'class':
                # doc_uri = URIRef(get_func_klass_uri(doc_details.module_name, doc_details.klass_name, function_details.graph_main_prefix))
                # g = add_edge(g, doc_uri, URIRef(function_details.graph_main_prefix + 'name'), Literal(doc_details.klass_name),
                #              URIRef(function_details.graph_main_prefix))

                doc_uri = URIRef(get_new_func_klass_uri(doc_details.klass_name))
                g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'label'), Literal(doc_details.klass_name),
                             graph_uri)

                query = get_pure_class_or_function_query(doc_details.klass_name)
                res = es.search(request_timeout=30, index=function_details.index_name, body=query)
                print(f"Number of matches in ES for {doc_details.klass_name} is {len(res['hits']['hits'])}")
                if len(res['hits']['hits']) == 0:
                    continue
                res = filter_results(res, doc_details.klass_name, es)
                key = doc_details.klass_name
                klass_searches[key] = {'module': doc_details.module_name, 'klass': doc_details.klass_name,
                                       'stackoverflow': res['hits']['hits']}
                function_details.number_of_found_klass_searches += 1
                g = add_stackoverflow_triples(res['hits']['hits'], doc_uri, g, function_details.graph_main_prefix)
            elif doc_details.type == 'method':
                # doc_uri = URIRef(get_method_uri(doc_details.klass_name, doc_details.function_name, function_details.graph_main_prefix))
                doc_uri = URIRef(get_new_func_klass_uri(doc_details.klass_name + '.' + doc_details.function_name))
                # g = add_edge(g, doc_uri, URIRef(function_details.graph_main_prefix + 'name'),
                #              Literal(doc_details.klass_name + '.' + doc_details.function_name),
                #              URIRef(function_details.graph_main_prefix))
                g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'label'),
                             Literal(doc_details.function_name), graph_uri)
                g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'altLabel'),
                             Literal(doc_details.klass_name + '.' + doc_details.function_name), graph_uri)

                query = get_class_function_query(doc_details.function_name, doc_details.klass_name)
                res = es.search(request_timeout=30, index=function_details.index_name, body=query)
                print(f"Number of matches in ES for {doc_details.klass_name}, {doc_details.function_name}  is {len(res['hits']['hits'])}")
                if len(res['hits']['hits']) == 0:
                    continue
                res = filter_results(res, doc_details.klass_name + '.' + doc_details.function_name, es)
                if doc_details.klass_name not in all_functions_searches:
                    all_functions_searches[doc_details.klass_name] = []
                all_functions_searches[doc_details.klass_name].append({'module': doc_details.module_name, 'klass': doc_details.klass_name,
                                      'function': doc_details.function_name,
                                      'stackoverflow': res['hits']['hits']})
                function_details.number_of_found_method_searches += 1
                g = add_stackoverflow_triples(res['hits']['hits'], doc_uri, g, function_details.graph_main_prefix)

            else:
                # doc_uri = URIRef(get_func_klass_uri(doc_details.module_name, doc_details.function_name, function_details.graph_main_prefix))
                doc_uri = URIRef(get_new_func_klass_uri(doc_details.function_name))
                # g = add_edge(g, doc_uri, URIRef(function_details.graph_main_prefix + 'name'),
                #              Literal(doc_details.function_name),
                #              URIRef(function_details.graph_main_prefix))
                g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'label'),
                             Literal(doc_details.function_name), graph_uri)
                query = get_pure_class_or_function_query(doc_details.function_name)
                res = es.search(request_timeout=30, index=function_details.index_name, body=query)
                print(f"Number of matches in ES for {doc_details.function_name} is {len(res['hits']['hits'])}")
                if len(res['hits']['hits']) == 0:
                    continue
                res = filter_results(res, doc_details.function_name, es)
                key = doc_details.function_name
                klass_searches[key] = {'module': doc_details.module_name, 'function': doc_details.function_name,
                                       'stackoverflow': res['hits']['hits']}
                function_details.number_of_found_function_searches += 1
                g = add_stackoverflow_triples(res['hits']['hits'], doc_uri, g, function_details.graph_main_prefix)

            if g is None:
                print(
                    f'Could not create a graph for: module {doc_details.module_name}, class: {doc_details.klass_name}, func: {doc_details.function_name}')
            else:
                g_num_triples = 0
                for e in g:
                    g_num_triples += 1
                    # print(e)
                if g_num_triples > 0:
                    function_details.total_num_triples += g_num_triples
                    rnd_str = ''.join(random.choices(string.ascii_uppercase + string.digits, k=10))
                    filename = '{}_{}.nq'.format(rnd_str, function_details.lib_name)
                    g.serialize(destination=function_details.stack_output_dir + '/' + filename, format='nquads')

                    lines_count = num_lines(function_details.stack_output_dir + '/' + filename)
                    print(f'Actual number of lines in file: {lines_count} vs. num_of_edges {g_num_triples}')
        except:
            print(f'Failed to generate graph for {doc_details.module_name}.{doc_details.klass_name}.{doc_details.function_name}')
            traceback.print_exc(file=sys.stdout)

    ##Save found matches if any
    output_path = os.path.join(function_details.stack_output_dir, function_details.lib_name)
    if not os.path.exists(output_path):
        try:
            os.mkdir(output_path)
        except:
            pass

    for k, v in klass_searches.items():
        try:
            pth_json = os.path.join(output_path, k + '.json')
            with open(pth_json, 'w') as out:
                json.dump(v, out, indent=4)
            print(f'Saving json matches of {pth_json}')
        except:
            print(f'Failed to write klass searches: {k} at output path {pth_json}')
            function_details.num_failed_writes += 1

    for k,v in all_functions_searches.items():
        try:
            pth_json = os.path.join(output_path, k + '_all_methods.json')
            with open(pth_json, 'w') as out:
                json.dump(v, out, indent=4)
            print(f'Saving json matches of {pth_json}')
        except:
            print(f'Failed to write method searches: {k} at output path {pth_json}')
            function_details.num_failed_writes += 1

    # if len(all_functions_searches) != 0:
    #     try:
    #         pth_json = os.path.join(output_path, doc_details.module_name + '_all_methods.json')
    #         with open(pth_json, 'w') as out:
    #             json.dump(all_functions_searches, out, indent=4)
    #         print(f'Saving json matches of {pth_json}')
    #
    #     except:
    #         print(f'Failed to write function searches: {doc_details.module_name} at output path {output_path}')
    #         function_details.num_failed_writes += 1
    # es.transport.close()
    lock.acquire()
    list_of_ES_conn.append(es)
    print(f'Finished with this ES port ... adding it back, new size = {len(list_of_ES_conn)}')
    lock.release()
    print(f'Finished graph creation with {function_details.total_num_triples} triples -- '
          f'module {doc_details.module_name}, class: {doc_details.klass_name}, func: {doc_details.function_name}')
    # time.sleep(20)
    return function_details

#num_cores > 10 crashes ES
def create_stackoverflow_graph(index_name, es, docstring_dir, stack_output_dir, graph_main_prefix, num_cores = 10):
    total_num_files = 0
    escaped_filed = 0
    number_of_found_klass_searches = 0
    number_of_found_method_searches = 0
    number_of_found_function_searches = 0
    num_failed_writes = 0
    # out_triple_file = open(stack_output_dir + '/stackoverflow_triples.nq', 'w')
    lib_cnt = 0
    total_num_triples = 0
    total_entries_processed = 0
    problem_set = []
    for lib in os.listdir(docstring_dir):
        if lib.startswith('.'):
            print('Skip ', lib)
            continue
        source_path = os.path.join(docstring_dir, lib)
        if not os.path.isdir(source_path):  # '../data/mods.22/pyvenv.cfg'
            continue
        lib_cnt += 1
        # if DEBUG and lib_cnt > 5:
        #     print('**********Debug STOOOOOPP*******')
        #     break
        for f in os.listdir(source_path):
            if not f.endswith('.json'):
                print('Skip ', lib)
                continue
            total_num_files += 1
            pth = os.path.join(source_path, f)
            with open(pth) as input:
                try:
                    functions = json.load(input)
                except:
                    print('Exception during loading file:' + pth)
                    escaped_filed += 1

                if type(functions) == dict:
                    functions = [functions]
                if type(functions) == list and len(functions) > 0 and type(functions[0])==dict: #handle last docstrings; all in one file
                    for function_dic in functions:
                        if DEBUG and 'tensorflow' not in str(function_dic):
                            continue
                        problem_set.append(FuncDetails(function_dic, lib, f, graph_main_prefix=graph_main_prefix,
                                                       index_name=index_name, stack_output_dir=stack_output_dir, split_further=False))
                else:
                    problem_set.append(FuncDetails(functions, lib, f, graph_main_prefix=graph_main_prefix,
                                               index_name= index_name, stack_output_dir=stack_output_dir, split_further=True))

    print('problem set size; ', len(problem_set))
    for i in range(1000):
        list_of_ES_conn.append(Elasticsearch([{'host': 'localhost', 'port': 9200}]))

    pool = Pool(num_cores)
    it = pool.imap_unordered(create_doc_graph, problem_set)
    for eps in range(len(problem_set)):
        try:
            print("Processing Func file: {} (out of {})".format(eps, len(problem_set)))
            function_details = it.next()#timeout=100)
            num_failed_writes += function_details.num_failed_writes
            number_of_found_klass_searches += function_details.number_of_found_klass_searches
            number_of_found_method_searches += function_details.number_of_found_method_searches
            number_of_found_function_searches += function_details.number_of_found_function_searches
            num_failed_writes += function_details.num_failed_writes
            escaped_filed += function_details.escaped_filed
            total_num_triples += function_details.total_num_triples
        except:
            print(f'Exception getting details about function number {eps}')
            traceback.print_exc(file=sys.stdout)

    print(f'num_failed_writes: {num_failed_writes}')
    print(f'number_of_found_klass_searches: {number_of_found_klass_searches}')
    print(f'number_of_found_method_searches: {number_of_found_method_searches}')
    print(f'number_of_found_function_searches: {number_of_found_function_searches}')
    print(f'total_num_triples: {total_num_triples}')
    merge_all_files(stack_output_dir, stack_output_dir+'/stackoverflow_triples.nq')


def merge_all_files(path, out_filename):
    file_list = os.listdir(path)
    outfile = open(out_filename, 'w')
    agg_lines_num = 0
    for filename in sorted(file_list):
        filepath = path + '/' + filename
        if os.path.isfile(filepath) and filepath.endswith('.nq'):
            with open(filepath, 'r') as f:
                content = f.readlines()
                agg_lines_num += len(content)
                for line in content:
                    if line.endswith('\n'):
                        outfile.write(line)
                    else:
                        outfile.write(line+'\n')
            try:
                os.remove(filepath)
            except:
                print(f'could not remove file: {filepath}')
        else:
            print(f'Skip non-file {filepath} or non-quad file')
            continue
    outfile.close()
    print(f'number of lines in individual files = {agg_lines_num}')

def add_stackoverflow_triples(stackoverflow_match, subj, g, graph_main_prefix, save_triples = True):
    if not save_triples:
        return 0
    # triples = []
    graph_uri = URIRef(prefixes[graph_main_prefix])
    for qa in stackoverflow_match:
        if 'good_match' in qa and qa['good_match'] == 'False':
            continue
            # g = add_edge(g, q_url, URIRef(graph_main_prefix + 'good_match'), Literal(qa['good_match']), graph_uri)
        q_url = prefixes[graph_main_prefix] + qa['_source']['question_id:']
        q_url = URIRef(q_url)
        g = add_edge(g, q_url, URIRef(prefixes['rdf'] +'type'), URIRef(prefixes['schema']+'Question'), graph_uri)
        g = add_edge(g, q_url, URIRef(prefixes['schema'] +'about'), subj, graph_uri)

        # #TODO: double check which prefix to use
        # g = add_edge(g, q_url, URIRef(prefixes['graph4code'] +'id'), URIRef(prefixes[graph_main_prefix] + qa['_source']['question_id:'])
        #              , graph_uri)
        g = add_edge(g, q_url, URIRef(prefixes['schema']+ 'name'), Literal(qa['_source']['title']), graph_uri)
        g = add_edge(g, q_url, URIRef(prefixes['sioc'] +'content'), Literal(qa['_source']['question_text:']), graph_uri)
        if 'tags' in qa['_source']:
            g = add_edge(g, q_url, URIRef(prefixes['schema'] + 'keywords'), Literal(qa['_source']['tags']), graph_uri)

        all_content = qa['_source']['question_text:']
        for ans in qa['_source']['answers']:
            # aId, aPostTypeId, aParentId, aAcceptedAnswerId, answerTitle, answerBody, aTags, avotes = answer
            ans_id = ans[0]
            ans_text = ans[5]
            all_content += ans_text
            ans_votes = ans[7]
            ans_url = prefixes[graph_main_prefix]+'a/' + ans_id
            ans_url = URIRef(ans_url)
            g = add_edge(g, q_url, URIRef(prefixes['schema'] + 'suggestedAnswer'), ans_url, graph_uri)
            g = add_edge(g, ans_url, URIRef(prefixes['rdf'] +'type'), URIRef(prefixes['schema']+'Answer'), graph_uri)
            g = add_edge(g, ans_url, URIRef(prefixes['sioc'] + 'content'), Literal(ans_text), graph_uri)
            g = add_edge(g, ans_url, URIRef(prefixes['schema'] + 'upvoteCount'), Literal(ans_votes), graph_uri)

        soup = BeautifulSoup(all_content, "html.parser")
        # codes = [p.get_text() for p in soup.find_all("code", text=True)]
        codes = [p.get_text() for p in soup.find_all("code", text=True) if '\n' in p.get_text()]
        code_id = 1
        for code in codes:
            if code is not [] and code is not None:
                code_uri = URIRef(prefixes[graph_main_prefix]+ qa['_source']['question_id:'] + '/code_snippet/' + str(code_id))
                g = add_edge(g, q_url, URIRef(prefixes['schema'] + 'hasPart'), code_uri,
                                 graph_uri)
                g = add_edge(g, code_uri, URIRef(prefixes['rdf'] + 'type'), URIRef(prefixes['schema']+'SoftwareSourceCode'),
                             graph_uri)
                g = add_edge(g, code_uri, URIRef(prefixes['prov'] + 'value'),
                             Literal(code),
                             graph_uri)
                code_id += 1
    return g

