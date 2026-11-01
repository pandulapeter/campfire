# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""Independent generator fixtures, using public-domain songs (Amazing Grace, and in the songbooks three more).

Run once with python-docx, ReportLab and pypdf; the generated files are committed,
not rebuilt by tests. LibreOffice and Chrome exports are separate manual commands
documented in README.md so their actual producers remain visible.
"""

import re
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml.ns import qn
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader, PdfWriter

ROOT = Path(__file__).resolve().parent
LYRIC = "Amazing grace how sweet the sound"
CHORDS = "G             C             G"


def word():
    doc = Document()
    section = doc.sections[0]
    section.page_width, section.page_height = Inches(8.5), Inches(11)
    for name in ("Normal", "Title", "Heading 1"):
        font = doc.styles[name].font
        font.name = "Courier New"
        font.size = Pt(12 if name == "Normal" else 22 if name == "Title" else 12)
        font.color.rgb = RGBColor(0, 0, 0)
        for border in list(doc.styles[name].element.iter(qn("w:pBdr"))):
            border.getparent().remove(border)
        for fonts in doc.styles[name].element.iter(qn("w:rFonts")):
            for attribute in ("asciiTheme", "hAnsiTheme", "eastAsiaTheme", "cstheme"):
                fonts.attrib.pop(qn("w:" + attribute), None)
        for color in doc.styles[name].element.iter(qn("w:color")):
            color.attrib.pop(qn("w:themeColor"), None)
    doc.add_paragraph("Amazing Grace", "Title")
    doc.add_paragraph("by John Newton")
    doc.add_paragraph("Verse 1", "Heading 1")
    doc.add_paragraph(CHORDS)
    doc.add_paragraph(LYRIC)
    path = ROOT / "python-docx.docx"
    doc.save(path)
    with ZipFile(path) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with ZipFile(path, "w", ZIP_DEFLATED) as stable:
        for name, data in entries:
            info = ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            stable.writestr(info, data)


def sheet(pdf, x=50, y=700, section="Verse 1", font="Courier", title=True):
    if title:
        pdf.setFont("Helvetica-Bold", 22)
        pdf.drawString(x, y + 50, "Amazing Grace")
        pdf.setFont("Helvetica", 10)
        pdf.drawString(x, y + 30, "by John Newton")
    pdf.setFont(font, 12)
    pdf.drawString(x, y, section)
    pdf.drawString(x, y - 20, CHORDS)
    pdf.drawString(x, y - 34, LYRIC)


def pdfs():
    pdf = canvas.Canvas(str(ROOT / "reportlab.pdf"), pagesize=(612, 792), pageCompression=1, invariant=1)
    sheet(pdf)
    pdf.save()
    pdfmetrics.registerFont(TTFont("Unicode", "/System/Library/Fonts/Supplemental/Arial.ttf"))
    pdf = canvas.Canvas(str(ROOT / "reportlab-unicode.pdf"), pagesize=(612, 792), pageCompression=1, invariant=1)
    pdf.setFont("Unicode", 12)
    pdf.drawString(50, 700, "\u00c1rv\u00edzt\u0171r\u0151 t\u00fck\u00f6rf\u00far\u00f3g\u00e9p \u03a9 \u0416")
    pdf.save()
    pdf = canvas.Canvas(str(ROOT / "two-column.pdf"), pagesize=(800, 792), pageCompression=1, invariant=1)
    sheet(pdf, x=40)
    sheet(pdf, x=420, section="Verse 2", title=False)
    pdf.save()
    pdf = canvas.Canvas(str(ROOT / "songbook.pdf"), pagesize=(612, 792), pageCompression=1, invariant=1)
    sheet(pdf)
    pdf.showPage()
    pdf.setFont("Helvetica-Bold", 22)
    pdf.drawString(50, 750, "Amazing Grace Second Sheet")
    sheet(pdf, section="Verse 2", title=False)
    pdf.save()
    songbooks()
    reader = PdfReader(ROOT / "reportlab.pdf")
    writer = PdfWriter()
    writer.append(reader)
    writer.encrypt("")
    writer.write(ROOT / "protected.pdf")


SONGS = [
    ("Amazing Grace", "John Newton", [
        "[G]Amazing grace how [C]sweet the [G]sound",
        "That saved a [D]wretch like me",
    ]),
    ("Oh! Susanna", "Stephen Foster", [
        "[F]Oh! Susanna, oh [C]don't you cry for me",
        "For I come from Alabama with my [G]banjo on my [C]knee",
        "I [C]come from Alabama with my banjo on my [G]knee",
        "I'm going to Louisiana, my true love [G]for to [C]see",
    ]),
    ("Home on the Range", "Brewster Higley", [
        "Oh [G]give me a home where the [C]buffalo roam",
        "Where the [G]deer and the antelope [D]play",
    ]),
    ("Shenandoah", None, [
        "Oh [C]Shenandoah, I [F]long to [C]see you",
        "[Am]Away, you [F]rolling [C]river",
    ]),
]


def song_lines(pdf, rows, x, y, chord_font, lyric_font):
    """Draws ChordPro-like rows, each chord over the character it stands before, measured in the lyric's own font."""
    for row in rows:
        font = lyric_font
        lyric = re.sub(r"\[[^]]*]", "", row)
        offset = 0
        pdf.setFont(*chord_font)
        for match in re.finditer(r"\[([^]]*)]", row):
            position = match.start() - offset
            pdf.drawString(x + pdfmetrics.stringWidth(lyric[:position], *font), y, match.group(1))
            offset += len(match.group())
        pdf.setFont(*font)
        pdf.drawString(x, y - 12, lyric)
        y -= 30
    return y


