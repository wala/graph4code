import inspect
import re
import pkgutil
import sys
from inspect import signature
import os
import json
import importlib
import traceback
from elasticsearch import Elasticsearch

from sphinxcontrib.napoleon import Config
from sphinxcontrib.napoleon.docstring import NumpyDocstring, GoogleDocstring
import re



"""
File that usese napolean to try and parse the different parts of a docstring, to structure 
the parameters, return values etc of a docstring out 
"""

config = Config(napoleon_use_param=True, napoleon_use_rtype=True)


def parse_docstring_into_restructured_text_google(docstring):
    lines = GoogleDocstring(docstring, config).lines()
    return lines


def parse_docstring_into_restructured_text_numpy(docstring):
    lines = NumpyDocstring(docstring, config).lines()
    return lines


def parse_docstring_into_restructured_text(docstring):
    try:
        lines = NumpyDocstring(docstring, config).lines()
    except:
        lines = GoogleDocstring(docstring, config).lines()
    if lines:
        return parse_rst(lines)
    else:
        return None, None, None, None


# lines from the RST format are highly stylized, and it looks like
# using some other docutils parser to parse the RST just gives us more grief.
# so parse this format instead.
def parse_rst(lines):
    curr_param = None
    curr_par_doc = None
    curr_type_param = None
    curr_par_type = None
    return_doc = None
    return_type = None

    param_to_doc = {}
    param_to_type = {}

    function_doc = ''
    # gather up function doc
    for index, line in enumerate(lines):
        if not line.startswith(':param'):
            function_doc = function_doc + '\n' + line
        elif line.startswith(':param'):
            break
    lines = lines[index:]

    for line in lines:
        if line.startswith(':param '):
            new_param = re.findall(':param ([^:].*):', line)[0]
            if new_param != curr_param:
                if curr_param:
                    param_to_doc[curr_param.strip()] = curr_par_doc
                if curr_type_param:
                    param_to_type[curr_type_param.strip()] = curr_par_type
                curr_param = new_param
            curr_par_doc = re.findall(':param [^:]*:(.*)', line)[0]
        elif line.startswith(':type '):
            curr_type_param = re.findall(':type([^:]*):', line)[0]
            curr_par_type = re.findall(':type [^:]*:(.*)', line)[0]
        elif line.startswith(':returns'):
            if curr_param:
                param_to_doc[curr_param.strip()] = curr_par_doc
            if curr_type_param:
                param_to_type[curr_type_param.strip()] = curr_par_type
            return_doc = re.findall(':returns:(.*)', line)[0]
        elif line.startswith(':rtype'):
            return_type = re.findall(':rtype:(.*)', line)[0]
            break
        elif curr_par_doc:
                curr_par_doc = curr_par_doc + '\n' + line
    """
    for p in param_to_doc:
        print("parameter:" + p)
        print(param_to_doc[p])
    for p in param_to_type:
        print("parameter:" + p + " type")
        print(param_to_type[p])
    print("return doc:" + return_doc)
    print("return type:" + return_type)
    """

    if return_doc and return_type:
        return function_doc, param_to_doc, param_to_type, {'doc': return_doc, 'type': return_type}
    else:
        return function_doc, param_to_doc, param_to_type, None

indexname = 'return_doc'

# global dict to hold all functions obtained so far, so we can patch the descriptions
# later
method_descriptions = {}

es = Elasticsearch("https://localhost:9200",
                   ca_certs=os.path.join(os.environ['ES_HOME'], "http_ca.crt"), basic_auth=("elastic", os.environ['ES_PASSWORD']))
print('starting')
cache_of_indexed_functions = {}


def get_class_name(c):
    if str(c) != "<class 'inspect._empty'>":
        return re.sub(r"[<>']", '', str(c)).replace('class', '').strip()
    else:
        return None


def inspect_all(f):
    if inspect.ismodule(f):
        return inspect_module(f)
    elif inspect.isclass(f):
        return inspect_class(f)


def inspect_module(f):
    module_to_classes = {}
    module_to_classes[f] = []
    for c_name, c in inspect.getmembers(f, inspect.isclass):
        module_to_classes[f].append(inspect_class(c))
    return module_to_classes


