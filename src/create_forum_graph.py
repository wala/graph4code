
from utils import read_stackoverflow_posts, build_elastic_search_index, create_stackoverflow_graph, elastic_search_setting
import sys, os
from elasticsearch import Elasticsearch
import subprocess, traceback
import shutil, argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Hierarchy prediction based on embeddings')
    parser.add_argument('--stackoverflow_in_dir', type=str, default='Directory where Stackoverflow/StackExchange data dump is',
                        help='location of docstrings directory')
    parser.add_argument('--docstring_dir', type=str, help='Directory of collected docstrings')
    parser.add_argument('--graph_output_dir', type=str, default='Output Directory for nq files')
    parser.add_argument('--pickled_files_out', type=str, default='intermediate directory for picked stackoverflow files')
    parser.add_argument('--delete_index', action='store_true')
    parser.add_argument('--build_index_only', action='store_true')
    parser.add_argument('--index_name', type=str, default='forum_index')
    parser.add_argument('--graph_main_prefix', type=str)

    args = parser.parse_args()

    # stackoverflow_in_dir = sys.argv[1]
    # docstring_dir = sys.argv[2]
    # stack_output_dir = sys.argv[3]
    # pickled_files_out = sys.argv[4]
    # delete_index = int(sys.argv[5])==1
    # graph_main_prefix = sys.argv[6]
    '''
    graph_main_prefix to use:
        "stackoverflow3" : "https://stackoverflow.com/questions/",
        "stats_stackexchange" : "https://stats.stackexchange.com/",
        "datascience_stackexchange" : "https://datascience.stackexchange.com/",
        "math_stackexchange" : "https://math.stackexchange.com/",
    '''

#     es = Elasticsearch([{'host': 'localhost', 'port': 9200}])
    es = Elasticsearch("https://localhost:9200",
                   ca_certs=os.path.join(os.environ['ES_HOME'], "http_ca.crt"), basic_auth=("elastic", os.environ['ES_PASSWORD']))
    load_posts_if_exists = True

    try:
        if args.delete_index:
            if es.indices.exists(index=args.index_name):
                print('Deleting index!!')
                proc = subprocess.Popen(["curl", "-XDELETE", "localhost:9200/" + args.index_name], stdout=subprocess.PIPE)
                (out, err) = proc.communicate()
                print(out)
        else:
            print(f'Delete index is disabled -- either will use already existing index {args.index_name}, or will recreate it!')
        shutil.rmtree(args.graph_output_dir)
        os.mkdir(args.graph_output_dir)
    except:
        traceback.print_exc(file=sys.stdout)
        pass

    rebuild_index = args.delete_index or not es.indices.exists(index=args.index_name)
    if rebuild_index:
        print('Create index: ', args.index_name)
        es.indices.create(
            index=args.index_name,
            body=elastic_search_setting)

    try:
        es.indices.delete(index='dummy_idx', ignore=[400, 404])
        es.indices.create(
            index='dummy_idx',
            body=elastic_search_setting)
    except:
        pass
    posts, postsVotes, question_answers = read_stackoverflow_posts(args.stackoverflow_in_dir, load_posts_if_exists, args.pickled_files_out)
    build_elastic_search_index(args.index_name, es, posts, postsVotes, question_answers, rebuild_index)

    if not args.build_index_only:
        create_stackoverflow_graph(args.index_name, es, args.docstring_dir, args.graph_output_dir, args.graph_main_prefix)

    print('stackexchange post extraction is done')
