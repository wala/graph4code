import sys, os, json
from rdflib import URIRef, BNode, Literal
import shutil, traceback
from rdflib.namespace import RDF, FOAF
from rdflib import Namespace, URIRef, Graph, ConjunctiveGraph
import validators
import re, argparse
'''
examples:
    "sklearn.config_context": {
        "function_docstring": "\nContext manager for global scikit-learn configuration\n",
        "param_names": [
            "new_config"
        ],
        "param_map": {
            "assume_finite": {
                "name": "assume_finite",
                "param_doc": " If True, validation for finiteness will be skipped,\n                      saving time, but leading to potential crashes. If\n                      False, validation for finiteness will be performed,\n                      avoiding error.  Global default: False.",
                "type": " bool, optional",
                "optional": true,
                "inferred_type": [
                    "bool"
                ]
            },
            "working_memory": {
                "name": "working_memory",
                "param_doc": " If set, scikit-learn will attempt to limit the size of temporary arrays\n                       to this number of MiB (per job when parallelised), often saving both\n                       computation time and memory on expensive operations that can be\n                       performed in chunks. Global default: 1024.",
                "type": " int, optional",
                "optional": true,
                "inferred_type": [
                    "int"
                ]
            }
        },
        "module": "sklearn",
        "function": "sklearn.config_context"
    },
    "sklearn.base.is_regressor": {
        "function_docstring": "\nReturn True if the given estimator is (probably) a regressor.\n",
        "param_names": [
            "estimator"
        ],
        "param_map": {
            "estimator": {
                "name": "estimator",
                "param_doc": " Estimator object to test.\n",
                "type": " object"
            }
        },
        "return_map": {
            "doc": " **out** -- True if estimator is a regressor and False otherwise.",
            "type": " bool",
            "inferred_type": [
                "bool"
            ]
        },
        "module": "sklearn",
        "function": "sklearn.base.is_regressor"
    },
    ...
    "base_classes": [
            "sklearn.base.BaseEstimator",
            "sklearn.base.ClassifierMixin",
            "sklearn.base.MetaEstimatorMixin"
        ]
    ...
    "param_types": [
            "code",
            "dict"
        ],
        
    "param_map": {
            "raw": {
                "name": "raw",
                "param_doc": " If true (default), retrieve raw history. Has no effect on the other\n            retrieval mechanisms.",
                "type": " bool",
                "inferred_type": [
                    "traitlets.traitlets.Bool",
                    "bool"
                ]
            },
    "ret_types": "typing.Tuple[str, typing.List[str], typing.List[str], typing.Iterable[IPython.core.completer._FakeJediCompletion]]",
'''

docstrings_uri = 'http://purl.org/twc/graph4code/docstrings'

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

num_lambda_exprs = 0
entities_with_space = 0
total_num_triples = 0

class_map = {}

class DocumentationDetails:
    def __init__(self, module):
        self.module_name = module
        self.type = None
        self.function_name = None
        self.klass_name = None
        self.function_docstring = None
        self.class_docstring = None
        self.param_names = None
        self.return_map = None
        self.param_map = None
        self.ret_types = None
        self.base_classes = None
        self.param_types = None


def get_param_name_key(param):
    key = param.replace('`', '').replace('{', '').replace('\"', '').replace('\'', '').replace('<', '').replace(':', ' ')
    arr = key.split(' ')
    arr = ' '.join(arr).split()
    if len(arr) == 1:
        return arr[0], None
    elif len(arr) == 2:
        key = arr[1]
        key_type =  arr[0]
        print(f'trying to shorten key: {param} to {key} with type {key_type}')
        return key, key_type
    elif len(arr) > 2:
        print(f'trying to shorten key: {param} to {arr[0]}')
        return arr[0], None
    else:
        return None, None

def add_edge(graph, subj, pred, obj, contxt):

    if type(subj) == URIRef and not validators.url(subj):
        print(f'Invalid subject URI: {subj}')
        return graph
    if type(pred) == URIRef and not validators.url(pred):
        print(f'Invalid predicate URI: {pred}')
        return graph
    if type(obj) == URIRef and not validators.url(obj):
        print(f'Invalid object URI: {obj}')
        return graph
    graph.add((subj, pred, obj, contxt))
    return graph

