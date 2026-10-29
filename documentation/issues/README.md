<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Pre-release bug hunt plans

Found at `f446744b9` by a behaviour-testing hunt (round trips over a corpus of messy songs, the PDF export imported back,
and the upgrade from `5.2.0`) rather than a review sweep. Each plan names the corpus song or test that shows it.

## Headlines

- Campfire's own PDF loses the chords of every lyric line holding an annotation (`[*Riff]`) when imported back (01).
- In a Hebrew or Arabic PDF, chords on neighbouring short words, inside Arabic words, or on a Latin word or number at
  a line's right end are moved by the export and read back a letter or a word off (04).
- A two-column landscape PDF, or a short Hebrew song with one long line, can import with its title read as lyrics (02).

## Fixed during the hunt

See the pull request: one commit per fix, each with its test.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Read the annotations Campfire's PDF prints among a row's chords back as annotations | medium | A |
| 02 | Read a Campfire PDF's heading before a second column that starts beside it, and a page of one column as one | medium | B |
| 03 | Join a lyric the export wrapped in a narrow column back into one line on import | low | A |
| 04 | Pad a right-to-left lyric so no chord over it is shifted off its letter | medium | A |

## Lanes

| Lane | Area | Plans | Files owned |
|------|------|-------|-------------|
| A | `chordpro/convert`, `ui/print`, `ui/songLayout` | 01, 04, 03 | `ChordSheetConverter.kt`, `ChordSheetCampfireLayout.kt`, `PrintLyricRows.kt`, `PrintLyricLayout.kt`, `PrintPdfWriter.kt`, `LyricsPadding.kt` |
| B | `data/formats` | 02 | `PdfLineLayout.kt` |

Merge order: B, then A (04 before 03, which pads the same rows). Every plan edits `PdfRoundTripTest.kt`'s settings,
`printable`, or the corpus, a line or a file each; rebase on it.
