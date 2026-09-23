# 21 — Replace overwrites a library song that the same import brings back unchanged

**Severity:** data loss (all platforms) · **Area:** `:domain:implementation` (`ImportPlanner.kt`, `ImportFilesUseCaseImpl.kt`)

**Read, not run.** This was found by reading the import planner at HEAD (2065e47f); it has not been reproduced in a
running build. The "Verification" section below is how to confirm it, and confirming it is the first step of the
work. It is the most important plan of the sixth review: the one thing in the app that overwrites a library file is
pointed at a file the user has every reason to believe is safe.

## What the user sees

The library holds `foo.cho` (song **A**). The user imports an archive that holds **A** unchanged *and* a different
song **B** whose header derives the same name — the second arrangement of a song that was kept next to the first as
`foo_2.cho` on the device that made the export, or a sync conflict copy `foo (2).cho`. The likeliest real case is
restoring a library export onto a device that already has some of it: the backup holds `foo.cho` and `foo_2.cho`,
the device holds `foo.cho`.

The import asks "`foo.cho` is already in your library — keep both, replace, skip". Whatever they pick:

- **Replace:** `foo.cho` is overwritten with **B**. Song **A** — which the archive itself carried, unchanged — is gone
  from the library. Every setlist of the archive that named `foo.cho` (meaning **A**) is written pointing at `foo.cho`,
  which now holds **B**; a setlist `[foo.cho, foo_2.cho]` becomes `[foo.cho, foo.cho]` and is written with one entry
  (setlist documents name a song once). The library's own setlists and saved transposition that meant **A** now mean
  **B**. Nothing says anything went wrong; the import summary counts one song replaced and one duplicate.
- **Skip:** **B** is silently dropped, although nothing in the library was ever in its way but a copy of a song the
  archive also brings.
- **Keep both:** correct result, but the question should never have been asked.

The root `CLAUDE.md` promises the opposite: "two different files of one batch that want the same name are never a
question: the second is numbered like any other collision". Here the batch carries two different files that want
`foo.cho` — **A** and **B** — and the second is put to the user as a conflict with the library, because the first
happened to be recognised as the library's copy.

It is independent of the order the two arrive in (both orders are traced below), and it has an exact twin for
setlists.

## Cause

