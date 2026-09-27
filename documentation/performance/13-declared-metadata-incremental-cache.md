<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 13 — Stop re-reading every directive on each keystroke for the editor toolbar

| | |
|---|---|
| Lane | B |
| Impact | low (24 µs per keystroke on desktop JVM, only while the insertion rows are expanded, which is the default on windows whose shorter side is at least 600dp) |
| Confidence | high (measured) |
| Platforms | all |
| Files | `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeader.kt`, the internal changed-span helper introduced by plan 12 (e.g. `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTextChange.kt`), `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeaderTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorToolbar.kt`, `chordpro/CLAUDE.md` |
| Depends on / conflicts with | Depends on 12 (the shared changed-span helper). No other overlap. |
| Commit message | `Work out the editor toolbar's declared metadata from the line that changed.` |

## Problem
```kotlin
// EditorToolbar.kt:106-108
val declaredMetadata by remember(text) {
    derivedStateOf { ChordProHeader.declaredMetadata(text.value) }
}
```
```kotlin
// ChordProHeader.kt:42-43
fun declaredMetadata(text: String): Set<String> = ChordProSyntax.splitLines(text)
    .mapNotNullTo(mutableSetOf()) { line -> ChordProSyntax.matchDirective(line.trim())?.let(ChordProSyntax::metadataKind) }
```
- The derived state recomputes on every keystroke. Each time it splits the whole document (two `replace`s, a `split` and one substring per line) and runs `trim` and `matchDirective` on every line.
- `Set` equality keeps the toolbar from recomposing, so the cost is the scan alone. Measured at 23.9 µs per keystroke for 357 lines / 10,961 chars on desktop JVM (Apple silicon, warmed up). That is an estimated ~0.25–0.5 ms on a low-end phone.
- It is one of three whole-document passes per keystroke. The other two are the highlighter and, before plan 12, the summary.
- The set can only change when the kind of some line changes: `set` → no `set`, or `title` → `artist`. A keystroke inside a lyric line, or inside a directive's value, never does that.

## Fix
Add a small incremental cache next to `declaredMetadata` in `ChordProHeader.kt`, built on the changed-span helper that plan 12 extracts from `ChordProSummaryCache`.
```kotlin
/** [declaredMetadata] for a text edited one keystroke at a time; always equal to it. Not thread safe. */
class DeclaredMetadataCache {
    private var previousText: String? = null
    private var previous: Set<String> = emptySet()

    fun declaredMetadataOf(text: String): Set<String> {
        val old = previousText
        if (old != null) {
            if (text === old) return previous
            val change = ChordProTextChange.between(old, text)          // common prefix / suffix, as in ChordProSummaryCache
            if (isWithinOneLine(old, text, change) && kindOfLineAt(old, change.oldStart) == kindOfLineAt(text, change.oldStart)) {
                previousText = text
                return previous
            }
        }
        previous = declaredMetadata(text)
        previousText = text
        return previous
    }

    /** Neither side of the edit holds a line break, and the edit does not sit between the two halves of a CRLF. */
    private fun isWithinOneLine(old: String, new: String, change: ChangedSpan): Boolean {
        for (i in change.oldStart until change.oldEnd) if (old[i] == '\r' || old[i] == '\n') return false
        for (i in change.oldStart until change.newEnd) if (new[i] == '\r' || new[i] == '\n') return false
        return !(change.oldStart > 0 && old[change.oldStart - 1] == '\r' && old.getOrNull(change.oldEnd) == '\n')
    }

    /** The metadata kind of the line around [offset], by the same rule [declaredMetadata] applies to every line. */
    private fun kindOfLineAt(text: String, offset: Int): String? {
        var start = offset; while (start > 0 && text[start - 1] != '\n' && text[start - 1] != '\r') start--
        var end = offset; while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
        return ChordProSyntax.matchDirective(text.substring(start, end).trim())?.let(ChordProSyntax::metadataKind)
    }
}
```
Why this is exact:
- `declaredMetadata` is the set of `metadataKind` values over the lines of `splitLines`.
- An edit that adds or removes no `\r` or `\n`, and does not split a CRLF pair, keeps the line structure. It changes the text of exactly one line, the one around `change.oldStart`, which is the same line on both sides because the prefix before it is shared.
- If that line's kind is the same before and after, every line's kind is unchanged, so the set is unchanged. Anything else falls back to the full scan.
- `declaredMetadata` does not track environments, so the cache does not either.

In `EditorToolbar.kt:106-108`:
```kotlin
val declaredMetadataCache = remember(text) { ChordProHeader.DeclaredMetadataCache() }
val declaredMetadata by remember(text) { derivedStateOf { declaredMetadataCache.declaredMetadataOf(text.value) } }
```
Update the comment above it (`:103-105`). The derived state no longer scans on every keystroke; only an edit that changes what a line declares does.

**What must NOT change:**
- A directive typed by hand still disables its button on the keystroke that completes it. Typing the closing `}` of `{year: 1970` changes that line's kind from none to `year`, which forces a full scan.
- A `{title: }` waiting for its value still counts as a title.
- `ChordProHeader.declaredMetadata` itself stays as it is.

## Verification
- `./gradlew :chordpro:desktopTest :presentation:compileKotlinDesktop`. Add these to `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeaderTest.kt`:
  1. **Random typing always equals the full function.** Use a fixed-seed `Random`, the same shape as `ChordProSummaryCacheTest`'s random test, over a song with a header, lyrics and chords. Type from an alphabet that includes `{}:` and letters that spell directive names (`title`, `year`, `key`, `tag`), `\n` and `\r`. After every edit, `assertEquals(ChordProHeader.declaredMetadata(text), cache.declaredMetadataOf(text))`.
  2. **CRLF safety.** In `"{title: T}\r\n{year: 1}"`, insert a character between the `\r` and the `\n`, then delete it. Both results equal the full function.
  3. **Completing and breaking a directive.** `{year: 1970` → `{year: 1970}` adds `year`, and deleting the `{` removes it again.
- Measurement: while typing in a lyric line or a directive value, `declaredMetadataOf` costs one prefix and suffix scan plus two short line reads. That is a few µs in place of the 24 µs.
- Manual: with the insertion rows expanded in the editor:
  - type `{year: 2000}` by hand; the Year button disables when the `}` is typed and enables again when the line is deleted;
  - Tag and Language stay enabled throughout;
  - undo and redo across those edits keep the buttons right.
- Documentation: the `ChordProHeader` bullet of `chordpro/CLAUDE.md` gains one sentence on `DeclaredMetadataCache` (the incremental form an editor calls per keystroke, always equal to `declaredMetadata`). Add a KDoc on the class.
