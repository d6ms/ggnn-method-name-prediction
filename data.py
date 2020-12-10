import random
from collections import deque
from typing import Iterable

import numpy as np
import torch

import config as cfg

class Vocabulary(object):

    def __init__(self, words: Iterable[str]):
        self.word2idx = {word: i for i, word in enumerate(['<unk>', '<pad>', *words])}
        self.idx2word = {v: k for k, v in self.word2idx.items()}
    
    def lookup_idx(self, word):
        if word not in self.word2idx:
            return 0
        else:
            return self.word2idx[word]

    def lookup_word(self, idx):
        if idx not in self.idx2word:
            return '<unk>'
        else:
            return self.idx2word[idx]

    def __len__(self):
        return len(self.word2idx)


class GraphDataloader(object):

    def __init__(self, data_path: str, word_vocab: Vocabulary, target_vocab: Vocabulary):
        self.data_path = data_path
        self.word_vocab = word_vocab
        self.target_vocab = target_vocab
        self.chunks = deque()
        with open(data_path, mode='r') as f:
            self.n_data = sum(1 for _ in f)

    def __len__(self):
        return self.n_data // cfg.BATCH_SIZE

    def __iter__(self):
        self.data = self.__generator()
        return self

    def __next__(self):
        samples = self.__next_batch_samples()
        am, vertices, target = map(list, zip(*samples))
        return torch.LongTensor(am), torch.LongTensor(vertices), torch.LongTensor(target)

    def __generator(self):
        with open(self.data_path, mode='r') as f:
            while True:
                head = f.readline()
                if not head:
                    break
                method_name, num_vertices = head.rstrip().split()
                num_vertices = int(num_vertices)
                vertices = f.readline().rstrip().split()
                edges = [list(map(int, f.readline().rstrip().split())) for _ in range(num_vertices)]
                f.readline()
                yield method_name, num_vertices, vertices, edges

    def __next_batch_samples(self):
        if len(self.chunks) == 0:
            self.__load_chunks()
        return self.chunks.popleft()

    def __pad(self, arr, length):
        if len(arr) >= length:
            return arr[:length]
        else:
            return arr + ['<pad>'] * (length - len(arr))
    
    def __load_chunks(self):
        samples = []
        for _ in range(cfg.BATCH_SIZE * cfg.CHUNK_SIZE):
            target, num_vertices, vertices, edges = self.data.__next__()
            am = self.__create_adjacency_matrix(edges)
            vertices = [self.__pad(v.split('|'), cfg.MAX_WORD_PARTS) for v in vertices]
            if len(vertices) < cfg.MAX_VERTICES:
                vertices += [['<pad>'] * cfg.MAX_WORD_PARTS] * (cfg.MAX_VERTICES - len(vertices))
            vertices = [[self.word_vocab.lookup_idx(subword) for subword in word] for word in vertices]
            target = self.target_vocab.lookup_idx(target)
            samples.append((am, vertices, target))
        
        # batch_size * chunk_size 個のデータをfetchしてからshuffleする
        random.shuffle(samples)

        # batch_size ごとの chunk に分割して queue に追加
        num_chunks = cfg.CHUNK_SIZE
        if len(samples) < cfg.BATCH_SIZE * cfg.CHUNK_SIZE:
            num_chunks -= 1
        for i in range(num_chunks):
            chunk = samples[i * cfg.BATCH_SIZE: (i + 1) * cfg.BATCH_SIZE]
            self.chunks.append(chunk)
    
    def __create_adjacency_matrix(self, edges):
        n_nodes, n_edge_types = cfg.MAX_VERTICES, cfg.MAX_EDGE_TYPES
        a = np.zeros([n_nodes, n_nodes * n_edge_types * 2])
        for src_idx, row in enumerate(edges):
            for tgt_idx, e_type in enumerate(row):
                if e_type == 1:
                    a[tgt_idx][(e_type - 1) * n_nodes + src_idx] = 1
                    a[src_idx][(e_type - 1 + n_edge_types) * n_nodes + tgt_idx] = 1
        return a
