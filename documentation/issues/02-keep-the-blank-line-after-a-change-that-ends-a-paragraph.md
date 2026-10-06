# Keep the blank line after a tempo or time change that ends a running paragraph when prettifying

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** sound
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifier.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifierTest.kt`, `chordpro/CLAUDE.md`

## Problem

Prettify (the editor's overflow action and every import) must not change what a song parses to. A change group
inside a running implicit paragraph is written without a blank line before it, which is right, but the blank line
*after* it is swallowed and the next line is then written straight under the group (`ChordProPrettifier.kt:116-125`
and `:140-143` at 1c52e5347):

```kotlin
if (directive != null && ChordProSyntax.metadataKind(directive) in ChordProHeader.changeableMetadata) {
    // A blank line before the group would close a paragraph the change only cuts, see cutsRunningSection.
    if (timings.isEmpty() && (gapBeforeNext || !isImplicitSectionRunning)) gap()
    gapBeforeNext = false
    timings += trimmed
    continue
}
if (timings.isNotEmpty()) {
    if (trimmed.isEmpty()) continue
    flushTimings()
}
…
if ((!isHeadedByTimings || isNewSong) && (gapBeforeNext || (start != null && !isLineMode) ||
```

`flushTimings` sets `isHeadedByTimings = true`, so no gap is written before the next line either. In the source the
blank line closed the paragraph; in the output it is gone and the following paragraph becomes a continuation of the
one the change cut. Verified at 1c52e5347: `{title: X}\n{tempo: 100}\n\nla\n{tempo: 120}\n\nlo` prettifies to
`{title: X}\n{tempo: 100}\n\nla\n{tempo: 120}\nlo\n`; the original parses `lo` as a new `Paragraph`
(`isContinuation = false`), the output as `isContinuation = true`. The song details screen and the PDF then lay `lo`
out as the rest of `la`'s paragraph.

## Fix

Remember that the group cut a running implicit section and that a blank line was swallowed after it:

- When a group starts (`timings.isEmpty()` in the changeable branch), record `val groupCutsSection = isImplicitSectionRunning && !gapBeforeNext`
  (the case in which no gap was written before it) in a `var` next to `timings`.
- In `if (timings.isNotEmpty()) { if (trimmed.isEmpty()) … }`, when `groupCutsSection` is set, also set a
  `closesAfterTimings = true` flag and `isImplicitSectionRunning = false` before `continue`.
- In `flushTimings`, after writing the group: if `closesAfterTimings`, call `gap()` and leave `isHeadedByTimings`
  false (the next line no longer follows the group directly; it starts a section of its own after a blank line, as
  in the source); otherwise set `isHeadedByTimings = true` as today. Reset both flags there.

The final `flushTimings()` after the loop needs no change: trailing blank lines are trimmed after it. A group that
started after a blank line (`la\n\n{tempo: 120}\n\nlo`) keeps today's output (`la\n\n{tempo: 120}\nlo`), which parses
the same as its source. Add a clause to the `prettify` KDoc's tempo paragraph and to the `ChordProPrettifier` entry
of `chordpro/CLAUDE.md` ("…except inside a running implicit paragraph or legacy heading section, which a blank line
would close") saying that a blank line that closed that paragraph after the group is kept after it.

## Tests

In `ChordProPrettifierTest`, for each of
`{title: X}\n{tempo: 100}\n\nla\n{tempo: 120}\n\nlo`,
`{title: X}\n{tempo: 100}\n\nla\n{tempo: 120}\n\n{time: 3/4}\n\nlo`,
`{title: X}\n{tempo: 100}\n\nla\n{tempo: 120}\nlo` (no blank: still a cut, still headed) and
`{title: X}\n{tempo: 100}\n\nla\n\n{tempo: 120}\n\nlo`:
assert `ChordProParser.parse(prettify(x)) == ChordProParser.parse(x)` and `prettify(prettify(x)) == prettify(x)`;
and assert the first one's exact output `{title: X}\n{tempo: 100}\n\nla\n{tempo: 120}\n\nlo\n`. The existing tests
`changes with only blank lines between them are one group heading what follows` and
`a change inside a paragraph stays inside it` keep passing.

## Manual check

In the editor, type a paragraph, a `{tempo: 120}` line straight under it, a blank line and a second paragraph; run
Prettify from the overflow menu. The blank line before the second paragraph stays, and the preview shows the two
paragraphs apart as before.
