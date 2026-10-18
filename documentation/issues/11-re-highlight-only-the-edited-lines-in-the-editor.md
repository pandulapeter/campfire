<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Re-highlight only the edited lines of a long song in the editor

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all (long documents on low-end phones and the web)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProOutputTransformation.kt`, the `ChordProHighlighter` it calls,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/LoadedSongEditor.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`, the highlighter's tests

## Problem

`ChordProOutputTransformation.kt`: `val changedText = text.toString()` then `tokens = ChordProHighlighter.tokenize(changedText)`,
and `LoadedSongEditor.kt` keeps `derivedStateOf { textFieldState.text.toString() }`. Every keystroke costs two O(n) string
copies and a full tokenize (`splitLines`, `lineStartOffsets`, `bodyStartIndex`, a scan and directive validation of every line)
on the main thread: well under 1 ms for a typical song, several ms per keystroke near `LONG_DOCUMENT_LENGTH` on a low-end
phone or the web.

Drop this plan if a measurement on a 40k-character document on a low-end device shows under 2 ms per keystroke.

## Fix

Tokenize from the first edited line, carrying forward the once-only directive state (`saidOnce`, `hasHeaderKey`) and the
environment state, and stop as soon as a re-tokenized line's state matches the old one's (the approach
`ChordProSummaryCache`'s safe lines take). Reuse the shared `text` string instead of a second `toString()`.

## Tests

The highlighter's existing tests, plus: tokenizing incrementally after an edit gives the same tokens as a full tokenize, for
edits inside a directive, inside an environment (`{start_of_chorus}`), and before a once-only directive.
