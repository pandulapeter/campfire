# Read a setlist entry's transposition the way the viewer does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
**Challenged:** amended — the "at minimum `lastOrNull`" fallback is removed (it still disagrees with the viewer for `[A:+5, A:0]`); the lookup is used on all three branches, the whole-setlist one included.

## Problem

`preparePrintSource` uses the stored semitones as they are and, for a song reached through a setlist, the **first**
entry naming it:

```kotlin
songSetlist != null -> songSetlist.entries.firstOrNull { it.songFileName == entry.songFileName }?.transposition ?: 0
```

The viewer wraps the amount (`wrapTransposition`, "a file or preferences may hold any amount") and reads it through
`Transpositions.get(songFileName, setlistFileName)`, built from `setlist.entries.filter { it.transposition != 0 }
.associate { … }`, where a later non-zero entry wins and zeros never take part. A hand-edited or imported setlist with
`+14`, or with the same song twice, prints in a different key than the screen shows. (The details stepper changes
every entry of a song by the same step, but wraps each entry on its own, so duplicates with different amounts are
reachable.)

## Fix

Read all three branches through the viewer's own lookup, `transpositions.value[entry.songFileName, setlistFileName]`
(`transpositions` is an eager `StateFlow`, so `.value` is current), with `setlistFileName` = `setlist.fileName` for a
whole-setlist export, `dialog.songSetlistFileName` for a song reached through a setlist and `null` for a library song.
`lastOrNull` or `wrapTransposition` alone are not enough: the lookup drops zero entries before choosing, so
`[A:+5, A:0]` shows +5 on screen and `lastOrNull` would print 0. For a whole-setlist export a song named twice
therefore prints in the one key the viewer shows for it, which is the point of the plan.

## Tests

None (view model).

## Manual check

Import a setlist whose entry carries transposition 14, and one that names a song twice with +5 and 0; the viewer and
the PDF show the same key in both.
