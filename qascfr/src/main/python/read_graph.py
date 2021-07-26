import networkx as nx
import csv
from tqdm import tqdm
import itertools as it
from collections import defaultdict

def read_data():
    with open('../../../data/entity_codes.tsv') as f:
        reader = csv.reader(f, delimiter='\t')
        entities = {int(code): ent for ent, code in tqdm(reader, desc="Reading entity codes")}

    with open('../../../data/extractedEdges.tsv') as f:
        reader = csv.reader(f, delimiter='\t')
        edges = []
        for ix, (agent, obj, predicate, sentence) in tqdm(enumerate(reader), desc="Reading graph"):
            # if ix == 100000:
            #     break
            agent, obj = int(agent), int(obj)
            if obj != 19 and agent != 19:
                edges.append((entities[int(agent)], entities[int(obj)], predicate, sentence))

    with open('../../../data/question_endpoints.tsv') as f:
        reader = csv.reader(f, delimiter='\t')
        data = dict()
        for row in tqdm(reader, desc="Reading search endpoints"):
            key = row[0]
            question = row[1]
            answer = row[2]
            start_points = row[3].split("|")
            end_points = row[4].split("|")
            data[key] = (question, answer, start_points, end_points)

    return edges, data


def resolve_question(start_points, end_points, g):
    for start, end in it.product(start_points, end_points):
        if start != end:
            try:
                path = nx.shortest_path(g, start, end)
            except:
                pass
            else:
                edges = zip(path, path[1:])
                detailed_edges = list()
                for a, b in edges:
                    edge_data = g[a][b]
                    detailed_edges.append((a, b, edge_data['label'], edge_data['sent']))
                return detailed_edges
    return []


def load_graph(edges):
    # g = nx.DiGraph()
    sentence_index = defaultdict(list)
    for agent, obj, pred, sentence in tqdm(edges, desc="Loading graph"):
        # g.add_edge(agent, obj, label=pred, sent=sentence)
        sentence_index[sentence].append((agent, obj))

    return sentence_index


def compute_coverage(data, g):
    matched = 0
    matches = dict()
    for key, (question, answer, starts, ends) in tqdm(data.items(), desc="Searching for paths"):
        path = resolve_question(starts, ends, g)
        if len(path) > 0:
            matched += 1
            matches[key] = (question, answer, path)

    print(f"Matched {matched} out of {len(data)}")

    return matches, matched
