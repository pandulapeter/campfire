# Compare an incoming setlist with its entries deduplicated, the way it is written

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`

## Problem

`ImportPlanner` compares an incoming setlist with the library's as it would be written, every entry following its
song to the name it lands under (`ImportPlanner.kt:188-189` at 8ee010b36):

```kotlin
fun Setlist.withSongFileNames(songFileNames: Map<String, String>) =
    copy(entries = entries.map { entry -> entry.copy(songFileName = songFileNames[entry.songFileName] ?: entry.songFileName) })
```

But the storage keeps one entry per song file (`SetlistLocalSourceImpl.saveSetlist`'s
`entries.distinctBy { it.songFileName }`, and `SetlistMappers` on both read and write), and `holdsTheSameAs` compares
`entries == other.entries` (`:236-239`). When two entries of an incoming setlist land on one song, the planner's
"as written" setlist has two entries where the written file has one:

An archive holds `x.cho` and `x_2.cho` with the same text (two songs created with one title and not filled in yet,
two copies made by hand) and a setlist naming both. The first import writes `x.cho`, maps `x_2.cho` to it as a repeat,
and the setlist is stored with one entry. Importing the same archive again, both songs are identical to `x.cho`, the
planner's setlist has `[x.cho, x.cho]` and the library's `[x.cho]`, so the unchanged setlist is a conflict on every
import, and Keep both adds another numbered copy each time.

## Fix

Deduplicate after mapping, as the storage does, so `planSetlists` / `replanSetlists` compare what will actually be
on disk:

```kotlin
/** The setlist as it is written: every entry following its song to the name [songFileNames] gives it, one per song file, as the storage keeps them. */
fun Setlist.withSongFileNames(songFileNames: Map<String, String>) = copy(
    entries = entries
        .map { entry -> entry.copy(songFileName = songFileNames[entry.songFileName] ?: entry.songFileName) }
        .distinctBy { it.songFileName },
)
```

`distinctBy` keeps the first entry and its transposition/tempo/capo, which is the entry `saveSetlist` keeps. The
executor also writes `entry.setlist.withSongFileNames(storedSongFileNames)`, which the storage deduplicated anyway, so
nothing written changes.

## Tests

In `ImportPlannerTest`: `a setlist whose two entries land on one song is the library's own on re-import` — library
songs `x.cho` (text A) and a library setlist `s.setlist.json` with one entry `x.cho`; two incoming songs both named
`x.cho` by their header (text A), arriving as `x.cho` and `x_2.cho` (`sourceFileName`) and an incoming setlist `s` naming `x.cho` and `x_2.cho` → the
setlist's status is `IDENTICAL`.

Run `./gradlew :domain:implementation:desktopTest`.

## Manual check

Create two new songs with the same title (leave both as the template), put both in a setlist, export the setlist as a
zip. Delete the two songs and the setlist, import the zip, then import the same zip again: the second import reports
everything as already in the library and asks no question.
