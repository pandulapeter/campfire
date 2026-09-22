# 06 · A huge file put into the library folder by hand is read whole on every start (iOS: killed at launch, every launch) and uploaded whole by every sync run

**Severity:** crash (iOS and desktop only, the two platforms whose library folder the user can reach; unlikely — it
takes a file of hundreds of megabytes with a song extension, `.cho` `.chopro` `.chordpro` `.crd` `.chord` `.pro`,
copied into `library/songs` or a `.setlist.json` into `library/setlists` by hand; but once it is there the app cannot
start on iOS until the user finds and removes the file in the Files app) · **Area:**
`:data:source:local:implementation` (`SongLocalSourceImpl`, `SetlistLocalSourceImpl`), `:data:repository:implementation`
(`SyncEngine`)

## Symptom
1. iOS: Files → On My iPhone → Campfire → library → songs, and copy a large file there under a song extension (a
   500 MB log or video renamed `.pro`, a folder of recordings dragged in and one of them renamed). Desktop: the same
   into `~/Library/Application Support/Campfire/library/songs` (macOS), `%APPDATA%\Campfire\…` (Windows) or
   `~/.local/share/campfire/…` (Linux).
2. Start the app. iOS: the scan reads the file whole (500 MB), decodes it into a string (UTF-16: 1 GB more), and hands
   that to the parser; the system kills the app for its memory use. Every launch after does the same. Desktop: with
   the default heap (a quarter of the RAM) an 8 GB machine throws `OutOfMemoryError`, which is not an `Exception`, so
   `readSong`'s `catch (exception: Exception)` (`SongLocalSourceImpl.kt:114`) does not skip the file and the scan
   fails as a whole or the app dies.
3. With sync connected, every run reads and hashes the file in `readLocalStates` and tries to upload all of it, and
   every other device refuses to download it anyway (`SyncEngine.downloadWithinLimit`, `MAXIMUM_REMOTE_FILE_SIZE =
   ImportLimits.MAX_TEXT_FILE_SIZE`, 8 MiB) — hundreds of megabytes of mobile data per run for a file that can never
   arrive anywhere.

Nothing the app writes is ever that large: an import refuses any text file over `ImportLimits.MAX_TEXT_FILE_SIZE`
(`ImportLimits.kt:24`), and sync refuses to download one. Only a file put there from outside reaches it.

## Cause
The library folder is reachable from outside the app on two platforms:
- iOS: `app/ios/iosApp/iosApp/Info.plist:38-41` sets `UIFileSharingEnabled` and `LSSupportsOpeningDocumentsInPlace`,
  and the library lives in `NSDocumentDirectory` (`FileStorage.ios.kt:159-160`), so it is a folder of the Files app.
- Desktop: an ordinary folder under the user's home (`FileStorage.desktop.kt:25-35`).

(Android's is app-private and the web's is OPFS; neither can receive a file from outside.)

Every size cap in the app sits on a way *in* (the import's `ImportBudget`, the zip reader's `MAX_ENTRY_SIZE`, sync's
`downloadWithinLimit`); the reads of what is already in the library have none, although the listing they start from
already knows each file's size (`StoredFileInfo.size`, `LibraryFile.size`):
- `SongLocalSourceImpl.readSong` (`:107-117`) reads and summarizes every song the listing names.
- `SetlistLocalSourceImpl.loadSetlists` (`:36-51`) reads and decodes every setlist.
- `SyncEngine.readLocalStates` (`SyncEngine.kt:160-174`) reads and hashes every library file, and `upload` (`:331-356`)
  sends it.

## Fix
One rule, the one the ways in already apply: a library file over `ImportLimits.MAX_TEXT_FILE_SIZE` is not read.

1. `SongLocalSourceImpl.readSong` (`:107`): return `null` before reading when `size > ImportLimits.MAX_TEXT_FILE_SIZE`,
   with a `println("Skipped the song \"$name\": $size bytes is more than a song file can hold.")`. It is the skip an
   unreadable file already gets, so the song is simply not in the list, and nothing else changes: `loadSong` goes
   through the same function (`info` reports the size too). KDoc of `loadSongs`: "One unreadable file must not empty
   the whole list" → "One unreadable file, or one far too large to be a song (put there from outside the app, which
   is the only way one gets in), must not empty the whole list".
2. `SetlistLocalSourceImpl.loadSetlists` (`:39-49`): the same check on `file.size` inside the `mapNotNull`, before
   `readText`.
