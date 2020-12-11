import logging
import pickle

import numpy as np
import torch
from torch.optim import Adam
from torch.nn import CrossEntropyLoss

import config as cfg
from models import CodeGGNN
from data import GraphDataloader, Vocabulary


def train(epochs, lr=0.001):
    logging.info(f'start training on {cfg.DEVICE}')

    with open(f'{cfg.DATA_PATH}/vocabulary.pkl', 'rb') as f:
        word_vocab = Vocabulary(pickle.load(f).keys())
        target_vocab = Vocabulary(pickle.load(f).keys())
    train_loader = GraphDataloader(f'{cfg.DATA_PATH}/training.graph', word_vocab, target_vocab)
    val_loader = GraphDataloader(f'{cfg.DATA_PATH}/validation.graph', word_vocab, target_vocab)
    logging.info(f'train on {len(train_loader)} batches, validate on {len(val_loader)} batches')

    # train settings
    model = CodeGGNN(len(word_vocab), len(target_vocab), cfg.EMBEDDING_DIM).to(cfg.DEVICE)
    optimizer = Adam(model.parameters(), lr=lr)
    loss_fn = CrossEntropyLoss().to(cfg.DEVICE)

    # train
    history = {'val_loss': list(), 'val_acc': list(), 'val_precision': list(), 'val_recall': list(), 'val_f1': list()}
    def after_batch(epoch, batch_idx):
        if batch_idx % cfg.SAVE_EVERY == 0 or batch_idx == len(train_loader):
            validate(model, history, loss_fn, val_loader, epoch, target_vocab)
            if len(history['val_f1']) == 1 or history['val_f1'][-1] > max(history['val_f1'][:-1]):
                torch.save(model.state_dict(), f'{cfg.MODEL_PATH}/ggnn-mnp.ckpt')
                logging.info(f'[epoch {epoch}] model saved')
    for epoch in range(1, epochs + 1):
        train_epoch(model, optimizer, loss_fn, train_loader, epoch, target_vocab, after_batch_callback=after_batch)

def train_epoch(model, optimizer, loss_fn, dataloader, epoch_idx, target_vocab, after_batch_callback=None):
    for i, (am, vertices, target) in enumerate(dataloader, 1):
        model.train()
        optimizer.zero_grad()

        out = model(am.to(cfg.DEVICE), vertices.to(cfg.DEVICE))
        target = target.to(out.device)
        loss = loss_fn(out, target)
        accuracy = compute_accuracy(out, target)
        precision, recall, f1 = compute_f1(out, target, target_vocab)

        loss.backward()
        optimizer.step()

        logging.info(f'[epoch {epoch_idx} batch {i}] loss: {loss.item()}, accuracy: {accuracy}, precision: {precision}, recall: {recall}, f1: {f1}')

        if after_batch_callback is not None:
            after_batch_callback(epoch_idx, i)

def validate(model, history, loss_fn, dataloader, epoch_idx, target_vocab):
    model.eval()

    total_loss, total_acc, total_precision, total_recall, total_f1, batch_cnt, data_cnt = 0, 0, 0, 0, 0, 0, 0
    with torch.no_grad():
        for i, (am, vertices, target) in enumerate(dataloader, 1):
            out = model(am.to(cfg.DEVICE), vertices.to(cfg.DEVICE))
            target = target.to(out.device)
            loss = loss_fn(out, target)

            data_cnt += target.shape[0]
            total_loss += loss.item() * target.shape[0]
            total_acc += compute_accuracy(out, target)
            precision, recall, f1 = compute_f1(out, target, target_vocab)
            total_precision += precision
            total_recall += recall
            total_f1 += f1
            batch_cnt += 1

    history['val_loss'].append(total_loss / data_cnt)
    history['val_acc'].append(total_acc / batch_cnt)
    history['val_precision'].append(total_precision / batch_cnt)
    history['val_recall'].append(total_recall / batch_cnt)
    history['val_f1'].append(total_f1 / batch_cnt)
    logging.info(f'[epoch {epoch_idx} val] loss: {total_loss / data_cnt}, accuracy: {total_acc / batch_cnt}, precision: {total_precision / batch_cnt}, recall: {total_recall / batch_cnt}, f1: {total_f1 / batch_cnt}')

def compute_accuracy(fx, y):
    pred_idxs = fx.max(1, keepdim=True)[1]
    correct = pred_idxs.eq(y.view_as(pred_idxs)).sum()
    acc = correct.float() / pred_idxs.shape[0]
    return acc

def compute_f1(fx, y, target_vocab):
    pred_idxs = fx.max(1, keepdim=True)[1]
    pred_names = [target_vocab.lookup_word(i.item()) for i in pred_idxs]
    original_names = [target_vocab.lookup_word(i.item()) for i in y]
    true_positive, false_positive, false_negative = 0, 0, 0
    for p, o in zip(pred_names, original_names):
        predicted_subtokens = p.split('|')
        original_subtokens = o.split('|')
        for subtok in predicted_subtokens:
            if subtok in original_subtokens:
                true_positive += 1
            else:
                false_positive += 1
        for subtok in original_subtokens:
            if not subtok in predicted_subtokens:
                false_negative += 1
    try:
        precision = true_positive / (true_positive + false_positive)
        recall = true_positive / (true_positive + false_negative)
        f1 = 2 * precision * recall / (precision + recall)
    except ZeroDivisionError:
        precision, recall, f1 = 0, 0, 0
    return precision, recall, f1

def correct_count(out, target):
    predicted = out.max(1, keepdim=True)[1]
    n_correct = predicted.eq(target.view_as(predicted)).sum()
    return n_correct
