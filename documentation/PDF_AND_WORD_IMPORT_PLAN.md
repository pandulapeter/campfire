<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: importing PDF and Word documents

Status: approved, with the five decisions below taken; nothing implemented yet.  
Date: 1 October 2026.

## Product decision

A `.pdf` or a `.docx` can be imported wherever a `.cho` can: the picker, a drop, a zip archive, and on Android a share.
The app reads the text out of the document, works out what it can about it — which lines are chords and where they sit
over the lyrics, what the sections are, the title, the artist, the key, the capo — and writes an ordinary ChordPro song
into the library. From there on it is a song like any other: nothing remembers that it was a document, and the document
itself is not kept.

It is a best attempt and says so. The bar is "better than retyping it", not "right":

- A chord sheet set the usual way (chords on a line of their own above the lyrics) should come out as a song that
  needs little or no fixing.
- A sheet laid out in a way the app does not understand comes out as lyrics with the chords left as lines of text,
  which the editor fixes faster than typing the song would.
- A document that is not a song at all comes out as its text. That is not an error and gets no special message.
- A document with no readable text in it (a scan, a protected PDF, a legacy `.doc`) is reported as one that could not
  be read, and nothing is written.

A plain `.txt` file, and text shared to the app on Android, gets the same conversion: a chords-over-lyrics sheet copied
from a website becomes a song rather than lyrics with lines of chords between them. The `.cho` family never does — the
content of a ChordPro file is the user's to the byte.

Nothing about this reaches the network. No OCR, no service, no model: the conversion is code in the app, the same on
all four platforms, so the sentence at the top of `CLAUDE.md` about what reaches the network does not change.

## Decisions taken

1. **The PDF reader is pure Kotlin**, in common code, rather than each platform's own library (reasons below).
2. **Plain text is converted too**: `.txt` files and text shared on Android, never the ChordPro extensions.
3. **Campfire does not register as an app that opens PDFs or Word documents.** The one system entry point added is the
   Android share target; everywhere else a document arrives through the picker, a drop or an archive.
4. **After an import nothing opens on its own.** The result message says how many songs were converted, and carries an
   "Open" action when the import was exactly one converted document.
5. **A document holding several songs is split** in the first version, conservatively (1.8).

## Approach in one paragraph

Three pure-Kotlin pieces in a row, each testable on its own. Two **extractors** turn a document's bytes into the same
thing: pages of lines of text spans, each with a horizontal position, a size and whether it is bold or monospaced. One
**converter** turns that into ChordPro, and it is where all the cleverness lives, so both formats get equally smart
and a third format later is only another extractor. The result enters `PrepareImportUseCase` as an incoming song like
any other, so naming, collisions, duplicates, the conflict dialog and sync all work without knowing about documents.

```
.pdf  ─ PdfTextExtractor  ┐
.docx ─ DocxTextExtractor ├─ ExtractedDocument ─ ChordSheetConverter ─ ChordPro text ─ ImportPlanner (unchanged)
.txt  ─ lines and columns ┘   (:data:model)        (:chordpro)
        (:data:source:local:implementation)
```

### Why pure Kotlin rather than the platforms' PDF libraries

The platforms do not offer the same thing, and half of them offer nothing usable:

| Platform | What it has | Problem |
| --- | --- | --- |
| iOS | PDFKit, text with bounds | fine |
| Android | `PdfRenderer` text contents | API 35 and later only; older devices would need a bundled library |
| Desktop | nothing; PDFBox as a dependency | several MB, and one more thing for ProGuard to break at start-up |
| Web | nothing; pdf.js as a dependency | a second runtime in the page, its own worker, more to keep in the offline cache |

Four implementations would give four different results for the same file, none of them covered by the desktop unit
tests that everything else in the repository relies on. The project already made this choice once, for zip: an
`Inflater` written from scratch "so that the zip support works identically on every platform". A PDF text extractor
is bigger than that (see the estimate at the end) but it is the same kind of code, it reuses that `Inflater`, and
text-only extraction is a small corner of the format — no rendering, no images, no fonts beyond their character maps
and widths.

