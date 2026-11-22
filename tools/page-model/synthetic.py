# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Pages made up for training, their labels known by construction: a ChordPro song of the import corpus rendered as a chord
sheet (chord rows over lyrics, section labels, its title, artist, and details), wrapped in runs of the clutter the real
training pages' `JUNK` lines hold, a few of them dropped inside the song as a site's ads are. They only widen the
training set: the gate always scores real pages, and a song whose document is held out is never rendered.
"""

import glob
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lines import normalized  # noqa: E402

DIRECTIVE = re.compile(r"^\{\s*([a-z_]+)\s*[: ]?\s*(.*?)\s*\}$")
CHORD = re.compile(r"\[([^\]]*)\]")
DETAIL_NAMES = {"key": "Key", "capo": "Capo", "tempo": "Tempo", "time": "Time", "composer": "Music", "lyricist": "Words"}


def rendered(cho, rng):
    """A `.cho` as a chord site shows it: (text, label) pairs."""
    lines = []
    for raw in cho.splitlines():
        line = raw.rstrip()
        directive = DIRECTIVE.match(line.strip())
        if directive:
            key, value = directive.group(1).lower(), directive.group(2)
            if key in ("title", "t") and value:
                lines.append((value + rng.choice(["", " Chords", " chords", " (Chords)", " Tab", " - Acordes"]), "TITLE"))
            elif key == "artist" and value:
                lines.append((rng.choice(["", "by ", "Artist: "]) + value, "ARTIST"))
            elif key in DETAIL_NAMES and value:
                lines.append((f"{DETAIL_NAMES[key]}: {value}", "METADATA"))
            elif key.startswith("start_of") or key in ("soc", "sov", "sob"):
                label = re.sub(r"^label=\"?|\"$", "", value) or key.replace("start_of_", "").replace("so", "").title()
                lines.append((rng.choice(["{}", "[{}]", "{}:"]).format(label), "SONG"))
            elif key in ("comment", "c", "ci") and value:
                lines.append((value, "SONG"))
            continue
        if not line.strip():
            lines.append(("", "BLANK"))
            continue
        if "[" in line:
            chords, lyric = "", ""
            for part in re.split(r"(\[[^\]]*\])", line):
                match = CHORD.fullmatch(part)
                if match:
                    chords = chords.ljust(len(lyric)) + match.group(1) + " "
                else:
                    lyric += part
            lines.append((chords.rstrip(), "SONG"))
            if lyric.strip():
                lines.append((lyric, "SONG"))
        else:
            lines.append((line, "SONG"))
    return lines


def junk_runs(pages):
    runs = []
    for page in pages:
        run = []
        for line, label in zip(page["lines"], page["labels"]):
            if label in ("JUNK", "BLANK"):
                run.append((line["text"], label))
            else:
                if len(run) >= 3:
                    runs.append(run)
                run = []
        if len(run) >= 3:
            runs.append(run)
    return runs


def synthetic_pages(import_corpus, training_pages, held_groups, seed, per_song=2):
    rng = random.Random(seed)
    runs = junk_runs(training_pages)
    if not runs:
        return []
    pages = []
    for path in sorted(glob.glob(os.path.join(import_corpus, "**", "*.cho"), recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as file:
            cho = file.read().split("{new_song}")[0]
        directives = [DIRECTIVE.match(line.strip()) for line in cho.splitlines()]
        title = next((match.group(2) for match in directives if match and match.group(1).lower() in ("title", "t")), None)
        if title and normalized(title) in held_groups:
            continue
        song = rendered(cho, rng)
        if sum(1 for _, label in song if label == "SONG") < 4:
            continue
        for _ in range(per_song):
            lines = []
            lines += rng.choice(runs)[: rng.randint(10, 80)]
            for text, label in song:
                lines.append((text, label))
                if label == "BLANK" and rng.random() < 0.08:
                    lines += rng.choice(runs)[: rng.randint(1, 3)]
            lines += rng.choice(runs)[: rng.randint(10, 120)]
            pages.append({
                "id": "synthetic",
                "source": "page",
                "lines": [{"text": text} for text, _ in lines],
                "labels": [label if text.strip() else "BLANK" for text, label in lines],
                "noSong": False,
                "group": "synthetic",
                "isSynthetic": True,
            })
    return pages
