import collections
import json
import os
from pyvis.network import Network
import sys

Node = collections.namedtuple('Node', 'index source is_import paths args expr data_flow_edges control_flow_edges constant_edge')

# global variable to keep track of nodes.  Note that this variable is changed by every call to add a new graph
# change if this not a behavior you want


def parse_wala_into_graph(data, add_args=False):
    df_edges = []
    cf_edges = []
    nodes = {}

    expr_index = 1
    for node in data['turtle_analysis']:
        if node is None:
            continue
        if node['path'][-1] == 'expr':
            nodes[node['nodeNumber']] = 'expression' + str(expr_index)
            expr_index += 1
        else:
            nodes[node['nodeNumber']] = '.'.join(node['path'])

    for node in data['turtle_analysis']:
        if node is None:
            continue
        if node['path'][-1] == 'expr':
            src = nodes[node['nodeNumber']]
            df_edges.append({'source':src, 'target': node['op'], 'label': 'op'})
        else:
            src = '.'.join(node['path'])

        if 'edges' in node:
            if 'immediatelyPrecedes' in node['edges']:
                for dest in node['edges']['immediatelyPrecedes']:
                    cf_edges.append({'source': src, 'target': nodes[int(dest)]}) 
            if 'flowsTo' in node['edges']:
                for label in node['edges']['flowsTo']:
                    for dest in node['edges']['flowsTo'][label]:
                        df_edges.append({'source': src, 'target': nodes[int(dest)], 'label': label})
    return (nodes, df_edges, cf_edges)


def get_subgraph_as_html(edges, source_turtles = None, height = "500px", width = "500px"):
    graph = Network(height=height, width=width, notebook=True)

    seen_nodes = {}
    adjust_weights(edges)

    for e in edges:
        src = e['source']
        tgt = e['target']
        label = None
        weight = None
        if 'weight' in e:
            weight = e['weight']
        if 'label' in e:
            label = e['label']
        edge_color = None
        if 'edge_color' in e:
            edge_color = e['edge_color']
        src_tooltip = None
        target_tooltip = None
        if source_turtles:
            src_tooltip = source_turtles[src] if src in source_turtles else None
            target_tooltip = source_turtles[tgt] if tgt in source_turtles else None

        if src not in seen_nodes:
            seen_nodes[src] = 1
            source_color = None
            if 'source_color' in e:
                source_color = e['source_color']
            graph.add_node(src, label=src, title = src_tooltip, color = source_color)
        if tgt not in seen_nodes:
            seen_nodes[tgt] = 1
            tgt_color = None
            if 'target_color' in e:
                tgt_color = e['target_color']
            graph.add_node(tgt, label=tgt, title = target_tooltip, color = tgt_color)
        if label:
            graph.add_edge(src, tgt, label=label, arrows='to', color = edge_color, width = weight)
        else:
            graph.add_edge(src, tgt, arrows='to', color=edge_color, width = weight)
    # graph.enable_physics(True)

    return graph


def adjust_weights(edges):
    max = 0
    assert len(edges) > 0
    if 'weight' not in edges[0]:
        return
    for e in edges:
        w = e['weight']
        if w > max:
            max = w
    for e in edges:
        e['weight'] = int((e['weight'] / max) * 10)

def show_analysis(data):
    nodes, data_flow_edges, control_flow_edges = parse_wala_into_graph(data)
    return get_subgraph_as_html(data_flow_edges)

def main():
    with open(sys.argv[1]) as f:
        data = json.load(f)
        show_analysis(data)
        
if __name__ == "__main__":
    main()
