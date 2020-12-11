import torch
import torch.nn as nn

import config as cfg

PAD = 1


class AttrProxy(object):
    """
    Translates index lookups into attribute lookups.
    To implement some trick which able to use list of nn.Module in a nn.Module
    see https://discuss.pytorch.org/t/list-of-nn-module-in-a-nn-module/219/2
    """

    def __init__(self, module, prefix):
        self.module = module
        self.prefix = prefix

    def __getitem__(self, i):
        return getattr(self.module, self.prefix + str(i))


class Propogator(nn.Module):
    """
    Gated Propogator for GGNN
    Using LSTM gating mechanism
    """

    def __init__(self, state_dim, n_node, n_edge_types):
        super(Propogator, self).__init__()

        self.n_node = n_node
        self.n_edge_types = n_edge_types

        self.reset_gate = nn.Sequential(
            nn.Linear(state_dim * 3, state_dim),
            nn.Sigmoid()
        )
        self.update_gate = nn.Sequential(
            nn.Linear(state_dim * 3, state_dim),
            nn.Sigmoid()
        )
        self.tansform = nn.Sequential(
            nn.Linear(state_dim * 3, state_dim),
            nn.Tanh()
        )

    def forward(self, state_in, state_out, state_cur, A):
        # TODO error が出るから float() つけたけどいいのか？？？
        A_in = A[:, :, :self.n_node * self.n_edge_types].float()
        A_out = A[:, :, self.n_node * self.n_edge_types:].float()

        a_in = torch.bmm(A_in, state_in)
        a_out = torch.bmm(A_out, state_out)
        a = torch.cat((a_in, a_out, state_cur), 2)

        r = self.reset_gate(a)
        z = self.update_gate(a)
        joined_input = torch.cat((a_in, a_out, r * state_cur), 2)
        h_hat = self.tansform(joined_input)

        output = (1 - z) * state_cur + z * h_hat

        return output


class GGNN(nn.Module):
    """
    Gated Graph Sequence Neural Networks (GGNN)
    Mode: SelectNode
    Implementation based on https://arxiv.org/abs/1511.05493
    """

    def __init__(self, state_dim):
        super(GGNN, self).__init__()
        self.state_dim = state_dim
        self.n_edge_types = cfg.MAX_EDGE_TYPES
        self.n_node = cfg.MAX_VERTICES
        self.n_steps = cfg.PROPAGATION_STEPS

        for i in range(self.n_edge_types):
            # incoming and outgoing edge embedding
            in_fc = nn.Linear(self.state_dim, self.state_dim)
            out_fc = nn.Linear(self.state_dim, self.state_dim)
            self.add_module("in_{}".format(i), in_fc)
            self.add_module("out_{}".format(i), out_fc)

        self.in_fcs = AttrProxy(self, "in_")
        self.out_fcs = AttrProxy(self, "out_")

        # Propogation Model
        self.propogator = Propogator(self.state_dim, self.n_node, self.n_edge_types)

        # Output Model
        self.out = nn.Sequential(
            nn.Linear(self.state_dim, self.state_dim),
            nn.Tanh(),
            nn.Linear(self.state_dim, 1)
        )

        self.soft_attention = nn.Sequential(
            nn.Linear(self.state_dim, self.state_dim),
            nn.Tanh(),
            nn.Linear(self.state_dim, 1),
            nn.Sigmoid(),
        )

        self._initialization()

    def _initialization(self):
        for m in self.modules():
            if isinstance(m, nn.Linear):
                m.weight.data.normal_(0.0, 0.02)
                m.bias.data.fill_(0)

    def forward(self, prop_state, A):
        for i_step in range(self.n_steps):
            in_states = []
            out_states = []
            for i in range(self.n_edge_types):
                in_states.append(self.in_fcs[i](prop_state))
                out_states.append(self.out_fcs[i](prop_state))
            in_states = torch.stack(in_states).transpose(0, 1).contiguous()
            in_states = in_states.view(-1, self.n_node * self.n_edge_types, self.state_dim)
            out_states = torch.stack(out_states).transpose(0, 1).contiguous()
            out_states = out_states.view(-1, self.n_node * self.n_edge_types, self.state_dim)

            prop_state = self.propogator(in_states, out_states, prop_state, A)

        # Graph level output
        aw = self.soft_attention(prop_state)
        # aw: (batch_size, n_vertices, 1)
        # TODO print(aw) 何故か全ての要素が 0.5 になるけど…
        output = torch.mul(prop_state, aw)
        output = output.sum(1)
        return output


class CodeGGNN(nn.Module):

    def __init__(self, word_vocab_size, target_vocab_size, embedding_dim):
        super(CodeGGNN, self).__init__()
        self.embedding = nn.Embedding(word_vocab_size, embedding_dim)
        self.ggnn = GGNN(embedding_dim)
        self.out = nn.Linear(embedding_dim, target_vocab_size)
    
    def forward(self, am, vertices):
        # am: (batch_size, n_vertices, n_vertices * 2)
        # vertices: (batch_size, n_vertices, max_word_parts)

        x = self.embedding(vertices)
        # x: (batch_size, n_vertices, max_word_parts, embedding_dim)

        # embed した subtoken の average を取る
        x *= (vertices != PAD).float().unsqueeze(3)
        x = torch.mean(x, dim=2)
        # x: (batch_size, n_vertices, embedding_dim)

        h_g = self.ggnn(x, am)  # h_g: (batch_size, embedding_dim)

        output = self.out(h_g)  # output: (batch_size, target_vocab_size)
        return output