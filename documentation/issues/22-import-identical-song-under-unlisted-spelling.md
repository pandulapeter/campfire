# 22 — An import records a song under a spelling of its name the library does not list

**Severity:** wrong state, spurious conflict questions (macOS and Windows desktop; any platform for a decomposed name on APFS) · **Area:** `:domain:implementation` (`ImportPlanner.kt`, `ImportFilesUseCaseImpl.kt`), `:data:model` (`ImportPlan.kt`)

**Read, not run.** This was found by reading the import planner at HEAD (2065e47f) together with the desktop file
storage; it has not been reproduced in a running build. The "Verification" section below is how to confirm it, and
confirming it is the first step of the work.

## What the user sees

The library holds a song under a name the app would spell differently — `Wonderwall.cho`, put into the desktop
library folder by hand or brought down by sync from a folder somebody else fills — and its header derives
`wonderwall.cho`. On a case-insensitive file system (macOS and Windows by default):

- **Re-importing an export of the library** (or of a setlist holding the song) asks about a conflict that is not
  one: the library's setlist naming `Wonderwall.cho` is offered for replacement by the archive's copy of the same
  setlist, because the archive's copy is compared pointing at `wonderwall.cho`. Keep both writes a numbered duplicate
  of the setlist that shows the song as missing.
- **A setlist imported next to the song** points at `wonderwall.cho`, which the song list does not have (it lists
  `Wonderwall.cho`), so the setlist shows the song as missing although it is right there.
- **Importing the single file** reports it as already in the library and then tries to open `wonderwall.cho`, a name
  the song list does not hold.
- **Replace**, for a different song of that header, writes `wonderwall.cho` over `Wonderwall.cho`. On APFS the atomic
  replace (`Files.move(..., REPLACE_EXISTING)`) may leave the file under either spelling; the import records
  `wonderwall.cho` either way, and everything that pointed at `Wonderwall.cho` may be left pointing at a name that is
  no longer listed.

The same happens on APFS (macOS, iOS) for a name listed in decomposed Unicode form — any name with a non-Latin
letter carrying a mark (`й` is `и` + U+0306 decomposed), which `LibraryFiles.normalizedName` keeps rather than folds —
since APFS answers a read in either form with the file stored in the other. That case is worse: the listed name is
not even found as a member of the song's family, so an identical song is imported again as a numbered duplicate.

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt:184-200`:

```kotlin
    private suspend fun readSongFamily(
        desired: String,
        members: List<String>,
        readLibraryText: suspend (fileName: String) -> String?,
    ): SongFamily {
        val libraryFileNames = mutableMapOf<String, String>()
        var isNameTaken = false
        // The name itself first and the rest by their number, so that where the library holds the same text twice
        // an incoming copy of it is always said to be the same one of them.
        val candidates = (members + desired).distinct().sortedWith(compareBy<String>({ it != desired }, { it.length }, { it }))
        candidates.forEach { fileName ->
            val text = readLibraryText(fileName) ?: return@forEach
            if (fileName == desired) isNameTaken = true
            libraryFileNames.getOrPut(ChordProSplitter.comparable(text)) { fileName }
        }
        return SongFamily(isNameTaken = isNameTaken, libraryFileNames = libraryFileNames)
    }
```

`desired` (`wonderwall.cho`) is read first even though the listing (`members`, from the scan) does not hold it.
`readLibraryText` is `songContentRepository.loadSongContent(fileName, shouldCache = false)`
(`PrepareImportUseCaseImpl.kt:127`), which ends in `JvmFileStorage.readText`
(`data/source/local/implementation/src/desktopMain/.../storage/file/JvmFileStorage.kt:70-72`, `file(directory, name).isFile`),
and on a case-insensitive file system that read succeeds with the content of `Wonderwall.cho`. So the family records
the text under `wonderwall.cho` (`getOrPut` keeps the first), the listed spelling read next is dropped, and the song
becomes `IDENTICAL` with `fileName = "wonderwall.cho"` (`:67`, `:71`). That name is what `plannedSongFileNames`
(`:120-129`) and `ImportFilesUseCaseImpl`'s `storedSongFileNames` (`ImportFilesUseCaseImpl.kt:56-60`) map the
archive's `Wonderwall.cho` onto, so every setlist of the batch is compared (`planInOrder`, `:145`) and written
(`ImportFilesUseCaseImpl.kt:82`) pointing at `wonderwall.cho`, and the song list and the view model look entries up by
exact name (`presentation/.../ui/CampfireViewModel.kt:637`, `songsByFileName[entry.songFileName]`).

The `arrivedAs` route cannot rescue it: it is only tried when the family found nothing (`:67-68`, `?:`), and the
family found the text — under the wrong spelling.

For a different song, `isNameTaken` is set by that same read, the entry is `CONFLICTING` under `wonderwall.cho`, and
Replace writes `importSong(fileName = "wonderwall.cho", shouldReplace = true)` (`ImportFilesUseCaseImpl.kt:49-53`),
which returns a `Song` named `wonderwall.cho` whatever the file system then lists (`SongLocalSourceImpl.kt:96-100`
reads it back through `info(name)`, which reports the name it was asked for).

For the decomposed form, the family lookup itself misses: `familyKeys` (`:232-236`) lowercases but does not compose,

```kotlin
    private fun String.familyKeys(extensions: List<String>): List<String> {
        val extension = extensions.firstOrNull { endsWith(it, ignoreCase = true) } ?: return emptyList()
        val name = dropLast(extension.length).lowercase()
        return listOfNotNull(name, LibraryFiles.withoutCollisionSuffix(name))
    }
