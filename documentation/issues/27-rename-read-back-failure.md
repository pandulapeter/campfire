# 27 — A rename whose file cannot be read back is reported as "nothing moved", though it moved

**Severity:** wrong state after a rare storage hiccup (all platforms; likeliest on Windows) · **Area:** `:data:source:local` (`SongLocalSourceImpl.renameSong`)

**Read, not run.** This was found by reading the rename path at HEAD (2065e47f); it has not been reproduced in a
running build. The "Verification" section below is how to confirm it, and confirming it is the first step of the
work.

## What the user sees

"Update file name" on a song moves its file (written under the new name, the old one deleted), and then reads the new
file back for the song list. If that one read fails — an antivirus or a sync client on Windows opening the brand new
file for a scan the moment it appears is the common way — the whole rename is treated as if nothing had happened:

- the song list keeps a row for the **old** name, whose file is gone (opening it shows the song as missing);
- the new file is not in the list until the next rescan;
- no setlist and no saved transposition follow the rename — they keep naming the deleted file and the setlists show
  the song as missing;
- the open song details / editor screens stay on the old name;
- no message is shown at all.

## Cause

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt:102-112`:

```kotlin
    override suspend fun renameSong(song: Song): Song? {
        val extension = song.fileName.knownExtension()
        val desired = songFileName(title = song.title, artist = song.artist, extension = extension)
        if (song.fileName.isNamed(desired)) return null
        val text = fileStorage.readText(StorageDirectory.SONGS, song.fileName) ?: return null
        val fileName = fileStorage.uniqueName(StorageDirectory.SONGS, desired, currentName = song.fileName)
        fileStorage.moveFile(StorageDirectory.SONGS, currentName = song.fileName, newName = fileName) { name ->
            fileStorage.writeText(StorageDirectory.SONGS, name, text)
        }
        return loadSong(fileName)
    }
```

`loadSong` (`:75-77`) is `fileStorage.info(...)?.readSong()`, and `readSong` (`:118-133`) catches every exception but
cancellation and answers null. So once `moveFile` has written the new file and deleted the old one, a failed read-back
returns `null` — which the contract (`SongLocalSource.kt`, "or null when the file could not be read or is already named
that way") and every caller take to mean *nothing moved*.

`data/repository/implementation/.../SongRepositoryImpl.kt:95-102`:

```kotlin
    override suspend fun renameSong(song: Song): Song? = naming {
        val renamed = songLocalSource.renameSong(song) ?: return@naming null
        songContentRepository.invalidate(song.fileName)
        updateData { current ->
            current.orEmpty().filterNot { it.fileName == song.fileName || it.fileName == renamed.fileName } + renamed
        }
        renamed
    }
```

returns before the cache update and the content invalidation. `RenameSongFileUseCaseImpl.kt:43`
(`songRepository.renameSong(song)?.fileName ?: return null`) returns null — its KDoc: "nothing moved" — so no reference
is followed, and `CampfireViewModel.updateSongFileName` (`presentation/.../ui/CampfireViewModel.kt:1090`,
`renameSongFile(song) ?: return@launchLibraryChange`) leaves every screen where it was.

`createSong` and `importSong` in the same file (`:87`, `:99`) throw instead in the same situation ("disappeared right
after it was written"), which is the right answer for them — nothing has been undone that the caller must follow. A
rename is different: the old file is already gone, so throwing would be no better (the use case would propagate it,
the view model would say "Something went wrong", and still nothing would follow).

## The change

Invoke the **`code-style`** skill before the first edit.

After the move, the answer must be "it moved, and here is where", whatever the read-back says. The metadata of the
moved song is known without reading it: the rename wrote exactly the text it read (`:106`, `:109`), so everything the
scan derives from the text is what `song` already carries; only the name (and `canUpdateFileName`, which is false for a
file just named by its metadata) differ. So fall back to the `Song` the caller passed in, renamed.

`SongLocalSourceImpl.kt`, replace the last line of `renameSong`:

```kotlin
        // The file has moved by now whatever the read back says, so the answer is where it went: a null here would tell
        // every caller that nothing moved, and the setlists, the saved transposition and the open screens would stay on
        // a name that is gone. The text was written unchanged, so the song is the one that was renamed, under the new
        // name - which only a read that failed (a scanner holding the brand new file open) makes the fallback.
        val moved = try {
            loadSong(fileName)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the song \"$fileName\" back after the rename: ${exception.message}")
            null
        }
        return moved ?: song.copy(fileName = fileName, canUpdateFileName = false)
