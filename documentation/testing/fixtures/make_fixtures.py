# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Writes the files the manual test documents in documentation/testing/ refer to.

    python3 make_fixtures.py                     # everything except the large library
    python3 make_fixtures.py --large 2000 50     # also a generated library of 2000 songs and 50 setlists

The output is a `campfire-fixtures/` folder and a `campfire-fixtures.zip` of it, written into the current directory.
Run it outside the repository (the Desktop, Downloads, a USB stick) so nothing generated is ever committed. Python 3.8+
and nothing else; the encodings are written byte for byte, so the files are the same on every machine.
"""

import argparse
import json
import os
import shutil
import sys
import unicodedata
import zipfile

ROOT = "campfire-fixtures"
BOM = "﻿"


def write_bytes(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as file:
        file.write(data)


def write_text(path, text, encoding="utf-8", newline="\n"):
    write_bytes(path, text.replace("\n", newline).encode(encoding))


def setlist(title, files, description=None, extra=None, entry_extra=None):
    document = {"title": title}
    if description:
        document["description"] = description
    document.update({"priority": 0, "isArchived": False})
    document["songs"] = [
        dict({"file": name, "transposition": 0}, **(entry_extra or {}) if index == 0 else {})
        for index, name in enumerate(files)
    ]
    if extra:
        document.update(extra)
    return json.dumps(document, ensure_ascii=False, indent=4) + "\n"


def zip_folder(zip_path, entries):
    """entries: list of (name inside the archive, bytes)."""
    full = os.path.join(ROOT, zip_path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with zipfile.ZipFile(full, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries:
            archive.writestr(name, data)


# ---------------------------------------------------------------------------------------------------------------------
# songs/ — one file per ChordPro case, each imported on its own

SONGS = {
    # Plan 35: four chords on a right-to-left line. Expected: Am over the rightmost word, F over the leftmost.
    "songs/rtl-hebrew.cho": """{artist: בדיקה}
{title: שיר}

{start_of_verse}
[Am]שלום [C]עולם [G]שיר [F]יפה
[Dm]אנחנו [G]שרים [C]יחד
{end_of_verse}
""",
    "songs/rtl-arabic.cho": """{artist: اختبار}
{title: أغنية}

{start_of_verse}
[Am]مرحبا [C]بالعالم [G]أغنية [F]جميلة جدا وطويلة بما يكفي لتلتف على سطر ثان في نافذة ضيقة
[Dm]نحن [G]نغني [C]معا
{end_of_verse}
""",
    # Plan 09: lowercase bass notes follow the root and keep their case.
    "songs/bass-notes.cho": """{title: Bass Notes}
{artist: Campfire Tests}
{key: D}

