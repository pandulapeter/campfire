<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/convert

`ChordSheetConverter` is one deterministic conversion for plain text, PDF, and Word; it knows no file format. Its
heuristics and near misses are pinned in `ChordSheetConverterTest`: a change belongs in a test first.

- **No recognized song structure means escaped prose, not a guessed song** (`ChordProLiteralText`), and a line that is
  as likely sung (a single chord, `La La La`, "Time after time") is a chord or a field only with corroborating evidence.
- It reads back Campfire's own PDF export (the heading row, wrapped chord lines, narrow-column titles).
- `convertFragment` converts what is pasted into the editor: the body as `convert` writes it (chords set, headings as
  sections, prose escaped), but no title or credit guessed and metadata lines kept as text, since the song it goes into
  has its own header. Only a paste with a chord row is converted.
- The heading vocabulary lives in `ChordSheetHeadings`. A heading word that is also sung (`Hook`, `Tag`, `Coro`, `R`)
  is one only in brackets, with a colon, or with a chord row or a blank line after it.
- The title shapes a copied page opens with come from `ChordSheetTitles`. `Artist - Title` is read artist first, a
  documented limit, since both orders are common on the web.
- A row's columns are read only in text laid out in characters: a row of three or more chords typed one space apart
  over a much longer lyric lost them on copy and is written above its lyric, not into it.
- Genius's injected `You might also like` and closing `12Embed` are left out of a song (never of prose). Ultimate
  Guitar's page furniture (`Difficulty:`, `Author:`, a rating) is many lines in no fixed shape and is not filtered.
