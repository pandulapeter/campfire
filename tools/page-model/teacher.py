# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The teacher's side of the page labels: Claude labels each saved page's lines twice, independently, answering only line
numbers and labels, never text.

    .venv/bin/python -I tools/page-model/teacher.py prepare <corpus folder>
    .venv/bin/python -I tools/page-model/teacher.py merge <corpus folder>

`prepare` writes each page as numbered lines into `teacher/<id>.txt` (long lines cut, so a page stays small), and the
instructions the labeller follows into `teacher/INSTRUCTIONS.md`. The labeller writes `teacher/<id>.run1.json` and
`teacher/<id>.run2.json`, each `{"TITLE": [n], "ARTIST": [n], "METADATA": [ranges], "SONG": [ranges], "noSong": bool}`,
a range being `n` or `[first, last]`, every other line `JUNK`. `merge` keeps a page whose two runs agree on nearly every
line (`labels/<id>.json`), the lines they disagree on left out of training (`IGNORE`), and drops the rest.
"""

import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

MAX_LINES = 1500
MAX_WIDTH = 160
MIN_AGREEMENT = 0.95

INSTRUCTIONS = """# Labelling a page's lines

Each `teacher/<id>.txt` is one web page's text as a browser renders it, one numbered line a row (`123| text`), long
lines cut. Its first rows give the page's title and address. Label every line with one of:

- `SONG`: the song itself, from its first section to its last: lyrics, chord rows, inline chords, section labels
  (Verse, Chorus, [Intro]…), tab staves, chord grids, ABC notation, repeat marks, and the blank lines inside it.
  A chord diagram's name list or a "Chords used" block that sits right above or below the song and only names the
  song's chords is `SONG` too. Anything the site put inside the song that is not the song (an ad, "Report this",
  a "Transpose" control, a video placeholder, "continue reading", a print button) is not.
- `TITLE`: the one line that best holds the song's title (it may also carry the artist, "X chords by Y": still TITLE).
- `ARTIST`: the one line that best holds the artist's or performer's name on its own, where there is one apart from
  the title line.
- `METADATA`: lines giving the song's own details: key, capo, tempo/BPM, time signature, tuning, difficulty is NOT
  metadata (JUNK); credits (words/music/composer/lyricist), and its album or year where stated beside the title.
- everything else is `JUNK`: menus, cookie banners, login, search, ratings, comments, related songs, footers, the
  site's calls to action, advertising, share buttons, other songs' lists, the site's own transposer or font controls.

Where a page holds no song at all (an index, a news article, search results) or only a fragment of one behind a
paywall, write `"noSong": true` and nothing else. A page with several complete songs: label only the first.

Answer in JSON only, as `{"TITLE": [n], "ARTIST": [n], "METADATA": [ranges], "SONG": [ranges]}`, a range being a
line number `n` or `[first, last]` inclusive; leave out lines that are JUNK. Use the line numbers as printed. Never
copy any of the page's text into the answer.
"""


def expand(ranges):
    numbers = set()
    for item in ranges or []:
        if isinstance(item, list):
            numbers.update(range(item[0], item[1] + 1))
        else:
            numbers.add(int(item))
    return numbers


def labels_of(answer, count):
    labels = ["JUNK"] * count
    if answer.get("noSong"):
        return labels
    for name in ("SONG", "METADATA", "ARTIST", "TITLE"):
        for number in expand(answer.get(name)):
            if 0 <= number < count:
                labels[number] = name
    return labels


def prepare(corpus):
    folder = os.path.join(corpus, "teacher")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "INSTRUCTIONS.md"), "w", encoding="utf-8") as file:
        file.write(INSTRUCTIONS)
    count = 0
    for path in sorted(glob.glob(os.path.join(corpus, "pages", "*.json"))):
        with open(path, encoding="utf-8") as file:
            page = json.load(file)
        rows = [f"# title: {page['title']}", f"# address: {page['finalUrl']}"]
        for number, line in enumerate(page["lines"][:MAX_LINES]):
            text = line if len(line) <= MAX_WIDTH else line[:MAX_WIDTH] + " …"
            rows.append(f"{number}| {text}")
        with open(os.path.join(folder, page["id"] + ".txt"), "w", encoding="utf-8") as file:
            file.write("\n".join(rows) + "\n")
        count += 1
    print(f"prepared {count} pages")


def merge(corpus):
    kept, dropped, missing = 0, [], 0
    os.makedirs(os.path.join(corpus, "labels"), exist_ok=True)
    for path in sorted(glob.glob(os.path.join(corpus, "pages", "*.json"))):
        with open(path, encoding="utf-8") as file:
            page = json.load(file)
        runs = []
        for run in ("run1", "run2"):
            answer_path = os.path.join(corpus, "teacher", f"{page['id']}.{run}.json")
            if os.path.isfile(answer_path):
                with open(answer_path, encoding="utf-8") as file:
                    runs.append(json.load(file))
        if len(runs) < 2:
            missing += 1
            continue
        count = min(len(page["lines"]), MAX_LINES)
        first, second = (labels_of(run, count) for run in runs)
        printed = [index for index in range(count) if page["lines"][index].strip()]
        agreed = sum(1 for index in printed if first[index] == second[index])
        agreement = agreed / max(len(printed), 1)
        if agreement < MIN_AGREEMENT or bool(runs[0].get("noSong")) != bool(runs[1].get("noSong")):
            dropped.append((page["id"], round(agreement, 3)))
            continue
        labels = []
        for index in range(count):
            if not page["lines"][index].strip():
                labels.append("BLANK")
            elif first[index] == second[index]:
                labels.append(first[index])
            else:
                labels.append("IGNORE")
        with open(os.path.join(corpus, "labels", page["id"] + ".json"), "w", encoding="utf-8") as file:
            json.dump({"id": page["id"], "source": "page", "group": "page:" + page["id"], "labels": labels, "agreement": round(agreement, 3), "noSong": bool(runs[0].get("noSong"))}, file)
        kept += 1
    for identity, agreement in dropped:
        print(f"dropped {identity}: agreement {agreement}")
    print(f"kept {kept} pages, dropped {len(dropped)}, unlabelled {missing}")


if __name__ == "__main__":
    {"prepare": prepare, "merge": merge}[sys.argv[1]](sys.argv[2])