def add_triples_from_param_map(doc_uri, g, data_list, param_names):
    '''
    {'adding_headers': {'name': 'adding_headers', 'param_doc': ''},
    'forcing_headers': {'name': 'forcing_headers', 'param_doc': ''},
     'streaming': {'name': 'streaming', 'param_doc': ' defaults to **False**'}, 'method': {'name': 'method', 'param_doc': ' one of ``httpretty.GET``, ``httpretty.PUT``, ``httpretty.POST``, ``httpretty.DELETE``, ``httpretty.HEAD``, ``httpretty.PATCH``, ``httpretty.OPTIONS``, ``httpretty.CONNECT``'}, 'kw: keyword-arguments passed onto the :py:class': {'name': 'kw: keyword-arguments passed onto the :py:class', 'param_doc': ' keyword-arguments passed onto the :py:class:`~httpretty.core.Entry`'}, 'body': {'name': 'body', 'param_doc': ''}, 'uri': {'name': 'uri', 'param_doc': ''}, 'status': {'name': 'status', 'param_doc': ' defaults to **200**'}}

    '''
    delimiters = " ", ":"
    regexPattern = '|'.join(map(re.escape, delimiters))

    if param_names is None:
        print(f'Skip adding param map since param_names is None:  {data_list}')
        return g
    if data_list is not None:
        for key, value in data_list.items():
            if ' ' in key or ':' in key:
                key = key.strip()
                arr = re.split(regexPattern, key)
                #in case of key is kw: keyword-arguments passed onto the :py:class -- switch to kw
                #param map key is str name -- switch to str

                # if ' ' in key and len(arr)>1:
                #     nkey = arr[1]
                # else:
                nkey = ''
                for part in arr:
                    if part in param_names:
                        nkey = arr[0]
                        break
                if nkey == '':
                    print(f'Could not find index of parameter {key} in param_names: {param_names}, arr: {arr}')
                    continue
                print(f'param map key is {key} -- switch to {nkey}')
                key = nkey
                #for cases like "int status_code", "io.IOBase body"

            # param_uri = URIRef(prefixes['graph4code'] + key)
            # param_index = param_names.index(value['name']) if 'name' in value else param_names.index(key)
            if key not in param_names:
                print(f'Could not find index of parameter {key} in param_names: {param_names}')
                continue
            param_index = param_names.index(key)+1
            param_uri = URIRef(str(doc_uri) +'/p/'+ str(param_index))

            g = add_edge(g, param_uri, URIRef(prefixes['rdf'] + 'type'),
                         URIRef(prefixes['graph4code']+'Parameter'), URIRef(docstrings_uri))
            g = add_edge(g, param_uri, URIRef(prefixes['rdfs'] + 'label'),
                         Literal(key.strip()), URIRef(docstrings_uri))
            g = add_edge(g, param_uri, URIRef(prefixes['graph4code'] + 'param_index'),
                         Literal(param_index), URIRef(docstrings_uri))
            g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'param'),
                         param_uri, URIRef(docstrings_uri))

            for key2, value2 in value.items():
                pred_uri = URIRef(prefixes['graph4code'] + key2)
                if key2 == 'name':
                    pred_uri = URIRef(prefixes['rdfs'] + 'label')
                elif key2 == 'param_doc':
                    pred_uri = URIRef(prefixes['skos']+ 'definition')
                elif key2 == 'type':
                    pred_uri = URIRef(prefixes['graph4code'] + 'param_type')
                elif key2 == 'inferred_type':
                    pred_uri = URIRef(prefixes['graph4code'] + 'param_inferred_type')
                elif key2 == 'optional':
                    pred_uri = URIRef(prefixes['graph4code'] + 'optional')
                if type(value2) == list:
                    if key2 == 'inferred_type':
                        for val in value2: #['float', 'tuple']
                            g = add_edge(g, param_uri, pred_uri, URIRef(prefixes['py']+val), URIRef(docstrings_uri))
                    else:
                        print(f'Found return map key2 as list and it is not an inferred_type {key2}: {value2}')
                        print()
                else:
                    #value2 can be str or bool
                    if type(value2) == str:
                        value2 = value2.strip()
                    g = add_edge(g, param_uri, pred_uri, Literal(value2), URIRef(docstrings_uri))
    return g

