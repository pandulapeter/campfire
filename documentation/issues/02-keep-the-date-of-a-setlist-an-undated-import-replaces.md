# Keep the date of a library setlist that an undated incoming one replaces

**Challenged:** amended — the fallback must be keyed on the `shouldReplace` the write already computes, not on `action == REPLACE`: `shouldReplace` is false for a setlist whose name was already replaced by an earlier one of the batch or that the batch brings back unchanged, and that one is written *numbered* (`importSetlist` asks the storage for a free name), so it is a new setlist and must be dated today. `librarySetlists` is in scope (declared just above the setlist loop) and `entry.fileName` is the library file being replaced (the planner's conflicting entry carries `setlist.fileName`, which is a key of the library's setlists by construction), so the lookup is right.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`,
`domain/implementation/CLAUDE.md` (only if it describes how an import dates a setlist)

## Problem

`ImportFilesUseCaseImpl` dates every setlist it writes that carries no date with today, the replaced one included:

```kotlin
Action.WRITE, Action.REPLACE -> importedSetlists += setlistRepository.importSetlist(
    setlist = entry.setlist.withSongFileNames(storedSongFileNames).let { it.copy(date = it.date ?: today) },
```

The library holds `gig.setlist.json` dated 2026-01-10. A file of the same name arrives from an older export (or from
somebody on 4.5.0), undated and with a different song list, and the user answers **Replace**. The setlist is written
with today's date, so under "Sort by date" it jumps from January to the top of the list, and the day it was planned
for — which the incoming file said nothing about — is gone. Replacing is meant to take what the incoming file says,
and it says nothing about the date.

## Fix

For a write that really replaces a library file only, fall back on the date of that library setlist before today. Compute
`shouldReplace` once in the loop and use it for both the argument and the fallback:

```kotlin
val shouldReplace = action == Action.REPLACE && entry.fileName !in keptSetlistFileNames && replacedSetlistFileNames.add(entry.fileName)
val fallbackDate = if (shouldReplace) librarySetlists.firstOrNull { it.fileName == entry.fileName }?.date else null
setlist = ....let { it.copy(date = it.date ?: fallbackDate ?: today) },
shouldReplace = shouldReplace,
```

(`replacedSetlistFileNames.add` has a side effect, so it must be evaluated exactly once, and only inside the `Action.WRITE, Action.REPLACE` branch.)

`librarySetlists` (already in scope, what `ImportPlanner.replanSetlists` is handed) is the list to read; do not read the repository again. `Action.WRITE` (a new or a numbered
"keep both" setlist) keeps today, as the root `CLAUDE.md` documents. A dated incoming file keeps its own date either way.

## Tests

In `ImportFilesUseCaseImplTest`: a library setlist `gig` dated 2026-01-10, an incoming undated `gig` with different
songs, resolution `REPLACE` → the written setlist is dated 2026-01-10. With `KEEP_BOTH`, the numbered one is dated
today; and two different undated incoming setlists of one batch wanting the library's `gig` name with `REPLACE`: the first is dated 2026-01-10, the second is numbered and dated today (the fake clock / `todayIn` the existing tests use; if the tests cannot pin today, assert "not 2026-01-10 and not
null").

## Manual check

None beyond the test.
