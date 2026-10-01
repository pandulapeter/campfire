# Describe the preview to a screen reader by its page, not by every fragment on it

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`

## Problem

```kotlin
val description = pageLabel + "\n" + page.texts.joinToString("\n") { it.text }
```

`texts` is in layout order — chord, lyric fragment, chord, fragment, padding spaces — so TalkBack/VoiceOver read a
page as "Am, Hello, G, world" for hundreds of items, rebuilt on every recomposition.

## Fix

`contentDescription = pageLabel` only (the song is readable in the viewer; the preview is a picture of a page), with
`Role.Image`. If plan 23 leaves any control without the visible label as its description, add `stateDescription` with
the value ("12 pt").

## Tests

None.

## Manual check

TalkBack on the preview announces "Page 2 of 5".
