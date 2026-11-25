# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The page model's gate (step 6 of `tools/page-model/CLAUDE.md`): trains the page model three ways
(pages only, documents only, both) with five-fold cross-validation, so every labelled page and document is scored once
by a model that never saw it, and compares each crop with the converter alone, which keeps every line of the page.

    .venv/bin/python -I tools/page-model/gate.py <corpus folder> [--synthetic <import corpus>] [--epochs N]
        [--variants both] [--rescore]

The report goes to `<corpus>/.runs/gate.txt` and the scores per page to `gate.tsv`: for each source and variant, how
many pages the crop beats keeping everything on (line F1 of the song's lines, its metadata included), how many lose a
song line (and how many lines), how pages with no song are told, and how often the title and artist lines are hit.
Each variant's predictions are kept in `predictions.json`, so `--rescore` tries the crop rules again without training.
A song line shown as the title or artist is not counted lost, since the app shows those lines too.
"""

import argparse
import glob
import json
import os
import random
import re
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
SHOWN = {"SONG", "METADATA", "TITLE", "ARTIST"}
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
        labels = staves_ignored(lines, labelled["labels"])
        examples.append({"id": labelled["id"], "source": labelled["source"], "lines": lines, "labels": labels, "noSong": labelled.get("noSong", False), "group": labelled.get("group", labelled["id"]), "isSynthetic": labelled["id"].startswith("synthetic__")})
    return examples


def staves_ignored(lines, labels):
    """
    The labels with each staff string after the song's first line that the teacher called clutter made `IGNORE`: the crop
    keeps such a line whatever the model says (`tab_lines`), and a page printing its tab twice, one copy labelled
    clutter, would otherwise teach the model that a staff deep in a page is clutter, which costs a long tab its second half.
    """
    first = next((index for index, label in enumerate(labels) if label == "SONG"), None)
    if first is None:
        return labels
    return [
        "IGNORE" if index > first and label == "JUNK" and index < len(lines) and is_staff(lines[index]["text"]) else label
        for index, label in enumerate(labels)
    ]


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
        probabilities = model(characters, values).softmax(dim=1)
    kept = probabilities[:, [LABELS.index(label) for label in KEPT]].sum(dim=1)
    return {"labels": [LABELS[index] for index in probabilities.argmax(dim=1).tolist()], "kept": [round(value, 4) for value in kept.tolist()]}


RULES = ("line", "unsure")
UNSURE = 0.02


def cropped(prediction, printed, rule):
    """
    The lines a crop keeps: `line` keeps each line predicted as the song or its metadata, `unsure` also each line the
    model gives even a small chance of being either, so a song line it is not sure of stays at the cost of a little
    clutter.
    """
    found = {index for index in printed if prediction["labels"][index] in KEPT}
    if rule == "unsure":
        found |= {index for index in printed if prediction["kept"][index] >= UNSURE}
    return found


STAFF_GAP = 8
STAFF_START = re.compile(r"^[ \t]*[A-Ga-g][#b]?[ \t]*\|")


def is_staff(text):
    """A tab staff's string, whatever its playing marks: a string's name, a bar line, and a run of dashes or tildes."""
    return bool(STAFF_START.match(text)) and sum(text.count(mark) for mark in "-~") >= 8 and text.count("|") >= 2


def tab_lines(lines, printed, first_song_line):
    """
    What the `unsure` crop keeps of a tab whatever the model says (`CropRule.kt`): every staff string after the song's
    first line, and the printed lines between two of them, `STAFF_GAP` at most (a part's name, a player's note).
    """
    staves = sorted(index for index, line in enumerate(lines) if index > first_song_line and is_staff(line["text"]))
    between = {index for a, b in zip(staves, staves[1:]) if b - a - 1 <= STAFF_GAP for index in range(a + 1, b)}
    return (set(staves) | between) & printed


def score(example, prediction, rule="line"):
    """The crop against the converter alone on one page; lines with no text and lines the teacher disagreed on are left out."""
    printed = [index for index, line in enumerate(example["lines"]) if line["text"].strip() and example["labels"][index] != "IGNORE"]
    truth = {index for index in printed if example["labels"][index] in KEPT}
    song = {index for index in printed if example["labels"][index] == "SONG"}
    predicted = prediction["labels"]
    found = cropped(prediction, printed, rule)
    is_song = sum(1 for index in found if predicted[index] == "SONG") >= MIN_SONG_LINES
    # Where the model does not see a song, nothing is cropped and the converter has the whole page, as today.
    kept = found if is_song else set(printed)
    if is_song and rule == "unsure":
        kept |= tab_lines(example["lines"], set(printed), min(index for index in found if predicted[index] == "SONG"))

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
        "lostSong": len(lost := {index for index in song - kept if predicted[index] not in SHOWN}),
        "lost": [(index, example["lines"][index]["text"], predicted[index]) for index in sorted(lost)],
        "songLines": len(song),
        "extra": len(kept - truth),
        "title": None if not title else any(predicted[index] == "TITLE" for index in title),
        "artist": None if not artist else any(predicted[index] == "ARTIST" for index in artist),
    }


def folds(examples, seed):
    """Groups of held-out examples, a song's group whole in one, so no fold trains on a song it scores in another form."""
    groups = sorted({example["group"] for example in examples})
    random.Random(seed).shuffle(groups)
    fold_of = {group: index % FOLDS for index, group in enumerate(groups)}
    return [[example for example in examples if fold_of[example["group"]] == fold] for fold in range(FOLDS)]


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
    parser.add_argument("--variants", default="pages only,documents only,both", help="comma-separated variants to run")
    parser.add_argument("--rescore", action="store_true", help="score the predictions the last run saved instead of training")
    arguments = parser.parse_args()
    torch.set_num_threads(max(1, (os.cpu_count() or 2) - 2))
    examples = load(arguments.corpus)
    scored = [e for e in examples if not e["isSynthetic"]]
    print(f"{sum(1 for e in scored if e['source'] == 'page')} pages, {sum(1 for e in scored if e['source'] == 'document')} documents, "
          f"{sum(1 for e in examples if e['isSynthetic'])} made-up documents")
    held_folds = folds(scored, arguments.seed)
    variants = {"pages only": {"page"}, "documents only": {"document"}, "both": {"page", "document"}}
    chosen = [name.strip() for name in arguments.variants.split(",")]
    runs = os.path.join(arguments.corpus, ".runs")
    os.makedirs(runs, exist_ok=True)
    saved_path = os.path.join(runs, "predictions.json")
    saved = {}
    if os.path.isfile(saved_path):
        with open(saved_path, encoding="utf-8") as file:
            saved = json.load(file)
    by_id = {e["id"]: e for e in scored}
    report, rows, lost = [], [], []
    for variant in chosen:
        sources = variants[variant]
        if not any(e["source"] in sources for e in scored):
            continue
        started = time.time()
        if not arguments.rescore:
            predictions = {}
            for fold in range(FOLDS):
                held = held_folds[fold]
                held_groups = {e["group"] for e in held}
                training = [e for e in examples if e["source"] in sources and e["group"] not in held_groups]
                if arguments.synthetic and "page" in sources:
                    training += synthetic_pages(arguments.synthetic, [e for e in training if e["source"] == "page"], held_groups, seed=arguments.seed + fold)
                model = train(training, arguments.epochs, arguments.seed + fold)
                for example in held:
                    predictions[example["id"]] = predict(model, example)
            saved[variant] = predictions
            with open(saved_path, "w", encoding="utf-8") as file:
                json.dump(saved, file)
        for rule in RULES:
            scores = [score(by_id[identity], predicted, rule) for identity, predicted in saved[variant].items() if identity in by_id]
            for source in ("page", "document"):
                part = [s for s in scores if s["source"] == source]
                name = f"{variant}, {rule} crop"
                report.append(summary(f"{name}, scored on {source}s", part))
                lost += [f"## {name}: {s['id']}\n" + "\n".join(f"{index}\t{label}\t{text}" for index, text, label in s["lost"]) for s in part if s["lost"]]
                rows += [f"{name}\t{s['source']}\t{s['id']}\t{s['baseline']:.3f}\t{s['crop']:.3f}\t{s['lostSong']}\t{s['songLines']}\t{s['extra']}" for s in part]
        print(f"{variant}: {time.time() - started:.0f} s", flush=True)
    with open(os.path.join(runs, "gate.txt"), "w", encoding="utf-8") as file:
        file.write("\n\n".join(report) + "\n")
    with open(os.path.join(runs, "gate.tsv"), "w", encoding="utf-8") as file:
        file.write("variant\tsource\tid\tkeepAllF1\tcropF1\tlostSongLines\tsongLines\textraLines\n" + "\n".join(rows) + "\n")
    with open(os.path.join(runs, "lost.txt"), "w", encoding="utf-8") as file:
        file.write("\n\n".join(lost) + "\n")
    print("\n\n".join(report))


if __name__ == "__main__":
    main()
