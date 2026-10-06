# Point the batch's setlists at the library's spelling of a song name whose conflict was answered with Skip

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** desktop macOS and Windows (case), macOS and iOS (Unicode form)
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`

## Problem

When an incoming song's derived name is held by the library under another spelling (case or Unicode form) on a file
system that does not tell them apart, `ImportPlanner.planSongs` marks the song `CONFLICTING` and records the listed
spelling in `replacesFileName` (`ImportPlanner.kt:124-129` at 8ee010b36; `songFamilyOf` sets `takenFileName` to the
listed spelling). The executor honours that spelling for **Replace** (`entry.replacesFileName ?: entry.fileName`) and
an identical song uses the listed name, but **Skip** stores the derived one
(`ImportFilesUseCaseImpl.kt:130-136`):

```kotlin
Action.LEAVE_ALONE -> entry.fileName.also {
    leftAloneIndices += index
    skippedConflicts += it
}
...
storedNames[index] = storedName
entry.sourceFileName?.let { storedSongFileNames[it] = storedName }
```

Scenario: the library holds `Beatles-Yesterday.cho` (hand-placed, synced from elsewhere or named before the
normalization rule — such a file keeps its spelling until Update file name). An archive brings a different
`beatles-yesterday.cho` and a setlist naming it. On macOS or Windows the read of the derived name answers with the
capitalised file, so the song is a conflict with `replacesFileName = "Beatles-Yesterday.cho"`. The user answers
**Skip**; the setlist is written with an entry `beatles-yesterday.cho`, which the song list does not hold (setlists
resolve entries by exact file name), so the imported setlist shows the song as missing although the library has it.
That breaks the module promise "no setlist of the batch is pointed at a name the song list does not hold". The
skipped-conflicts row in the import report also shows a name the library does not list.

## Fix

```kotlin
Action.LEAVE_ALONE -> (entry.replacesFileName ?: entry.fileName).also {
    leftAloneIndices += index
    skippedConflicts += it
}
```

The `DISREGARD` branch for a repeat of a skipped song already takes `storedNames[repeatedEntryIndex]`, so it follows.
`skippedConflicts` for a repeat (`skippedConflicts += entry.fileName` in the `DISREGARD` branch) should take the same
listed spelling: use `it` (the stored name) there instead of `entry.fileName`. Plan 17 (listing a library file once)
touches the same lines; whichever lands second keeps both changes.

## Tests

In `ImportFilesUseCaseImplTest`: `a skipped conflict points the batch's setlists at the library's spelling` — a
`FakeSongRepository` holding `Beatles-Yesterday.cho` (text A); a plan with a song entry
`fileName = "beatles-yesterday.cho"`, `status = CONFLICTING`, `replacesFileName = "Beatles-Yesterday.cho"`,
`sourceFileName = "beatles-yesterday.cho"`, text B, and a setlist whose entry names `beatles-yesterday.cho`; invoke
with `ImportConflictResolution.SKIP` → the written setlist's entry is `Beatles-Yesterday.cho`, and
`skippedConflictingFileNames == listOf("Beatles-Yesterday.cho")`.

Run `./gradlew :domain:implementation:desktopTest`.

## Manual check

On a Mac (desktop or iOS): put a song file named `Beatles-Yesterday.cho` into the library folder by hand (or rename one
in the folder), then import a zip holding a different `beatles-yesterday.cho` (header Beatles / Yesterday) and a setlist
naming it; answer Skip. The imported setlist shows the library's Yesterday, not a missing song.
