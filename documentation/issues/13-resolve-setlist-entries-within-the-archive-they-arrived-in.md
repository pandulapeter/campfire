# Resolve a setlist's songs among the files of the archive it arrived in before the rest of the batch

**Kind:** bug · **Severity:** low · **Platforms:** all
**Challenged:** amended — sound as designed, but the signatures are kept source-compatible (the ~20 test call sites pass a flat `Map`), `replanSetlists` must carry `SetlistEntry.origin` back into `IncomingSetlist`, `origin` is appended last so the `(setlist, sourceFileName)` destructuring in `planInOrder` is untouched, and it lands after plan 12. Recommendation to the user: drop — the cost (four files, a public model field, a new type through planner and applier) is out of proportion to a two-backups-in-one-pick case.
**Files:**
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt` (an `origin` on `SongEntry` and `SetlistEntry`)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`
- `domain/implementation/CLAUDE.md` (the `PrepareImportUseCaseImpl` / `ImportPlanner` bullet)

This is the most invasive plan of the lane for the least likely case; it is written so the user can decide, and may
be dropped.

## Problem

A setlist entry names its song by the file name the song arrived under, and the whole batch shares one map from that
name to where the song lands (`ImportPlanner.kt:176-185` at 1c52e5347):

```kotlin
fun plannedSongFileNames(songs: List<ImportPlan.SongEntry>): Map<String, String> {
    …
    songs.forEachIndexed { index, entry ->
        val fileName = entry.repeatedEntryIndex?.let(fileNames::getOrNull) ?: entry.fileName
        fileNames[index] = fileName
        entry.sourceFileName?.let { songFileNames[it] = fileName }
    }
```

and the applier builds the same map again after writing (`ImportFilesUseCaseImpl.kt:138`,
`entry.sourceFileName?.let { storedSongFileNames[it] = storedName }`), which `withSongFileNames` and `replanSetlists`
read. A later file of the same arriving name overwrites an earlier one's entry, so every setlist that named that
name points at the **last** song that arrived under it.

Names are unique only within one archive. `SongbookProBackup` names its batch files by raw titles (`Amazing Grace.cho`,
`SongbookProBackup.kt:72`), so two SongbookPro backups picked together — an old and a new one of the same library, or
two people's — whose songs share a title but differ (different artist or edited text, so both are imported, the second
one numbered) leave the first backup's sets playing the second backup's version. The same holds, less likely, for two
Campfire exports from different devices with a same-named but different song, a loose `x.cho` picked beside an archive
holding another `x.cho`, and for one archive whose folders hold the same file name twice (`ArchiveLocalSourceImpl`
flattens paths, `ArchiveLocalSourceImpl.kt:105`). Verified by reading; no plan of an earlier sweep settles it.

## Fix

Give every incoming file an **origin** — which picked file it came out of — and resolve a setlist's names within its
own origin first.

- `PrepareImportUseCaseImpl.plan`: files picked loose share one origin (`null`); everything unpacked from the picked
  file at index `i` gets origin `i`. Carry it through `sort` (a `Pair` or a small private wrapper around
  `ImportedFile`, since `ImportedFile` is a `:data:model` type the platforms create) into
  `ImportPlanner.IncomingSong(origin = …)` and `IncomingSetlist(origin = …)`, then into `ImportPlan.SongEntry.origin` and
  `ImportPlan.SetlistEntry.origin` (`Int?`, defaulted to `null`, so every existing constructor call still compiles).
- `ImportPlanner.plannedSongFileNames` returns a small type instead of a flat map, e.g.

  ```kotlin
  class SongFileNames(private val byOrigin: Map<Int?, Map<String, String>>) {
      /** What a setlist from [origin] points at: its own archive's songs first, then the rest of the batch's. */
      fun forOrigin(origin: Int?): Map<String, String> = byOrigin.values.fold(emptyMap<String, String>()) { a, b -> a + b } + byOrigin[origin].orEmpty()
  }
  ```

  (the fallback to the rest of the batch keeps a loose setlist picked beside loose songs, and a setlist naming a song
  that only arrived elsewhere in the batch, working as today). Cache `forOrigin` per origin; it is called once per
  setlist.
