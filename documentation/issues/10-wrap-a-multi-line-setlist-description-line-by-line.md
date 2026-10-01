# Wrap a multi-line setlist description line by line

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`

## Problem

The setlist dialog's description field takes up to three lines, and the overview hands it over as one string:

```kotlin
if (source.description.isNotBlank()) place(wrapped(source.description))
```

`wrapPrintText` treats `\n` as a character; the renderer's `TextMeasurer` honours it, so one `Row` of height
`size * 1.45` draws two or three lines and the lower ones overlap the first song of the running order.

## Fix

Make `wrapped()` split on `\n` (`text.lines().flatMap { … }`), which also protects titles, artists and comments that
carry a stray line break; `\r` is dropped.

## Tests

`PrintLayoutTest`: a description `"a\nb"` yields two texts on consecutive rows and no emitted `PrintText` anywhere
contains `\n`.

## Manual check

Give a setlist a three-line description and export its running order.
