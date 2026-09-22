# 40 · "Update file name" that renames the file but fails to update a setlist closes the song and says nothing was changed

**Severity:** minor (all platforms. Needs a setlist or preferences write to fail right after the song file was moved:
a full disk, the web's storage quota, a file locked by another program on Windows. Rare; the result is a song screen
that closes or jumps to another song and a message that says the rename failed when it happened) · **Area:**
`:domain` (`RenameSongFileUseCase`), `:presentation` (`CampfireViewModel.updateSongFileName`)

## Symptom
1. Open a song that is in a setlist and whose file name no longer matches its header, from the song list or the
   setlist.
2. Pick "Update file name" in its menu while one of the setlist writes fails.
3. The file is renamed on disk, but the snackbar says "Something went wrong". The song details screen still names
   the old file: it closes itself if that was its only song (or pages to a different song), and the in-memory text
   stays keyed by the old name.

## Cause
Since the rename follows every reference and only then reports the failures, the use case throws *after* the point
of no return (`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt:40-44`, `:69-71`):

```kotlin
val renamed = songRepository.renameSong(song)?.fileName ?: return null
withContext(NonCancellable) { updateReferences(song = song, renamed = renamed) }
return renamed
...
if (failures.isNotEmpty()) {
    throw IllegalStateException("The song was renamed, but ${failures.size} reference(s) to it could not be updated.", failures.first())
}
```