def add_triples_from_return_map(doc_uri, g, data_list):
    '''
   {'doc': ' ProxyManager', 'type': ' urllib3.ProxyManager'}
    '''
    if data_list is not None:
        return_index = 1
        if 'type' in data_list:
            ret_uri = URIRef(str(doc_uri) + '/r/' + str(return_index))
            g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'return'),
                         ret_uri, URIRef(docstrings_uri))
            g = add_edge(g, ret_uri, URIRef(prefixes['rdf'] + 'type'),
                         URIRef(prefixes['graph4code'] + 'Return'), URIRef(docstrings_uri))
            pred_uri = URIRef(prefixes['graph4code'] + 'return_type')
            g = add_edge(g, ret_uri, pred_uri, URIRef(prefixes['py'] + data_list['type'].strip()), URIRef(docstrings_uri))
            g = add_edge(g, ret_uri, URIRef(prefixes['graph4code'] + 'return_index'),
                         Literal(return_index), URIRef(docstrings_uri))

            pred_uri = URIRef(prefixes['graph4code'] + 'return_inferred_type')
            if 'inferred_type' in data_list:
                for inf in data_list['inferred_type']:
                    g = add_edge(g, doc_uri, pred_uri,
                                 URIRef(prefixes['py'] + inf), URIRef(docstrings_uri))
            pred_uri = URIRef(prefixes['skos'] + 'definition')
            if 'doc' in data_list:
                g = add_edge(g, ret_uri, pred_uri, Literal(data_list['doc'].strip()), URIRef(docstrings_uri))
    return g

def add_triples_from_dic_of_dic(doc_uri, g, data_list, edge_name):
    # "param_map": {
    #     "estimator": {
    #         "name": "estimator",
    #         "param_doc": " Estimator object to test.\n",
    #         "type": " object"
    #          "inferred_type": [
    #                 "traitlets.traitlets.Bool",
    #                 "bool"
    # ]
    #     }
    # },
    if data_list is not None:
        for key, value in data_list.items():
            if value is None or key is None:
                continue
            # print(key)
            new_key, key_type = get_param_name_key(key)
            if new_key is None:
                print(f'KEY IS NONE: {key} -- {new_key}')
                continue
            if type(value) == str or type(value) == int:
                g = add_edge(g, doc_uri, URIRef(docstrings_uri + edge_name +'/'+ str(new_key)),
                             Literal(str(value)), URIRef(docstrings_uri))
                continue
            elif type(value) == list :
                for v in value:
                    g = add_edge(g, doc_uri, URIRef(docstrings_uri + edge_name +'/'+ str(new_key)),
                                 Literal(str(v)), URIRef(docstrings_uri))
                continue
            #key: estimator
            val_uri = URIRef(doc_uri +'/' + edge_name +'/' + str(new_key))
            g = add_edge(g, doc_uri, URIRef(docstrings_uri + edge_name),
                         val_uri, URIRef(docstrings_uri))
            if key_type is not None:
                g = add_edge(g, val_uri, RDF.type, URIRef(docstrings_uri + str(key_type)),
                             URIRef(docstrings_uri))
            for key2, value2 in value.items():
                new_key2, key_type = get_param_name_key(key2)
                if new_key2 is None:
                    print(f'KEY IS NONE: {key2} -- {new_key2}')
                    continue

                #"name": "estimator",
                if value2 is None:
                    continue
                if type(value2) == str:
                    g = add_edge(g, val_uri, URIRef(docstrings_uri +'/'+ new_key2), Literal(str(value2)), URIRef(docstrings_uri))
                elif type(value2) == list:
                    for v in value2:
                        g = add_edge(g, val_uri, URIRef(docstrings_uri +'/'+edge_name +'/'+ new_key2), Literal(str(v)),
                                     URIRef(docstrings_uri))

    return g