def numbered_songbook():
    """A word processor's songbook: numbered titles and chords in bold at the lyrics' size, one song a page, a cover in
    a larger type, a contents page, and a song whose title has a bold label under it, as a chorus does, which only the
    contents tells from one."""
    pdf = canvas.Canvas(str(ROOT / "songbook-numbered.pdf"), pagesize=(612, 792), pageCompression=1, invariant=1)
    page = 1
    bold, plain = ("Helvetica-Bold", 10), ("Helvetica", 10)

    def finish():
        nonlocal page
        pdf.setFont(*plain)
        pdf.drawCentredString(306, 40, str(page))
        pdf.showPage()
        page += 1

    def heading(index, title, artist):
        return f"{index + 1}. {title}" + (f" ({artist})" if artist else "")

    pdf.setFont("Helvetica", 20)
    pdf.drawString(72, 700, "Songs for Singing Along")
    pdf.setFont(*plain)
    pdf.drawString(72, 676, "Compiled by friends")
    finish()
    pdf.setFont(*plain)
    pdf.drawString(72, 720, "Contents")
    for index, (title, artist, _) in enumerate(SONGS):
        pdf.drawString(72, 700 - index * 14, heading(index, title, artist))
    finish()
    for index, (title, artist, rows) in enumerate(SONGS):
        pdf.setFont(*bold)
        pdf.drawString(72, 720, heading(index, title, artist))
        if len(rows) > 2:
            # The chorus first, under a bold label, and the verse on the next page.
            pdf.drawString(72, 700, "CHORUS:")
            song_lines(pdf, rows[:2], 72, 680, bold, plain)
            finish()
            pdf.setFont(*bold)
            pdf.drawString(72, 720, "VERSE:")
            song_lines(pdf, rows[2:], 72, 700, bold, plain)
        else:
            song_lines(pdf, rows, 72, 690, bold, plain)
        finish()
    pdf.save()


def styled_songbook():
    """A typeset songbook: a large bold title type, songs following each other on a page, a title wrapped in two, and a
    chorus set apart in italic with no heading."""
    pdf = canvas.Canvas(str(ROOT / "songbook-styled.pdf"), pagesize=(612, 792), pageCompression=1, invariant=1)
    chord_font, lyric_font = ("Helvetica-Oblique", 9), ("Helvetica", 10)

    def title(lines, artist, y):
        pdf.setFont("Helvetica-Bold", 17)
        for line in lines:
            pdf.drawString(72, y, line)
            y -= 20
        if artist:
            pdf.setFont("Helvetica-Bold", 10)
            pdf.drawString(72, y, artist)
            y -= 14
        return y - 16

    pdf.setFont("Helvetica-Bold", 25)
    pdf.drawString(72, 700, "Guitar Songbook")
    pdf.setFont("Helvetica-Bold", 12)
    pdf.drawString(72, 670, "Folk songs")
    pdf.showPage()
    pdf.setFont("Helvetica-Bold", 17)
    pdf.drawString(72, 720, "Songs Index")
    pdf.setFont("Helvetica-Bold", 10)
    for index, (name, _, _) in enumerate(SONGS):
        pdf.drawString(72, 690 - index * 14, name + " " + "." * (60 - len(name)) + f" {index + 1}")
    pdf.showPage()
    y = title(["1 Amazing Grace"], "John Newton", 720)
    y = song_lines(pdf, SONGS[0][2], 72, y, chord_font, lyric_font)
    y = title(["2 Oh! Susanna, oh don't", "you cry for me"], "Stephen Foster", y - 20)
    song_lines(pdf, SONGS[1][2][2:], 72, y, chord_font, lyric_font)
    pdf.showPage()
    # The chorus in italic, with no heading, the verse before it plain.
    y = song_lines(pdf, SONGS[1][2][:2], 72, 720, chord_font, ("Helvetica-Oblique", 10))
    y = title(["3 Home on the Range"], "Brewster Higley", y - 20)
    y = song_lines(pdf, SONGS[2][2], 72, y, chord_font, lyric_font)
    y = title(["4 Shenandoah"], None, y - 20)
    song_lines(pdf, SONGS[3][2], 72, y, chord_font, lyric_font)
    pdf.showPage()
    pdf.save()


def songbooks():
    numbered_songbook()
    styled_songbook()


if __name__ == "__main__":
    word()
    pdfs()