```

(`loadSong` already swallows a failure of the *read*; the `try` covers `fileStorage.info`, which on iOS and the web
can throw on its own — see plan 11. `lastModified` and `size` of the fallback are the old file's, which is harmless:
the text is the same length, and the next rescan corrects the time.)

Update the contract in `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SongLocalSource.kt:71-77`:

```kotlin
    /**
     * Moves the song's file to the name its own metadata gives it and returns it under that name, or null when nothing
     * moved: the file could not be read before the move, or is already named that way ([Song.canUpdateFileName] is what
     * asks beforehand). Once the file has moved the answer is never null - a song that cannot be read back is returned
     * as it was, under the new name - since a caller told that nothing moved would leave everything that names the old
     * file pointing at one that is gone. A move that fails throws.
     *
     * Only the file moves. Everything that refers to the song by its old name - the setlists holding it, the saved
     * transposition - is the caller's to follow, the same way a deletion is.
     */
```

Nothing changes in `SongRepositoryImpl.renameSong` or `RenameSongFileUseCaseImpl`: with a non-null answer they update
the cache, invalidate the old content, follow the references and move the screens exactly as for a rename that read
back fine. The repository's KDoc in `SongRepository.kt:54-58` ("returns it under that name, or null when nothing
moved") stays true.

### Fix the reviewer's first suggestion

The reviewer offered "throw like createSong" as one option. That is **not** the fix: the use case would propagate the
exception after the file had moved, the view model would say "Something went wrong", and the references and screens
would still stay on the deleted name. The fix is the second option ("return the new name even when unreadable"), done
in the local source so that no caller has to know.

## Tests

`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/RenameTest.kt`
builds a `SongLocalSourceImpl` over a real `JvmFileStorage` in a temporary folder. Add:

1. `a rename that cannot read the moved file back still says where it went` — wrap the storage so that `info` of the
   *new* name fails:

   ```kotlin
       val failingInfo = object : FileStorage by fileStorage {
           override suspend fun info(directory: StorageDirectory, name: String) =
               if (name == "bar.cho") throw LibraryStorageException("Held open.") else fileStorage.info(directory, name)
       }
   ```

   (use whatever exception type `JvmFileStorage` throws — `LibraryStorageException` from `:data:source:local:api`),
   write `Old name.cho` holding `{title: Bar}\n`, load it through the unwrapped source, rename through
   `SongLocalSourceImpl(failingInfo)`: the answer is not null, its `fileName` is `bar.cho`, `title` is `Bar`,
   `canUpdateFileName` is false, and the folder lists only `bar.cho`.
2. The same with `info` answering null for the new name (a file system that has not caught up): same assertions.
3. The existing two song rename tests stay green.

Run `./gradlew :data:source:local:implementation:desktopTest`, then the root unit test command.

## Verification

Hard to provoke by hand; the unit tests are the main check. On Windows, a way to see it: rename a song while a tool
that opens every new file in the folder exclusively for a moment is running (e.g. a Defender custom scan of the
library folder, or Dropbox syncing the app's data folder).

- **Before:** the list keeps the old title's row, which opens as missing; setlists show it missing; no message.
- **After:** the list shows the song under its new name; setlists follow; the song details screen shows the renamed song.

## Docs

- `SongLocalSource.renameSong` KDoc as given above.
- `data/source/local/implementation/CLAUDE.md`: if it describes the rename's answer (grep "renameSong"), add "once the
  file has moved the answer is never null".

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SongLocalSource.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/RenameTest.kt`

## Depends on

- Lane B plans touching `SongLocalSourceImpl.kt` — **14** (APFS decomposed names on rename, which edits this same
  `renameSong`), **10** and **11** — and `RenameTest.kt` (14 adds tests there). Land after 14 or merge carefully: the
  change here is the last statement of `renameSong` only.
- Plan 26 renames `loadSongFileNames` in the same file; independent lines.
- Plan 23 edits `RenameSongFileUseCaseImpl.kt`; this plan does not touch it.