`.docx` needs no decision: it is a zip archive of XML, the zip reader exists, and the XML needs a pull parser of a
couple of hundred lines.

## Where the code goes

| Piece | Module | Notes |
| --- | --- | --- |
| `ExtractedDocument` (pages → lines → spans) | `:data:model` | plain data, no logic |
| `PdfTextExtractor`, `DocxTextExtractor`, `XmlPullParser` | `:data:source:local:implementation`, new `document/` package next to `zip/` | internal; reuses `ZipReader` and `Inflater` |
| `DocumentLocalSource` | `:data:source:local:api` | `suspend fun extract(file: ImportedFile): ExtractedDocument?`, on `Dispatchers.Default` like `ArchiveLocalSource` |
| `DocumentRepository` (+ `Impl`) | `:data:repository:*` | a pass-through, the way `ArchiveRepository` is |
| `ChordSheetConverter`, its input model `ChordSheet` | `:chordpro` | dependency-free, so it takes its own small input type; uses `ChordProChordNames.isChordName` |
| mapper `ExtractedDocument` → `ChordSheet` | `:domain:implementation` | the layer boundary rule |
| document bucket in `PrepareImportUseCaseImpl` | `:domain:implementation` | see Import plumbing |

Plain text needs no extractor: `ChordSheet.ofPlainText` in `:chordpro` makes one span per run of non-space
characters, positioned by column, with a tab advancing to the next multiple of eight.

`ChordSheet` is the converter's whole view of the world: a list of pages, each a list of lines, each a list of spans
`(text, start, end, size, isBold, isMonospace)` where `start` and `end` are horizontal positions in any consistent
unit. For a PDF they are points on the page; for a `.docx` they are estimates; for plain text they are column numbers.
The converter never knows which.

## Step 1 — the converter (`:chordpro`)

Done first because it is the part that decides the quality, it needs no document to be developed against (plain text
with column positions exercises all of it), and its tests are the cheapest to write.

### 1.1 Normalising

- Non-breaking and other Unicode spaces become spaces; soft hyphens and zero-width characters go.
- `♯` and `♭` become `#` and `b` in chord tokens (`ChordProNotation.withAsciiAccidentals` already does this).
- Ligatures a PDF font emits as one character (`ﬁ`, `ﬂ`, `ﬀ`…) are expanded.
- Everything is brought to NFC (`normalizedToNfc`), since PDFs written on a Mac often carry decomposed accents.
- Trailing whitespace goes; runs of more than two blank lines collapse to one.

### 1.2 Is it already ChordPro?

A ChordPro file printed to PDF, pasted into Word or saved as `.txt` is common. If the text carries `{title:…}`-style
directives or inline `[chord]` brackets in a good share of its lyric lines, it is passed through untouched —
converting it "again" could only damage it. For plain text, untouched means the original string, not one rebuilt from
its lines: a `.txt` that holds ChordPro (a collection separated by `{new_song}` among them) has to import exactly as
it does today, byte for byte, or a library exported before this change would stop matching itself.

### 1.3 Classifying each line

Every line gets one class. The order below is the order they are tried in.

| Class | How it is recognised |
| --- | --- |
| blank | nothing on it |
| tab | `e|---`, `B|-3-` and friends: a string name, a bar, mostly dashes and digits; two or more in a row |
| section label | alone on its line, optionally in `[ ]` or `( )` or ending in `:`; from a table of names in English and Hungarian — Verse, Chorus, Refrain, Bridge, Pre-Chorus, Intro, Outro, Solo, Interlude, Instrumental, Versszak, Refrén, `Ref.`, `R.` — with an optional number |
| metadata | near the top only: `Capo 3`, `Capo: 3rd fret`, `Key: G`, `Hangnem: G`, `Tempo: 120`, `120 BPM`, `Time: 4/4`, `Artist:` / `Előadó:`, `by …`, `©` |
| chord line | every token is a chord name or chord-line furniture (`|`, `-`, `/`, `%`, `N.C.`, `x2`, `(2x)`, parentheses), with at least one real chord |
| lyric | everything else |

The chord line is the one that needs care:

- Chord names are whatever `ChordProChordNames` accepts, including lowercase minors (`am`) and German `H`; the app
  already reads German-notated files, so they are written as found.