def inspect_class(f):
    class_to_methods = {}
    class_to_methods[f] = []
    for m_name, m in inspect.getmembers(f, inspect.isfunction):
        class_to_methods[f].append((m, m_name, inspect.getfullargspec(m), inspect.getdoc(m)))
    for m_name, m in inspect.getmembers(f, inspect.ismethod):
        class_to_methods[f].append((m, m_name, inspect.getfullargspec(m), inspect.getdoc(m)))

    return class_to_methods

def add_types(anns):
    l = []
    for t in anns:
        s = get_class_name(str(t))
        if s:
            l.append(s)
    return l


def add_to_method_desc(key, value):
    if key in method_descriptions:
        desc = method_descriptions[key]
        # python inspect seems to load the same function across modules and worse yet, produce less information
        # about a function in some modules.  String length is a crude proxy for keeping the 'more complete' object
        if len(str(value)) > len(str(desc)):
            method_descriptions[key] = value
    else:
        method_descriptions[key] = value


def extract_function(f, ret, name, clazz=None, mod=None):
    overall_doc = inspect.getdoc(f)

    ret['function_docstring'] = overall_doc

    try:
        sig = signature(f)

        if sig.parameters.values():
            ann = [p.annotation for p in sig.parameters.values()]
            param_types = add_types(ann)
            if len(param_types) > 0:
                ret['param_types'] = param_types
        if sig.return_annotation:
            ret_types = get_class_name(sig.return_annotation)
            if ret_types:
                ret['ret_types'] = ret_types

        param_names = list(sig.parameters)
        if sig.parameters.items():
            param_defaults = {
                k: v.default
                for k, v in sig.parameters.items()
                if v.default is not inspect.Parameter.empty and not isinstance(v.default, object)
            }
            for i in param_defaults:
                if isinstance(param_defaults[i], tuple):
                    param_defaults[i] = list(param_defaults[i])

            if len(param_defaults) > 0:
                ret['param_defaults'] = param_defaults

        if param_names and len(param_names) > 0:
            if 'self' in param_names:
                param_names.remove('self')
            if len(param_names) > 0:
                ret['param_names'] = param_names
    except:
        exc_type, exc_value, exc_traceback = sys.exc_info()
        print("*** error: print_tb:")
        traceback.print_tb(exc_traceback, limit=3, file=sys.stdout)
        pass
    if overall_doc is not None:
        if clazz:
            key = clazz + '.' + name
        else:
            key = name

        method_doc, param_doc_map, param_types_map, return_map = \
            parse_docstring_into_restructured_text(overall_doc)

        ret['function_docstring'] = method_doc
        if param_doc_map:
            param_map = create_parameter_map(param_doc_map, param_types_map, key)

            if param_map:
                ret['param_map'] = param_map
            if return_map:
                create_returns_map(return_map, name, clazz)
                ret['return_map'] = return_map
            if method_doc != "":
                ret['function_docstring'] = method_doc

    ret['module'] = mod
    ret['function'] = name


