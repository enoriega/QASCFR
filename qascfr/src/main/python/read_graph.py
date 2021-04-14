import networkx as nx
import csv
from tqdm import tqdm
import itertools as it
from networkx import NetworkXNoPath

with open('entity_codes.tsv') as f:
    reader = csv.reader(f, delimiter='\t')
    entities = {int(code):ent for ent, code in reader}

with open('extractedEdges.tsv') as f:
    reader = csv.reader(f, delimiter='\t')
    edges = []
    for agent, obj, predicate in reader:
        agent, obj = int(agent), int(obj)
        if obj != 19 and agent != 19:
            edges.append((entities[int(agent)], entities[int(obj)], predicate))

with open('question_endpoints.tsv') as f:
    reader = csv.reader(f, delimiter='\t')
    data = dict()
    for row in reader:
        question = row[0]
        answer = row[1]
        start_points = row[2].split("|")
        end_points = row[3].split("|")
        data[question] = (answer, start_points, end_points)

def resolve_question(start_points, end_points, g):
    for start, end in it.product(start_points, end_points):
        if start != end:
            try:
                path = nx.shortest_path(g, start, end)
            except:
                pass
            else:
                return path
    return []

g = nx.DiGraph()
for agent, obj, pred in tqdm(edges, desc="Loading graph"):
    g.add_edge(agent, obj, label=pred)

matched = 0
matches = dict()
for question, (answer, starts, ends) in tqdm(data.items(), desc="Searching for paths"):
    path = resolve_question(starts, ends, g)
    if len(path) > 0:
        matched += 1
        matches[question] = (answer, path)

print(f"Matched {matched} out of {len(data)}")

