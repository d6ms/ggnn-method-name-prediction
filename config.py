import os

import torch

BASE_PATH = os.path.dirname(os.path.realpath(__file__))
DATA_PATH = f'{BASE_PATH}/data/java-large.preprocessed'
MODEL_PATH = f'{BASE_PATH}/models'
LOG_PATH = f'{BASE_PATH}/logs'

BATCH_SIZE = 256
CHUNK_SIZE = 10
SAVE_EVERY = 3000

MAX_WORD_PARTS = 5
MAX_VERTICES = 500
MAX_EDGE_TYPES = 1

EMBEDDING_DIM = 128
PROPAGATION_STEPS = 8

DEVICE = torch.device('cuda:0' if torch.cuda.is_available() else 'cpu')
