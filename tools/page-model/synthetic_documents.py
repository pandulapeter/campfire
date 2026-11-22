# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Documents made up for training, their labels known by construction: each ChordPro song of the import corpus set as a
page in several layouts (chord rows in a monospace type, chords over their syllables in a proportional one, one to three
columns, several types and sizes) with the clutter a printed web page or a songbook carries around it (a site's header,
menus, controls, ads, related songs, comments, a footer, the browser's printed date and address, a running header, a page
number), printed to PDF by headless Chrome from a local page, never anything fetched. `PageCorpusDocumentsDump` then reads
them through the app's own reader, and `align_documents.py` labels each line by the text it was made from.

    .venv/bin/python -I tools/page-model/synthetic_documents.py <corpus folder> <import corpus> [--per-song N]

Writes `synthetic/<id>.pdf` and `synthetic/<id>.lines.json` (the made lines with their labels and the song's group).
Clutter comes from the agreed pages' `JUNK` runs where they are labelled, else from the built-in lines below.
"""

import argparse
import asyncio
import glob
import hashlib
import html
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from playwright.async_api import async_playwright  # noqa: E402

from lines import normalized  # noqa: E402

DIRECTIVE = re.compile(r"^\{\s*([a-z_]+)\s*[: ]?\s*(.*?)\s*\}$")
CHORD = re.compile(r"(\[[^\]]*\])")
DETAILS = {"key": ["Key", "Tonalidad", "Tom", "Tonart", "Hangnem"], "capo": ["Capo", "Cejilla", "Kapodaszter"], "tempo": ["Tempo", "BPM"], "time": ["Time", "Takt"]}
FONTS = ["Georgia, serif", "Arial, sans-serif", "Helvetica, sans-serif", "'Times New Roman', serif", "Verdana, sans-serif", "'Courier New', monospace"]
BUILT_IN_JUNK = [
    "Home", "Songs", "Artists", "Chords", "Tabs", "Log in", "Sign up", "Search", "Menu", "Print", "Share", "Favourite",
    "Transpose -1 +1", "Font size A- A+", "Autoscroll", "Download PDF", "Report a problem", "Rate this song ★★★★☆",
    "Advertisement", "Related songs", "More songs by this artist", "Comments (12)", "Add a comment", "Top 100 songs",
    "Privacy policy", "Terms of use", "Contact", "© 2025 All rights reserved", "Accept cookies", "Difficulty: intermediate",
    "Views: 12,345", "Last edited 3 days ago", "Submit corrections", "Chord diagrams", "Start jam", "Show chords",
    "Simplify chords", "Inicio", "Canciones", "Accueil", "Startseite", "Kezdőlap", "Strona główna", "Úvod", "Início",
    "Imprimer", "Drucken", "Nyomtatás", "Drukuj", "Tisk", "Imprimir", "Stampa", "Publicidade", "Werbung", "Hirdetés",
]


def parse(cho):
    """A `.cho` as (kind, value) rows: title, artist, detail, label, chords-and-lyric, blank."""
    rows = []
    for raw in cho.split("{new_song}")[0].splitlines():
        line = raw.rstrip()
        directive = DIRECTIVE.match(line.strip())
        if directive:
            key, value = directive.group(1).lower(), directive.group(2)
            if key in ("title", "t") and value:
                rows.append(("title", value))
            elif key in ("artist", "subtitle", "st") and value:
                rows.append(("artist", value))
            elif key in DETAILS and value:
                rows.append(("detail", (key, value)))
            elif (key.startswith("start_of") or key in ("soc", "sov", "sob")) and value:
                rows.append(("label", re.sub(r"^label=\"?|\"$", "", value)))
            elif key in ("comment", "c", "ci") and value:
                rows.append(("label", value))
            continue
        if line.startswith("#"):
            continue
        rows.append(("blank", "") if not line.strip() else ("line", line))
    return rows


def pieces(line):
    """A ChordPro line as (chord, text) pairs, the chord over the text after it."""
    result, chord = [], ""
    for part in CHORD.split(line):
        if CHORD.fullmatch(part):
            if chord:
                result.append((chord, ""))
            chord = part[1:-1]
        elif part or chord:
            result.append((chord, part))
            chord = ""
    if chord:
        result.append((chord, ""))
    return result


def song_html(rows, layout, rng, made):
    """The song's own lines as HTML, each made line recorded in `made` with its label."""
    out = []
    detail_names = {key: rng.choice(names) for key, names in DETAILS.items()}
    title_tag = rng.choice(["h1", "h2"])
    for kind, value in rows:
        if kind == "title":
            text = value + rng.choice(["", "", " Chords", " (chords)", " – Acordes", " Akordy", " Tab"])
            out.append(f"<{title_tag} class='title'>{html.escape(text)}</{title_tag}>")
            made.append((text, "TITLE"))
        elif kind == "artist":
            text = rng.choice(["", "by ", "Artist: ", ""]) + value
            out.append(f"<div class='artist'>{html.escape(text)}</div>")
            made.append((text, "ARTIST"))
        elif kind == "detail":
            text = f"{detail_names[value[0]]}: {value[1]}"
            out.append(f"<div class='detail'>{html.escape(text)}</div>")
            made.append((text, "METADATA"))
        elif kind == "label":
            text = rng.choice(["{}", "[{}]", "{}:"]).format(value)
            out.append(f"<div class='label'>{html.escape(text)}</div>")
            made.append((text, "SONG"))
        elif kind == "blank":
            out.append("<div class='gap'></div>")
        else:
            parts = pieces(value)
            chords = [chord for chord, _ in parts if chord]
            lyric = "".join(text for _, text in parts)
            if layout == "rows":
                row, column = "", 0
                for chord, text in parts:
                    if chord:
                        row = row.ljust(column) + chord + " "
                    column += len(text)
                if chords:
                    out.append(f"<pre class='chords'>{html.escape(row.rstrip())}</pre>")
                    made.append((" ".join(chords), "SONG"))
                if lyric.strip():
                    out.append(f"<pre class='lyric'>{html.escape(lyric)}</pre>")
                    made.append((lyric, "SONG"))
            else:
                cells = "".join(
                    f"<span class='cell'><span class='chord'>{html.escape(chord) or '&nbsp;'}</span><span class='word'>{html.escape(text).replace(' ', '&nbsp;') or '&nbsp;'}</span></span>"
                    for chord, text in parts
                )
                out.append(f"<div class='sung'>{cells}</div>")
                if chords:
                    made.append((" ".join(chords), "SONG"))
                if lyric.strip():
                    made.append((lyric, "SONG"))
    return "\n".join(out)


def junk_block(runs, rng, made, count):
    lines = []
    if runs and rng.random() < 0.7:
        run = rng.choice(runs)
        start = rng.randrange(max(1, len(run) - count))
        lines = [text for text in run[start:start + count] if text.strip()]
    if not lines:
        lines = rng.sample(BUILT_IN_JUNK, min(count, len(BUILT_IN_JUNK)))
    for text in lines:
        made.append((text, "JUNK"))
    return "".join(f"<div class='junk'>{html.escape(text)}</div>" for text in lines)


def document_html(rows, rng, runs):
    made = []
    layout = rng.choice(["rows", "rows", "cells"])
    columns = rng.choice([1, 1, 1, 2, 3])
    font = rng.choice(FONTS)
    size = rng.choice([10, 11, 12, 13, 14])
    kind = rng.choice(["web", "web", "book"])
    top = junk_block(runs, rng, made, rng.randint(3, 25)) if kind == "web" else ""
    song = song_html(rows, layout, rng, made)
    inserted = ""
    if kind == "web" and rng.random() < 0.4:
        inserted = junk_block(runs, rng, made, rng.randint(1, 3))
    bottom = junk_block(runs, rng, made, rng.randint(5, 40)) if kind == "web" else ""
    # Made lines are matched in reading order, so the song's ad block goes after the song in the list as on the page.
    style = f"""
      body {{ font-family: {font}; font-size: {size}pt; margin: 0; }}
      .song {{ column-count: {columns}; column-gap: 2em; }}
      .title {{ font-size: {rng.choice([1.4, 1.8, 2.2])}em; margin: 0.2em 0; column-span: all; }}
      .artist {{ font-size: {rng.choice([1.0, 1.2])}em; font-style: {rng.choice(['normal', 'italic'])}; column-span: all; }}
      .detail {{ font-size: 0.9em; }}
      .label {{ font-weight: bold; margin-top: 0.6em; }}
      .gap {{ height: 0.8em; }}
      pre {{ margin: 0; font-family: 'Courier New', monospace; white-space: pre-wrap; }}
      .chords {{ font-weight: {rng.choice(['bold', 'normal'])}; color: #333; }}
      .sung {{ display: flex; flex-wrap: wrap; }}
      .cell {{ display: inline-flex; flex-direction: column; break-inside: avoid; }}
      .chord {{ font-weight: bold; font-size: 0.85em; padding-inline-end: 0.4em; }}
      .junk {{ font-size: {rng.choice([0.7, 0.8, 0.9, 1.0])}em; color: #555; }}
    """
    body = f"<div class='top'>{top}</div><div class='song'>{song}</div><div>{inserted}</div><div class='bottom'>{bottom}</div>"
    letters = [c for _, value in rows if isinstance(value, str) for c in value if c.isalpha()]
    direction = "rtl" if sum(1 for c in letters if "\u0590" <= c <= "\u06ff") > len(letters) / 2 else "ltr"
    return f"<!doctype html><meta charset='utf-8'><style>{style}</style><body dir='{direction}'>{body}</body>", made, kind


def junk_runs_of(corpus):
    runs = []
    for path in glob.glob(os.path.join(corpus, "labels", "*.json")):
        with open(path, encoding="utf-8") as file:
            labelled = json.load(file)
        if labelled["source"] != "page":
            continue
        with open(os.path.join(corpus, "pages", labelled["id"] + ".json"), encoding="utf-8") as file:
            page = json.load(file)
        run = []
        for text, label in zip(page["lines"], labelled["labels"]):
            if label == "JUNK":
                run.append(text)
            elif label != "BLANK":
                if len(run) >= 3:
                    runs.append(run)
                run = []
        if len(run) >= 3:
            runs.append(run)
    return runs


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus")
    parser.add_argument("import_corpus")
    parser.add_argument("--per-song", type=int, default=4)
    parser.add_argument("--seed", type=int, default=11)
    arguments = parser.parse_args()
    rng = random.Random(arguments.seed)
    runs = junk_runs_of(arguments.corpus)
    folder = os.path.join(arguments.corpus, "synthetic")
    os.makedirs(folder, exist_ok=True)
    songs = sorted(glob.glob(os.path.join(arguments.import_corpus, "**", "*.cho"), recursive=True))
    written = 0
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        context = await browser.new_context(offline=True)
        page = await context.new_page()
        for path in songs:
            with open(path, encoding="utf-8", errors="replace") as file:
                rows = parse(file.read())
            if sum(1 for kind, _ in rows if kind == "line") < 4:
                continue
            title = next((value for kind, value in rows if kind == "title"), os.path.basename(path))
            for copy in range(arguments.per_song):
                markup, made, kind = document_html(rows, rng, runs)
                identity = hashlib.sha1(f"{path}:{copy}".encode()).hexdigest()[:12]
                await page.set_content(markup)
                header = kind == "web" or rng.random() < 0.6
                await page.pdf(
                    path=os.path.join(folder, identity + ".pdf"),
                    format=rng.choice(["A4", "Letter"]),
                    display_header_footer=header,
                    header_template=f"<div style='font-size:8px;width:100%;padding:0 1cm'><span class='date'></span> <span style='float:right'>{html.escape(title)}</span></div>" if header else "",
                    footer_template="<div style='font-size:8px;width:100%;padding:0 1cm'>https://songs.example.org/" + identity + " <span style='float:right'><span class='pageNumber'></span>/<span class='totalPages'></span></span></div>" if header else "",
                    margin={"top": "1.5cm", "bottom": "1.5cm", "left": "1.5cm", "right": "1.5cm"},
                )
                with open(os.path.join(folder, identity + ".lines.json"), "w", encoding="utf-8") as file:
                    json.dump({"id": identity, "group": normalized(title), "lines": made}, file, ensure_ascii=False)
                written += 1
        await browser.close()
    print(f"wrote {written} synthetic documents into {folder}")


if __name__ == "__main__":
    asyncio.run(main())
