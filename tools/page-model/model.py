# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The page model: each line read by its characters (hashed code points, so no script needs a vocabulary) through a few
convolutions pooled into one vector, joined with the line's features, then a bidirectional GRU across the page's lines
that lets a line be judged by its neighbours, and one label per line. Every layer is one the app can run as plain
matrix arithmetic.
"""

import torch
from torch import nn

from lines import FEATURES, LABELS, visible

BUCKETS = 4096
MAX_CHARS = 160


def hashed(text):
    """A line's visible characters as bucket numbers from 1, 0 padding; the same hash the app's library computes."""
    codes = [((ord(c) * 2654435761) % 4294967296) % BUCKETS + 1 for c in visible(text)[:MAX_CHARS]]
    return codes + [0] * (MAX_CHARS - len(codes))


class PageModel(nn.Module):
    def __init__(self, embedding=32, channels=96, line=128, hidden=96):
        super().__init__()
        self.embedding = nn.Embedding(BUCKETS + 1, embedding, padding_idx=0)
        self.convolutions = nn.Sequential(
            nn.Conv1d(embedding, channels, 3, padding=1),
            nn.ReLU(),
            nn.Conv1d(channels, channels, 3, padding=2, dilation=2),
            nn.ReLU(),
        )
        self.line = nn.Sequential(nn.Linear(channels + FEATURES, line), nn.ReLU())
        self.sequence = nn.GRU(line, hidden, num_layers=2, bidirectional=True, batch_first=True)
        self.output = nn.Linear(hidden * 2, len(LABELS))

    def forward(self, characters, features):
        """`characters` is lines × MAX_CHARS bucket numbers, `features` lines × FEATURES; answers lines × labels logits."""
        mask = (characters > 0).unsqueeze(1)
        encoded = self.convolutions(self.embedding(characters).transpose(1, 2))
        pooled = encoded.masked_fill(~mask, -1e4).max(dim=2).values
        pooled = torch.where(mask.any(dim=2), pooled, torch.zeros_like(pooled))
        lines = self.line(torch.cat([pooled, features], dim=1))
        sequence, _ = self.sequence(lines.unsqueeze(0))
        return self.output(sequence.squeeze(0))
