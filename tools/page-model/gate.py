# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The plan's feasibility gate (`documentation/plans/song-from-any-page.md`, step 0): trains the page model three ways
(pages only, documents only, both) with five-fold cross-validation, so every labelled page and document is scored once
by a model that never saw it, and compares each crop with the converter alone, which keeps every line of the page.

    .venv/bin/python -I tools/page-model/gate.py <corpus folder> [--synthetic <import corpus>] [--epochs N]

The report goes to `<corpus>/.runs/gate.txt` and the scores per page to `gate.tsv`: for each source and variant, how
many pages the crop beats keeping everything on (line F1 of the song's lines, its metadata included), how many lose a
song line (and how many lines), how pages with no song are told, and how often the title and artist lines are hit.
"""

import argparse
import glob
import json
import os
import random
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import torch  # noqa: E402
from torch import nn  # noqa: E402

from lines import LABELS, features  # noqa: E402
from model import PageModel, hashed  # noqa: E402
from synthetic import synthetic_pages  # noqa: E402

FOLDS = 5
MIN_SONG_LINES = 4
KEPT = {"SONG", "METADATA"}
WEIGHTS = torch.tensor([1.0, 1.0, 4.0, 4.0, 2.0])


def load(corpus):
    examples = []
    for path in sorted(glob.glob(os.path.join(corpus, "labels", "*.json"))):
        with open(path, encoding="utf-8") as file:
            labelled = json.load(file)
        if labelled["source"] == "page":
            with open(os.path.join(corpus, "pages", labelled["id"] + ".json"), encoding="utf-8") as file:
                page = json.load(file)
            lines = [{"text": text} for text in page["lines"][: len(labelled["labels"])]]
        else:
            with open(os.path.join(corpus, "documents", labelled["id"] + ".json"), encoding="utf-8") as file:
                lines = json.load(file)["lines"]
        examples.append({"id": labelled["id"], "source": labelled["source"], "lines": lines, "labels": labelled["labels"], "noSong": labelled.get("noSong", False)})
    return examples


def tensors(example):
    characters = torch.tensor([hashed(line["text"]) for line in example["lines"]], dtype=torch.long)
    values = torch.tensor([features(line, example["source"]) for line in example["lines"]], dtype=torch.float32)
    targets = torch.tensor([LABELS.index(label) if label in LABELS else -100 for label in example["labels"]], dtype=torch.long)
    return characters, values, targets


def train(examples, epochs, seed):
    torch.manual_seed(seed)
    model = PageModel()
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=1e-4)
    loss = nn.CrossEntropyLoss(weight=WEIGHTS, ignore_index=-100)
    prepared = [tensors(example) for example in examples]
    order = list(range(len(prepared)))
    random.seed(seed)
    for _ in range(epochs):
        random.shuffle(order)
        model.train()
        for index in order:
            characters, values, targets = prepared[index]
            if (targets >= 0).sum() == 0:
                continue
            optimizer.zero_grad()
            value = loss(model(characters, values), targets)
            value.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
    return model


def predict(model, example):
    model.eval()
    characters, values, _ = tensors(example)
    with torch.no_grad():
        return [LABELS[index] for index in model(characters, values).argmax(dim=1).tolist()]


def score(example, predicted):
    """The crop against the converter alone on one page; lines with no text and lines the teacher disagreed on are left out."""
    printed = [index for index, line in enumerate(example["lines"]) if line["text"].strip() and example["labels"][index] != "IGNORE"]
    truth = {index for index in printed if example["labels"][index] in KEPT}
    song = {index for index in printed if example["labels"][index] == "SONG"}
    found = {index for index in printed if predicted[index] in KEPT}
    is_song = sum(1 for index in found if predicted[index] == "SONG") >= MIN_SONG_LINES
    # Where the model does not see a song, nothing is cropped and the converter has the whole page, as today.
    kept = found if is_song else set(printed)

    def f1(chosen):
        if not truth:
            return 1.0 if not chosen else 0.0
        hits = len(chosen & truth)
        precision = hits / max(len(chosen), 1)
        recall = hits / len(truth)
        return 0.0 if hits == 0 else 2 * precision * recall / (precision + recall)

    title = [index for index in printed if example["labels"][index] == "TITLE"]
    artist = [index for index in printed if example["labels"][index] == "ARTIST"]
    return {
        "id": example["id"],
        "source": example["source"],
        "noSong": example["noSong"] or not truth,
        "saysSong": is_song,
        "baseline": f1(set(printed)),
        "crop": f1(kept),
        "lostSong": len(song - kept),
        "songLines": len(song),
        "extra": len(kept - truth),
        "title": None if not title else any(predicted[index] == "TITLE" for index in title),
        "artist": None if not artist else any(predicted[index] == "ARTIST" for index in artist),
    }


def folds(examples, seed):
    shuffled = examples[:]
    random.Random(seed).shuffle(shuffled)
    return [shuffled[fold::FOLDS] for fold in range(FOLDS)]


def summary(name, scores):
    songs = [s for s in scores if not s["noSong"]]
    none = [s for s in scores if s["noSong"]]
    wins = sum(1 for s in songs if s["crop"] > s["baseline"] + 1e-9)
    ties = sum(1 for s in songs if abs(s["crop"] - s["baseline"]) <= 1e-9)
    losses = [s for s in songs if s["lostSong"] > 0]
    lost_lines = sum(s["lostSong"] for s in songs)
    all_song_lines = sum(s["songLines"] for s in songs)
    titles = [s["title"] for s in songs if s["title"] is not None]
    artists = [s["artist"] for s in songs if s["artist"] is not None]
    rows = [
        f"## {name}",
        f"song pages: {len(songs)}; crop beats keeping everything on {wins}, ties {ties}, worse on {len(songs) - wins - ties}",
        f"mean line F1: keep everything {mean(s['baseline'] for s in songs):.3f}, crop {mean(s['crop'] for s in songs):.3f}",
        f"pages losing a song line: {len(losses)} ({lost_lines} of {all_song_lines} song lines lost)",
        f"pages with no song: {len(none)}, told as none {sum(1 for s in none if not s['saysSong'])}",
        f"title line hit {sum(titles)}/{len(titles)}, artist line hit {sum(artists)}/{len(artists)}",
    ]
    worst = sorted(losses, key=lambda s: -s["lostSong"])[:8]
    if worst:
        rows.append("most song lines lost: " + ", ".join(f"{s['id']} ({s['lostSong']}/{s['songLines']})" for s in worst))
    return "\n".join(rows)


def mean(values):
    values = list(values)
    return sum(values) / max(len(values), 1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus")
    parser.add_argument("--epochs", type=int, default=12)
    parser.add_argument("--synthetic", metavar="IMPORT_CORPUS", help="add pages made of the import corpus's songs to training")
    parser.add_argument("--seed", type=int, default=7)
    arguments = parser.parse_args()
    torch.set_num_threads(max(1, (os.cpu_count() or 2) - 2))
    examples = load(arguments.corpus)
    pages = [e for e in examples if e["source"] == "page"]
    documents = [e for e in examples if e["source"] == "document"]
    print(f"{len(pages)} pages, {len(documents)} documents")
    page_folds, document_folds = folds(pages, arguments.seed), folds(documents, arguments.seed)
    variants = {"pages only": (True, False), "documents only": (False, True), "both": (True, True)}
    report, rows = [], []
    for variant, (uses_pages, uses_documents) in variants.items():
        scores = []
        started = time.time()
        for fold in range(FOLDS):
            held = page_folds[fold] + document_folds[fold]
            training = []
            if uses_pages:
                training += [e for f, examples_of in enumerate(page_folds) if f != fold for e in examples_of]
            if uses_documents:
                training += [e for f, examples_of in enumerate(document_folds) if f != fold for e in examples_of]
            if arguments.synthetic and uses_pages:
                training += synthetic_pages(arguments.synthetic, [e for e in training if e["source"] == "page"], held, seed=arguments.seed + fold)
            model = train(training, arguments.epochs, arguments.seed + fold)
            for example in held:
                scores.append(score(example, predict(model, example)))
        for source in ("page", "document"):
            part = [s for s in scores if s["source"] == source]
            report.append(summary(f"{variant}, scored on {source}s", part))
            rows += [f"{variant}\t{s['source']}\t{s['id']}\t{s['baseline']:.3f}\t{s['crop']:.3f}\t{s['lostSong']}\t{s['songLines']}\t{s['extra']}" for s in part]
        print(f"{variant}: {time.time() - started:.0f} s", flush=True)
    runs = os.path.join(arguments.corpus, ".runs")
    os.makedirs(runs, exist_ok=True)
    with open(os.path.join(runs, "gate.txt"), "w", encoding="utf-8") as file:
        file.write("\n\n".join(report) + "\n")
    with open(os.path.join(runs, "gate.tsv"), "w", encoding="utf-8") as file:
        file.write("variant\tsource\tid\tkeepAllF1\tcropF1\tlostSongLines\tsongLines\textraLines\n" + "\n".join(rows) + "\n")
    print("\n\n".join(report))


if __name__ == "__main__":
    main()
