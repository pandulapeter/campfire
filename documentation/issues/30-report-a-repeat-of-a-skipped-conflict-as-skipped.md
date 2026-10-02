# Report the repeat of a skipped conflicting song as skipped, not as a duplicate

**Challenged:** sound

**Kind:** bug (reporting)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`

## Problem
A batch holds song A (conflicts with a different library song under the same name) and song B with the same text as A
(a repeat: `ImportPlan.SongEntry(status = IDENTICAL, repeatedEntryIndex = indexOfA)`). With `SKIP`, A is `LEAVE_ALONE`
and its `storedNames[index]` is `entry.fileName` (the library's different song). B is `DISREGARD`:
```kotlin
Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also { duplicateFileNames += it }
```
so B is reported as a duplicate of the library's file, which is a *different* song, and the "skipped" count under-reports
what the user chose to leave out.

## Fix
Keep `val leftAloneIndices = mutableSetOf<Int>()`; add the index in the `LEAVE_ALONE` branch. In the `DISREGARD` branch,
when `entry.repeatedEntryIndex in leftAloneIndices`, do `skippedConflicts += entry.fileName`, add the index to
`leftAloneIndices` itself (a repeat of the repeat), and keep the `storedNames` value as it is (the setlist mapping is
unchanged: pointing at the library's name is the existing, documented behaviour for the skipped song itself). Every
other case is untouched.

## Tests
In `ImportFilesUseCaseImplTest` (fakes already exist there): library has `x.cho` with text T1; plan has two songs with
text T2 wanting `x.cho` (first CONFLICTING, second IDENTICAL with `repeatedEntryIndex = 0`); with `SKIP` assert
`skippedConflictingFileNames` has two entries and `duplicateFileNames` is empty; with `KEEP_BOTH` the second is still a
duplicate of the written copy.

## Manual check
Import a zip with two identical songs whose name is taken by a different library song, choose Skip: the report lists
both under "left out", none under duplicates.
