<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Avoid full-song summary scans for ordinary lyric edits

Priority: medium. Affects long songs on every platform; web has no worker thread for this Kotlin code.

## Evidence

`SongEditorScreen` makes a `String` from the entire text on each edit and derives `summary` with `ChordProParser.summarize(text.value)` (around lines 283-296). `summarize` walks the file's lines to collect metadata and detect chords (`chordpro/.../ChordProParser.kt`, `scan`). The summary is displayed in the app bar and controls, so it is read during composition. The preview is already debounced by 150 ms, but the summary deliberately follows typing immediately; a plain debounce here would change that behavior.

## Implementation plan

1. Profile a long song while typing ordinary lyric text. Count calls and time spent in `ChordProParser.summarize`; retain the existing immediate title/key update as a behavior constraint.
2. Add a pure `ChordProSummaryCache` in `:chordpro` with `summaryOf(text: String)`. On first use, call `ChordProParser.summarize`. On a later value, find the changed span with common-prefix/common-suffix comparisons. Return the cached summary only for a conservative edit wholly inside an ordinary lyric line that is known to be outside delegated/tab/grid environments and whose old and new line contain no directive, chord, comment, or grid syntax. For every other edit, call the full parser and update the cache. If the safe case cannot be proved, fall back; correctness matters more than hit rate.
3. Keep the cache with `remember(textFieldState)` in `SongEditorScreen.kt`, and replace only the call in its `derivedStateOf`. Do not debounce the app bar or the enabled state of transposition controls. On a file replacement/revert, clear or refresh the cache.
4. In `:chordpro` common tests, run sequences of edits through the cache and assert `summaryOf(text) == ChordProParser.summarize(text)` after **every** edit. Include plain lyric typing, adding/removing the only chord, metadata and `{transpose}` edits, newlines, source comments, delegated environments, tab/grid lines, and undo/redo-like whole-document replacements.

## Done when

- Ordinary lyric typing avoids a full parser scan while the title, artist, key, and transposition controls still update immediately when relevant text changes.
- The cache returns exactly the same summary as a full parse for all tested edit sequences.
- Manually verify typing, undo/redo, revert, and toolbar actions on web with a file above 50,000 characters.
