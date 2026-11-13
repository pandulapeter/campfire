<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: chord grids drawn as bars

**Status:** not started. A `{start_of_grid}` line is drawn today as monospaced text (`| C · · · | Dm · · · |`), padded
into columns. It becomes a row of drawn bars: a rounded box per bar, each chord's part of it as wide as its beats
(sketch A of the design canvas the owner picked). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

## Decisions

- **A box per bar, divided by beats.** A chord's segment runs from its cell to the next chord; `.` and `/` are beats
  that keep it sounding, `C~G` is one segment naming both. A 2-beat bar is half as wide as a 4-beat one.
- **A chord's segment is at least as wide as its name.** Width is beats × a beat width (in ems of the chord text), or
  the name plus padding where that is wider; a bar is as wide as its segments.
- **Bars line up in columns.** Within a run, every column is as wide as its widest bar on any line, as the text grid's
  columns are today. Lines with fewer bars leave their last columns empty.
- **Wrapping keeps the columns.** A run too wide for its column breaks every line after the same number of bars, the
  most that fit; each row starts with a whole box, so no opening bar line is redrawn.
- **A bar that only holds the previous chord** (an empty bar, or beats before a bar's first chord) shows a faint line
  in the chord colour across that part. `%` and `%%` keep the repeat sign, centred over one or two bars' width: a file
  that writes `%` means "repeat the bar", which is not the same as letting a chord ring.
- **How long an empty bar is:** the run's most common bar length in beats, else 4. The shape in
  `{start_of_grid: 4x4}` would say it, but the parser does not keep it on the line (out of scope here).
- **Bar lines become box edges:** `|` a plain edge; `|:` and `:|` a heavy edge with two dots inside; `:|:` both; `||` a
  heavy edge; `|.` a heavy end. A volta (`|1`, `:|2`) draws a bracket over its bar labelled `1.`, `2.`.
- **Text around the bars stays text:** a margin label (`Intro` before the first bar) in a column of its own, aligned
  like the cells' columns; what follows the last bar (`x2`) after it.
- **Chords in the song's chord style**, not monospace, named as the page names them (transposition, capo, and notation
  already applied to the tokens, as for the text grid). Colours from the scheme: box `outlineVariant` on
  `surfaceContainerLow`, dividers `surfaceContainerHighest`, chords the second accent, the hold line the second accent
  at 35%, signs and edges `outline`; so both themes and every theme colour work.
- **Unchanged:** grids stay left to right and at the line end in a right-to-left song (`atLineEnd`); lyrics-only mode
  drops them; folding, including a lone unnamed row's having no toggle (`SectionItem.RunSlot.isFoldable`); the editor's
  preview draws what song details draws.
- **Read aloud as chords and beats**, not as bar characters: a bar is "C, 4 beats", "D minor, 2 beats, A minor, 2
  beats", a hold "held", a `%` "repeat bar"; new strings in every language, the beats a `<plurals>`.
- **In the PDF**, the same drawing; the text layer holds the line as ChordPro writes it, not drawn (`isDrawn = false`,
  as engraved tablature does), and the drawn chord names are left out of it (`isSelectable = false`).

## Where it hooks in today

- Tokens: `GridToken` (`libs/chordpro/.../model/ChordProLine.kt`): `Bar`, `Chord`, `Beat`, `Repeat`, `Text`.
- Text alignment: `ui/songLayout/GridColumns.kt` (`bars`, `alignedGridBars`, `GridCell`, `wrappedOpening`).
- Screen: `ui/songView/SongGridLine.kt` and `GridRowPositions.kt`, drawn from `SectionRun` in
  `ui/songView/SongSectionContent.kt`; `SongTextMeasurements.gridBars` / `gridWidth` cache the alignment and the
  right-to-left offset. Pagination measures what is composed.
- PDF: `PrintRows.gridRows` writes text rows; `PrintPage` has filled rectangles only (`PrintRule`), so outlined
  rounded boxes and dots need a new primitive in `PrintDocument.kt` and `PrintPdfWriter.kt`.
- `songView/model/LayoutBudget.kt` counts a grid line's characters; the count stays an estimate of its size.

## Steps

### 1. The bar model (`ui/songLayout`, common, pure)

- `GridBarLine` from a line's tokens: margin text, bars, trailing text. A bar: its opening and closing edge, its volta,
  and its segments (`Chord(names, beats)`, `Hold(beats)`, `Repeat(bars)`), with its length in beats.
- `GridBarLayout` for a run, given the measured widths of the chord names and the text, the beat width, and the
  available width: each column's width, the bars a row holds, and each segment's x. Plain numbers in, positions out, so
  the screen and the PDF share it.
- Tests (`commonTest`): durations (`Dm . Am .`, `Am . E7 Am`, a leading `.`), `~`, an empty bar's length, `%` and `%%`,
  every edge and volta, a margin label and a trailing `x2`, columns across lines of different lengths, the common wrap,
  and a bar wider than the whole width (a row of its own, never cut).

### 2. Drawing on screen (`ui/songView`)

- `SongGridLine` draws one line's bars from the layout (a `Canvas` for the boxes, edges, dots, and hold lines; text
  for the chord names, margin, and trailing text, measured once through `SongTextMeasurements`). The run's column
  widths and row breaks are computed once per run and passed to every line, as `gridBars` is today.
- Semantics per bar as in Decisions; the boxes are decor.
- The right-to-left offset (`gridWidth`) comes from the layout instead of the text's width.

### 3. Drawing in the PDF (`ui/print`)

- A `PrintBox` (stroked and/or filled rounded rectangle, gray levels, line width) drawn under the texts, and the dots
  as small filled ones; `PrintPdfWriter` writes them as paths.
- `gridRows` lays out with the same `GridBarLayout`, in points, and emits boxes, chord texts, and the hidden text line.
- Tests in `PrintLayoutTest`: a wrapped run keeps its columns, the text layer holds the ChordPro line once, chords are
  not doubled in it, and lyrics-only drops the drawing.

### 4. Tests through the app, docs, and cleanup

- A UI test in `tools/screenshots` on a song with a beat chart and a repeat (its resources already hold grid songs):
  the bars draw, a narrow window wraps at a common column, and a right-to-left song keeps them left to right.
- Delete the text grid's code once nothing draws it: `alignedGridBars`, `GridCell`, `wrappedOpening`, `gridBarText`,
  `displayText` and its beat symbol, `GridRowPositions` if the layout replaces it, `PrintRows`' text grid rows, and their
  tests.
- Update `ui/songView/CLAUDE.md` (the grid bullets), `ui/print/CLAUDE.md`, and the `songLayout` notes; the root
  `CLAUDE.md` changes only if a product rule line does.

## Out of scope

- Tapping a chord in a grid to see its diagram, and highlighting the bar being played.
- Keeping the grid's shape in the parser (it would set an empty bar's length exactly).
- Any change to how grids are imported or written to files.