def inspect_module_sub_package(module, mod, scoping_mod, all_classes, is_base = False):
    modules = inspect_all(module)
    m = inspect.getmembers(module, inspect.isfunction)

    for function in m:
        ret = {}
        extract_function(function[1], ret, scoping_mod + '.' + function[1].__name__, mod)
        add_to_method_desc(scoping_mod + '.' + function[1].__name__, ret)

    for _, classes in modules.items():
        for c in classes:
            for clazz, methods in c.items():
                if get_class_name(str(clazz)).split('.')[0] != scoping_mod.split('.')[0]:
                    x = get_class_name(str(clazz)).split('.')[0]
                else:
                    x = mod

                all_classes[get_class_name(str(clazz))] = 1
                class_doc = clazz.__doc__
                ret = {}
                ret['module'] = x
                ret['klass'] = get_class_name(str(clazz))
                if isinstance(class_doc, str):
                    ret['class_docstring'] = class_doc
                # clazz.__bases__ should not be null
                ret['base_classes'] = [get_class_name(str(c)) for c in clazz.__bases__]

                add_to_method_desc(get_class_name(str(clazz)), ret)

                # for classes in the base module, another reference is directly from the module,
                # which of course does not exist in the code. E.g. pandas.DataFrame.  Add these in as well
                # KAVITHA_TODO I think this code is wrong - its not just base modules that have this.  Basically any
                # module has this issue.  Need to check that this bit of code can be eliminated by the next bit of code
                additional_class_names = []
                """
                if is_base:
                    ret = {}
                    ret['module'] = x
                    cname = x + '.' + get_class_name(str(clazz)).split('.')[-1]
                    all_classes[cname] = 1
                    ret['klass'] = cname
                    ret['base_classes'] = [get_class_name(str(c)) for c in clazz.__bases__]
                    if isinstance(class_doc, str):
                        ret['class_docstring'] = class_doc
                    add_to_method_desc(cname, ret)
                    additional_class_names.append[cname]
                """

                # in cases like RandomForestClassifier, the scoping mod is different from the actual class name
                # need to add a reference to this class as well
                if scoping_mod != get_class_name(str(clazz))[0:-1]:
                    ret = {}
                    ret['module'] = x
                    cname = scoping_mod + '.' + get_class_name(str(clazz)).split('.')[-1]
                    all_classes[cname] = 1
                    ret['klass'] = cname
                    ret['base_classes'] = [get_class_name(str(c)) for c in clazz.__bases__]
                    if isinstance(class_doc, str):
                        ret['class_docstring'] = class_doc
                    add_to_method_desc(cname, ret)
                    additional_class_names.append(cname)

                for method in methods:
                    def add_method(m, c):
                        m = method[1]
                        ret = {}
                        extract_function(method[0], ret, m, get_class_name(str(clazz)))
                        ret['module'] = x
                        ret['klass'] = c
                        key = c + '.' + m
                        add_to_method_desc(key, ret)
                    add_method(m, get_class_name(str(clazz)))
                    for c in additional_class_names:
                        add_method(m, c)




def create_returns_map(return_map, func, clazz):

    val = return_map['type']
    if val is not None:
        if 'shape' in val:
            shape = find_shape(val, False)
            return_map['dimensionality'] = shape
    if clazz:
        key = clazz + '.' + func
    else:
        key = func
    if key not in cache_of_indexed_functions:
        if clazz:
            doc = {'title': key, 'function': func, 'return_type': True, 'klass': clazz, 'content': return_map['type']}
        else:
            doc = {'title': key, 'function': func, 'return_type': True, 'content': return_map['type']}
        es.index(index=indexname, body=doc)
        cache_of_indexed_functions[key] = 1

    return return_map


def find_optional(param_str):
    return param_str.find('optional') > -1


def find_shape(param_str, first=True):
    if first:
        pattern = r'shape\s*=?\s*[\(\[{](.*)[\)\]}]'
    else:
        pattern = r'[\(\[{](.*)[\)\]}]'
    shapes = re.findall(pattern, param_str)
    dims = 0
    if shapes is not None and len(shapes) > 0:
        dimensions = shapes[0].split(',')
        dims = len(dimensions)
        if len(dimensions) > 1 and dimensions[len(dimensions) - 1] == '':
            dims -= 1
    return dims


def create_parameter_map(param_docs, param_doc_types, key):
    param_map = {}

    for p in param_docs:
        param_obj = {}
        param_obj['name'] = p
        param_obj['param_doc'] = param_docs[p]
        if p in param_doc_types:
            t = param_doc_types[p]
            if t is not None:
                param_obj['type'] = t
                key_param = key + '.' + p
                if key_param not in cache_of_indexed_functions:
                    doc = {'title': key, 'param_name': p, 'content': t}
                    es.index(index=indexname, body=doc)
                    cache_of_indexed_functions[key_param] = 1

            opt = find_optional(t)
            if opt:
                param_obj['optional'] = opt

            if 'shape' in t:
                shapes = []
                prev = 0
                for m in re.finditer('[\)\]}]', t):
                    shapes.append(t[prev:m.end()])
                    prev = m.end() + 1

                if len(shapes) == 1:
                    param_obj["dimensionality"] = [find_shape(t)]
                elif len(shapes) > 1:
                    dims = []
                    for i, m in enumerate(shapes):
                        first = True
                        if i > 0:
                            first = False
                        dim = find_shape(m, first)
                        if dim > 0:
                            dims.append(dim)
                    param_obj["dimensionality"] = list(set(dims))

        param_map[p] = param_obj
    return param_map


def write_json(ret_json, path, modname):
    try:
        json.dumps(ret_json)
    except:
        print(ret_json)
        raise RuntimeError("JSON is invalid")

    with open(os.path.join(path, modname + '.json'), 'w') as f:
        json.dump(ret_json, f, indent=4)

