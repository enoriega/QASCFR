import json

import matplotlib.pyplot as plt
from tqdm import tqdm

from enum import Enum

import pandas as pd
import seaborn as sns

class Result(Enum):
    FULL = 1
    PARTIAL = 2
    MISSING = 3

class Result2(Enum):
    RETRIEVED_F1 = 1
    RETRIEVED_F2 = 2
    NONE = 3



def evaluate(entry):
    levels = [10, 20, 30, 40, 50, 100, 1000, 2000, 3000, 4000, 5000]

    facts = set(entry['phrases'][3:4])

    f1 = entry['phrases'][2]
    f2 = entry['phrases'][3]

    res1 = entry['res1']
    res2 = entry['res2']

    ret = dict()

    # for level in levels:
    #     support = set(res2[:level])
    #     intersection = len(support & facts)
    #
    #     if intersection == 0:
    #         ret[level] = Result.MISSING
    #     elif intersection == 1:
    #         ret[level] = Result.PARTIAL
    #     else:
    #         ret[level] = Result.FULL

    for level in levels:
        support1 = set(res1[:level])
        support2 = set(res2[:level])

        if f1 in support1:
            if f2 in support2:
                ret[level] = Result.FULL
            else:
                ret[level] = Result.PARTIAL

        else:
            ret[level] = Result.MISSING

    return ret


def main(ir_results:str):
    coverage = dict()
    with open(ir_results) as f:
        for line in tqdm(f, desc='Processing data ...'):
            entry = json.loads(line)
            id = entry['id']
            res = evaluate(entry)
            coverage[id] = res

    keys = list(coverage.keys())
    raw_frame = pd.DataFrame([coverage[k] for k in keys], index=keys)
    aggregated_frame = raw_frame.apply(lambda c: c.value_counts(normalize=True))

    return raw_frame, aggregated_frame


def plot_distributions(frame):
    sns.set_theme()
    plt.figure()
    melted = frame.melt()
    melted.variable = melted.variable.map(str)
    sns.displot(melted, x='variable', hue='value', multiple='stack', bins=melted.variable.drop_duplicates())
    plt.show()



if __name__ == '__main__':
    frame, dist = main('query_results.jsonl')
    print(dist.describe())
    plot_distributions(frame)