[D/f#]One [d/f#]two [A/c#]three [Am7/G]four
""",
    # Plan 09 + German notation: C/h is C over B.
    "songs/german-bass.cho": """{title: German Bass}
{artist: Campfire Tests}
{key: C}

[C/h]Eins [B]zwei [F]drei [H7]vier
""",
    # Plan 08: brackets that are not chords never move; plan 14: [ *softly] is an annotation, [] is nothing.
    "songs/bracket-labels.cho": """{title: Bracket Labels}
{artist: Campfire Tests}
{key: G}

[Intro] [G] [C] [Break] [D]
[Chorus 2x]Sing [G]it [Bass]twice [Ebony]now
[ *softly]quietly [] here [ G ]spaced
""",
    # Review 4: lowercase roots are minors; a German song.
    "songs/lowercase-minors.cho": """{title: Lowercase Minors}
{artist: Campfire Tests}

[a]Egy [e]kettő [h]három [C]négy
""",
    "songs/german-notation.cho": """{title: Boci boci tarka}
{artist: Népdal}
{key: B}

[B]Boci [F]boci [H7]tarka, se [B]füle se farka
""",
    # Plans 11 and 12: a tab and a grid with non-breaking spaces, and a tab row wide enough to wrap that holds
    # characters of more than one UTF-16 unit (emoji, a clef, a decomposed é).
    "songs/nbsp-tab-grid.cho": """{title: NBSP Tab And Grid}
{artist: Campfire Tests}

{start_of_tab}
e| -0- -2- -3-|
B| -1- -3- -0-|
{end_of_tab}

{start_of_grid}
| C . G . | Am . F . |
{end_of_grid}
""",
    "songs/wide-tab-multibyte.cho": """{title: Wide Tab Multibyte}
{artist: Campfire Tests}

{start_of_tab}
e|--0--🎸--2--𝄞--3--é--5--|--0--🎸--2--𝄞--3--é--5--|--0--🎸--2--𝄞--3--é--5--|--0--🎸--2--𝄞--3--é--5--|
B|--1-----3-----0------1--|--1-----3-----0------1--|--1-----3-----0------1--|--1-----3-----0------1--|
{end_of_tab}
""",
    # Review 2: symbol titles, sorting sections.
    "songs/sort-7-years.cho": "{title: 7 Years}\n{artist: Lukas Graham}\n\n[C]Once I was\n",
    "songs/sort-donde.cho": "{title: ¿Dónde estás?}\n{artist: ¡Forward, Russia!}\n\n[G]Hola\n",
    "songs/sort-justice.cho": "{title: …And Justice}\n{artist: 2Pac}\n\n[Em]For all\n",
    "songs/sort-irmak.cho": "{title: ırmak}\n{artist: Test}\n\n[Am]Su\n",
    "songs/sort-katyusha.cho": "{title: Катюша}\n{artist: Кино}\n\n[Am]Расцветали\n",
    "songs/sort-gesi.cho": "{title: Gęsi za wodą}\n{artist: Kasia}\n\n[D]Gęsi\n",
    "songs/sort-ymca.cho": "{title: Y.M.C.A.}\n{artist: Village People}\n\n[G]Young man\n",
    # Review 2: user text with percent signs and other characters a formatter trips over.
    "songs/percent-100-sure.cho": """{title: 100% sure}
{artist: Ke$ha \\ AC/DC}
{composer: 50% Dylan}
{tempo: 120%}
{key: 100% sure}
{tag: 100% live}

[C]Percent %d%s%%
""",
    # Plan 13: a file joined from two, each with its own byte order mark.
    "songs/bom-joined.cho": BOM + "{title: First}\n[C]One\n{new_song}\n" + BOM + "{title: Second}\n[G]Two\n",
    # Review 4: colon-less and meta directives.
    "songs/colonless-directives.cho": """{title Wonderwall}
{artist Oasis}

{start_of_verse Verse 1}
[Em7]Today is [G]gonna be the day
{end_of_verse}
{Verse 2}
""",
    "songs/meta-directives.cho": """{meta: title Amazing Grace}
{meta: artist John Newton}
{meta: key G}

[G]Amazing [C]grace, how [G]sweet the sound
""",
    # Review 2: unicode accidentals.
    "songs/unicode-accidentals.cho": "{title: Unicode Accidentals}\n{key: F♯m}\n\n[B♭]la [F♯m7♭5]la\n",
    # Review 3: a single section tall enough to trip the layout's size limit.
    "songs/tall-section.cho": "{title: Tall}\n{artist: Campfire Tests}\n\n{start_of_verse}\n"
    + "".join("[C]Line %d of a very tall verse\n" % n for n in range(1, 6001)) + "{end_of_verse}\n",
    # Review 2: hostile input.
    "songs/crafted-comment.cho": "{title: Crafted}\n{c:" + " " * 5000 + "\n[C" + "b9" * 5000 + "]x\n",
}

EVERY_DIRECTIVE = """{title: Every Directive}
{subtitle: The Kitchen Sink}
{artist: Campfire Tests}
{composer: A. Composer}
{lyricist: A. Lyricist}
{album: Test Album}
{year: 2026}
{duration: 3:45}
{key: G}
{capo: 2}
{tempo: 96}
{time: 3/4}
{tag: Test}
{tag: Every Directive}
{meta: language en}
{meta: language hu}

{comment: A plain comment}
{comment_italic: An italic comment}
{comment_box: A boxed comment}
{highlight: Key change!}

{start_of_intro: Intro}
[G] [C] [D] [G]
{end_of_intro}

{start_of_verse: Verse 1}
[G]Chords over [C]lyrics, a [*N.C.]pause and a [D7]wide [Gmaj7/F#]chord
{end_of_verse}

{start_of_chorus: Chorus}
[C]This is the [G]chorus
[D]sung every [G]time
{end_of_chorus}

{start_of_bridge: Bridge}
[Em]Across the [C]bridge
{end_of_bridge}

{chorus}

{start_of_tab: Riff}
e|-----0-----|
B|---1---1---|
G|-2-------2-|
{end_of_tab}

{start_of_grid: Grid}
| G . . . | C . D . |
| Em . . . | C~D . G . :|
{end_of_grid}

{start_of_chorus-guitar}
[G]A chorus only a guitar should see
{end_of_chorus-guitar}

{start_of_abc}
X:1
K:G
GABc|
{end_of_abc}

{start_of_outro: Outro}
[G]The end
{end_of_outro}
"""


def build_songs():
    for path, text in SONGS.items():
        write_text(path, text)
    write_text("songs/every-directive.cho", EVERY_DIRECTIVE)
    # Plan 10: line endings of an old Mac file, CR only; a CRLF one next to it.
    cr_song = "{title: Old Mac}\n{artist: Campfire Tests}\n# a comment\n\n{start_of_tab}\ne|--[x]--|\n{end_of_tab}\n[C]Carriage [G]return\n"
    write_text("songs/cr-only.cho", cr_song, newline="\r")
    write_text("songs/crlf.cho", cr_song.replace("Old Mac", "Windows Line Endings"), newline="\r\n")
    # Review 3: a byte order mark at the start, doubled.
    write_bytes("songs/bom-start.cho", (BOM + "{title: Byte Order Mark}\n[C]La\n").encode("utf-8"))
    write_bytes("songs/bom-double.cho", (BOM + BOM + "{title: Two marks}\n[C]La\n").encode("utf-8"))
    # Review 2 and 4: legacy encodings and UTF-16.
    write_text("songs/cp1250-hungarian.cho", "{title: Árvíztűrő tükörfúrógép}\n{artist: Teszt}\n\n[C]őszi [G]űrhajó\n", encoding="cp1250")
    write_text("songs/cp1252-french.cho", "{title: Café à la crème}\n{artist: Teszt}\n\n[C]sûr, [G]déjà\n", encoding="cp1252")
    utf16 = "{title: Ősz}\n{artist: Teszt}\n\n[C]őszi eső\n"
    write_bytes("songs/utf16-with-bom.cho", utf16.encode("utf-16"))
    write_bytes("songs/utf16-no-bom.cho", utf16.encode("utf-16-le"))
    # Plan 07: the same titles composed (NFC) and decomposed (NFD), as a Mac or an iPhone may hand them over.
    for form in ("NFC", "NFD"):
        suffix = form.lower()
        for slug, title, artist in (("katyusha", "Катюша й", "Кино"), ("cafe", "Café Élan", "Teszt"), ("vietnamese", "Tiếng Việt", "Teszt")):
            text = unicodedata.normalize(form, "{title: %s}\n{artist: %s}\n\n[Am]test\n" % (title, artist))
            write_bytes("songs/%s/%s-%s.cho" % (suffix, slug, suffix), text.encode("utf-8"))
    # AppleDouble and Finder litter, which must never become songs.
    write_bytes("songs/hidden/._test.cho", b"\x00\x05\x16\x07\x00\x02\x00\x00Mac OS X")
    write_bytes("songs/hidden/.DS_Store", b"\x00\x00\x00\x01Bud1")


# ---------------------------------------------------------------------------------------------------------------------
# import/ — archives and setlists

def song_file(title, artist, lyrics):
    return "{title: %s}\n{artist: %s}\n\n%s\n" % (title, artist, lyrics)


def build_import():
    alpha = song_file("Alpha", "Campfire Tests", "[C]Alpha song")
    beta = song_file("Beta", "Campfire Tests", "[G]Beta song")
    names = ["campfire_tests-alpha.cho", "campfire_tests-beta.cho"]
    zip_folder("import/library-archive.zip", [
        ("songs/" + names[0], alpha.encode()),
        ("songs/" + names[1], beta.encode()),
        ("setlists/test_set.setlist.json", setlist("Test set", names, "A setlist inside an archive").encode()),
    ])
    write_text("import/setlist-missing-song.setlist.json", setlist("Missing song", ["campfire_tests-alpha.cho", "does_not_exist-anywhere.cho"]))
    write_text("import/setlist-future-field.setlist.json", setlist(
        "Future fields", ["campfire_tests-alpha.cho"],
        extra={"venue": "Pécs"}, entry_extra={"note": "capo 2"},
    ))
    # The keep-both import: first bring in keep-both-1.zip, then keep-both-2.zip, which holds an edited song under
    # the same name and the setlist unchanged. Keep both must leave a numbered setlist pointing at the numbered song.
    name = "campfire_tests-keep_both_test.cho"
    the_setlist = setlist("Keep both test", [name]).encode()
    zip_folder("import/keep-both-1.zip", [
        ("songs/" + name, song_file("Keep Both Test", "Campfire Tests", "[C]The original words").encode()),
        ("setlists/keep_both_test.setlist.json", the_setlist),
    ])
    zip_folder("import/keep-both-2.zip", [
        ("songs/" + name, song_file("Keep Both Test", "Campfire Tests", "[C]The EDITED words").encode()),
        ("setlists/keep_both_test.setlist.json", the_setlist),
    ])
    # A Windows "Send to > Compressed folder" archive names entries in the OEM code page (852 on a Hungarian
    # machine) and sets no UTF-8 flag. zipfile flags every non-ASCII name as UTF-8, so the entry is written under an
    # ASCII placeholder of the same length and its bytes are swapped afterwards; the CRC covers the content only.
    oem_name = "Tükörfúrógép.cho".encode("cp852")
    placeholder = b"X" * (len(oem_name) - 4) + b".cho"
    full = os.path.join(ROOT, "import/windows-oem-names.zip")
    with zipfile.ZipFile(full, "w") as archive:
        archive.writestr(placeholder.decode(), "[C]Tükörfúrógép\n".encode("utf-8"))
    with open(full, "rb") as file:
        data = file.read()
    with open(full, "wb") as file:
        file.write(data.replace(placeholder, oem_name))
    # A Finder archive with its __MACOSX companions, plus files the import must skip.
    zip_folder("import/finder-with-junk.zip", [
        ("Songs 2.0/Hey.Jude.cho", b"{title: Hey Jude}\n{artist: The Beatles}\n\n[F]Hey Jude\n"),
        ("Songs 2.0/Who Are You?.cho", b"{title: Who Are You}\n\n[C]Who\n"),
        ("Songs 2.0/Con.cho", b"{title: Con}\n\n[C]Con\n"),
        ("__MACOSX/Songs 2.0/._Hey.Jude.cho", b"\x00\x05\x16\x07"),
        ("Songs 2.0/cover.pdf", b"%PDF-1.4 not really"),
        ("Songs 2.0/track.mp3", b"ID3 not really"),
    ])
    # A text songbook of several songs split by {new_song}, one of them title-less.
    write_text("import/songbook.txt", "{title: Book One}\n[C]One\n{new_song}\n{title: Book Two}\n[G]Two\n{ns}\n[Am]No title here\n")


# ---------------------------------------------------------------------------------------------------------------------
# size/ — the import caps: 8 MiB a text file, 24 MiB a selection or an unpacked archive

def build_size():
    line = b"[C]A line of lyrics long enough to fill a file quickly with padding padding padding\n"
    def filled(size):
        head = b"{title: Oversized}\n{artist: Campfire Tests}\n\n"
        return head + line * ((size - len(head)) // len(line) + 1)
    write_bytes("size/oversized-9mib.cho", filled(9 << 20))
    write_bytes("size/huge-20mb.cho", filled(20 << 20))


# ---------------------------------------------------------------------------------------------------------------------
# dropbox-only/ — names a Windows machine cannot store, to be uploaded to the Dropbox folder from the web UI

def build_dropbox_only():
    for name in ("con.cho", "a.cho ", "a:b.cho", "what?.cho", "Hallelujah.cho"):
        body = "{title: %s}\n{artist: Dropbox Only}\n\n[C]Uploaded from dropbox.com\n" % name.strip()
        write_text("dropbox-only/" + name, body)
    write_text("dropbox-only/README.txt", (
        "Upload these files into Apps/Campfire/songs/ from the Dropbox web site (not through Campfire), then sync a\n"
        "Windows build: con.cho, 'a.cho ' (trailing space), a:b.cho and what?.cho cannot exist on Windows and must be\n"
        "skipped and named once. Hallelujah.cho is for the case-only rename check. See 04-windows.md and\n"
        "07-sync-multi-device.md. macOS may show a:b.cho as a/b.cho in Finder; that is the same file.\n"
    ))


# ---------------------------------------------------------------------------------------------------------------------
# large-library/ — generated on request

def build_large(song_count, setlist_count):
    names = []
    for index in range(1, song_count + 1):
        name = "generated_artist_%d-song_%05d.cho" % (index % 97, index)
        names.append(name)
        tags = "{tag: Generated}\n{tag: Group %d}\n" % (index % 12)
        language = "{meta: language %s}\n" % ("en" if index % 3 else "hu")
        body = "{title: Song %05d}\n{artist: Generated Artist %d}\n{key: %s}\n%s%s\n{start_of_verse}\n" % (
            index, index % 97, "CDEFGAB"[index % 7], tags, language,
        ) + "[C]Line one of song %d [G]with chords\n[Am]Line two [F]goes here\n{end_of_verse}\n" % index
        write_text("large-library/songs/" + name, body)
    step = max(1, song_count // max(1, setlist_count))
    for index in range(1, setlist_count + 1):
        chosen = names[(index * 7) % song_count:][:min(40, step * 2)] or names[:10]
        write_text("large-library/setlists/generated_set_%03d.setlist.json" % index, setlist("Generated set %d" % index, chosen))
    full = os.path.join(ROOT, "large-library.zip")
    with zipfile.ZipFile(full, "w", zipfile.ZIP_DEFLATED) as archive:
        base = os.path.join(ROOT, "large-library")
        for folder, _, files in os.walk(base):
            for file in files:
                path = os.path.join(folder, file)
                archive.write(path, os.path.relpath(path, base))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--large", nargs=2, type=int, metavar=("SONGS", "SETLISTS"), help="also generate a large library")
    arguments = parser.parse_args()
    if os.path.exists(ROOT):
        shutil.rmtree(ROOT)
    build_songs()
    build_import()
    build_size()
    build_dropbox_only()
    if arguments.large:
        build_large(*arguments.large)
    archive = ROOT + ".zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for folder, _, files in os.walk(ROOT):
            for file in files:
                path = os.path.join(folder, file)
                bundle.write(path, path)
    print("Wrote %s/ and %s" % (ROOT, archive))


if __name__ == "__main__":
    sys.exit(main())
