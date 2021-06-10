import itertools as it
import json
import os
import random
from collections import defaultdict

import pytorch_lightning as pl
import spacy
import torch
import torch.nn.functional as F
from pytorch_lightning.callbacks.early_stopping import EarlyStopping
from torch import nn
from torch.utils.data import DataLoader
from torch.utils.data import Dataset
from tqdm import tqdm

# Read the GT paths
DATASET_DIR = "/home/enrique/Downloads/QASC_Dataset"

nlp = spacy.load("en_core_web_sm")

def pre_process(text):
    text = text.lower()
    doc = nlp.make_doc(text)
    return " ".join(t.text for t in doc)

def load_paths(path):
    ret = list()
    with open(path) as f:
        for l in f:
            entry = json.loads(l)
            question = entry['question']['stem']
            choices = {c['label']:c['text'] for c in entry['question']['choices']}
            answer = choices[entry["answerKey"]]
            f1 = entry['fact1']
            f2 = entry['fact2']

            seq = (question, f1, f2, answer)
            ret.append(tuple(pre_process(t) for t in seq))

    return ret


def make_triplets(paths):

    positive_pairs = dict()
    # First do the positive pairs
    for path in tqdm(paths, desc='making positive pairs'):
        pairs = [(a, b) for a, b in zip(path, path[1:])]
        positive_pairs[path] = pairs

    negative_pairs = defaultdict(list)
    # Now do the negative pairs
    for path in tqdm(paths, desc='making negative pairs'):
        for other_path in random.sample(paths, 200):
            other_path = other_path[1:] # Drop the question
            # Only consider paths with disjoint terms
            if len(set(path) & set(other_path)) == 0:
                for a in path:
                    for b in other_path:
                        negative_pairs[a].append(b)
                        negative_pairs[b].append(a)

    # Now generate the triplets
    return it.chain.from_iterable(((a, b, c) for c in (negative_pairs[a] if len(negative_pairs[a]) < 100 else random.sample(negative_pairs[a], 100))) for a, b in it.chain.from_iterable(positive_pairs.values()))

def build_vocab(*triplets):
    terms = set()
    for phrase in tqdm(it.chain.from_iterable(it.chain.from_iterable(triplets)), desc="Building Vocab ..."):
       terms |= set(phrase.split())

    return {term:ix for ix, term in enumerate(terms)}


class TripletDataset(Dataset):

    def __init__(self, triplets):
        self.triplets = list(triplets)

    def __len__(self):
        return len(self.triplets)

    def __getitem__(self, idx):
        return self.triplets[idx], "LABEL"


class QASCEntailmentEncoder(pl.LightningModule):
    def __init__(self, vocab):
        super().__init__()
        self.vocab = dict(vocab)

        # Computational graph definition
        self.embeds = nn.EmbeddingBag(len(self.vocab), 200)  # Embedding matrix. For now, is trainable

        # Encoder from embeddings to the new vector space
        self.encoder = nn.Sequential(
            nn.Linear(200, 200),
            nn.ReLU(),
            nn.Linear(200, 100),
        )

    def forward(self, phrases):
        # We get phrases as input, embbed them and project them into the learnt vector space
        batch = list()
        for phrase in phrases:
            codes = torch.tensor([[self.vocab[term] for term in phrase.split()]], dtype=torch.long).to(self.device)
            avg = self.embeds(codes)
            batch.append(avg)

        avg_embeddings = torch.vstack(batch)

        result = self.encoder(avg_embeddings)

        return result

    def training_step(self, batch, batch_idx):
        # training_step defined the train loop.
        # It is independent of forward
        (anchors, pos, neg), _ = batch

        new_anchors = self(anchors)
        new_pos = self(pos)
        new_neg = self(neg)

        loss = F.triplet_margin_loss(new_anchors, new_pos, new_neg, margin=1)
        # Logging to TensorBoard by default
        self.log('train_loss', loss)
        return loss

    def validation_step(self, batch, batch_idx):
        # training_step defined the train loop.
        # It is independent of forward
        (anchors, pos, neg), _ = batch

        new_anchors = self(anchors)
        new_pos = self(pos)
        new_neg = self(neg)

        loss = F.triplet_margin_loss(new_anchors, new_pos, new_neg, margin=1)
        # Logging to TensorBoard by default
        self.log('val_loss', loss)
        return loss

    def configure_optimizers(self):
        optimizer = torch.optim.Adam(self.parameters(), lr=1e-3)
        return optimizer


if __name__ == "__main__":
    train_paths = load_paths(os.path.join(DATASET_DIR, 'train.jsonl'))
    test_paths = load_paths(os.path.join(DATASET_DIR, 'dev.jsonl'))

    train_triplets = set(tqdm(make_triplets(train_paths), desc='Making training triplets'))
    test_triplets = set(tqdm(make_triplets(test_paths), desc='Making training triplets'))

    # Remove the intersecting triplets to avoid leaking data
    shared = train_triplets & test_triplets
    train_triplets -= shared
    test_triplets -= shared

    all_triplets = train_triplets | test_triplets

    # Build the vocabulary
    voc = build_vocab(train_triplets, test_triplets)

    # Build the torch datasets
    training_dataset = TripletDataset(train_triplets)
    testing_dataset = TripletDataset(test_triplets)

    # Make the data loaders
    train_dataloader = DataLoader(training_dataset, batch_size=100, shuffle=True, num_workers=16)
    test_dataloader = DataLoader(testing_dataset, batch_size=100, shuffle=False, num_workers=16)

    # Instantiate the model, trainer, etc

    trainer = pl.Trainer(gpus=1, callbacks=[EarlyStopping(monitor='val_loss')])
    model = QASCEntailmentEncoder(voc)
    trainer.fit(model, train_dataloader, test_dataloader)