- A one-token line is ambiguous: `A`, `Am`, `E`, `Go`. It counts as a chord line only when the document has other,
  unambiguous chord lines, and it is followed by a lyric line.
- `A long time ago` is not a chord line because not every token is a chord; `Am I` neither, because `I` is not.
- A label and chords on one line (`Intro: Am F C G`) is a section label followed by a chord line.
- Latin (`Do Re Mi`) and Nashville numbers are not recognised, since the app does not support them yet (`TO_DO.md`);
  those documents end up as text.

### 1.4 Putting chords over the lyrics

A chord line directly followed by a lyric line is merged into one ChordPro line. For each chord, its `start` is
looked up in the lyric line's spans to find the character it sits over, and `[chord]` is inserted there.

- **Snapping.** Hand-aligned sheets are rarely exact. A chord that lands within about one character of a word's
  start goes to the word's start; one that lands on the space before a word goes to that word. A chord that lands
  clearly inside a word stays inside it, since chord changes on a later syllable are real and common.
- **Past the end.** Chords positioned after the last lyric character are appended, separated by spaces.
- **Order is kept**, whatever the positions say: two chords never swap, and two that land on the same character are
  written one after the other.
- A chord line followed by another chord line, a blank line or a label is a line of chords on its own:
  `[Am] [F] [C] [G]`, bars kept as text.

Positions make this work for proportional fonts too, which is where the column-counting converters found online fail:
in a PDF the chord's x coordinate and the lyric glyphs' x coordinates are both known exactly, whatever the font.

### 1.5 Other ways people write chords

- **Inline in parentheses**: `(Am) Today is gonna be (C) the day`. Converted to brackets when most parenthesised
  tokens in the document are chord names, so that `(repeat)` and `(softly)` in an ordinary lyric sheet are left alone.
- **Inline, set apart by style**: a bold or superscript run inside a lyric line whose text is a chord name becomes
  `[chord]`. The extractors report bold and raised runs for this.
- **Chords in table cells** (Word): handled by the extractor, see step 3.

### 1.6 Structure and header

- **Title and artist.** On the first page, before the first chord or lyric pair: the largest or boldest line (or the
  one in a Title / Heading style) is the title; a shorter, smaller line right after it is the artist, with a leading
  `by` or `words and music by` dropped. `Artist – Title` on one line is split at the dash. When nothing looks like a
  title, none is written, and the document's file name stands in for it exactly as it does for any untitled song
  (`fallbackTitle` in `PrepareImportUseCaseImpl`).
- **Metadata lines** become `{capo}`, `{key}`, `{tempo}`, `{time}`, `{artist}`, `{copyright}` and leave the body.
- **Sections.** A label opens `{start_of_verse}` / `{start_of_chorus}` / `{start_of_bridge}` with the label as
  written (`{start_of_verse: Verse 2}`), closed at the next label or at the end. A bare `Ref.` or `Chorus` with
  nothing under it is the chorus being called again: `{chorus}`. Labels that are none of the three (Intro, Solo,
  Outro) become `{comment: …}`. Paragraphs without a label stay paragraphs.
- **Tab** lines are wrapped in `{start_of_tab}` … `{end_of_tab}` verbatim. For a PDF the line is rebuilt column by
  column from the glyph positions so the strings still line up.

### 1.7 When it is not a song

The converter keeps a count of what it recognised. With no chord lines, no inline chords and no section labels, the
document is taken as plain text: the lines are written out in reading order and nothing else is attempted — no
title guessing beyond the first line, no sections.

Either way, text that came out of a document must not be read as markup by accident. `ChordProParser` skips a line
whose first character after trimming is `#`, reads a `{…}` line as a directive and every `[…]` as a chord, and it has
no escape for any of them. Teaching it one would touch the parser, the serializer, the highlighter, both transposers
and the tag editor for the sake of a rare line, so the converter rewrites instead, in lyric text only (never in what
1.2 passes through): square brackets and the braces of a line that would read as a directive become parentheses, and
a leading `#` becomes the full-width `＃`. A small helper next to `ChordProSyntax`, with tests of its own.

