from triplet_encoder import *
from ir_baseline import evaluate, plot_distributions

import pandas as pd
import spacy
import torch



nlp = spacy.load("en_core_web_lg")
nlp.vocab.set_vector('@OOV@', vector=np.zeros(nlp.vocab.vectors.shape[1]))

spacyids = {term:nlp.vocab.strings[term] for term in nlp.vocab.strings}
oovid = nlp.vocab.strings['@OOV@']
voc = {term:nlp.vocab.vectors.key2row[id] if id in nlp.vocab.vectors.key2row else nlp.vocab.vectors.key2row[oovid] for term, id in spacyids.items()}

ckpt = torch.load('version_6/checkpoints/epoch=6-step=167.ckpt', map_location=torch.device('cpu'))
encoder = QASCEntailmentEncoder(voc, torch.FloatTensor(nlp.vocab.vectors.data))

encoder.load_state_dict(ckpt['state_dict'])
encoder.eval()

@torch.no_grad()
def rerank_entry(entry):
    question = entry['phrases'][0]
    answer = entry['phrases'][1]
    f1 = entry['phrases'][2]
    f2 = entry['phrases'][3]

    res1 = entry['res1']

    e_question = encoder([question])
    e_res1 = encoder(res1)

    e_question = e_question.squeeze()

    e_question /= e_question.norm()
    e_res1 /= e_res1.norm(dim=0)

    cos_sim = e_res1 @ e_question

    sorted_ixs = torch.argsort(cos_sim, descending=True)

    new_res1 = [res1[ix] for ix in sorted_ixs]

    entry['res1'] = new_res1
    return entry


def main(ir_results:str):
    coverage = dict()
    with open(ir_results) as f:
        for line in tqdm(f, desc='Processing data ...'):
            entry = json.loads(line)
            id = entry['id']
            reranked_entry = rerank_entry(entry)
            res = evaluate(reranked_entry)
            coverage[id] = res

    keys = list(coverage.keys())
    raw_frame = pd.DataFrame([coverage[k] for k in keys], index=keys)
    aggregated_frame = raw_frame.apply(lambda c: c.value_counts(normalize=True))

    return raw_frame, aggregated_frame


if __name__ == '__main__':
    frame, dist = main('query_results.jsonl')
    plot_distributions(frame)