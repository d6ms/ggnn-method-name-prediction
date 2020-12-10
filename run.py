
import random

import numpy as np
import torch

from data import Vocabulary, GraphDataloader
from models import CodeGGNN


def fix_seeds(seed=0):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.deterministic = True


if __name__ == '__main__':
    fix_seeds(0)
    graph_path = 'data/training/cargotracker.graph'
    vocab_path = 'data/training/cargotracker.vocab'
    target_path = 'data/training/cargotracker.target'

    with open(vocab_path, mode='r') as f:
        word_vocab = Vocabulary([line.split()[0] for line in f.readlines()])
    with open(target_path, mode='r') as f:
        target_vocab = Vocabulary([line.split()[0] for line in f.readlines()])

    dataloader = GraphDataloader(graph_path, word_vocab, target_vocab)
    model = CodeGGNN(len(word_vocab), 128)
    for am, vertices, target in dataloader:
        y = model(am, vertices)
        print(y)
        exit()

