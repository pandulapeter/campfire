# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Labels the documents' lines (`documents/*.json`, written by `PageCorpusDocumentsDump`) without anyone reading them, into
`labels/<id>.json`:

- an import corpus document by its hand-corrected `.cho`: a line holding one of the song's lyrics, chord rows, or
  section labels is `SONG`, the line holding the `{title}` or `{artist}` value is `TITLE` or `ARTIST`, one naming a key,
  capo, tempo, time, or credit is `METADATA`, the rest `JUNK`;
- a web page printed to PDF by its page's agreed labels (`labels/<page id>.json`), each printed line taking the label of
  the page line it matches.

A document whose song is not found well enough among its lines is left out rather than fixed by hand.

    .venv/bin/python -I tools/page-model/align_documents.py <corpus folder>
"""

import difflib
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lines import chord_tokens, is_labelled, normalized  # noqa: E402

MIN_FOUND = 0.6
DIRECTIVE = re.compile(r"^\{\s*([a-z_]+)\s*[: ]?\s*(.*?)\s*\}$")
INLINE_CHORD = re.compile(r"\[[^\]]*\]")
METADATA_KEYS = {"key", "capo", "tempo", "time", "composer", "lyricist", "copyright", "arranger", "meta", "duration", "year", "album"}
SECTION_KEYS = {"start_of_verse", "start_of_chorus", "start_of_bridge", "start_of_tab", "start_of_grid", "comment", "c", "soc", "sov", "sob", "ci", "comment_italic"}


def song_parts(cho):
    """What a `.cho` says, as the document's lines would show it."""
    parts = {"lyrics": set(), "chords": set(), "labels": set(), "titles": [], "artists": [], "metadata": [], "lyricCount": 0}
    for raw in cho.splitlines():
        line = raw.strip()
        directive = DIRECTIVE.match(line)
        if directive:
            key, value = directive.group(1).lower(), directive.group(2)
            if key in ("title", "t", "subtitle", "st"):
                parts["titles"].append(normalized(value))
            elif key in ("artist",):
                parts["artists"].append(normalized(value))
            elif key in METADATA_KEYS:
                parts["metadata"].append(normalized(value.replace("label=", "")))
            elif key in SECTION_KEYS or key.startswith("start_of"):
                label = re.sub(r"^label=\"?|\"$", "", value)
                if label:
                    parts["labels"].add(normalized(label))
            continue
        if not line or line.startswith("#"):
            continue
        chords = INLINE_CHORD.findall(line)
        if chords:
            parts["chords"].add(" ".join(chord.strip("[]") for chord in chords))
        lyric = normalized(INLINE_CHORD.sub("", line))
        if lyric:
            parts["lyrics"].add(lyric)
            parts["lyricCount"] += 1
        elif not chords:
            parts["lyrics"].add(normalized(line))
    return parts


def is_song_line(text, parts, lyric_words):
    words = normalized(text)
    if not words:
        return None
    if words in parts["lyrics"] or words in parts["labels"]:
        return words
    tokens = chord_tokens(text.strip())
    if tokens and all(token.strip("()[]") in parts["chords_set"] for token in tokens):
        return ""
    # A lyric a narrow column wrapped is a run of a song line's words; a joined or respelled one matches most of one.
    if len(words) >= 8:
        for lyric in lyric_words:
            if words in lyric:
                return lyric
    best = max(((difflib.SequenceMatcher(None, words, lyric).ratio(), lyric) for lyric in lyric_words if abs(len(lyric) - len(words)) < 40), default=(0, None))
    return best[1] if best[0] >= 0.8 else None


def label_import(document, cho):
    parts = song_parts(cho)
    parts["chords_set"] = {chord for row in parts["chords"] for chord in row.split()}
    lyric_words = list(parts["lyrics"])
    labels, found = [], set()
    title_done = artist_done = False
    for line in document["lines"]:
        text = line["text"]
        words = normalized(text)
        label = "JUNK"
        if not words:
            label = "BLANK"
        elif not title_done and any(title and (words == title or (title in words and len(words) < len(title) + 12)) for title in parts["titles"]):
            label, title_done = "TITLE", True
        elif not artist_done and any(artist and artist in words and len(words) < len(artist) + 16 for artist in parts["artists"]):
            label, artist_done = "ARTIST", True
        elif is_labelled(text.strip()) or any(value and re.search(rf"\b{re.escape(value)}\b", words) and len(words) < len(value) + 12 and words not in parts["lyrics"] for value in parts["metadata"]):
            label = "METADATA"
        else:
            match = is_song_line(text, parts, lyric_words)
            if match is not None:
                label = "SONG"
                if match:
                    found.add(match)
        labels.append(label)
    share = len(found & parts["lyrics"]) / max(len(parts["lyrics"]), 1)
    return labels, share


def label_printed(document, page, page_labels):
    """The printed lines take the labels of the page's lines they match, in order."""
    page_words = [normalized(line) for line in page["lines"]]
    printed_words = [normalized(line["text"]) for line in document["lines"]]
    labels = ["BLANK" if not words else None for words in printed_words]
    matcher = difflib.SequenceMatcher(None, page_words, printed_words, autojunk=False)
    for tag, a0, a1, b0, b1 in matcher.get_opcodes():
        if tag == "equal":
            for offset in range(a1 - a0):
                labels[b0 + offset] = page_labels[a0 + offset] if printed_words[b0 + offset] else "BLANK"
    matched = sum(1 for label in labels if label not in (None, "BLANK"))
    printed = sum(1 for words in printed_words if words)
    labels = ["IGNORE" if label is None else label for label in labels]
    return labels, matched / max(printed, 1)


def main(corpus):
    written, skipped = 0, []
    os.makedirs(os.path.join(corpus, "labels"), exist_ok=True)
    for path in sorted(glob.glob(os.path.join(corpus, "documents", "*.json"))):
        with open(path, encoding="utf-8") as file:
            document = json.load(file)
        if document["id"].startswith("import__"):
            cho_path = os.path.splitext(document["source"])[0] + ".cho"
            if not os.path.isfile(cho_path):
                skipped.append((document["id"], "no .cho"))
                continue
            with open(cho_path, encoding="utf-8", errors="replace") as file:
                labels, share = label_import(document, file.read())
        else:
            page_id = document["id"].split("__", 1)[1]
            label_path = os.path.join(corpus, "labels", page_id + ".json")
            page_path = os.path.join(corpus, "pages", page_id + ".json")
            if not os.path.isfile(label_path):
                skipped.append((document["id"], "page not labelled"))
                continue
            with open(label_path, encoding="utf-8") as file:
                page_labels = json.load(file)["labels"]
            with open(page_path, encoding="utf-8") as file:
                page = json.load(file)
            labels, share = label_printed(document, page, page_labels)
        if share < MIN_FOUND:
            skipped.append((document["id"], f"aligned {share:.2f}"))
            continue
        with open(os.path.join(corpus, "labels", document["id"] + ".json"), "w", encoding="utf-8") as file:
            json.dump({"id": document["id"], "source": "document", "labels": labels, "aligned": round(share, 3)}, file)
        written += 1
    for identity, reason in skipped:
        print(f"skipped {identity}: {reason}")
    print(f"labelled {written} documents, skipped {len(skipped)}")


if __name__ == "__main__":
    main(sys.argv[1])