- `planSetlists` / `planInOrder` / `replanSetlists` take `SongFileNames` and call `withSongFileNames(songFileNames.forOrigin(incoming.origin))`
  per setlist; `withSongFileNames(Map)` itself is unchanged.
- Keep the existing entry points source-compatible: `planSetlists` and `replanSetlists` are called with a plain
  `Map<String, String>` (or `emptyMap()`) from about twenty places in `ImportPlannerTest` and
  `ImportFilesUseCaseImplTest`, and `plannedSongFileNames(...)` is compared to a map literal in four. Give
  `SongFileNames` a constructor from a flat map (everything under origin `null`) and an `asMap()` for those
  assertions, or keep `Map` overloads that wrap it, rather than rewriting every test.
- `replanSetlists` rebuilds `IncomingSetlist` from each `ImportPlan.SetlistEntry` — pass `origin = it.origin` there,
  or the write-time replan silently falls back to the whole batch and the fix only holds for the preview.
- Add `origin` as the **last** parameter of `IncomingSong`, `IncomingSetlist`, `SongEntry` and `SetlistEntry`:
  `planInOrder` destructures `IncomingSetlist` as `(setlist, sourceFileName)`.
- `ImportFilesUseCaseImpl` keys `storedSongFileNames` the same way (by `entry.origin`), and passes
  `forOrigin(planned.origin)` where it calls `withSongFileNames` (`ImportFilesUseCaseImpl.kt:175`).

What this does not cover: two SongbookPro backups nested inside **one** picked zip share an origin. Covering that means
setting the origin per `unpack` call in `ArchiveLocalSourceImpl`, i.e. a field on `ImportedFile` — more surface for a
case nobody is likely to hit; leave it unless the executor finds it falls out naturally.

Smaller alternative, if the user wants something cheaper: only in `SongbookProBackup`, name batch files with a prefix
unique to that backup. Rejected as the recommendation because the batch name is what the import report shows, and
because it fixes only SongbookPro.

Checked and unchanged by it: a disregarded duplicate still maps to the library's file (`Action.DISREGARD`'s
`storedName` is recorded under the entry's origin like any other), a repeat of an earlier song of the batch still
follows `storedNames[repeatedEntryIndex]`, the family and arrived-as lookups in `planSongs` never read the origin, and
the report and its Open use `fileName` / `sourceFileName` as today. Note that before anything is written two
same-titled songs with the same header plan to the *same* `fileName` (the second is only numbered by the storage), so
the visible difference is at write time, in `storedSongFileNames`; the planner test needs different headers (as
written) to see it in the plan.

Order: after plan 12, which changes the same `files.forEachIndexed` loop and `sort` in `PrepareImportUseCaseImpl`.

Add one sentence to the `PrepareImportUseCaseImpl` / `ImportPlanner` bullet of `domain/implementation/CLAUDE.md`: a
setlist's entries are resolved among the songs of the picked file it came out of before the rest of the batch, since
file names are only unique within one archive.

## Tests

`ImportPlannerTest`: two incoming songs both with `sourceFileName = "Song.cho"`, texts A (origin 0, planned
`a-song.cho`) and B (origin 1, planned `b-song.cho`), and two setlists naming `Song.cho` with origins 0 and 1; assert
the first setlist plans to point at `a-song.cho` and the second at `b-song.cho`. And a loose setlist (origin `null`)
naming a song that arrived in origin 0 still resolves to it. `ImportFilesUseCaseImplTest`: the same batch through
`ImportFilesUseCaseImpl` writes the two setlists pointing at the two different stored names.

## Manual check

Make two SongbookPro backups whose libraries each hold a set with a song titled the same but with different lyrics;
import both in one pick: each imported setlist opens its own backup's version of the song.
