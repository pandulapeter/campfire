<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 12 — Let the editor's summary cache reuse the summary while typing on a chorded line

| | |
|---|---|
| Lane | B |
| Impact | low-medium (per keystroke on the main thread, for the most common edit) |
| Confidence | high (measured) |
| Platforms | all |
| Files | `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSummaryCache.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSummaryCacheTest.kt`, `chordpro/CLAUDE.md` |
| Depends on / conflicts with | 13 builds on this (it reuses the changed-span helper); 14 touches `ChordProParser.scan`, which this cache calls, with no textual conflict |
| Commit message | `Reuse the editor's song summary while typing on a line with chords.` |

## Problem
The editor's title bar and transposition stepper read the song's summary on every keystroke:
```kotlin
// SongEditorScreen.kt:295-296
val summary by remember(textFieldState) { derivedStateOf { summaryCache.summaryOf(text.value) } }
```
`ChordProSummaryCache` only reuses the previous summary when the edit stays inside a *plain* lyric line. Two places enforce that:
- `ChordProSummaryCache.kt:122-130`:
  ```kotlin
  if (character in "{}[]#|/\\-\r\n") return false
  ```
  This applies to the whole line, both when the safe line is chosen (`safeLineAt`, `:94`) and when an edit is checked (`:48-49`).
- In a chord-over-lyrics song almost every lyric line holds a `[`. So typing lyrics on a chorded line, the most common edit, always falls through to `scanAndRemember` (`:55`). That runs a full `ChordProParser.summarize`, plus a `safeLineAt` walk from the top of the file that makes a `substring().trim()` per line.

Measured on desktop JVM (Apple silicon, warmed up), with the demo "House of the Rising Sun" repeated to 357 lines / 10,961 chars:

| keystroke | time |
|---|---|
| on a chorded line | 98.7 µs |
| on a plain line (fast path) | 19.3 µs |

That is roughly 80 µs of avoidable work per keystroke, and an estimated 1–1.5 ms on a low-end Android phone. The estimate uses a 10–20× ART multiplier and is not measured.

## Fix
What `summarize` reads from a line that is outside tab, grid and delegated environments, and is neither a directive nor a `#` comment:
- the names inside its closed bracket pairs (for `hasChords` and German notation, `ChordProParser.kt:188-196` / `:212-217`);
- whether the line is non-blank (`startBody`, `:187`).

An edit therefore cannot change the summary when all of these hold:

1. neither the removed nor the inserted text contains any of `{}[]#|/\-\r\n`. The last five are kept for the same conservative reason the current check has them;
2. the edit starts outside every bracket pair of its line, which is decided on the common prefix: the last `[` before the start is followed by a `]` before the start, or there is no `[` at all;
3. the edited line, after the edit, is non-blank and its trimmed text starts with neither `{` nor `#`. Deleting a leading `a` from `a# b` makes it a comment;
4. the line is outside tab, grid and delegated environments. `safeLineAt` already works this out and keeps doing so.

Implementation sketch. Keep `changedSpan`, but move it to an internal top-level helper (for example `internal object ChordProTextChange`) so that plan 13 can reuse it.
```kotlin
private fun safeLineAt(text: String, cursor: Int): IntRange? {
    ...
    if (!isLyricLine(text, start, end)) return null        // was isPlainLyricLine
    ... // the environment walk is unchanged
}

/** Non-blank, not a directive and not a `#` comment; chords are allowed, since only the edit itself is checked. */
private fun isLyricLine(text: String, start: Int, end: Int): Boolean {
    if (start < 0 || end > text.length || start >= end) return false
    var first = start
    while (first < end && text[first].isWhitespace()) first++
    return first < end && text[first] != '{' && text[first] != '#' && (start until end).none { text[it] == '\r' || text[it] == '\n' }
}

private fun isHarmlessEdit(old: String, new: String, change: ChangedSpan, lineStart: Int): Boolean {
    for (i in change.oldStart until change.oldEnd) if (old[i] in EDIT_SENSITIVE) return false
    for (i in change.oldStart until change.newEnd) if (new[i] in EDIT_SENSITIVE) return false
    val open = new.lastIndexOf('[', change.oldStart - 1).takeIf { it >= lineStart } ?: return true
    val close = new.lastIndexOf(']', change.oldStart - 1)
    return close > open                                     // the last bracket before the edit is closed
}
private const val EDIT_SENSITIVE = "{}[]#|/\\-\r\n"
```
In `summaryOf`, replace the `isPlainLyricLine(text, line.first, …)` term (`:49`) with:
```kotlin
isHarmlessEdit(oldText, text, change, line.first) && isLyricLine(text, line.first, line.last + text.length - oldText.length)
```
Keep the rest of the condition and the `safeLine` update as they are.

**What must NOT change:** the promise in the class KDoc that *"the answer is always exactly what a full parse would give"*. Anything the cache cannot prove harmless still re-scans.
- Delegated environments and tabs or grids are still refused by `safeLineAt`.
- `clear()` and the revert path are untouched.

Update the class KDoc (`:14-22`), which today says an edit is reused only for *"a line of plain lyrics - no directive, no chord…"*. Update the `isPlainLyricLine` KDoc (`:118-121`) with it, and the `ChordProParser` bullet in `chordpro/CLAUDE.md` (*"returns the summary it had for a keystroke that stays inside a line of plain lyrics"*).

## Verification
- `./gradlew :chordpro:desktopTest`. The tests live in `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSummaryCacheTest.kt`, whose counting constructor (`ChordProSummaryCache { text -> scans++; ChordProParser.summarize(text) }`) is what the new tests use. Add:
  1. **Typing lyrics on a chorded line reuses the summary.** This is the existing first test with the typed line prefixed `[Am]` and a `[G]` after it:
     - each step is `prefix + "[Am]Hello w… [G]x" + suffix`;
     - `assertEquals(ChordProParser.summarize(text), cache.summaryOf(text))` holds at every step;
     - `assertEquals(2, scans)`.
  2. **Typing inside a bracket re-scans and stays exact.** Under a `{key: B}` header, go `[A]x` → `[Am]x` → `[Am7]x` → `[Hm7]x`. Every step equals a full scan, including `hasChords`. At the last step the summary's key becomes `Bb` (checked against the built jar: `{key: B}\n[Am7]x` → `B`, `{key: B}\n[Hm7]x` → `Bb`).
  3. **Leading deletions that turn a line into a comment or directive stay exact.** Go `a# note [G]x` → `# note [G]x`, and `a{title: X}` → `{title: X}`. Both equal a full scan.
  4. **An edit after an unclosed bracket stays exact.** Go `x [Am y` → `x [Am yz`, then add the `]`.
  5. **Random typing stays exact at a stricter ratio.** Extend the existing random-typing test with a second song whose body lines all carry chords, for example `"[Am]First line of the [C]verse\n…"`. Keep `assertEquals` against a full scan on every one of the 300 × 20 edits, and assert `scans < edits / 2`. Today that song would scan on nearly every edit.
- Measurement: re-run the scratchpad benchmark (`SummaryCache keystroke on chord line`). It should drop from ~99 µs to ~20 µs, in line with the plain-line case.
- Manual: in the editor, type into a chorded line, a `{title: }` value and a `{key: }` value. The title bar and the stepper's key must still follow the text right away.