def add_part_of_edges(g, doc_uri, class_or_module, is_function=False):
    class_or_module_comp = class_or_module.split('.')
    part_uri = URIRef(get_new_func_klass_uri(class_or_module))
    if is_function:
        pred = URIRef(prefixes['graph4code'] + 'classMember')
    else:
        pred = URIRef(prefixes['dcterms'] + 'isPartOf')

    g = add_edge(g, doc_uri, pred,
                 part_uri,
                 URIRef(docstrings_uri))
    g = add_edge(g, part_uri, URIRef(prefixes['rdfs'] + 'label'),
                 Literal(class_or_module),
                 URIRef(docstrings_uri))
    prev_seg = get_new_func_klass_uri(class_or_module_comp[0])
    for i in range(1, len(class_or_module_comp)):
        doc_uri_tgt = URIRef(prev_seg)
        doc_uri_src = URIRef(prev_seg + '.' + class_or_module_comp[i])
        g = add_edge(g, doc_uri_src, URIRef(prefixes['dcterms'] + 'isPartOf'),
                     doc_uri_tgt, URIRef(docstrings_uri))
        g = add_edge(g, doc_uri_tgt, URIRef(prefixes['rdfs'] + 'label'),
                     Literal(doc_uri_tgt.replace(prefixes['py'], '').strip()), URIRef(docstrings_uri))
        prev_seg += '.' + class_or_module_comp[i]
    return g
def add_name_end(name, g, doc_uri):
    name = name.strip().replace('"', '')
    comp = name.strip().split('.')
    path_end = comp[len(comp) - 1]
    if path_end.strip() == "" or path_end.strip() == "_":
        print(f'skipping empty name_end === {path_end}')
    elif len(comp) > 1:
        g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'name_end'), Literal(path_end), URIRef(docstrings_uri))
    return g
def get_new_func_klass_uri(function_or_klass_name):
    return prefixes['py'] + function_or_klass_name

