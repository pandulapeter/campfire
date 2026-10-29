# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""Hebrew chord sheets written in visual order, the way other apps' PDFs carry right-to-left text.

The words are Psalm 133:1 ("Hine ma tov"), public domain, with chords of no published arrangement. Run once with
ReportLab; the generated files are committed, not rebuilt by tests. Chrome prints the HTML written here as a separate
manual command documented in README.md, so that its actual producer stays visible.

- reportlab-hebrew.pdf: every line drawn as one string already in visual order (right-to-left runs reversed and their
  brackets mirrored by this script), right-aligned, with no ActualText, as a generator with no bidi engine of its own
  writes it. Each chord hangs from the syllable it is sung on: its right edge at a Hebrew letter's right edge, its left
  edge at a Latin letter's left edge.
- chrome-hebrew.txt and chrome-hebrew.html: the same song as a plain-text sheet whose chord rows are typed to stand over
  their syllables in a right-to-left display (the HTML shows it in a `<pre dir="rtl">`), which Chrome lays out with its
  own bidi engine.
"""

from pathlib import Path

from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parent
MIRRORED = {"(": ")", ")": "(", "[": "]", "]": "[", "<": ">", ">": "<"}

TITLE = "הנה מה טוב"
SUBTITLE = "תהלים 133:1"
# Each lyric with the chords sung on it, by the index of the character (in reading order) a chord is sung on.
LINES = [
    ("הנה מה טוב ומה נעים", {0: "Am", 11: "Dm"}),
    ("שבת אחים גם יחד", {0: "E", 12: "Am"}),
    ("שבת אחים גם יחד (Psalm 133)", {0: "Dm", 17: "E"}),
    ("Hine ma tov (הנה מה טוב)", {0: "Am", 20: "E"}),
]


def kind(character):
    if "֐" <= character <= "׿":
        return "R"
    if character.isalpha():
        return "L"
    if character.isdigit():
        return "EN"
    return "N"


def levels(text, rtl):
    """The embedding level of each character: the subset of UAX #9 a line of lyrics needs, no explicit embeddings."""
    kinds = [kind(c) for c in text]
    base = "R" if rtl else "L"
    resolved = list(kinds)
    if not rtl:
        for index, value in enumerate(kinds):
            if value == "EN":
                before = next((k for k in reversed(kinds[:index]) if k in ("R", "L")), base)
                after = next((k for k in kinds[index + 1:] if k in ("R", "L")), base)
                resolved[index] = "ENR" if before == after == "R" else "L"
    result = []
    for index, value in enumerate(resolved):
        if value == "N":
            before = next((k for k in reversed(resolved[:index]) if k != "N"), base)
            after = next((k for k in resolved[index + 1:] if k != "N"), base)
            if rtl:
                ltr = before in ("L", "EN") and after in ("L", "EN")
                spaced = before == after == "EN" and " " in text[index:index + 1]
                result.append(2 if ltr and not spaced else 1)
            else:
                result.append(1 if before in ("R", "ENR") and after in ("R", "ENR") else 0)
        elif value == "R":
            result.append(1)
        elif value in ("L", "EN"):
            result.append(2 if rtl else 0)
        else:
            result.append(2)
    return result


def visual(text, rtl):
    """The order the characters of [text] are drawn in from the left, as indices into it, and the drawn text."""
    order = list(range(len(text)))
    marks = levels(text, rtl)
    for level in range(max(marks), 0, -1):
        index = 0
        while index < len(order):
            if marks[order[index]] >= level:
                end = index
                while end < len(order) and marks[order[end]] >= level:
                    end += 1
                order[index:end] = reversed(order[index:end])
                index = end
            else:
                index += 1
    drawn = "".join(MIRRORED.get(text[i], text[i]) if marks[i] % 2 else text[i] for i in order)
    return order, drawn


def is_rtl(text):
    return sum(1 if kind(c) == "R" else -1 if kind(c) == "L" else 0 for c in text) > 0


def reportlab():
    pdfmetrics.registerFont(TTFont("Arial", "/System/Library/Fonts/Supplemental/Arial.ttf"))
    pdfmetrics.registerFont(TTFont("Arial-Bold", "/System/Library/Fonts/Supplemental/Arial Bold.ttf"))
    pdf = canvas.Canvas(str(ROOT / "reportlab-hebrew.pdf"), pagesize=(612, 792), pageCompression=1, invariant=1)
    right = 562
    pdf.setFont("Arial-Bold", 22)
    pdf.drawRightString(right, 740, visual(TITLE, True)[1])
    pdf.setFont("Arial", 10)
    pdf.drawRightString(right, 720, visual(SUBTITLE, True)[1])
    y = 680
    for lyric, chords in LINES:
        rtl = is_rtl(lyric)
        order, drawn = visual(lyric, rtl)
        width = pdfmetrics.stringWidth(drawn, "Arial", 12)
        left = right - width if rtl else 50
        pdf.setFont("Arial", 12)
        pdf.drawString(left, y - 14, drawn)
        for index, chord in chords.items():
            at = order.index(index)
            start = left + pdfmetrics.stringWidth(drawn[:at], "Arial", 12)
            end = start + pdfmetrics.stringWidth(drawn[at], "Arial", 12)
            chord_width = pdfmetrics.stringWidth(chord, "Arial", 12)
            pdf.drawString(end - chord_width if kind(lyric[index]) == "R" else start, y, chord)
        y -= 40
    pdf.save()


def plain_text():
    """Chord rows typed so that a right-to-left display stands each chord over its syllable, as the PDF hangs them."""
    rows = [TITLE, SUBTITLE, ""]
    for lyric, chords in LINES:
        order, _ = visual(lyric, True)
        # A column counted from the right edge every line of a right-to-left display is aligned to.
        cells = {}
        for index, chord in chords.items():
            column = len(lyric) - 1 - order.index(index)
            first = column + len(chord) - 1 if kind(lyric[index]) == "R" else column
            for offset, character in enumerate(chord):
                cells[first - offset] = character
        # The chords and the spaces between them are one left-to-right run, which the display draws from its left end,
        # so the row is typed as it shows from the left, after the spaces that set it in from the right.
        row = " " * min(cells) + "".join(cells.get(column, " ") for column in range(max(cells), min(cells) - 1, -1))
        rows += [row, lyric]
    text = "\n".join(rows) + "\n"
    (ROOT / "chrome-hebrew.txt").write_text(text, encoding="utf-8")
    header = (ROOT / "chrome.html").read_text(encoding="utf-8").split("<!doctype html>")[0]
    escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    (ROOT / "chrome-hebrew.html").write_text(
        header + '<!doctype html>\n<html lang="he" dir="rtl">\n<head><meta charset="utf-8"><title>Hine ma tov</title>\n'
        "<style>\n@page { size: letter; margin: 50pt; }\n"
        'body { margin: 0; color: #000; font: 12pt "Courier New", monospace; }\n'
        "pre { margin: 0; font: inherit; line-height: 16pt; }\n</style></head>\n"
        f'<body><pre dir="rtl">{escaped}</pre>\n</body></html>\n',
        encoding="utf-8",
    )


if __name__ == "__main__":
    reportlab()
    plain_text()
