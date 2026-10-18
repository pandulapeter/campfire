# Read chord rows that lost their alignment or their line breaks when the page was copied

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`
**Challenged:** amended — as written, steps 1 and 3 broke every PDF import: a PDF's chord row renders with one space
between chords whatever their gap (`PdfLineLayout`, `render`: one space for a proportional font), so `G C G` over
`Amazing grace how sweet the sound` (five of `DocumentGoldenTest`'s fixtures) was "unaligned" and "bare letters".
Both now apply only to character-column text. Step 3 also broke `a capitalized lone chord can end a headed section`
(a lone `C` has no second token to be positioned by); it now needs two tokens and no heading in the sheet.

## Problem

Three shapes a browser produces that the converter reads as if the columns still meant something:

1. **Whitespace collapsed on copy** (a page that does not use `<pre>`): `G D Em C` / `The river runs beside the
   road` → `[G]Th[D]e [Em]riv[C]er runs beside the road`. `merge` places every chord at the column it has in the row,
   and with single spaces those columns are the first eight characters of the lyric. The pinned test `merges and snaps
   chords without changing their order` expects `[Am]Hel[C]lo world` for `Am C` over `Hello world`, which this plan
   keeps: two chords over a short line may well be placed that way.
2. **A blank line after every line** (a blog or Google Docs copy where each line is a paragraph): `G        D` /
   blank / lyric / `Em C` / blank / lyric → every chord row is bracketed on its own line and no chord is set, since
   `writeBody` merges a chord row only with the `LYRIC` directly under it.
3. **A lettered list in prose**: `Options:` / `A B C D` / `Pick one of them.` → `[A]Pi[B]ck [C][D]one of them.`
   `chordCandidates` guards bare Latin notes (`Do Re Mi`) but not bare single capitals.

## Fix

Steps 1 and 3 read a row's columns, which mean something only in text laid out in characters: a private
`isCharacterColumns(line) = line.source.spans.all { it.isMonospace }` (plain text, a monospaced Word or PDF run). A
proportional PDF or Word row is positioned by its measured spans whatever its text spacing, and both steps leave it
alone.

1. In `writeBody`'s `Kind.CHORD` branch: when the row is in character columns, not positioned
   (`!isPositioned(candidates[index]!!)`), holds three or more chords, and the lyric under it is more than twice as
   long as the row, write
   `bracketed(...)` as its own line above the lyric instead of `merge`. Say so in `CLAUDE.md`: a row whose columns
   cannot mean anything is written above its lyric, not into it.
2. Before classifying, detect double spacing: no two non-blank lines adjacent, at least four non-blank lines, every
   non-blank line but the last followed by at least one blank, and the sheet has at least one row of two or more
   chords (`chordCandidates`). Then drop the single blank lines and treat a run of two or more blanks as one stanza
   break. Do it in `convert` on the rendered
   lines (a pure `List<Rendered> -> List<Rendered>`), before `expandLabels`; not in `convertFragment`, whose caller
   pastes into an existing song.
3. In `chordCandidates`, extend the bare-note guard: a row in character columns of **two or more** tokens, not
   positioned, and consisting only of one-letter majors (`A`–`G`, no accidental, no quality) counts as chords only
   when another row of the sheet holds a chord beyond a bare letter (`Am`, `G7`, `F#`), or the sheet has a heading
   (`ChordSheetHeadings.parse`, plan 01: a `[Verse]` sheet of `G C D` rows is a song). A lone chord keeps the
   corroboration `classify` already asks of it. Mirror the existing `hasLatinChords` shape.

## Tests

- `an unaligned chord row is written above a much longer lyric`: `G D Em C` / `The river runs beside the old mill
  road` → `[G] [D] [Em] [C]` / the lyric; `Am C` / `Hello world` still → `[Am]Hel[C]lo world`.
- `a paste with a blank line after every line is read as single-spaced`: the shape in problem 2 → chords set into
  the lyrics, stanza breaks kept where two blanks were.
- `a lettered list in prose is not a chord row`: problem 3 → escaped prose; the same `A B C D` row in a sheet with
  an `Am` row elsewhere → chords.

Keep `merges and snaps chords without changing their order`, `one chord is ambiguous until another line makes the
context clear`, `a capitalized lone chord can end a headed section`, and `seeded sheets keep their chord placement`
green, and run `:data:source:local:implementation:desktopTest`: `DocumentGoldenTest` pins the PDF and Word imports
(`reportlab`, `libreoffice`, `chrome`, `two-column`, `songbook`: a `G C G` row over a long lyric) this plan must not
change. Add a converter test with a proportional `G C G` row over `Amazing grace how sweet the sound` that stays
merged.
