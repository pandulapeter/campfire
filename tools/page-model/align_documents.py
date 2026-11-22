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

- an import corpus document by its hand-corrected `.cho`: a chord row, a line holding one of the song's lyrics or
  section labels, and a wrapped fragment of a lyric between those are `SONG`, the line holding the `{title}` or
  `{artist}` value is `TITLE` or `ARTIST`, one naming a key, capo, tempo, time, or credit is `METADATA`, the rest `JUNK`;
- a made-up document (`synthetic_documents.py`) by the lines it was made from;
- a web page printed to PDF by its page's agreed labels (`labels/<page id>.json`), each printed line taking the label of
  the page line it matches.

Each label file names the song's group (its title, or its page), so the gate never trains on a song it scores. A
document whose song is not found well enough among its lines is left out rather than fixed by hand.

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
    parts = {"lyrics": set(), "labels": set(), "titles": [], "artists": [], "metadata": [], "lyricCount": 0}
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
    # Every chord row of a song's own document is the song's, whatever spelling (D#, Eb) or voicing it prints.
    if chord_tokens(text.strip()):
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
    lyric_words = list(parts["lyrics"])
    labels, found = [], set()
    for line in document["lines"]:
        text = line["text"]
        words = normalized(text)
        label = "JUNK"
        if not words:
            label = "BLANK"
        elif is_labelled(text.strip()) or any(value and re.search(rf"\b{re.escape(value)}\b", words) and len(words) < len(value) + 12 and words not in parts["lyrics"] for value in parts["metadata"]):
            label = "METADATA"
        else:
            match = is_song_line(text, parts, lyric_words)
            if match is not None:
                label = "SONG"
                if match:
                    found.add(match)
        labels.append(label)
    # A word or two a narrow column wrapped onto a line of its own ("us", "minds.") is the song's when it sits between
    # the song's lines and a lyric holds it.
    printed = [index for index, label in enumerate(labels) if label != "BLANK"]
    for position, index in enumerate(printed):
        if labels[index] != "JUNK" or not 0 < position < len(printed) - 1:
            continue
        words = normalized(document["lines"][index]["text"])
        if labels[printed[position - 1]] == "SONG" and labels[printed[position + 1]] == "SONG" and any(words in lyric for lyric in lyric_words):
            labels[index] = "SONG"
    # The title and the artist are picked among the lines that are not the song, since a chorus often sings the title: the
    # largest line holding the title (a site's "X chords by Y" included), then the shortest other one holding the artist.
    others = [index for index, label in enumerate(labels) if label in ("JUNK", "METADATA")]
    title = pick(document, others, parts["titles"], slack=60, key=lambda index: (-document["lines"][index].get("size", 1.0), index))
    if title is not None:
        labels[title] = "TITLE"
    artist = pick(document, [index for index in others if index != title], parts["artists"], slack=40, key=lambda index: (len(document["lines"][index]["text"]), index))
    if artist is not None:
        labels[artist] = "ARTIST"
    share = len(found & parts["lyrics"]) / max(len(parts["lyrics"]), 1)
    return labels, share


def pick(document, indices, values, slack, key):
    candidates = [
        index for index in indices
        if any(value and value in normalized(document["lines"][index]["text"]) and len(normalized(document["lines"][index]["text"])) <= len(value) + slack for value in values)
    ]
    return min(candidates, key=key) if candidates else None