3. `SyncEngine`: an oversized local file is left out of the run on both sides and named as failed, so that it is
   neither read nor uploaded, and so that leaving it out is not read as a deletion:
   - `readLocalStates` returns the keys it skipped next to the states: files whose `LibraryFile.size` is over the limit
     are not read (`private class LocalListing(val states: List<LocalFileState>, val tooLarge: Set<SyncKey>)`).
   - In `synchronize`, fold the remote names against the local ones **including** the skipped keys (so a remote
     `Gig.cho` still folds onto a skipped local `gig.cho`: pass
     `local.states + local.tooLarge.map { LocalFileState(it, hash = "") }` to `foldRemoteNamesOntoLocal`), then drop
     every skipped key from `remote` and from `index` before `SyncPlanner.plan`.
     Dropping the index entry is what keeps the planner from seeing "gone locally, unchanged remotely" and deleting
     the remote copy. It costs nothing: the day the file is small enough again, it is on both sides with no entry,
     which the planner compares by content (the same bytes → in step; different → the usual conflict copy).
   - Add the skipped names to the run's summary once (not once per pass): `summary = summary.plus(SyncSummary(failed =
     tooLarge.map { it.name }))` after the first `readLocalStates`, so Settings names them the way it names any file
     that could not be synced (review 2 plan 16).
   Reuse `MAXIMUM_REMOTE_FILE_SIZE` (it is `ImportLimits.MAX_TEXT_FILE_SIZE` already) and rename it
   `MAXIMUM_FILE_SIZE` with the KDoc "The largest file a run reads or downloads: what an import accepts, since a
   larger one is not a song and no other device would take it either."

Do **not**:
- throw from the storage for a large file (`FileStorage.readText` has callers — the editor, `readLibraryFile` — whose
  contract is "null or the bytes", and a size refusal is a decision of the callers that know what the file is for);
- delete or move the file: it is the user's, put there by hand;
- catch `OutOfMemoryError`: not reading the file is the fix, and an `Error` caught on the JVM leaves the heap in no
  state to carry on.

## Tests
- `:data:source:local:implementation` `desktopTest`, next to `LibraryListingTest` (JVM storage over a temporary
  directory): `a song file larger than any song is left out of the scan` — write `a.cho` (a normal song) and `b.cho` of
  `ImportLimits.MAX_TEXT_FILE_SIZE + 1` bytes of `x`; `SongLocalSourceImpl(storage).loadSongs {}` holds only `a.cho`.
  The same for a `.setlist.json` with `SetlistLocalSourceImpl`.
- `:data:repository:implementation` `SyncEngineTest` (the fake reports `size = bytes.size`):
  - `a local file too large to sync is neither uploaded nor taken for a deletion`: local `song(1)` of
    `MAX_TEXT_FILE_SIZE + 1` bytes, an index entry for it and the same key on the remote (the file grew after it was
    synced). After the run: no upload, no remote deletion, the remote file is still there, and
    `summary.failed == listOf(song(1).name)`.
  - `a local file too large to sync is not uploaded when it is new`: no index entry, not on the remote → no upload,
    named as failed.

## Verify
1. `./gradlew :data:source:local:implementation:desktopTest :data:repository:implementation:desktopTest`.
2. Desktop: `mkfile 600m ~/Library/Application\ Support/Campfire/library/songs/huge.cho` (macOS; `fallocate -l 600M` on
   Linux), `./gradlew :app:desktop:run`: the app opens with the rest of the library; the log names the skipped file.
   With sync connected, **Sync now**: the run finishes, Settings names `huge.cho` among the files that could not be
   synced, and the network monitor shows no 600 MB upload. Delete the file: the next run is clean.
3. iOS simulator: copy the same file into the app's Documents/library/songs (`xcrun simctl get_app_container booted
   <bundle id> data`), launch: the app starts.

## Docs
- `data/source/local/implementation/CLAUDE.md`, the `source/` bullet about the scan: "A file that cannot be read is
  skipped" → "A file that cannot be read is skipped, and so is one larger than `ImportLimits.MAX_TEXT_FILE_SIZE`,
  which only the user can have put there (the folder is the Files app's on iOS and a plain folder on the desktop)".
- `data/repository/implementation/CLAUDE.md`, the `sync/` paragraph: "A local file larger than a run downloads is not
  read or uploaded either: it is left out of the plan on both sides — its index entry too, so that it is not taken
  for a deletion — and named among the run's failures."

## Touches
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- the two test source sets above, `data/source/local/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. 43 edits `SongLocalSourceImpl.loadSongs` (the publishing, not `readSong`) and 03 edits
`SyncEngine.download`/`resolve` (not `synchronize`/`readLocalStates`); schedule each pair one after the other. 02 adds
`SetlistLocalSourceImpl.loadSetlist` for a single file: whichever of the two lands second gives it the same check,
throwing `LibraryStorageException("\"$fileName\" is too large to be a setlist.")` before reading — 02's `latest`
catches that and falls back on the cached setlist, which for a file the scan skipped is none.
