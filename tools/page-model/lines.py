# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
What the page model reads of a line besides its characters: the few things a chord sheet's converter would see in it on
its own (a chord row, a tab staff's string, a section heading, a labelled value), and the layout a document gives it.
The feasibility gate reads them from here; the app's library computes the same ones, and a golden file keeps the two
copies equal.
"""

import re
import unicodedata

LABELS = ["JUNK", "SONG", "TITLE", "ARTIST", "METADATA"]

CHORD = re.compile(
    r"^[(\[]?(?:[A-H]|Do|Re|Ré|Mi|Fa|Sol|La|Si)(?:#|b|♯|♭|is|es)?"
    r"(?:m|min|maj|M|dim|aug|sus|add|°|ø|\+|-|Δ|[0-9]|\(|\)|/[0-9])*"
    r"(?:/(?:[A-H]|Do|Re|Mi|Fa|Sol|La|Si)(?:#|b|♯|♭)?)?[)\]]?$"
)
FURNITURE = re.compile(r"^(?:\||\|\||/|-|x[0-9]+|[0-9]+x|\(x?[0-9]+x?\)|\.+|:\|\||\|\|:|%|N\.C\.?|NC)$", re.IGNORECASE)
TAB = re.compile(r"^\s*[a-gA-G]?[#b]?\s*[|:]?[-0-9hpbr/\\~x|*().^ ]{8,}\|?\s*$")
HEADING = re.compile(
    r"^\s*[\[(]?\s*(?:verse|chorus|bridge|intro|outro|pre-?chorus|refrain|coda|interlude|solo|instrumental|ending|tag|"
    r"strofa|refren|ritornello|estribillo|estrofa|puente|couplet|refrão|ponte|strophe|vers|refrén|sloka|zwrotka|"
    r"versszak|refrén|bridge|pont|verso|coro|ref\.?|r\.?|v\.?)\s*[0-9]*\s*[\])]?\s*:?\s*$",
    re.IGNORECASE,
)
LABELLED = re.compile(
    r"^\s*(?:key|capo|tempo|bpm|time|tuning|tonalidad|tom|tonalité|tonart|hangnem|tonalitate|tonacja|tónina|"
    r"cejilla|capotraste|kapodaszter|kapodastr|afinación|afinação|accordatura|stimmung|hangolás|words|music|lyrics|"
    r"composer|written by|letra|música|paroles|musique|text|musik|szöveg|zene|versuri|muzica|słowa|muzyka|hudba)\b"
    r"\s*[:=-]",
    re.IGNORECASE,
)


def normalized(text):
    """A line's words for matching across sources: NFC, lowercase, punctuation and spacing folded."""
    text = unicodedata.normalize("NFC", text).lower()
    text = re.sub(r"[^\w\s]", " ", text)
    return " ".join(text.split())


def chord_tokens(text):
    words = text.split()
    if not words:
        return None
    chords = [word for word in words if CHORD.match(word)]
    if chords and all(CHORD.match(word) or FURNITURE.match(word) for word in words):
        return chords
    return None


def is_chord_row(text):
    return chord_tokens(text) is not None


def is_tab(text):
    return bool(TAB.match(text)) and text.count("-") >= 6


def is_heading(text):
    return bool(HEADING.match(text)) and len(text) < 40


def is_labelled(text):
    return bool(LABELLED.match(text))


def features(line, source):
    """
    The line's numbers beside its characters, all in about 0..1: what it reads as, its length, and, for a document, its
    layout. A page's text carries no layout, so those inputs are zero for it and the source flag tells the two apart.
    """
    text = line["text"]
    stripped = text.strip()
    letters = sum(1 for c in stripped if c.isalpha())
    values = [
        1.0 if source == "document" else 0.0,
        1.0 if not stripped else 0.0,
        1.0 if is_chord_row(stripped) else 0.0,
        1.0 if is_tab(stripped) else 0.0,
        1.0 if is_heading(stripped) else 0.0,
        1.0 if is_labelled(stripped) else 0.0,
        min(len(stripped), 200) / 200.0,
        letters / max(len(stripped), 1),
        (len(text) - len(text.lstrip())) / max(len(text), 1),
        1.0 if re.search(r"https?://|www\.|\.com\b", stripped) else 0.0,
        1.0 if re.search(r"[0-9]", stripped) else 0.0,
    ]
    if source == "document":
        values += [
            min(line.get("size", 1.0), 3.0) / 3.0,
            1.0 if line.get("bold") else 0.0,
            1.0 if line.get("italic") else 0.0,
            1.0 if line.get("monospace") else 0.0,
            max(0.0, min(line.get("indent", 0.0), 1.0)),
            (line.get("column", -1) + 1) / 4.0,
            min(line.get("page", 0), 20) / 20.0,
            1.0 if line.get("heading") else 0.0,
        ]
    else:
        values += [0.0] * 8
    return values


FEATURES = 19