def output_documentation_triples(doc_details, out_triple_file):
    g = ConjunctiveGraph()
    doc_details.module_name = doc_details.module_name.strip().replace(' ', '.') if doc_details.module_name is not None else doc_details.module_name
    doc_details.klass_name = doc_details.klass_name.strip().replace(' ', '.') if doc_details.klass_name is not None else doc_details.klass_name
    doc_details.function_name = doc_details.function_name.strip().replace(' ', '.') if doc_details.function_name is not None else doc_details.function_name
    num_triples = 0
    doc_name = ''
    if doc_details.type == 'class':
        # doc_uri = URIRef(get_func_klass_uri(doc_details.module_name, doc_details.klass_name))
        doc_uri = URIRef(get_new_func_klass_uri(doc_details.klass_name))
        doc_name = doc_details.klass_name
        # g = add_edge(g, doc_uri, URIRef(prefixes['skos']+'notation'),  Literal(doc_details.klass_name), URIRef(docstrings_uri))
        g = add_edge(g, doc_uri, URIRef(prefixes['rdf']+'type'),  URIRef(prefixes['graph4code']+'Class'), URIRef(docstrings_uri))
        g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'label'), Literal(doc_details.klass_name), URIRef(docstrings_uri))
        # if doc_details.module_name is not None:
        #     g = add_edge(g, doc_uri, URIRef(prefixes['skos'] + 'altLabel'), Literal(doc_details.module_name+'.'+doc_details.klass_name), URIRef(docstrings_uri))
        func_prefix = '.'.join(doc_details.klass_name.split('.')[:-1])
        g = add_part_of_edges(g, doc_uri, func_prefix, is_function = False)
        g = add_name_end(doc_details.klass_name, g, doc_uri)
        if doc_details.klass_name in class_map:
            alias_uri = URIRef(get_new_func_klass_uri(class_map[doc_details.klass_name]))
            g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'aliasOf'),
                         alias_uri,
                         URIRef(docstrings_uri))

    elif doc_details.type == 'function':
        doc_uri = URIRef(get_new_func_klass_uri(doc_details.function_name))
        doc_name = doc_details.function_name
        # g = add_edge(g, doc_uri, URIRef(prefixes['skos']+'notation'),  Literal(doc_details.function_name), URIRef(docstrings_uri)) #same info is used in rdfs:label
        g = add_edge(g, doc_uri, URIRef(prefixes['rdf']+'type'),  URIRef(prefixes['graph4code']+'Function'), URIRef(docstrings_uri))
        g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'label'), Literal(doc_details.function_name), URIRef(docstrings_uri))
        # if doc_details.module_name is not None:
        #     g = add_edge(g, doc_uri, URIRef(prefixes['skos'] + 'altLabel'), Literal(doc_details.module_name + '.' + doc_details.function_name),
        #                  URIRef(docstrings_uri))
        func_prefix = '.'.join(doc_details.function_name.split('.')[:-1])
        g = add_part_of_edges(g, doc_uri, func_prefix, is_function = True)
        g = add_name_end(doc_details.function_name, g, doc_uri)
    else:
        doc_name = doc_details.klass_name +'.' +doc_details.function_name
        doc_uri = URIRef(get_new_func_klass_uri(doc_name))
        g = add_edge(g, doc_uri, URIRef(prefixes['rdf']+'type'),   URIRef(prefixes['graph4code']+'Method'), URIRef(docstrings_uri))
        # g = add_edge(g, doc_uri, URIRef(prefixes['skos']+'notation'),   Literal(doc_details.function_name), URIRef(docstrings_uri))
        g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'label'), Literal(doc_details.klass_name + '.' + doc_details.function_name), URIRef(docstrings_uri))
        # if doc_details.module_name is not None:
        #     g = add_edge(g, doc_uri, URIRef(prefixes['skos'] + 'altLabel'),
        #            Literal(doc_details.module_name + '.' + doc_details.klass_name + '.' + doc_details.function_name), URIRef(docstrings_uri))
        g = add_part_of_edges(g, doc_uri, doc_details.klass_name, is_function = True)
        g = add_name_end((doc_details.klass_name + '.' + doc_details.function_name), g, doc_uri)
    #param_names is a list
    #base_classes is a list
    # g = add_triples_from_list(doc_uri, g, doc_details.base_classes,
    #                           prefixes['rdfs'] + 'subClassOf', as_URI=True, prefix_for_uri='py') #TODO: py or graph4code
    if doc_details.base_classes is not None:
        for base in doc_details.base_classes:
            g = add_edge(g, doc_uri, URIRef(prefixes['rdfs'] + 'subClassOf'), URIRef(prefixes['py'] + base), URIRef(docstrings_uri))

    g = add_triples_from_return_map(doc_uri, g, doc_details.return_map)


    #TODO: consolidate param_names, param_map and param_type
    #NOte that the param_names, param_map and param_type don't always appear together, that's why param label, type ... are repeated in each case
    ##TODO: THIS ASSUMES THE ORDER IN PARAMETER NAMES IS CORRECT
    if doc_details.param_names is not None:
        param_index = 1
        for param in doc_details.param_names:
            param_uri = URIRef(str(doc_uri) + '/p/' + str(param_index))
            g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'param'),
                         param_uri, URIRef(docstrings_uri))
            g = add_edge(g, param_uri, URIRef(prefixes['rdf'] + 'type'),
                         URIRef(prefixes['graph4code']+'Parameter'), URIRef(docstrings_uri))
            g = add_edge(g, param_uri, URIRef(prefixes['rdfs'] + 'label'), Literal(param),
                 URIRef(docstrings_uri))
            g = add_edge(g, param_uri, URIRef(prefixes['graph4code'] + 'param_index'),
                         Literal(param_index), URIRef(docstrings_uri))
            param_index += 1

    g = add_triples_from_param_map(doc_uri, g, doc_details.param_map, doc_details.param_names)
    if doc_details.module_name is not None:
        class_or_module_comp = doc_name.split('.')
        module_uri = URIRef(get_new_func_klass_uri(class_or_module_comp[0]))
        # module_uri = URIRef(get_new_func_klass_uri(doc_details.module_name))
        g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'module'),
               module_uri, URIRef(docstrings_uri))
        g = add_edge(g, module_uri, URIRef(prefixes['rdf'] + 'type'),
                     URIRef(prefixes['graph4code'] + 'Module'), URIRef(docstrings_uri))

    #TODO: param type should use the same uri as param map, also add position num
    # g = add_triples_from_list(doc_uri, g, doc_details.param_types, prefixes['graph4code'] + 'param_type')
    # if doc_details.param_types is not None:
    #     param_index = 1
    #     for param in doc_details.param_types:
    #         param_uri = URIRef(str(doc_uri) + '/p/' + str(param_index))
    #         g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'param'),
    #                      param_uri, URIRef(docstrings_uri))
    #         g = add_edge(g, param_uri, URIRef(prefixes['rdf'] + 'type'),
    #                      URIRef(prefixes['graph4code']+'Parameter'), URIRef(docstrings_uri))
    #         g = add_edge(g, param_uri, URIRef(prefixes['graph4code'] + 'param_type'),
    #                      URIRef(prefixes['py'] + param), URIRef(docstrings_uri))
    #         g = add_edge(g, param_uri, URIRef(prefixes['graph4code'] + 'param_index'),
    #                      Literal(param_index), URIRef(docstrings_uri))
    #         param_index += 1
    # param_uri = URIRef(str(doc_uri) + '/p/' + key)
    # g = add_edge(g, param_uri, URIRef(prefixes['rdf'] + 'type'),
    #              URIRef(prefixes['graph4code'] + 'Parameter'), URIRef(docstrings_uri))
    # g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'param'),
    #              param_uri, URIRef(docstrings_uri))
    if doc_details.function_docstring is not None:
        g = add_edge(g, doc_uri, URIRef(prefixes['skos']+ 'definition'), Literal(doc_details.function_docstring), URIRef(docstrings_uri))
    if doc_details.class_docstring is not None:
        g = add_edge(g, doc_uri, URIRef(prefixes['skos']+ 'definition'), Literal(doc_details.class_docstring), URIRef(docstrings_uri))
    if doc_details.ret_types is not None:
        g = add_edge(g, doc_uri, URIRef(prefixes['graph4code'] + 'ret_types'), Literal(doc_details.ret_types), URIRef(docstrings_uri))

    if g is None:
        print(f'Could not create a graph for: module {doc_details.module_name}, class: {doc_details.klass_name}, func: {doc_details.function_name}')
        return 0
    for t in g:
        num_triples+= 1
        # print(t)
    g.serialize(destination=out_triple_file, format='nquads', encoding='utf-8')
    return num_triples