def debug(ret_json):
    try:
        for r in ret_json:
            print(r)
            json.dumps(r)
    except:
        raise RuntimeError("JSON is invalid")


def get_pure_class_or_function_query(c, key_terms=None, number_of_matches = None):
    should_clauses = []
    must_clauses = []

    for term in c.split('.'):
        should_clauses.append({"match": {"content": term}})
    key_term = c.split('.')[-1]
    must_clauses.append({"match": {"content": key_term}})
    if key_terms:
        must_clauses.append({"match": {"content": key_terms}})
    query = {
        "from": 0, "size": 5000,
        "query": {
            "bool": {
                "must": [],
                "should": []
            }
        }
    }
    query['query']['bool']['should'] = should_clauses
    query['query']['bool']['must'] = must_clauses
    if not number_of_matches:
        query['query']['bool']["minimum_should_match"] = len(should_clauses) - 1
    else:
        query['query']['bool']["minimum_should_match"] = 1

    return query


def patch_types(all_classes):
    if not es.indices.exists(index=indexname):
        return
    # add base types
    all_classes['str'] = 1
    all_classes['string'] = 1
    all_classes['integer'] = 1
    all_classes['int'] = 1
    all_classes['bool'] = 1
    all_classes['boolean'] = 1
    all_classes['float'] = 1
    all_classes['list'] = 1
    all_classes['tuple'] = 1
    all_classes['iterator'] = 1
    all_classes['map'] = 1
    all_classes['set'] = 1
    all_classes['array'] = 1

    for c in all_classes:
        res = es.search(index=indexname, body=get_pure_class_or_function_query(c, None, 1))
        if len(res['hits']['hits']) > 0:
            for res in res['hits']['hits']:
                key = res['_source']['title']
                if key not in method_descriptions:
                    print('WARNING: key not found:' + key)
                    continue
                desc = method_descriptions[key]
                if 'param_name' in res['_source']:
                    assert 'param_map' in desc
                    param_map = desc['param_map']
                    p = res['_source']['param_name']
                    if p in param_map:
                        if 'inferred_type' in param_map[p]:
                            param_map[p]['inferred_type'].append(c)
                        else:
                            param_map[p]['inferred_type'] = [c]
                elif 'return_type' in res['_source']:
                    assert 'return_map' in desc, desc
                    return_map = desc['return_map']
                    if c == 'boolean':
                        c = 'bool'
                    if c == 'integer':
                        c = 'int'
                    if c == 'string':
                        c = 'str'
                    if 'inferred_type' in return_map:
                        return_map['inferred_type'].append(c)
                    else:
                        return_map['inferred_type'] = [c]


def main():

    p = sys.argv[1]
    p = p.strip()
    path = os.path.join(sys.argv[2], p)
    all_classes = {}

    try:
        package = importlib.import_module(p)
        print('loaded:' + p)
        if not os.path.isdir(path):
            os.mkdir(path)
        inspect_module_sub_package(package, p.strip(), p.strip(), all_classes, True)

        for importer, modname, ispkg in pkgutil.walk_packages(path=package.__path__,
                                                              prefix=package.__name__ + '.',
                                                              onerror=lambda x: print(x)):
            try:
                if '.tests.' in modname:
                    continue
                print(modname)
                module = importer.find_module(modname).load_module(modname)
                inspect_module_sub_package(module, p.strip(), modname, all_classes)

            except:
                exc_type, exc_value, exc_traceback = sys.exc_info()
                print("*** error: print_tb:")
                traceback.print_tb(exc_traceback, limit=3, file=sys.stdout)
                pass
        #print(all_classes)
        patch_types(all_classes)
        #print(method_descriptions)
        if len(method_descriptions) > 0:
            write_json(list(method_descriptions.values()), path, p)

    except ModuleNotFoundError:
        print("failed to load module" + p)
        exc_type, exc_value, exc_traceback = sys.exc_info()
        print("*** error: print_tb:")
        traceback.print_tb(exc_traceback, limit=3, file=sys.stdout)

    #except:
        print("generic error")
        exc_type, exc_value, exc_traceback = sys.exc_info()
        print("*** error: print_tb:")
        traceback.print_tb(exc_traceback, limit=3, file=sys.stdout)

    es.indices.delete(index=indexname, ignore=[400, 404])


if __name__ == "__main__":
    main()