### Songs

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt:51-82`
plans the batch in one pass. Each song is compared with the library family of its name and with the songs of the
batch planned before it; whichever song first reaches the `else` branch while the library holds a file under the
name is made the conflict:

```kotlin
        return incoming.inArrivingOrder(LibraryFiles.SONG_EXTENSIONS, { it.fileName }, { it.sourceFileName }).mapIndexed { index, song ->
            val family = families.getOrPut(song.fileName) {
                readSongFamily(
                    desired = song.fileName,
                    members = song.fileName.familyKeys(LibraryFiles.SONG_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty(),
                    readLibraryText = readLibraryText,
                )
            }
            ...
            val arrivedAs = song.sourceFileName?.takeIf { it != song.fileName && it in libraryFileNameSet }
            ...
            val comparable = if (family.hasNothingToCompareWith && arrivedAs == null) null else ChordProSplitter.comparable(song.text)
            val libraryFileName = comparable?.let(family::libraryFileNameOf)
                ?: arrivedAs?.takeIf { readLibraryText(it)?.let(ChordProSplitter::comparable) == comparable }
            val repeatedEntryIndex = comparable?.let(family::plannedEntryIndexOf)
            when {
                libraryFileName != null -> song.toEntry(fileName = libraryFileName, status = ImportPlan.Status.IDENTICAL)
                repeatedEntryIndex != null -> song.toEntry(status = ImportPlan.Status.IDENTICAL, repeatedEntryIndex = repeatedEntryIndex)
                else -> {
                    family.plan(index = index, text = song.text)
                    // The question is about the one file the library has under this name, so it is raised once:
                    // a second song wanting the name has nothing left to replace, and goes in numbered.
                    val isConflicting = family.isNameTaken && !family.hasConflict
                    family.hasConflict = family.hasConflict || isConflicting
                    song.toEntry(status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW)
                }
            }
        }
```

The `IDENTICAL` branch (`:71`) never tells the family that the batch already accounts for the file under the name.
`isNameTaken` (`:196`, set when the read of the desired name itself succeeds) stays true, `hasConflict` stays false,
and the next different song of the family becomes `CONFLICTING` at `:77`.

Traced for library `{ foo.cho: A }`:

| Batch (arriving order) | A | B | Result today |
|---|---|---|---|
| A (`foo.cho`), B (`foo_2.cho`) | `IDENTICAL` → `foo.cho` | `CONFLICTING` | question; Replace destroys A |
| B (`foo.cho` from folder 1), A (`foo.cho` from folder 2) | `IDENTICAL` → `foo.cho` | `CONFLICTING` (first to reach `else`) | same |
| A, B as two songs of one `{new_song}` collection (`sourceFileName` null) | `IDENTICAL` | `CONFLICTING` | same |

(`inArrivingOrder`, `:246-258`, sorts a numbered arrival after its unnumbered sibling and keeps everything else in the
order it came; the bug does not depend on it — a `foo (2).cho` conflict copy sorts the same as `foo_2.cho`.)

A second route to the same state crosses families. `arrivedAs` recognises a library file named by an older rule:
library `{ x.cho: A }` where **A**'s header derives `y.cho` today. The batch brings **A** (arrived as `x.cho`,
desired `y.cho`) → `IDENTICAL` → `x.cho` through `arrivedAs`; and **B**, whose header derives `x.cho` → family `x`,
`isNameTaken`, different text → `CONFLICTING`. Replace overwrites `x.cho` — the file the batch's first entry was just
mapped onto.

### What Replace then does

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt:47-61`:

```kotlin
            plan.songs.forEachIndexed { index, entry ->
                val storedName = when (val action = entry.action(resolution)) {
                    Action.WRITE, Action.REPLACE -> songRepository.importSong(
                        fileName = entry.fileName,
                        text = entry.text,
                        shouldReplace = action == Action.REPLACE && replacedSongFileNames.add(entry.fileName),
                    ).fileName.also { importedSongFileNames += it }

                    // Already in the library, or already written by this import, so the name it arrived under points there.
                    Action.DISREGARD -> (entry.repeatedEntryIndex?.let(storedNames::getOrNull) ?: entry.fileName).also { duplicateFileNames += it }
                    Action.LEAVE_ALONE -> entry.fileName
                }
                storedNames[index] = storedName
                entry.sourceFileName?.let { storedSongFileNames[it] = storedName }
            }
```

**A** (`DISREGARD`) records `foo.cho → foo.cho`; **B** (`REPLACE`) writes `foo.cho` with `shouldReplace = true`
(`SongLocalSourceImpl.importSong`, `data/source/local/implementation/.../source/SongLocalSourceImpl.kt:96-100`, skips
`uniqueName` then) and records `foo_2.cho → foo.cho`. Every setlist of the batch is then written through
`withSongFileNames(storedSongFileNames)` (`:82`), so both of its entries point at `foo.cho`.

### Setlists

`ImportPlanner.kt:135-164`, `planInOrder` — shared by `planSetlists` (before anything is written) and
`replanSetlists` (after the songs have been written, `ImportFilesUseCaseImpl.kt:68-72`):

```kotlin
        return incoming.map { (setlist, sourceFileName) ->
            val written = setlist.withSongFileNames(songFileNames)
            val members = setlist.fileName.familyKeys(SETLIST_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty()
                .mapNotNull(librarySetlistsByFileName::get)
            val identical = members.firstOrNull { it.holdsTheSameAs(written) }
                // See planSongs: a setlist file named before today's rule, arriving under that name.
                ?: librarySetlistsByFileName[sourceFileName]?.takeIf { it.holdsTheSameAs(written) }
            val planned = plannedSetlists.getOrPut(setlist.fileName) { mutableListOf() }
            ...
            when {
                identical != null -> entry(fileName = identical.fileName, status = ImportPlan.Status.IDENTICAL)
                planned.any { it.holdsTheSameAs(written) } -> entry(fileName = setlist.fileName, status = ImportPlan.Status.IDENTICAL)
                else -> {
                    planned += written
                    val isConflicting = setlist.fileName in librarySetlistsByFileName && conflictingFileNames.add(setlist.fileName)
                    entry(fileName = setlist.fileName, status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW)
                }
            }
        }
```

Library `{ summer.setlist.json: X }`; the batch brings **X** unchanged and **Y** — a different setlist with the same
title (so the same derived `fileName`), e.g. the `summer_2.setlist.json` of the exporting device. **X** →
`IDENTICAL` (`:155`), **Y** → `CONFLICTING` (`:159`) in either order; Replace (`ImportFilesUseCaseImpl.kt:81-84`)
writes **Y** over `summer.setlist.json`, which the batch said was **X**. The `arrivedAs` route (`:150`) crosses
families here too.

## The change

Invoke the **`code-style`** skill before the first edit.

The rule to implement: **a library file that the batch itself carries unchanged is never replaced.** A different
song (or setlist) that wants such a file's name is one more file of the batch wanting a name that an earlier file of
the batch has — `NEW`, numbered by the storage layer as it is written — and never a question. That is "the names
taken by something *different*" read correctly: the batch has told us the file under that name belongs in the
library.

Because the file the batch carries may arrive *after* the song that wants its name, the decision needs to know the
whole batch's library matches before it hands out a single `CONFLICTING`. So the planner works in **two passes**:
first every entry against the library (which is independent of order), then the repeats within the batch and the
conflicts in arriving order, with the set of library files the batch carries known.

The set is keyed by the **library file name the match was made against**, not by family: that covers a match on
the desired name itself, and the cross-family `arrivedAs` route, with one rule. A match on a *numbered sibling*
(`foo_2.cho`) protects `foo_2.cho` and not `foo.cho` — the batch does not carry `foo.cho`, so a different song wanting
`foo.cho` is still a question (today's behaviour, kept, and covered by a test below).

### `ImportPlanner.kt` — songs

Replace `planSongs` (`:42-83`):

```kotlin
    /**
     * Plans each song by comparing only the library files in its collision family.
     *
     * Two passes, because whether a song is a question depends on the rest of the batch: a library file the batch
     * brings back unchanged is one the batch says belongs in the library, and replacing it with a different song that
     * wants its name would lose the very song the batch carries - and point every setlist of the batch that names it
     * at the song that replaced it. So every song is held against the library first, and only then against the songs
     * before it, with the library files the batch carries known. A song that wants the name of one of those is one more
     * song of the batch wanting a name an earlier one has, which goes in numbered.
     */
    suspend fun planSongs(
        incoming: List<IncomingSong>,
        libraryFileNames: Collection<String>,
        readLibraryText: suspend (fileName: String) -> String?,
    ): List<ImportPlan.SongEntry> {
        val libraryFamilies = libraryFileNames.groupedByFamily(LibraryFiles.SONG_EXTENSIONS)
        val libraryFileNameSet = libraryFileNames.toHashSet()
        val families = mutableMapOf<String, SongFamily>()
        val songs = incoming.inArrivingOrder(LibraryFiles.SONG_EXTENSIONS, { it.fileName }, { it.sourceFileName })
        val libraryMatches = songs.map { song ->
            val family = families.getOrPut(song.fileName) {
                readSongFamily(
                    desired = song.fileName,
                    members = song.fileName.familyKeys(LibraryFiles.SONG_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty(),
                    readLibraryText = readLibraryText,
                )
            }
            // The library file the song arrived under, where that is not its own name. An export hands its songs out under
            // their library names while the import names each one by its header, and a library file named before the rule it
            // would be named by today (a letter the table did not know yet, or a file nobody ever renamed) is still the same
            // song when it holds the same text.
            val arrivedAs = song.sourceFileName?.takeIf { it != song.fileName && it in libraryFileNameSet }
            // Most songs of most imports are the only one of their name, and folding the text of every one of them
            // for a comparison nothing asks for would copy the whole batch once more.
            val comparable = if (!family.hasLibraryTexts && arrivedAs == null) null else ChordProSplitter.comparable(song.text)
            val libraryFileName = comparable?.let(family::libraryFileNameOf)
                ?: arrivedAs?.takeIf { readLibraryText(it)?.let(ChordProSplitter::comparable) == comparable }
            LibraryMatch(family = family, comparable = comparable, libraryFileName = libraryFileName)
        }
        val keptLibraryFileNames = libraryMatches.mapNotNullTo(hashSetOf()) { it.libraryFileName }
        return songs.mapIndexed { index, song ->
            val (family, libraryComparable, libraryFileName) = libraryMatches[index]
            if (libraryFileName != null) {
                return@mapIndexed song.toEntry(fileName = libraryFileName, status = ImportPlan.Status.IDENTICAL)
            }
            // Folded only once there is an earlier song of the family to hold it against.
            val comparable = libraryComparable ?: if (family.hasPlannedSongs) ChordProSplitter.comparable(song.text) else null
            val repeatedEntryIndex = comparable?.let(family::plannedEntryIndexOf)
            if (repeatedEntryIndex != null) {
                return@mapIndexed song.toEntry(status = ImportPlan.Status.IDENTICAL, repeatedEntryIndex = repeatedEntryIndex)
            }
            family.plan(index = index, text = song.text)
            // The question is about the one file the library has under this name, so it is raised once: a second song
            // wanting the name has nothing left to replace, and goes in numbered. So does every song wanting a name
            // whose file the batch brings back unchanged, which is not the library's to give up.
            val isConflicting = family.isNameTaken && song.fileName !in keptLibraryFileNames && !family.hasConflict
            family.hasConflict = family.hasConflict || isConflicting
            song.toEntry(status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW)
        }
    }

    /** What the first pass of [planSongs] found out about one song: the library file it already is, if any. */
    private data class LibraryMatch(
        val family: SongFamily,
        /** The song's text folded for comparison, where the first pass needed it; null where there was nothing to hold it against. */
        val comparable: String?,
        val libraryFileName: String?,
    )
```

and in `SongFamily` (`:202-229`) replace `hasNothingToCompareWith` (`:213`), which mixed the two passes' questions,
with one property per pass:

```kotlin
        /** Whether the library holds any text in this family, which is what makes folding an incoming song worth it. */
        val hasLibraryTexts get() = libraryFileNames.isNotEmpty()

        /** Whether an earlier song of the batch has been planned under this name, which is what a repeat is held against. */
        val hasPlannedSongs get() = plannedSongs.isNotEmpty()
```

Notes for the implementer:

- The folding economy is kept: pass 1 folds a song only where the library has texts in its family or it arrived
  under a library name (the same condition as today, minus the planned songs, which do not exist yet in pass 1);
  pass 2 folds the rest only when the family has a planned song. No song is folded twice — pass 2 reuses pass 1's
  `comparable`.
- The library is read exactly as often as today: `readSongFamily` once per family (pass 1), `arrivedAs` once per song
  that has one (pass 1).
- `repeatedEntryIndex` refers to the index in the returned list, which is `songs` (the sorted list) in both passes —
  unchanged from today, where `mapIndexed` ran over the same sorted list.
- `song.fileName !in keptLibraryFileNames` compares the *desired* name with the names matches were recorded under.
  Plan 22 changes what those names are on a case-insensitive file system (the listed spelling) and changes this
  comparison to `family.takenFileName !in keptLibraryFileNames` accordingly; land 21 first.

### `ImportPlanner.kt` — setlists

Replace `planInOrder` (`:135-164`) with the same two passes:

```kotlin
    private fun planInOrder(
        incoming: List<IncomingSetlist>,
        librarySetlists: List<Setlist>,
        songFileNames: Map<String, String>,
    ): List<ImportPlan.SetlistEntry> {
        val libraryFamilies = librarySetlists.map { it.fileName }.groupedByFamily(SETLIST_EXTENSIONS)
        val librarySetlistsByFileName = librarySetlists.associateBy { it.fileName }
        val written = incoming.map { it.setlist.withSongFileNames(songFileNames) }
        // See planSongs: every setlist against the library first, so that a library setlist the batch brings back
        // unchanged is known before a different one wanting its name could be made a question about replacing it.
        val identicalSetlists = incoming.mapIndexed { index, (setlist, sourceFileName) ->
            val members = setlist.fileName.familyKeys(SETLIST_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty()
                .mapNotNull(librarySetlistsByFileName::get)
            members.firstOrNull { it.holdsTheSameAs(written[index]) }
                // See planSongs: a setlist file named before today's rule, arriving under that name.
                ?: librarySetlistsByFileName[sourceFileName]?.takeIf { it.holdsTheSameAs(written[index]) }
        }
        val keptLibraryFileNames = identicalSetlists.mapNotNullTo(hashSetOf()) { it?.fileName }
        val plannedSetlists = mutableMapOf<String, MutableList<Setlist>>()
        val conflictingFileNames = mutableSetOf<String>()
        return incoming.mapIndexed { index, (setlist, sourceFileName) ->
            fun entry(fileName: String, status: ImportPlan.Status) =
                ImportPlan.SetlistEntry(fileName = fileName, setlist = setlist, status = status, sourceFileName = sourceFileName)
            val identical = identicalSetlists[index]
            val planned = plannedSetlists.getOrPut(setlist.fileName) { mutableListOf() }
            when {
                identical != null -> entry(fileName = identical.fileName, status = ImportPlan.Status.IDENTICAL)
                planned.any { it.holdsTheSameAs(written[index]) } -> entry(fileName = setlist.fileName, status = ImportPlan.Status.IDENTICAL)
                else -> {
                    planned += written[index]
                    val isConflicting = setlist.fileName in librarySetlistsByFileName &&
                        setlist.fileName !in keptLibraryFileNames &&
                        conflictingFileNames.add(setlist.fileName)
                    entry(fileName = setlist.fileName, status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW)
                }
            }
        }
    }
```

`replanSetlists` goes through the same function, so the rule holds after the songs have been written as well.
What that means in the two directions, both already safe with the fix (and both tested below):

- A setlist that was `IDENTICAL` at plan time can stop being so at replan time (its song was kept numbered). The
  library file it matched is then no longer "kept" in the replan, so a different setlist wanting that name *could*
  come out `CONFLICTING` at replan time — but it was `NEW` in the plan the user answered, and
  `ImportFilesUseCaseImpl.kt:75` already writes "a setlist the question was not about" numbered
  (`entry.status == CONFLICTING && planned.status != CONFLICTING -> Action.WRITE`). The previously identical setlist is
  in the same position (`IDENTICAL` → `CONFLICTING`) and is written numbered too. The library's file is untouched.
- A setlist that was `CONFLICTING` at plan time (the user answered Replace) and whose name turns out to be carried
  unchanged by another setlist at replan time becomes `NEW` → written numbered rather than replacing. That is the
  safe direction: the answer was about a file the batch, as written, now brings back.

### `ImportFilesUseCaseImpl.kt` — the last line of defence

The planner is where the rule lives, and the tests pin it there. Add one guard at the only place that overwrites a
file, so that a future change to the planner cannot bring this back silently. Before the song loop (`:45`):

```kotlin
            // The planner never asks about a name whose library file this import brings back unchanged; held here too,
            // since this is the one place anything is overwritten and the song lost would be one the import carries.
            val keptSongFileNames = plan.songs
                .filter { it.status == ImportPlan.Status.IDENTICAL && it.repeatedEntryIndex == null }
                .mapTo(hashSetOf()) { it.fileName }
```

and in the `REPLACE` arm (`:52`):

```kotlin
                        shouldReplace = action == Action.REPLACE && entry.fileName !in keptSongFileNames &&
                            replacedSongFileNames.add(entry.fileName),
```

The same for setlists, on the replanned list (after `:72`):

```kotlin
            val keptSetlistFileNames = setlists.filter { it.status == ImportPlan.Status.IDENTICAL }.mapTo(hashSetOf()) { it.fileName }
```

and at `:83`: `shouldReplace = action == Action.REPLACE && entry.fileName !in keptSetlistFileNames && replacedSetlistFileNames.add(entry.fileName)`.

With `shouldReplace` false the repository numbers the name (`uniqueName`), which is exactly "keep both" for that one
file.

### `ImportPlan.kt` (`:data:model`) — KDoc

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt:82`, `Status.NEW`, reads
"The name is free, or taken only by an earlier file of the same import, so the storage layer numbers it if needed."
Make it: "The name is free, taken only by an earlier file of the same import, or taken by a library file that the same
import brings back unchanged — which it is not the library's to give up — so the storage layer numbers it if needed."
And `CONFLICTING` (`:93`): "The name is taken in the library by something else that the import does not bring back
itself, which only the user can decide about."

## Tests

All in `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`,
using its existing helpers (`plan`, `song` — every `song()` wants `x.cho` — `setlist`, and the texts `A`, `B`, `C`).
Every ordering is its own assertion, since the bug is that the result depended on which entry reached the `else`
branch first.

1. `aDifferentSongWantingANameTheBatchBringsBackUnchangedIsNew` — library `x.cho = A`; batch
   `song(A, "x.cho"), song(B, "x_2.cho")` → statuses `[IDENTICAL, NEW]`, file names `[x.cho, x.cho]`,
   `ImportPlan(songs = plan).hasConflicts == false`. `plannedSongFileNames(plan)` is `{x.cho → x.cho, x_2.cho → x.cho}`
   (the numbered name is only known once written; that is the existing contract).
2. Same library, batch `song(B, "x (2).cho"), song(A, "x.cho")` (a sync conflict copy, given first) →
   `inArrivingOrder` puts A first → `[IDENTICAL, NEW]`, no conflicts.
3. Same library, no source names, **both orders**: `song(A), song(B)` → `[IDENTICAL, NEW]`; `song(B), song(A)` →
   `[NEW, IDENTICAL]`. No conflicts in either. This is the collection / two-folders case, where arriving order is the
   given order.
4. Repeats within the batch, library `x.cho = A`: `song(B), song(A), song(B)` → `[NEW, IDENTICAL, IDENTICAL]` with
   `repeatedEntryIndex == 0` on the last; `song(A), song(B), song(B)` → `[IDENTICAL, NEW, IDENTICAL]` with
   `repeatedEntryIndex == 1`. No conflicts.
5. Two different songs and the library's copy: library `x.cho = A`, batch `song(B), song(C), song(A)` →
   `[NEW, NEW, IDENTICAL]`, no conflicts (today: `[CONFLICTING, NEW, IDENTICAL]`).
6. Unchanged behaviour when the batch does not carry the library's copy: library `x.cho = A`, `song(B)` →
   `[CONFLICTING]` (the existing `batchDuplicatesFollowTheFirstEntryAndOnlyOneEntryConflicts` stays green unchanged).
7. A numbered sibling protects only itself: library `x.cho = A, x_2.cho = B`; batch `song(B, "x_2.cho"), song(C)`.
   `inArrivingOrder` sorts the numbered arrival after the unnumbered one, so the result is `[C, B]` →
   `[CONFLICTING, IDENTICAL (fileName x_2.cho)]`, and `summary.conflictingFileNames == [x.cho]`: the batch does not
   bring `x.cho` back, so replacing it stays the user's call.
8. Cross-family `arrivedAs`, both orders: library `x.cho = A` only;
   `IncomingSong(fileName = "y.cho", text = A, sourceFileName = "x.cho")` and
   `IncomingSong(fileName = "x.cho", text = B, sourceFileName = null)`. Given in that order →
   `[IDENTICAL (x.cho), NEW]`; given the other way round → `[NEW, IDENTICAL (x.cho)]`. No conflicts in either.
9. The existing `songsAreComparedAcrossTheirWholeCollisionFamily`,
   `numberedArrivalsAreHandledInTheirLibraryOrderAndOnlyReadTheirFamily` (which also pins that only the family is
   read — the two-pass version must read the same set) and the `arrivedAs` tests stay green unchanged.
10. Setlists, both orders: library `[X]` with `X = setlist("summer.setlist.json", "Summer")`; `Y = X.copy(entries =
    listOf(Setlist.Entry("song.cho")))`. `planSetlists(incoming = [X, Y])` → `[IDENTICAL, NEW]`;
    `[Y, X]` → `[NEW, IDENTICAL]`; `hasConflicts == false` in both.
11. Setlists, numbered sibling protects only itself: library `[X (summer), Y' (summer_2, different entries)]`, batch
    `[Y' as it is in the library but with fileName summer.setlist.json, Z]` → `[IDENTICAL (summer_2), CONFLICTING]`.
12. Setlists, cross-family `arrivedAs`: library `[old = setlist("old_name.setlist.json", "Summer")]`; batch
    `IncomingSetlist(old.copy(fileName = "summer.setlist.json"), "old_name.setlist.json")` and a different setlist
    whose derived `fileName` is `old_name.setlist.json` (title "Old name") → `[IDENTICAL (old_name), NEW]`.
13. `replanSetlists`, identical turning different: build the plan with `planEditedSongWithItsSetlist()`'s library
    (`SONG_SETLIST`) plus a second incoming setlist `W = SONG_SETLIST.copy(entries = listOf(Setlist.Entry("other.cho")))`
    under the same file name → planned `[IDENTICAL, NEW]` (nothing asked). Replan with `song.cho → song_2.cho` →
    `[CONFLICTING, NEW]`; assert that neither entry is both planned `CONFLICTING` and replanned `CONFLICTING` (which
    is the only combination `ImportFilesUseCaseImpl` would turn into a Replace).
14. `replanSetlists`, conflicting turning safe: library `SONG_SETLIST` pointing at `song_2.cho`; incoming the same
    setlist pointing at `song.cho` plus `W` as above. Planned with `plannedSongFileNames` expecting `song.cho` →
    `[CONFLICTING, NEW]`. Replan with `song.cho → song_2.cho` → `[IDENTICAL, NEW]`.

And one test of the applier, new file
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`,
with an in-memory `SongRepository` (a `MutableMap<String, String>` of files; `importSong` numbers `x_2`, `x_3`… unless
`shouldReplace`, `rescan` and `loadSongsIfNeeded` answer from the map, everything else
`UnsupportedOperationException`, like the fakes in `ExportLibraryUseCaseImplTest`) and an in-memory `SetlistRepository`
(same shape, `importSetlist` numbering the same way):

15. `replace never overwrites a song the import brings back` — library `foo.cho = A` and no setlists; plan built by
    `ImportPlanner.planSongs` / `planSetlists` (with `plannedSongFileNames`) for songs `A (foo.cho)`, `B (foo_2.cho)`
    and a setlist `set = [foo.cho, foo_2.cho]`; `plan.hasConflicts` is false. Applied with each of the three
    resolutions (the answer is never asked for, but the applier is handed one either way): `foo.cho == A`,
    `foo_2.cho == B`, and the written setlist names `[foo.cho, foo_2.cho]` in all three. (At HEAD, with `REPLACE`:
    `foo.cho == B`, no `foo_2.cho`, and the setlist handed to `importSetlist` names `[foo.cho, foo.cho]`, which the real
    document then keeps once.)
16. `the applier refuses to replace a kept name even if the plan asks` — hand-build an `ImportPlan` with
    `[IDENTICAL foo.cho (A), CONFLICTING foo.cho (B)]` (the plan HEAD produces) and apply with `REPLACE`: `foo.cho`
    still holds A and B lands at `foo_2.cho`. This pins the guard independently of the planner.

Run: `./gradlew :domain:implementation:desktopTest`, then the root unit test command
`./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`.

## Verification

Confirm the loss first, on a scratch library (the desktop build is the easiest: its library is a folder).

1. With the app closed, write into the desktop library (macOS: `~/Library/Application Support/Campfire/library/songs/`)
   `foo.cho` = `{title: Foo}` + a verse line `A`, and nothing else named `foo*`.
2. Build an archive `test.zip` holding `songs/foo.cho` (identical bytes to the library's) and `songs/foo_2.cho` =
   `{title: Foo}` + a verse line `B`, plus `setlists/set.setlist.json` naming `foo.cho` and `foo_2.cho` (copy the shape
   of any exported setlist).
3. Start the app, import `test.zip`.
   - **Before the fix:** the conflict dialog names `foo.cho`. Choose Replace: `foo.cho` now holds `B`, there is no
     `foo_2.cho`, and the imported setlist shows one entry. Song A is gone.
   - **After the fix:** no dialog. The summary reports one song imported and one duplicate. The library holds
     `foo.cho` (A) and `foo_2.cho` (B), and the setlist shows both.
4. Repeat with the zip entries in the other order (build it with `foo_2.cho` added first), and with the two songs as one
   file split by `{new_song}`: same result after the fix.
5. Regression: import a zip that holds only `foo_2.cho` (B, header `Foo`) into a library that has `foo.cho` (A). The
   dialog must still appear (the batch does not bring A back) and Keep both / Replace / Skip must still do what they
   say.
6. Same for setlists: a library setlist `Summer`, an archive with it unchanged and a second setlist titled `Summer`
   with different entries → no dialog after the fix, the second one lands as `summer_2.setlist.json`.

## Docs

- Root `CLAUDE.md`, the "An **import decides before it writes**" convention (line ~181 in the working tree): the
  sentence "and two different files of one batch that want the same name are never a question: the second is
  numbered like any other collision" is now true; extend it with: "— the library's own file among them: a song or
  setlist the batch brings back unchanged is never offered up for replacement, so a different one wanting its name is
  numbered next to it." Note that the root `CLAUDE.md` has uncommitted edits by another agent at the time of writing;
  merge rather than overwrite.
- `domain/implementation/CLAUDE.md`, the `PrepareImportUseCaseImpl` / `ImportPlanner` bullet (line 51: "records a
  repeat in the batch by entry index, asks one conflict question per library name"): add after it "— and none about a
  library file the batch itself brings back unchanged, which the planner finds in a first pass over the whole batch
  before it plans in arriving order, since the unchanged copy may come after the song that wants its name.
  `ImportFilesUseCaseImpl` holds the same rule once more at the one place a file is overwritten."
- `ImportPlan.kt` KDoc as given in **The change**.
- `ImportPlanner`'s class KDoc (`:17-26`) needs no change; `planSongs` gets the KDoc given above.

## Files touched

- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt` (KDoc only)
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt` (new)
- `CLAUDE.md`, `domain/implementation/CLAUDE.md`

## Depends on

Nothing. **Land it first in lane C**: plan 22 edits the same functions of `ImportPlanner.kt` (`readSongFamily`,
`SongFamily`, the conflict condition written here) and `ImportFilesUseCaseImpl.kt`'s `REPLACE` arm, and is written
against the code as this plan leaves it. No other lane touches these files.
