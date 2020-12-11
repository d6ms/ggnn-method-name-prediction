import os
import random
import logging
from datetime import datetime

import numpy as np
import torch

import config as cfg


def fix_seeds(seed=0):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.deterministic = True

def create_dirs():
    for dir in [cfg.MODEL_PATH, cfg.LOG_PATH]:
        if not os.path.exists(dir):
            os.makedirs(dir)

def configure_logger(name: str):
    timestamp = datetime.now().strftime('%Y-%m-%d-%H-%M-%S')
    logging.basicConfig(
        filename=f'{cfg.LOG_PATH}/{name}-{timestamp}.log', 
        level=logging.INFO,
        format='%(asctime)s [%(levelname)s] %(message)s'
    )
