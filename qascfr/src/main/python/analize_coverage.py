from collections import defaultdict
import json
import jsonlines
import pandas as pd
import ipdb

#%run src/main/python/read_graph.py
import read_graph

path = "/home/enrique/Downloads/QASC_Dataset/train.jsonl"

with jsonlines.open(path) as reader:
    train_data = list(reader)

train = {j['id']:j for j in train_data}

sentence_index = dict(sentence_index)




def preprocess_text(s):
    return s.replace(".", " .").replace(",", " ,")

sents = set(it.chain(x['fact1'], x['fact2']) for x in train_data)

sents = set(it.chain.from_iterable((x['fact1'], x['fact2']) for x in train_data))



keys = set(sentence_index.keys())

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
    if s in sentence:
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
    return len(nodes1 & nodes2) > 0

coverage = [is_inner_reachable(x) for x in train_data]
pd.Series(coverage).value_counts()

