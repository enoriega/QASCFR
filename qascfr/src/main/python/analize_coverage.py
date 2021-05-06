from collections import defaultdict
import json
import jsonlines
import pandas as pd
import itertools as it
import read_graph


def preprocess_text(s):
    return s.replace(".", " .").replace(",", " ,")


def is_in_index(s):
    if s in sentence_index:
        return True
    elif preprocess_text(s) in sentence_index:
        return True
    elif s.replace('.', '') in sentence_index:
        return True
    else:
        return False


def get_endpoints(s):
    if s in sentence_index:
        return sentence_index[s]
    elif preprocess_text(s) in sentence_index:
        return sentence_index[preprocess_text(s)]
    elif s.replace('.', '') in sentence_index:
        return sentence_index[s.replace('.', '')]
    else:
        return []


def is_inner_reachable(question):
    e1 = get_endpoints(question['fact1'])
    e2 = get_endpoints(question['fact2'])
    nodes1 = set(it.chain.from_iterable(e1))
    nodes2 = set(it.chain.from_iterable(e2))

    if len(e1) == 0:
        return "Not found", question['fact1']
    elif len(e2) == 0:
        return "Not found", question['fact2']
    elif len(nodes1 & nodes2) == 0:
        return "No intersect", (set(it.chain.from_iterable(e1)), set(it.chain.from_iterable(e2)))
    else:
        return True, None


def print_instance(instance, endpoints):
    id = instance['id']
    e1, e2 = endpoints[id]

    choices = {c['label']: c['text'] for c in instance['question']['choices']}

    q = instance['question']['stem']
    a = choices[instance['answerKey']]

    f1, f2 = instance['fact1'], instance['fact2']

    print(f'Q: {q}', f'A: {a}', sep=" -- ")
    print(f1, e1, sep=" -- ")
    print(f2, e2, sep=" -- ")


if __name__ == "__main__":
    edges, data = read_graph.read_data()

    sentence_index = read_graph.load_graph(edges)

    path = "/home/enrique/Downloads/QASC_Dataset/train.jsonl"

    with jsonlines.open(path) as reader:
        train_data = list(reader)

    train = {j['id']: j for j in train_data}

    sentence_index = dict(sentence_index)

    keys = set(sentence_index.keys())

    sents = set(it.chain.from_iterable((x['fact1'], x['fact2']) for x in train_data))

    # coverage = [is_inner_reachable(x) for x in train_data]
    # print(pd.Series(coverage).value_counts())

    no_extractions = list()
    no_intersection = list()
    covered = list()

    missing_sents = list()
    disjoint_sets = {}

    for x in train_data:
        status, aux = is_inner_reachable(x)
        container = None
        if status == "Not found":
            container = no_extractions
            missing_sents.append(aux)
        elif status == "No intersect":
            container = no_intersection
            disjoint_sets[x['id']] = aux
        else:
            container = covered

        container.append(x)

# Sentence without extractions
# Empty intersection
