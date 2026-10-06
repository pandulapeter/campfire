# List each library file once in an import's result

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportResult.kt` (KDoc only — lane A's
module), `domain/implementation/CLAUDE.md` (if it describes the result lists)

## Problem

The import result's lists are filled once per incoming **entry**, but they hold **library** file names
(`ImportFilesUseCaseImpl.kt:121-136` and `:183-184` at 8ee010b36):

```kotlin
Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also {
    if (entry.repeatedEntryIndex in leftAloneIndices) {
        leftAloneIndices += index
        skippedConflicts += entry.fileName
    } else {
        duplicateFileNames += it
    }
}
Action.LEAVE_ALONE -> entry.fileName.also { leftAloneIndices += index; skippedConflicts += it }
...
Action.DISREGARD -> duplicateFileNames += entry.fileName      // setlists
```

When one batch brings the same song twice and the library already has it, `ImportPlanner.planSongs` matches both
entries to the library file (`:108-118`: each is `IDENTICAL` with `fileName` = that file, no repeat index), so
`duplicateFileNames` is `[x.cho, x.cho]`. The import report then lists the song twice under "Already in the library"
— two identical rows that open the same song — and the song pager opened from that section is given the same key
twice, which crashes it (found by lane D, which is making the pager and list robust to repeated names on its side).
The same happens to `skippedConflictingFileNames` for a skipped conflict and its repeat (the existing test
`a repeat of a skipped conflicting song is skipped with it…` asserts `listOf("x.cho", "x.cho")`), and to setlists
that arrive twice. The snackbar's "already in the library" count counts the copies, not the songs.

A related case: a repeat of a song **this import wrote** (`KEEP_BOTH` in the same test) goes to
`duplicateFileNames` with the name it was just written under, so `x_2.cho` is both "Imported" and "Already in the
library", although it was not in the library before the import.

## Fix

Each list names each file once, and a file this import wrote is not also "already in the library":

```kotlin
duplicateFileNames = duplicateFileNames.distinct() - importedSongs.mapTo(hashSetOf()) { it.fileName },
skippedConflictingFileNames = skippedConflicts.distinct(),
```

in the `ImportResult` the use case builds (around `:212-219`), or equivalently by making the three collections
`LinkedHashSet`s and dropping the written names from `duplicateFileNames` at the end. Setlist names are disjoint from
song names (`.setlist.json`), so the subtraction only touches songs. Order stays the order of first appearance.

Update `ImportResult.duplicateFileNames`'s KDoc ("Files the library already had under the same name and with the same
content") to add "each named once, however many copies the batch brought" — `:data:model` is lane A's module, so this
one KDoc line is a cross-lane touch (or leave it to lane A). Mention the rule in `domain/implementation/CLAUDE.md` if
it describes what the result lists hold.

Recommended default for the related case is to leave such a repeat out of "Already in the library" as above; the
alternative is to keep it there and only deduplicate within each list (`distinct()` alone).

## Tests

In `ImportFilesUseCaseImplTest`:
- Change `a repeat of a skipped conflicting song is skipped with it…` to expect `listOf("x.cho")` for
  `skippedConflictingFileNames`, and, for `KEEP_BOTH`, `duplicateFileNames` empty with `importedSongFileNames ==
  listOf("x_2.cho")` (if the recommended default is taken).
- Add `a song the batch brings twice is listed once as already in the library`: library `x.cho` (text A); plan with two
  `IDENTICAL` entries `fileName = "x.cho"`, sources `a/x.cho` and `b/x.cho` → `duplicateFileNames == listOf("x.cho")`.
- Add the same for two identical setlist entries → the setlist named once.

Run `./gradlew :domain:implementation:desktopTest`.

## Manual check

Put one song from the library into a zip twice (in two folders of the archive) and import it: the result says one
song is already in the library, and the import report (Details) lists it once; opening it from there pages normally.