### 1.8 More than one song in a document

A songbook PDF holds many songs. The split is conservative: a new song starts at a page whose first line is a title
by the rules above *and* at least two pages of the document start that way. A two-page song has no title on its
second page, so it stays whole. The converter returns a list of texts; `PrepareImportUseCaseImpl` already knows what a
collection is (parts with no `sourceFileName`, each named by its own header).

### 1.9 Tests

`commonTest` in `:chordpro`, on plain-text fixtures written inline: that ChordPro text comes back as the same string,
every class of 1.3 with its near misses, the snapping rules of 1.4 character by character, proportional positions
(hand-made spans), the parenthesis threshold, the header guesses, the not-a-song path, escaping, and that the conversion
is deterministic — importing the same document twice has to produce the same text, which is what makes the second import
a duplicate rather than a conflict.

## Step 2 — import plumbing

Small, and done before either extractor so that each of them is usable the day it lands.

- `LibraryFiles`: `DOCUMENT_EXTENSIONS = listOf(".pdf", ".docx")`, added to `IMPORTABLE_EXTENSIONS`. The web picker's
  `accept` list is built from that; Android (`*/*`), iOS (`UTTypeData`) and the desktop dialog already let them
  through, as do drops.
- `ImportLimits`: a `MAX_DOCUMENT_FILE_SIZE` returned by `maxSizeOf` for those extensions. A document with embedded
  fonts and a logo is far larger than its text; the limit is 16 MB, under the 24 MB the selection as a whole may read.
  The text that comes *out* is held to `MAX_TEXT_FILE_SIZE`. The zip path needs nothing: `ArchiveLocalSourceImpl`
  asks `maxSizeOf` per entry, and a `.docx` inside an archive is not mistaken for a nested archive, since that check
  is on the `.zip` extension.
- **Plain text**: a `.txt` goes through `ChordSheet.ofPlainText` and the converter before `ChordProSplitter`, and is
  marked as converted only where the converter changed it. Text shared on Android reaches the import as a file named
  `.cho` today (`importSharedTexts` in `AndroidFileImport.kt`); it is named `.txt` instead, which is the whole of that
  change, since the name it is given only ever stood in for the title.
- `PrepareImportUseCaseImpl.sort` gets a third bucket. Each document is extracted, converted, and its songs join the
  `IncomingSong` list before `ImportPlanner.planSongs`, so the planner is untouched. The extension chooses the
  extractor, and the extractor checks the magic bytes (`%PDF-`, or a zip holding `word/document.xml`) before
  believing it.
- A document that yields no text goes to a new `ImportPlan.unreadableDocumentFileNames` rather than into
  `skippedFileNames`, whose message ("neither a song nor a setlist") would be wrong for it. A legacy `.doc` is
  recognised by its extension and its OLE signature and lands there too.
- `ImportPlan.SongEntry` gets `isConverted`, carried into `ImportResult.convertedSongFileNames`, so the UI can say
  how many songs were converted. It is set for every song out of a document, and for a text file's only where the
  conversion changed the text.
- Extraction `yield()`s between pages and between songs, as the rest of the preparation does, since the web's
  default dispatcher is its only thread.

### What the user sees

- `settings_import` and `songs_empty_hint` name documents too ("Import songs, documents or zip archives…").
- The import result gains a line when anything was converted: "Converted: N — worth a look in the editor." When the
  import was exactly one converted file, the message carries an "Open" action that opens that song's details. Nothing
  opens on its own.
- A new plural for unreadable documents: "One document has no text Campfire can read (a scan, a protected PDF or an
  old Word format)." — in the conflict dialog's summary and in the result.
- Every new string in both `values` and `values-hu`.

## Step 3 — the `.docx` extractor

1. `ZipReader` reads `word/document.xml` and `word/styles.xml` only, by name, within the document size limit.
2. `XmlPullParser`: elements, attributes, text, the five predefined entities and character references, CDATA.
   No DTD, so no entity expansion to defend against; a depth limit all the same.