```

so a listed `йога.cho` (decomposed) and a desired `йога.cho` (composed — `normalizedName` composes, `LibraryFiles.kt:107`)
have different keys, and the listed file is never a member.

On a case-*sensitive* file system (Linux, Android's app storage, OPFS) the read of `wonderwall.cho` returns null, the
listed `Wonderwall.cho` is read as a member and recorded under its own spelling, and none of this happens. That
behaviour is right and must not change.

## The change

Invoke the **`code-style`** skill before the first edit. This plan is written against `ImportPlanner.kt` and
`ImportFilesUseCaseImpl.kt` **as plan 21 leaves them**.

The rule: **every name the planner records is one the library lists.** When the read of the desired name succeeds
although the listing does not hold it, the file system has answered for a listed file under another spelling of the
same name, and that listed spelling is what is recorded — as the identical song, and as the file a replacement
overwrites. Whether two spellings are one name stays the file system's call (the read decides, as today), so a
case-sensitive platform keeps treating `Wonderwall.cho` and `wonderwall.cho` as two names.

### `ImportPlanner.kt`

1. `familyKeys` composes before it folds case, so that a decomposed listed name is a member of the family of its
   composed spelling (`import com.pandulapeter.campfire.data.model.domain.normalizedToNfc`):

   ```kotlin
           val name = dropLast(extension.length).normalizedToNfc().lowercase()
   ```

   (Composed first and folded second, the order `LibraryFiles.normalizedName` uses and for the same reason.)
   `inArrivingOrder` goes through `familyKeys` too and stays consistent.

2. `readSongFamily` records the listed spelling:

   ```kotlin
       /** Reads a family once per import, however many incoming songs share its desired name. */
       private suspend fun readSongFamily(
           desired: String,
           members: List<String>,
           readLibraryText: suspend (fileName: String) -> String?,
       ): SongFamily {
           val libraryFileNames = mutableMapOf<String, String>()
           // The spelling the library lists the desired name under. Every name recorded here is what the setlists of the
           // batch are pointed at and what the import opens, so it has to be one the song list holds - and a file system
           // that does not tell case apart (macOS and Windows), or the two Unicode forms (APFS), answers a read of the
           // derived name with the file listed under another spelling of it.
           val listedDesired = members.firstOrNull { it == desired } ?: members.firstOrNull { it.isSpellingOf(desired) }
           var takenFileName: String? = null
           // The name itself first and the rest by their number, so that where the library holds the same text twice
           // an incoming copy of it is always said to be the same one of them.
           val candidates = (members + desired).distinct().sortedWith(compareBy<String>({ it != desired }, { it.length }, { it }))
           candidates.forEach { fileName ->
               // Already read, through the derived name the file system answered with it.
               if (fileName != desired && fileName == takenFileName) return@forEach
               val text = readLibraryText(fileName) ?: return@forEach
               // Only a read of the derived name itself says whether a replacement would have a file to write over. Where
               // the listing does not hold that name, the file that answered is the one it lists under another spelling;
               // with nothing listed under any spelling of it, it is a file written since the scan, under its own name.
               val listedName = if (fileName == desired) listedDesired ?: desired else fileName
               if (fileName == desired) takenFileName = listedName
               libraryFileNames.getOrPut(ChordProSplitter.comparable(text)) { listedName }
           }
           return SongFamily(takenFileName = takenFileName, libraryFileNames = libraryFileNames)
       }

       /** Whether this listed name is [name] as a file system that ignores case and Unicode form would read it. */
       private fun String.isSpellingOf(name: String) = normalizedToNfc().equals(name.normalizedToNfc(), ignoreCase = true)
   ```

3. `SongFamily` carries the spelling instead of a flag:

   ```kotlin
       private class SongFamily(
           /**
            * The library file under the derived name itself, as the library lists it: what makes a different song a
            * question, and what replacing it writes over. Null while the name is free.
            */
           val takenFileName: String?,
           /** The library file each comparable text of the family is found in. */
           private val libraryFileNames: Map<String, String>,
       ) {
           val isNameTaken get() = takenFileName != null
           ...
   ```

4. In `planSongs` (as plan 21 wrote it), the conflict is about the listed file, and the entry says which one it is:

   ```kotlin
               val isConflicting = family.isNameTaken && family.takenFileName !in keptLibraryFileNames && !family.hasConflict
               family.hasConflict = family.hasConflict || isConflicting
               song.toEntry(
                   status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW,
                   replacesFileName = family.takenFileName?.takeIf { isConflicting && it != song.fileName },
               )
   ```

   with `toEntry` (`:171-181`) taking and passing on `replacesFileName: String? = null`. (`keptLibraryFileNames` holds
   the names identical songs were recorded under, which after step 2 are listed spellings, so the comparison has to
   be against the listed spelling too — that is the one line plan 21 wrote with `song.fileName` for this reason.)

### `ImportPlan.kt` (`:data:model`)

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt`, add to `SongEntry`
(after `repeatedEntryIndex`, `:65`):

```kotlin
        /**
         * For a [Status.CONFLICTING] entry whose name the library lists under another spelling of it -
         * `Wonderwall.cho` for `wonderwall.cho`, on a file system that does not tell the two apart: the file a
         * replacement writes over, so that the song keeps the name the library and its setlists know it by. Keeping
         * both still writes under [fileName], the name the app gives the song, which the storage layer numbers.
         */
        val replacesFileName: String? = null,
```

and in `summary` (`:42`), name the file the question is about:

```kotlin
            conflictingFileNames = songs.filter { it.status == Status.CONFLICTING }.map { it.replacesFileName ?: it.fileName } +
```

### `ImportFilesUseCaseImpl.kt`

The `WRITE, REPLACE` arm (as plan 21 left it) writes over the listed spelling when it replaces, and under the derived
name when it keeps both:

```kotlin
                    Action.WRITE, Action.REPLACE -> {
                        // A replacement goes over the file as the library lists it; anything else is written under the
                        // name the app gives the song, which the storage layer numbers if it is taken.
                        val replacedFileName = entry.replacesFileName ?: entry.fileName
                        val shouldReplace = action == Action.REPLACE && replacedFileName !in keptSongFileNames &&
                            replacedSongFileNames.add(replacedFileName)
                        songRepository.importSong(
                            fileName = if (shouldReplace) replacedFileName else entry.fileName,
                            text = entry.text,
                            shouldReplace = shouldReplace,
                        ).fileName.also { importedSongFileNames += it }
                    }
```

Keep both on a case-insensitive file system then writes `wonderwall.cho` → `uniqueName` finds it taken
(`FileNames.kt:62`, `exists` is case-insensitive there) → `wonderwall_2.cho`, a normalized name, as it should be.

### What is deliberately left alone

- Setlists compare their conflict membership by exact name (`setlist.fileName in librarySetlistsByFileName`,
  `ImportPlanner.kt:159`) and do not read the file system. A library setlist listed `Summer.setlist.json` and a
  different incoming `summer.setlist.json` are then `NEW`, and the storage numbers the incoming one on a
  case-insensitive file system — no question is asked, nothing is lost, and the identical case is already found
  through the case-folded family (`familyKeys`) and recorded under the library's own `fileName`. Not worth a second
  mechanism.
- The view model's exact lookup of setlist entries is plan 17's (lane B) fallback, which makes a setlist that already
  points at another spelling show its song. This plan keeps the import from writing such a setlist in the first place;
  the two are independent and each is useful without the other.

## Tests

In `ImportPlannerTest.kt`, with two read functions over a `Map<String, String>` library:
`exact = { library[it] }` and a folding one standing for macOS,
`folding = { name -> library.entries.firstOrNull { it.key.normalizedToNfc().equals(name.normalizedToNfc(), ignoreCase = true) }?.value }`.

1. `anIdenticalSongIsRecordedUnderTheSpellingTheLibraryLists` — library `{ "Wonderwall.cho": A }`,
   `IncomingSong(fileName = "wonderwall.cho", text = A, sourceFileName = "Wonderwall.cho")`, `folding` →
   `IDENTICAL`, `fileName == "Wonderwall.cho"`; `plannedSongFileNames == { "Wonderwall.cho" → "Wonderwall.cho" }`.
2. Same with `exact` → the same result (unchanged: found as a member).
3. A different song, `folding` → `CONFLICTING`, `fileName == "wonderwall.cho"`, `replacesFileName == "Wonderwall.cho"`,
   `summary.conflictingFileNames == ["Wonderwall.cho"]`.
4. A different song, `exact` → `NEW`, `replacesFileName == null` (a case-sensitive file system keeps two names).
5. Decomposed listing: library `{ "йога.cho": A }` (`йога`, decomposed), incoming
   `IncomingSong(fileName = "йога.cho", text = A, sourceFileName = null)`:
   with `folding` → `IDENTICAL` under the decomposed listed name; with `exact` → also `IDENTICAL` under the listed
   name, which only works once `familyKeys` composes (at HEAD the listed file is not a member and the result is `NEW`).
6. With plan 21: library `{ "Wonderwall.cho": A }`, batch `A (source "Wonderwall.cho")`, `B`, `folding` →
   `[IDENTICAL "Wonderwall.cho", NEW]`, no conflicts.
7. Setlist follow-through: the incoming setlist naming `Wonderwall.cho`, planned with `plannedSongFileNames` of test 1
   against a library setlist naming `Wonderwall.cho` → `IDENTICAL` (at HEAD: `CONFLICTING`).
8. In `ImportFilesUseCaseImplTest` (new in plan 21): a hand-built plan with one `CONFLICTING` entry
   `fileName = "wonderwall.cho", replacesFileName = "Wonderwall.cho"`: applied with `REPLACE` the fake's `importSong`
   is called with `("Wonderwall.cho", shouldReplace = true)`; with `KEEP_BOTH` with `("wonderwall.cho", false)`.

`normalizedToNfc` is an `expect` in `:data:model`, which `:domain:implementation` already depends on; the tests run on
the desktop target, where it is `java.text.Normalizer`.

Run `./gradlew :domain:implementation:desktopTest`, then the root unit test command.

## Verification

On macOS (default case-insensitive APFS), desktop build:

1. With the app closed, put `Wonderwall.cho` holding `{title: Wonderwall}` and a verse (no `{artist}`, so the derived
   name is `wonderwall.cho`, which differs from the file name only in case) into
   `~/Library/Application Support/Campfire/library/songs/`. Start the app, make a setlist holding it.
2. Export the library, then import the export.
   - **Before the fix:** the conflict dialog names the setlist; Keep both adds `…_2.setlist.json` that shows the song
     as missing.
   - **After the fix:** no dialog; the summary counts only duplicates.
3. Import `Wonderwall.cho` on its own (the file from step 1, copied elsewhere): after the fix it is reported as a
   duplicate and the song that opens is the library's `Wonderwall.cho`.
4. Put a different song with header `{title: Wonderwall}` in a zip and import it: the dialog names `Wonderwall.cho`.
   Replace → the library still lists `Wonderwall.cho`, now holding the new text, and the setlist from step 1 shows it.
   Keep both → `wonderwall_2.cho` appears.
5. Linux or Android, same files: nothing changes — the incoming `wonderwall.cho` is a separate file there, as today.

## Docs

- `domain/implementation/CLAUDE.md`, the import bullet (line 51, "compares a song with the whole family of its name"):
  add "The names it records are the library's own spellings: where the file system answers the derived name with a
  file listed under another spelling of it (another case on macOS and Windows, the other Unicode form on APFS), the
  listed one is what an identical song maps to and what a replacement writes over (`ImportPlan.SongEntry.replacesFileName`),
  so that no setlist of the batch is pointed at a name the song list does not hold."
- Root `CLAUDE.md`, the NFC paragraph ("sync's name matching composes too"): "sync's name matching and the import's
  family lookup compose too."
- KDoc as given above.

## Files touched

- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportPlan.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`
- `domain/implementation/CLAUDE.md`, `CLAUDE.md`

## Depends on

- **21**, which must land first: this plan edits the conflict condition and the `REPLACE` arm plan 21 writes.
- Related to **17** (lane B), the case-/NFC-folded fallback lookup of setlist entries in `CampfireViewModel`. No file
  in common; either can land without the other.
- Related to **14** (lane B, APFS decomposed names on rename), which touches `:data:source:local`'s `FileNames.kt`,
  not these files.