def get_func_documentation(function_dic):
    if 'module_name' in function_dic:
        module_name = function_dic['module_name']
    elif 'module' in function_dic:
        module_name = function_dic['module']
    else:
        module_name = None
    doc_details = DocumentationDetails(module_name)
    if 'klass' in function_dic and 'function' in function_dic:
        doc_details.function_name = function_dic['function']
        doc_details.klass_name = function_dic["klass"]
        doc_details.type = 'method'
        if module_name is None:
            doc_details.module_name = str(doc_details.klass_name).split('.')[0]
            print(f'Module is none {doc_details.klass_name}: use {doc_details.module_name} instead! ')

    if 'function' in function_dic and 'klass' not in function_dic:
        doc_details.function_name = function_dic['function']
        doc_details.type = 'function'
        if module_name is None:
            doc_details.module_name = str(doc_details.function_name).split('.')[0]
            print(f'Module is none {doc_details.function_name}: use {doc_details.module_name} instead! ')

    if 'klass' in function_dic and 'function' not in function_dic:
        doc_details.klass_name = function_dic["klass"]
        doc_details.type = 'class'
        if module_name is None:
            doc_details.module_name = str(doc_details.klass_name).split('.')[0]
            print(f'Module is none {doc_details.klass_name}: use {doc_details.module_name} instead! ')

    if 'function_docstring' in function_dic:
        doc_details.function_docstring = function_dic['function_docstring']
    if 'class_docstring' in function_dic:
        doc_details.class_docstring = function_dic['class_docstring']

    if 'param_names' in function_dic:
        doc_details.param_names = function_dic["param_names"]
    if "param_map" in function_dic:
        doc_details.param_map = function_dic["param_map"]
    if "param_types" in function_dic:
        doc_details.param_types = function_dic["param_types"]
    if 'return_map' in function_dic:
        doc_details.return_map = function_dic['return_map']
    if 'ret_types' in function_dic:
        doc_details.ret_types = function_dic['ret_types']
    if 'base_classes' in function_dic:
        doc_details.base_classes = function_dic['base_classes']
    return doc_details