3. Walk the body:
   - `w:p` is a line; `w:br` and `w:cr` inside it break it; a page break ends a page.
   - `w:r` is a span, with bold (`w:b`), size (`w:sz`), font (`w:rFonts`, monospace by a small list of names) and
     raised (`w:vertAlign`) from its own properties, falling back to the paragraph style and the document default.
   - `w:tab` advances to the next tab stop: the paragraph's own (`w:tabs`) or the default every 36 pt.
   - Heading and Title paragraph styles are reported as such, for 1.6.
   - Deleted tracked changes (`w:del`) are skipped, inserted ones read; hyperlink and text-box content is read;
     headers, footers and footnotes live in other parts of the archive and are never opened.
4. **Positions are estimated**, since a `.docx` stores no geometry. In a monospaced font a character is one column
   and the estimate is exact. In a proportional one each character advances by a width class (narrow `iljtf.,'`,
   wide `mwMW`, capitals, digits, the rest, space) times the run's size — crude, but the same error applies to the
   chord line and the lyric line under it, and the converter's snapping absorbs most of what is left.
5. **Tables.** Two layouts are common. Rows that alternate between chord-only cells and lyric cells are a chord
   grid: chord cell *i* goes at the start of lyric cell *i*, which is exact. Any other table is layout (two columns
   of verses), and its cells are read in order, each as its own run of lines.

Tests: `commonTest` on `document.xml` strings for the walk, and `desktopTest` on a few real files saved from Word,
LibreOffice and Google Docs.

Not in this step: `.doc` (a binary format with nothing in common with this), `.odt` and `.rtf`. `.odt` is a cheap
follow-up once the XML parser exists; see Later.

## Step 4 — the PDF extractor

The large one. Built in the order below, each stage testable before the next.

### 4.1 File structure

- A tokenizer and object parser: numbers, names, strings (literal and hex), arrays, dictionaries, streams, indirect
  references.
- The cross-reference table, classic and as a stream (PDF 1.5), with object streams and the `/Prev` chain of
  incremental saves. Where it is broken or missing, the file is scanned for `n g obj` and the table rebuilt — plenty
  of generators write slightly wrong offsets.
- Stream filters: `FlateDecode` (the two-byte zlib header, then the existing `Inflater`), with PNG and TIFF
  predictors for cross-reference streams; `ASCIIHexDecode`, `ASCII85Decode`, `LZWDecode`. Image filters are never
  touched, because image streams are never opened.
- `/Encrypt` present: not supported in the first version, reported as unreadable (see Later).

### 4.2 Pages and content

- The page tree, with inherited `Resources` and `MediaBox`, and `/Rotate`.
- The content stream interpreter, for the text subset only: `q Q cm`, `BT ET`, `Tf Tc Tw Tz TL Ts`, `Td TD Tm T*`,
  `Tj TJ ' "`, and `Do` into form XObjects. Everything else — paths, colours, images, shading — is parsed past.
- Each shown string becomes glyphs with a position in page space, from the text matrix, the CTM and the font's
  widths (`/Widths`, or `/W` and `/DW` for CID fonts). Text that is not horizontal after `/Rotate` is dropped.

### 4.3 Getting characters out of fonts

In order of preference, per font:

1. Its `/ToUnicode` CMap (`bfchar`, `bfrange` in both forms) — what every modern generator writes.
2. Its `/Encoding`: WinAnsi, MacRoman or Standard, with `/Differences` resolved through a glyph-name table. The
   table is a subset of the Adobe Glyph List covering Latin, Latin-1, Latin Extended-A (so `ohungarumlaut` and
   `uhungarumlaut` are there), Cyrillic and Greek, plus the `uniXXXX` and `uXXXXX` forms.
3. Neither: the font's text is unreadable. If that is most of the document, the document is unreadable.

Bold comes from the font name or the descriptor's weight, monospace from the descriptor's `FixedPitch` flag or the
name.

### 4.4 From glyphs to lines

- Glyphs with the same baseline (within a fraction of the font size) are a line, sorted by x. A gap wider than about
  a quarter of the font size is a space. Each word keeps its x extent, which is what the converter aligns by.
- Chords set as small raised text on the lyric's own line come out as a separate line just above it, since the
  baseline differs — which is exactly the shape the converter wants.
