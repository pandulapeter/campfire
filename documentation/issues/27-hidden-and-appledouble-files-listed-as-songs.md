# 27 · Hidden files in the library folder (`._song.cho`) are listed as songs and uploaded by sync

**Severity:** wrong behaviour (desktop, iOS — the two platforms where the library folder can be reached from outside
the app; needs files copied in by hand from a FAT/exFAT stick, an SMB share or an unzip tool that keeps the AppleDouble
companions) · **Area:** `:data:model` (`LibraryFiles`), `:data:source:local:implementation` (`SongLocalSourceImpl`,
`SetlistLocalSourceImpl`, `LibraryFileLocalSourceImpl`, `ArchiveLocalSourceImpl`), `:domain:implementation`
(`PrepareImportUseCaseImpl`)

## Symptom

1. On a Mac, copy a folder of `.cho` files from an exFAT USB stick (or an SMB share) into the desktop library folder
   `library/songs`. macOS writes an AppleDouble companion `._name.cho` next to every file on such a volume and copies
   it along. The same happens on iOS through the Files app (`UIFileSharingEnabled` and
   `LSSupportsOpeningDocumentsInPlace` are both on).
2. Start Campfire. Every song is there twice: once as itself and once as a song titled `._name` whose text is a screen
   of binary read through the Windows-1252 fallback. The song count is doubled.
3. With sync connected, the next run uploads every `._name.cho` to Dropbox and every other device downloads them.

The zip import already knows about these files and drops them; the library scan, the setlist scan and the listing sync
works from do not.

## Cause