def label_printed(document, page, page_labels):
    """
    The printed lines take the labels of the page's lines they match, in order; a line print wrapped differently from
    the screen takes the label of the page line holding it. The alignment is judged by the song's own lines found.
    """
    page_words = [normalized(line) for line in page["lines"][: len(page_labels)]]
    printed_words = [normalized(line["text"]) for line in document["lines"]]
    labels = ["BLANK" if not words else None for words in printed_words]
    matcher = difflib.SequenceMatcher(None, page_words, printed_words, autojunk=False)
    found = set()
    for tag, a0, a1, b0, b1 in matcher.get_opcodes():
        if tag == "equal":
            for offset in range(a1 - a0):
                if printed_words[b0 + offset]:
                    labels[b0 + offset] = page_labels[a0 + offset]
                    found.add(a0 + offset)
    # A two-column print reads its lines in another order than the screen: a line found anywhere on the page counts.
    label_of = {}
    for words, label in zip(page_words, page_labels):
        if words and label != "IGNORE":
            label_of.setdefault(words, set()).add(label)
    for index, label in enumerate(labels):
        if label is None and len(label_of.get(printed_words[index], ())) == 1:
            labels[index] = next(iter(label_of[printed_words[index]]))
        if labels[index] is None and len(printed_words[index]) >= 6:
            labels[index] = next((page_labels[at] for at, words in enumerate(page_words) if printed_words[index] in words and page_labels[at] != "IGNORE"), "IGNORE")
        elif labels[index] is None:
            labels[index] = "IGNORE"
    song = [index for index, label in enumerate(page_labels) if label in ("SONG", "METADATA") and page_words[index]]
    if not song:
        return labels, 1.0 if sum(1 for label in labels if label not in ("IGNORE", "BLANK")) > len(labels) / 2 else 0.0
    printed = set(printed_words)
    return labels, sum(1 for index in song if index in found or page_words[index] in printed) / len(song)


def label_synthetic(document, made):
    """
    A made-up document's lines take the labels they were made with. A printed line nothing was made for is the browser's
    running header or footer where it opens or closes its page, and is left out of training anywhere else.
    """
    made_words = [normalized(text) for text, _ in made]
    printed_words = [normalized(line["text"]) for line in document["lines"]]
    labels = ["BLANK" if not words else None for words in printed_words]
    matcher = difflib.SequenceMatcher(None, made_words, printed_words, autojunk=False)
    for tag, a0, a1, b0, b1 in matcher.get_opcodes():
        if tag == "equal":
            for offset in range(a1 - a0):
                if printed_words[b0 + offset]:
                    labels[b0 + offset] = made[a0 + offset][1]
    # A line a narrow column wrapped is a run of the words of the line it was made from.
    for index, label in enumerate(labels):
        if label is None and len(printed_words[index]) >= 6:
            label_of = next((made[at][1] for at, words in enumerate(made_words) if printed_words[index] in words), None)
            labels[index] = label_of
    pages = {}
    for index, line in enumerate(document["lines"]):
        if printed_words[index]:
            pages.setdefault(line.get("page", 0), []).append(index)
    edges = {index for indices in pages.values() for index in indices[:2] + indices[-2:]}
    for index, label in enumerate(labels):
        if label is None:
            labels[index] = "JUNK" if index in edges else "IGNORE"
    matched = sum(1 for label in labels if label not in ("IGNORE", "BLANK"))
    return labels, matched / max(sum(1 for words in printed_words if words), 1)


def group_of(cho):
    titles = song_parts(cho)["titles"]
    return titles[0] if titles else None


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
                cho = file.read()
            labels, share = label_import(document, cho)
            group = group_of(cho) or document["id"]
        elif document["id"].startswith("synthetic__"):
            with open(os.path.join(corpus, "synthetic", document["id"].split("__", 1)[1] + ".lines.json"), encoding="utf-8") as file:
                made = json.load(file)
            labels, share = label_synthetic(document, [tuple(row) for row in made["lines"]])
            group = made["group"]
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
            group = "page:" + page_id
        if share < MIN_FOUND:
            skipped.append((document["id"], f"aligned {share:.2f}"))
            continue
        with open(os.path.join(corpus, "labels", document["id"] + ".json"), "w", encoding="utf-8") as file:
            json.dump({"id": document["id"], "source": "document", "group": group, "labels": labels, "aligned": round(share, 3)}, file)
        written += 1
    for identity, reason in skipped:
        print(f"skipped {identity}: {reason}")
    print(f"labelled {written} documents, skipped {len(skipped)}")


if __name__ == "__main__":
    main(sys.argv[1])