- **Columns.** A vertical band with no ink through most of the page's height is a gutter: the page is read down the
  left column, then the right.
- **Running headers and footers**: a line at the same position with the same text (or only a number changing) on at
  least half the pages is dropped, as is a lone page number at the top or bottom edge.
- No text on any page: a scan. Unreadable.

### 4.5 Limits

A PDF is untrusted binary input and everything in it can lie. The existing `Inflater` caps apply to every stream;
on top of that: a maximum number of objects and pages, a depth limit for nested arrays, dictionaries, the page tree
and form XObjects, cycle checks on the page tree and the `/Prev` chain, and a cap on the text produced. Whatever
goes wrong inside is caught at the `DocumentLocalSource` boundary and reported as an unreadable document; a
document never fails the rest of the import, as a broken nested archive does not today.

### 4.6 Tests

- `commonTest`: a small test-only PDF writer (uncompressed, a page of positioned strings) to exercise the object
  parser, both kinds of cross-reference, the text operators, each encoding path and the line builder with exact
  expected positions.
- `desktopTest`: real files as resources, each next to the `.cho` it should become. One song — a public domain one,
  the demo songs are right there — produced by each generator that matters: Word, LibreOffice, Google Docs, Pages,
  Chrome and Safari "Print to PDF" of a chord site's layout, LaTeX, and a two-column songbook page. Golden-file
  tests: they pin behaviour and show at a glance what a change to a heuristic did to every sample.

## Step 5 — platform entry points and documentation

- **Android**: `application/pdf` and the `.docx` MIME type are added to the `SEND` / `SEND_MULTIPLE` filter, so a
  document can be shared to Campfire from a mail app or Drive. The `VIEW` filters are left alone: registering for the
  types system-wide would list Campfire next to every PDF on the device, which is what
  `LibraryFiles.IMPORTABLE_EXTENSIONS` was written to avoid for text, zip and JSON. For the same reason no document
  type is added on iOS or macOS and no association on Windows.
- **Desktop, iOS, web**: nothing; the pickers and drops already carry any file to the import.
- `CLAUDE.md` (root): the opening paragraph's "importing files and zip archives", the architecture block's line
  about `:data:source:local:implementation`, the import convention (a document's song is named by the header the
  converter wrote), and the list of what is tested. The module `CLAUDE.md` files of `:chordpro`,
  `:data:source:local:implementation` and `:domain:implementation`.
- The website's support page gains a question about importing documents and what to expect; `TO_DO.md` loses its
  line.
- `release-check.md` gains one manual check: import one PDF and one `.docx` on each platform.

## Order and size

| # | What | Rough size | Lands on its own? |
| --- | --- | --- | --- |
| 1 | Converter and its tests | ~700 lines + tests | yes, unused until 2 |
| 2a | Plumbing for plain text, strings, result reporting | ~200 lines | yes: converted `.txt` and shared text ship |
| 2b | Document bucket, limits, unreadable reporting | ~100 lines | with 3, since without an extractor every document is unreadable |
| 3 | `.docx` extractor, XML parser | ~500 lines + tests | yes: Word import ships |
| 4 | PDF extractor | ~2 000 lines + tests | yes: PDF import ships |
| 5 | Android share filter, documentation | small | with 3 and again with 4 |

Plain text first, because it puts the converter in front of real chord sheets with no parser between them and it.
Word before PDF because it is a fifth of the work and proves the document pipeline end to end; PDF is the one most
people will actually use, so the feature is not announced until 4 is in.

## Later

- **Protected PDFs.** Many are "encrypted" with an empty user password only to forbid editing, and reading them is
  legitimate and mechanical — but it takes RC4, AES-CBC, MD5 and SHA-256 in common code. Worth doing if the
  unreadable count in practice is noticeable.
- **`.odt`**: a zip of XML like `.docx`, with a different vocabulary; a few hundred lines on top of step 3.
- **`.rtf`**: a small format of its own; positions would be estimated as for `.docx`.
- **`.doc`**: no. The message tells the user to save it as `.docx`.
- **Scans**: no. OCR is a model or a service, and the app has neither.
- **Latin and Nashville chord names**: once the app supports them at all, the classifier's table grows with them.
