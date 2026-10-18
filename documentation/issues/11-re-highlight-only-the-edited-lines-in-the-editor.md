<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Re-highlight only the edited lines of a long song in the editor

**Challenged:** amended — the highlighter is in `:chordpro` (`chordpro/edit`, `explicitApi()`), so the incremental entry
point and its tests go there; the plan now carries the whole-document `bodyStart` into the resume/stop rule (it decides
`hasHeaderKey` and a body `{key}`'s DUPLICATE), resumes `ChordProLineScanner`'s environment, finds the edit by a
prefix/suffix diff against the cached text, and handles a `\r\n` split by an edit.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all (long documents on low-end phones and the web)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProOutputTransformation.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProHighlighter.kt`, `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProLineScanner.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProHighlighterTest.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProTokenCacheTest.kt`, `chordpro/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/LoadedSongEditor.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`, the highlighter's tests

## Problem

`ChordProOutputTransformation.kt`: `val changedText = text.toString()` then `tokens = ChordProHighlighter.tokenize(changedText)`,
and `LoadedSongEditor.kt` keeps `derivedStateOf { textFieldState.text.toString() }`. Every keystroke costs two O(n) string
copies and a full tokenize (`splitLines`, `lineStartOffsets`, `bodyStartIndex`, a scan and directive validation of every line)
on the main thread: well under 1 ms for a typical song, several ms per keystroke near `LONG_DOCUMENT_LENGTH` on a low-end
phone or the web.

Drop this plan if a measurement on a 40k-character document on a low-end device shows under 2 ms per keystroke.

## Fix

In `:chordpro` (public, so `explicitApi()` applies), next to `tokenize`: an incremental form that takes the previous text,
its tokens and per-line state, and the new text. `ChordProTokenCache` keeps those instead of the tokens alone.

1. **Where the edit is:** the common prefix and suffix of the cached string and the new text (the cache already compares
   them char by char). The first re-tokenized line is the one holding the first changed character, or the one before
   it where that character follows a `\r` (an edit between `\r` and `\n` changes the line split `ChordProLines` makes).
2. **State carried per line** (after each line): the scanner's open environment (add an internal
   `ChordProLineScanner.scan(lines, fromIndex, environment)` resuming the walk), `saidOnce` and `hasHeaderKey`.
   `saidOnce` changes on few lines, so store it only where it changes and share it otherwise.
3. **`bodyStart`** is computed over the whole document (`ChordProHeaderLayout.bodyStartIndex`), and a line's tokens
   depend on which side of it the line is (`hasHeaderKey`, a body `{key}` read past). Recompute it for the new lines
   (cheap: it stops at the first body line); if it moved, other than by the edit's line shift, re-tokenize from the
   earlier of the old and new body start.
4. **Stop** at the first line past the edited range whose raw text, state before and after, and side of `bodyStart`
   all equal the old line's (line index shifted by the edit's line delta); reuse the old tokens from there with their
   offsets shifted by the character delta.
5. Reuse the editor's derived string (`LoadedSongEditor`'s `derivedStateOf { textFieldState.text.toString() }`) where
   it is content-equal to `originalText`, instead of the cache's own `toString()`.

## Tests

In `ChordProHighlighterTest`: incremental after an edit equals a full `tokenize` of the new text, for edits inside a
directive, inside an environment (`{start_of_chorus}`, and a delegated one such as `{start_of_abc}`), before a
once-only directive (deleting the first `{title}` makes the second one plain), in the header making a line the body's
first or no longer (moving `bodyStart` past a body `{key}`), and splitting a `\r\n`. `ChordProTokenCacheTest` keeps
passing (same-content returns the same list).
