# Leave out a section label when hiding chords leaves nothing under it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`

## Problem

`rowsFor(Section)` adds the label before it knows whether any line survives the options:

```kotlin
sectionLabel?.takeUnless { block.isContinuation }?.let { addAll(wrapped(it, bold = true)) }
block.lines.forEachIndexed lineLoop@ { … }
```

With "Chords, tablature and grids" off, an instrumental intro prints a lone "Intro", and a song that is one tab prints
a page holding only its title and "Riff" (live run, `hard-nochords` pages 3 and 5). The test
`hiddenInstrumentalChordLinesDoNotReserveSpace` builds its expected document from a section with a label and no
lines, so it pins the lone label.

## Fix

Build the line rows first; return an empty list when none of them has a part with non-blank text, otherwise prepend the
label rows. A section whose source has no lines at all keeps its label (it is what the author wrote, e.g. a bare
`{start_of_bridge}` as a cue) — the rule is about lines the options removed, so: drop the label only when
`block.lines.isNotEmpty()`.

## Tests

Rewrite `hiddenInstrumentalChordLinesDoNotReserveSpace` to expect no "Instrumental" text at all; add a tab-only section
with chords hidden.

## Manual check

Export a song with an instrumental intro with chords off: no "Intro" heading.