def merge_all_files(path, out_filename):
    file_list = os.listdir(path)
    outfile = open(out_filename, 'w')
    for filename in sorted(file_list):
        filepath = path + '/' + filename
        with open(filepath, 'r') as f:
            content = f.readlines()
            for line in content:
                outfile.write(line)
        try:
            os.remove(filepath)
        except:
            print(f'could not remove file: {filepath}')
    outfile.close()

def create_docstrings_graph(docstring_dir, out_dir):
    total_num_files = 0
    total_entries_processed = 0
    escaped_filed = 0
    total_num_triples = 0
    # out_triple_file = open(out_dir+'/docstrings_triples.nq', 'w')
    all_functions_found = set([])
    all_klasses_found = set([])
    all_methods_found = set([])

    for lib in os.listdir(docstring_dir):
        if lib.startswith('.'):
            print('Skip ', lib)
            continue
        source_path = os.path.join(docstring_dir, lib)
        if not os.path.isdir(source_path): #'../data/mods.22/pyvenv.cfg'
            continue
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
                if len(functions) == 0:
                    continue
                # if type(functions) == dict:
                #     functions = [functions]
                functions_to_loop = []
                if type(functions) == dict:
                    for func_name, function_dic in functions.items():
                        print('-------Loading {}: {}-----'.format(func_name, f))
                        functions_to_loop.append(function_dic)
                else:
                    for function_dic in functions:
                        functions_to_loop.append(function_dic)
                for function_dic in functions_to_loop:
                    if 'module' not in function_dic:
                        escaped_filed += 1
                        continue
                    doc_details = get_func_documentation(function_dic)
                    # filename = '{}_{}_{}_{}.q'.format(doc_details.module_name, doc_details.klass_name, doc_details.function_name, total_entries_processed)
                    filename = '{}.q'.format(total_entries_processed)
                    total_num_triples += output_documentation_triples(doc_details, out_dir+'/'+ filename)
                    total_entries_processed += 1
                    if doc_details.type == 'class':
                        all_klasses_found.add(doc_details.klass_name)
                    elif doc_details.type == 'function':
                        all_functions_found.add(doc_details.function_name)
                    else:
                        all_methods_found.add(doc_details.function_name)

    print(f'Could not parse {escaped_filed} out of {total_num_files} files')
    print(f'Total number of triples = {total_num_triples}')

    merge_all_files(out_dir, out_dir+'/docstrings_triples.nq')
    print(f'writing files to {out_dir}/classes_found.txt')
    file = open(out_dir + '/classes_found.txt', 'w')
    for klass in all_klasses_found:
        file.write(klass + '\n')
        # print(klass)
    file.close()
    file = open(out_dir + '/methods_found.txt', 'w')
    for method in all_methods_found:
        file.write(method + '\n')
        # print(method)
    file.close()
    file = open(out_dir + '/functions_found.txt', 'w')
    for func in all_functions_found:
        file.write(func + '\n')
        # print(func)
    file.close()

if __name__ == "__main__":
    # docstring_dir = ''
    # docstring_dir = '../data/docstrings-merge-15-22_sample2/'
    # out_dir = '../data/docstring_graph/' #''/data/analysis_snippets_w_graphs/'
    # class_map_file =
    parser = argparse.ArgumentParser(description='Hierarchy prediction based on embeddings')
    parser.add_argument('--docstring_dir', type=str, default='../data/docstrings-merge-15-22/',
                        help='location of docstrings directory')
    parser.add_argument('--out_dir', type=str, default='../data/docstring_graph/')
    parser.add_argument('--class_map_file', type=str, default='../type_inference_data/classes.map')

    args = parser.parse_args()

    with open(args.class_map_file) as f:
        lines = f.readlines()
    for line in lines:
        parts = re.split('\s+', line.strip())
        # print(parts)
        if len(parts) > 1 and parts[1] != parts[0]:
            class_map[parts[0]] = parts[1]
            class_map[parts[1]] = parts[0]

    docstring_dir = args.docstring_dir
    out_dir = args.out_dir

    try:
        shutil.rmtree(out_dir)
    except:
        traceback.print_exc(file=sys.stdout)
        pass
    os.mkdir(out_dir)

    create_docstrings_graph(docstring_dir, out_dir)
    print(f'skipped triples from lambda expressions: {num_lambda_exprs}')
    print(f'skipped triples due to space in URI: {entities_with_space}')