Three listings decide "is this a library file" by extension alone, each with a copy of the rule:

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt:54,72`

```kotlin
val batches = fileStorage.list(StorageDirectory.SONGS).filter { it.name.isSongFileName() }.chunked(BATCH_SIZE)
…
private fun String.isSongFileName() = LibraryFiles.SONG_EXTENSIONS.any { endsWith(it, ignoreCase = true) }
```

`…/source/SetlistLocalSourceImpl.kt:38`

```kotlin
.filter { it.name.endsWith(SETLIST_EXTENSION, ignoreCase = true) }
```

`…/source/LibraryFileLocalSourceImpl.kt:64-67`

```kotlin
private fun LibraryFileKind.matches(name: String) = when (this) {
    LibraryFileKind.SONG -> LibraryFiles.SONG_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
    LibraryFileKind.SETLIST -> name.endsWith(LibraryFiles.SETLIST_EXTENSION, ignoreCase = true)
}
```

`._song.cho` ends in `.cho`, so all three take it. The one place that has the other half of the rule keeps it to
itself — `…/source/ArchiveLocalSourceImpl.kt:49,78`:

```kotlin
.filterNot { entry -> entry.name.substringAfterLast('/').startsWith(HIDDEN_NAME_PREFIX) }
…
const val HIDDEN_NAME_PREFIX = "."
```

A fourth place sorts incoming files by extension and has the same gap for a file that is handed to the import
directly rather than inside an archive (a "select all" in the Windows or Android picker on that same USB stick shows
the `._` files): `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt:38-42`.

Nothing the app writes is affected by excluding dot-names: `LibraryFiles.normalizedName` can never produce a name that
starts with a dot (it emits letters, digits and `_`, or `untitled`), and a sync conflict copy is the other device's
name plus ` (2)` before the extension.

## Fix

The rule becomes vocabulary in `:data:model`, next to the extension lists it completes, and every listing reads it
from there.

1. **`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`** — add, right
   after `IMPORTABLE_EXTENSIONS`:

   ```kotlin
   /**
    * Whether [name] is a file some tool wrote for itself rather than one somebody put there: macOS leaves an
    * AppleDouble `._name.cho` next to every file it copies to a volume that cannot hold extended attributes, under
    * the extension of the file it belongs to, and a `.DS_Store` in every folder it has shown. Every system marks
    * these the same way, with a leading dot, and no name the app writes starts with one.
    */
   fun isHiddenFileName(name: String) = name.startsWith(HIDDEN_NAME_PREFIX)

   /**
    * Whether a file called [name] in the songs folder is a song. Campfire writes [SONG_EXTENSION], but a folder the
    * user can also open in a file manager will hold whatever they put in it, and every ChordPro extension names the
    * same thing. The library scan and the listing sync works from both ask this, which is what keeps them agreeing
    * about which files exist: one that only sync saw would be uploaded without ever being shown, and one that only
    * the scan saw would never leave the device.
    */
   fun isSongFileName(name: String) = !isHiddenFileName(name) && SONG_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

   /** The same question about the setlists folder. */
   fun isSetlistFileName(name: String) = !isHiddenFileName(name) && name.endsWith(SETLIST_EXTENSION, ignoreCase = true)
   ```

   and, with the other private constants at the bottom of the object:

   ```kotlin
   private const val HIDDEN_NAME_PREFIX = "."
   ```

2. **`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFile.kt`** — the per-kind
   form of the same question, which is what sync asks. Plan 01 has made `matches` a member of `LibraryFileKind` here
   and filters the local listing, the remote listing and the loaded index with it; only its body changes, and the
   first sentence of its KDoc ("going by its extension alone" becomes "going by its name: the right extension, and
   not a hidden file, see [LibraryFiles.isSongFileName]"):

   ```kotlin
   fun matches(name: String) = when (this) {
       SONG -> LibraryFiles.isSongFileName(name)
       SETLIST -> LibraryFiles.isSetlistFileName(name)
   }
   ```

   With that the remote listing inherits the hidden-file rule with no further change, which is what stops a
   `._x.cho` another tool left in the Dropbox folder from being downloaded. (Should this plan be carried out before
   01 after all: add the member exactly as 01 describes it, with this body, and delete the private extension in
   `LibraryFileLocalSourceImpl` as step 3 says.)

3. **`…/source/LibraryFileLocalSourceImpl.kt`** — nothing, if plan 01 has landed: it already deleted the private
   `LibraryFileKind.matches` (lines 60-67) and `kind.matches(it.name)` on line 33 resolves to the member. Otherwise
   delete that private extension, its KDoc and the `LibraryFiles` import it was the only user of.

4. **`…/source/SongLocalSourceImpl.kt`** — line 54 becomes

   ```kotlin
   val batches = fileStorage.list(StorageDirectory.SONGS).filter { LibraryFiles.isSongFileName(it.name) }.chunked(BATCH_SIZE)
   ```

   and the private `String.isSongFileName()` with its KDoc (lines 68-72) is deleted; the `LibraryFiles` import stays.
   `loadSong(fileName)` is **not** filtered: it is only ever asked about a name the app already holds.

5. **`…/source/SetlistLocalSourceImpl.kt`** — line 38 becomes
   `.filter { LibraryFiles.isSetlistFileName(it.name) }`. Add the `LibraryFiles` import if the file does not have it;
   keep the `SETLIST_EXTENSION` import only if something else in the file still uses it (grep before removing).

6. **`…/source/ArchiveLocalSourceImpl.kt`** — line 49 becomes

   ```kotlin
   .filterNot { entry -> LibraryFiles.isHiddenFileName(entry.name.substringAfterLast('/')) }
   ```

   and `HIDDEN_NAME_PREFIX` with its KDoc is removed from the companion. The comment above the filter stays word for
   word. (Plan 15 moves this filter in front of the inflating; if it has landed, the predicate is swapped wherever
   the filter now sits — the rule is the same.)

7. **`domain/implementation/…/useCases/PrepareImportUseCaseImpl.kt`** — a hidden file that was handed over directly
   is counted as skipped rather than dropped without a word: unlike an archive's entries, somebody picked it.

   ```kotlin
   fun sort(file: ImportedFile) = when {
       // Picked along with the songs it sits next to by a "select all" on a volume macOS has written to. It carries
       // the extension of the file it belongs to, and decoded as a song it would be a screen of binary.
       LibraryFiles.isHiddenFileName(file.name) -> skippedFileNames.add(file.name)
       else -> when (file.name.substringAfterLast('.', "").lowercase()) {
           in SONG_EXTENSIONS -> songFiles.add(file)
           SETLIST_EXTENSION -> setlistFiles.add(file)
           else -> skippedFileNames.add(file.name)
       }
   }
   ```

   Archive entries never reach that branch, since `unpack` has already dropped them.

8. **`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLocalSource.kt:24`**
   — the KDoc of `loadLibraryFiles` becomes: "Every song and setlist file in the library, by the rule the library
   scan uses (`LibraryFiles.isSongFileName`). Whatever else the user keeps in the folder — files with another
   extension, the hidden ones a file manager writes for itself — is left where it is rather than uploaded to their
   cloud storage."

Do **not** put the filter into `FileStorage.list`: it has four actuals, the preferences directory goes through it
too, and plan 29 relies on the JVM storage seeing its own dot-prefixed temporary files in order to clean them up.

What happens to a `._x.cho` that an earlier version already synced: it stops being a library file, so it is exactly
what plan 01 calls a foreign file with an index entry. Neither listing shows it any more, `SyncPlanner` never sees
it, and 01's `withoutForeignEntries` drops its index entry — deleting the local copy where that copy is provably
still the bytes that are in the cloud folder, and leaving it alone otherwise. On the devices that downloaded the
companion that is the tidy-up one wants. On the Mac it came from it removes an AppleDouble file macOS wrote, not the
user; its bytes are still in the Dropbox folder, and the song it belonged to is untouched. The remote copies stay in
Dropbox, invisible to the app. This is accepted rather than worked around: nothing of the user's own is deleted.

## Tests

`:data:source:local:implementation`.

- `commonTest`, `FileNamesTest` — new case `hiddenFilesAreNotLibraryFiles`:
  - `LibraryFiles.isSongFileName("a.cho")`, `("A.CHO")`, `("a.crd")` → true
  - `LibraryFiles.isSongFileName("._a.cho")`, `(".cho")`, `(".DS_Store")`, `("a.txt")` → false
  - `LibraryFiles.isSetlistFileName("s.setlist.json")` → true; `("._s.setlist.json")`, `("s.json")` → false
  - `LibraryFileKind.SONG.matches("._a.cho")` → false, `LibraryFileKind.SETLIST.matches("s.setlist.json")` → true
- `:data:repository:implementation`: nothing new. Plan 01's `SyncEngineTest` cases and its `FakeLibraryFileLocalSource`
  go through `LibraryFileKind.matches`, so they pick the rule up; run them to see that none of their fixture names
  starts with a dot.
- `desktopTest`, new class `source/LibraryListingTest` (same set-up as `RenameTest`: a `JvmFileStorage` over
  `Files.createTempDirectory("campfire-listing")`, deleted in `@AfterTest`):
  - `` `a hidden companion of a song is not a song` `` — write `a.cho` (`{title: A}\n`) and `._a.cho`
    (`byteArrayOf(0, 5, 22, 7, 0, 2)`) with `fileStorage.writeText` / `writeBytes`;
    `SongLocalSourceImpl(fileStorage).loadSongs {}` returns exactly one song, `a.cho`.
  - `` `a hidden companion of a setlist is not a setlist` `` — save a setlist through `SetlistLocalSourceImpl`, write
    `._summer.setlist.json` bytes next to it; `loadSetlists()` has one element.
  - `` `sync does not see hidden files` `` — the same four files;
    `LibraryFileLocalSourceImpl(fileStorage).loadLibraryFiles().map { it.name }` is `["a.cho", "summer.setlist.json"]`.
- `ArchiveLocalSourceTest.leaves out what the archiving tool wrote for itself` stays green unchanged — it pins the
  behaviour step 6 must keep.

## Verify

1. `./gradlew :data:source:local:implementation:desktopTest`, then the compile checks for all four targets.
2. Desktop: `./gradlew :app:desktop:run`, note the song count. Quit. In the library folder's `library/songs`, run
   `printf '\x00\x05\x16\x07' > ._test.cho` and `cp ._test.cho .DS_Store`. Start again: the count is unchanged and no
   song called `._test` is listed. With sync connected, run it: Settings reports nothing uploaded, and the Dropbox
   folder has no `._test.cho`.
3. Import: pick `._test.cho` together with a real song in the file picker — one song imported, one skipped.

## Docs

- `data/model/CLAUDE.md`, the `domain/LibraryFiles.kt` bullet: after the sentence about `IMPORTABLE_EXTENSIONS` add
  "`isSongFileName` / `isSetlistFileName` are the one answer to which files of the library folder are the library —
  the right extension and not a hidden name (`._song.cho`, the AppleDouble companion macOS writes on volumes without
  extended attributes) — asked by the library scan, by the listing sync works from and, through
  `LibraryFileKind.matches`, by the remote listing; `isHiddenFileName` is the half of it the import uses."
- `data/source/local/api/CLAUDE.md`, `LibraryFileLocalSource` bullet: add "Its listing uses the same rule as the
  library scan (`LibraryFiles.isSongFileName` / `isSetlistFileName`), so sync never moves a file the app does not show."
- `data/source/local/implementation/CLAUDE.md`, `source/` bullet: after "reads the whole ChordPro family
  (`LibraryFiles.SONG_EXTENSIONS`)" add "— hidden files left out, by `LibraryFiles.isSongFileName`, the rule every
  listing of the folder shares —".
- `domain/implementation/CLAUDE.md`, `PrepareImportUseCaseImpl` bullet: "the archiving tool's own hidden files left
  where they were" gains ", and one that was picked directly counted as skipped".

## Touches

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFile.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryFileLocalSource.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryFileLocalSourceImpl.kt` (only if 01 has not landed)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/LibraryListingTest.kt` (new)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt`
- `data/model/CLAUDE.md`
- `data/source/local/api/CLAUDE.md`
- `data/source/local/implementation/CLAUDE.md`
- `domain/implementation/CLAUDE.md`

## Depends on

01 (it makes `LibraryFileKind.matches` a member in `:data:model` and filters the remote listing and the index with it;
this plan only changes what that function answers, as 01's own "Depends on" asks). Shares `ArchiveLocalSourceImpl.kt` with 15 and `PrepareImportUseCaseImpl.kt`
with 02, 15 and 34 — land after them.