The view model's in-memory half never runs, because `launchLibraryChange` (`CampfireViewModel.kt:1834-1843`) catches
the exception at the first line
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:998-1021`):

```kotlin
val fileName = renameSongFile(song) ?: return@launchLibraryChange   // throws after the move
_songTexts.update { ... }            // skipped
backStack.forEachIndexed { ... }     // skipped
```

and sends `Message.OperationFailed`.

## Fix
Return the outcome instead of throwing once the file has moved, and report the partial failure after the view model
has caught up.

1. New file `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/SongFileRename.kt`, with the
   MPL-2.0 header copied from `SongFilter.kt` in the same folder:

   ```kotlin
   package com.pandulapeter.campfire.domain.api.models

   /**
    * What [com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase] did: the file is under [fileName] from
    * now on either way, since a move is not undone.
    *
    * @param haveReferencesFollowed False where a setlist or the saved transposition could not be rewritten to the new
    *   name and still points at the old one - a setlist then shows the song as missing until the entry is fixed by hand.
    */
   data class SongFileRename(
       val fileName: String,
       val haveReferencesFollowed: Boolean,
   )
   ```

2. `domain/api/.../useCases/RenameSongFileUseCase.kt`: change the signature to
   `suspend operator fun invoke(song: Song): SongFileRename?` (import
   `com.pandulapeter.campfire.domain.api.models.SongFileRename`) and rewrite the last two KDoc paragraphs:

   ```kotlin
    * The move is not undone when a reference cannot be followed: every setlist and the transposition are still
    * attempted, and the result says whether all of them were. The setlists among those that were not go on pointing
    * at the old name, which shows the song as missing there until the entry is fixed by hand. Nothing is thrown once
    * the file has moved, since the caller has to catch up with the new name whatever else went wrong.
    *
    * @return The new file name and whether everything followed it, or null if nothing moved - the file could not be
    *   read, or it was already named that way. The caller is what still holds the old name: the screens showing the
    *   song, and the caches keyed by it.
   ```

3. `RenameSongFileUseCaseImpl.kt`:
   - `invoke` becomes
     ```kotlin
         override suspend operator fun invoke(song: Song): SongFileRename? {
             val renamed = songRepository.renameSong(song)?.fileName ?: return null
             val haveReferencesFollowed = withContext(NonCancellable) { updateReferences(song = song, renamed = renamed) }
             return SongFileRename(fileName = renamed, haveReferencesFollowed = haveReferencesFollowed)
         }
     ```
   - `updateReferences` returns `Boolean`: replace the final `if (failures.isNotEmpty()) { throw ... }` with
     ```kotlin
             failures.forEach { println("A reference to the renamed song could not be updated: ${it.message}") }
             return failures.isEmpty()
     ```
     (the `println` keeps what the thrown exception's message used to leave in the log via `launchLibraryChange`).
   - In the class KDoc, replace "and the failures are reported together at the end" with "and whether any failed is
     reported together at the end, in the result rather than as an exception: the move has happened, and the caller
     has to follow it either way".
   - Import `com.pandulapeter.campfire.domain.api.models.SongFileRename`.

4. `CampfireViewModel.updateSongFileName` (`:998-1021`):
   ```kotlin
       fun updateSongFileName(song: Song) = launchLibraryChange {
           val rename = renameSongFile(song) ?: return@launchLibraryChange
           val fileName = rename.fileName
           ... (the body as it is, down to persistBackStack()) ...
           // Said after the screens have followed the file, which has moved whatever else could not be rewritten.
           if (!rename.haveReferencesFollowed) sendMessage(Message.SongFileRenamedPartly)
       }
   ```
   Add to the KDoc of the function: "Where a setlist or the transposition could not follow, the rename still stands
   and the screens still follow it; that is said afterwards (`Message.SongFileRenamedPartly`)."

5. `Message` (`CampfireViewModel.kt` around `:1958-1964`): add
   ```kotlin
           /** The song's file was renamed, but a setlist or its saved transposition still names the old file. */
           data object SongFileRenamedPartly : Message
   ```
   and in `CampfireApp.kt`'s `Messages` `when` (`:517-535`), next to `OperationFailed`:
   ```kotlin
           CampfireViewModel.Message.SongFileRenamedPartly -> stringResource(Res.string.songs_update_file_name_partly)
   ```
   with the import `com.pandulapeter.campfire.presentation.resources.songs_update_file_name_partly`.

6. Strings, right after `songs_update_file_name` (line 85 in both files):
   - `values/strings.xml`:
     `<string name="songs_update_file_name_partly">The file was renamed, but a setlist or the saved transposition still points at the old name</string>`
   - `values-hu/strings.xml`:
     `<string name="songs_update_file_name_partly">A fájl új nevet kapott, de egy lista vagy a mentett transzponálás még a régi névre hivatkozik</string>`

`renameSongFile` has no other caller (grep `renameSongFile` / `RenameSongFileUseCase` in `presentation` and `domain`).
An exception from `renameSong` itself (the move failing) still reaches `launchLibraryChange` and says
`OperationFailed`, which is right: then nothing changed.

## Tests
None required: `RenameSongFileUseCaseImpl` has no test today and needs three repository fakes. If the implementer
adds one, it goes in `domain/implementation/src/commonTest/.../useCases/` next to `GetScreenDataUseCaseImplTest.kt`
(a setlist repository whose `updateSetlist` throws: result `haveReferencesFollowed == false`, the transposition still
moved), run with `./gradlew :domain:implementation:desktopTest`.

## Verify
1. Desktop: make a setlist file read-only after the app has read it (`chmod a-w library/setlists/x.setlist.json`
   inside the app data directory; on macOS also `chmod a-w` the folder so the atomic replace fails), then "Update file
   name" on a song in it from the song details screen. The song stays on screen under its new name, the snackbar says
   the file was renamed but a setlist still points at the old name, and the setlist shows the entry as missing.
2. Without the read-only file: the rename works as before, no snackbar.
3. A move that fails (read-only `songs` folder): "Something went wrong", nothing changes.

Compile: `:domain:implementation:compileKotlinDesktop`, `:presentation:compileKotlinDesktop`,
`:app:android:assembleDebug`, `:presentation:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Docs
- `domain/implementation/CLAUDE.md`, the `RenameSongFileUseCaseImpl` bullet: replace "the failures are thrown
  together at the end" with "whether any failed is returned at the end rather than thrown, since the move has happened
  and the caller has to follow it either way".
- `presentation/CLAUDE.md`, the `ui/CampfireViewModel.kt` bullet, after "…so that a song renamed from the details
  screen is still the song being read." add: "A reference that could not follow does not undo any of that: the
  screens follow the file, and a snackbar then says what still names the old one."

## Touches
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/SongFileRename.kt` (new)
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/RenameSongFileUseCase.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`
- `domain/implementation/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on
Nothing. 36 also edits `CampfireApp.kt`'s `Messages` area; run them one after another.
