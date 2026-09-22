# 32 — "Update file name" can name one song twice in a setlist, which crashes the setlist grid and the song pager

**Severity:** crash (all platforms) · **Area:** `:data:source:local:implementation` (`SetlistLocalSourceImpl`),
`:domain:implementation` (`RenameSongFileUseCaseImpl`), `:presentation` (`CampfireViewModel.updateSongFileName`)

**How likely:** it needs a setlist that names a file the library does not hold — which is exactly what a setlist
synced, imported or restored from another device looks like before its songs arrive, or after one of them was
deleted — *and* an **Update file name** that lands on that very name. That pairing is not rare in a synced library:
device A wrote the song down as `hello.cho`, device B as `adele-hello.cho`, and A's setlist came from B.

## What the user sees

1. The library holds `hello.cho`, whose header says `{artist: Adele}` / `{title: Hello}`.
2. A setlist (`gig.setlist.json`) names **both** `adele-hello.cho` (whose file this device does not have — it is
   drawn as a missing song) and `hello.cho`.
3. The song's menu offers **Update file name**. Taking it moves the file to `adele-hello.cho`, and the setlist now
   names `adele-hello.cho` twice.
4. The Setlists screen throws `IllegalArgumentException: Key "gig.setlist.json|adele-hello.cho" was already used. If
   you are using LazyColumn/Row please make sure you provide a unique key for each item.` Opening a song from that
   setlist throws the same from the song details pager. On Android the app is taken down; on the desktop and the web
   the screen dies under the chrome.

A second, independent route to the same crash: the details screen's own back stack entry. Renaming a song that a
`SongDetails` destination names *twice* — once under the old name, once under the new one, which is what an entry
opened on that setlist holds — rewrites the first into the second and leaves the pager with two pages keyed alike.

## Cause

Three things line up.

**1. The new name only avoids files on disk.**
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt:100-110`:

```kotlin
    override suspend fun renameSong(song: Song): Song? {
        val extension = song.fileName.knownExtension()
        val desired = songFileName(title = song.title, artist = song.artist, extension = extension)
        if (song.fileName.isNamed(desired)) return null
        val text = fileStorage.readText(StorageDirectory.SONGS, song.fileName) ?: return null
        val fileName = fileStorage.uniqueName(StorageDirectory.SONGS, desired, currentName = song.fileName)
```

`uniqueName` asks the storage, so a name that only a *setlist entry* holds — its file missing — is free.

**2. The rename maps the entries without merging.**
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/RenameSongFileUseCaseImpl.kt:50-62`:

```kotlin
        setlistRepository.loadSetlistsIfNeeded().orEmpty()
            .filter { setlist -> setlist.entries.any { it.songFileName == song.fileName } }
            .forEach { setlist ->
                attempt(failures) {
                    setlistRepository.updateSetlist(setlist.fileName) { latest ->
                        latest.copy(
                            entries = latest.entries.map { entry ->
                                if (entry.songFileName == song.fileName) entry.copy(songFileName = renamed) else entry
                            },
                        )
                    }
                }
            }
```

A setlist holding `[adele-hello.cho, hello.cho]` becomes `[adele-hello.cho, adele-hello.cho]`.

**3. The file is written without the duplicate, but the cache keeps it.** The two mappers already state the rule
(`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SetlistMappers.kt:24-29` on the way in,
`:39-42` on the way out):

```kotlin
    // Written the way it is read, so that a file never carries a duplicate whatever built the setlist in memory.
    songs = entries.distinctBy { it.songFileName }.map {
```

But the *model handed back* from a save is the one that came in, duplicate and all
(`.../source/SetlistLocalSourceImpl.kt:82-89`):

```kotlin
    /** Returns [setlist] carrying the size of what was written, since that is what the cache keeps from now on. */
    override suspend fun saveSetlist(setlist: Setlist): Setlist {
        val text = SetlistDocumentFormat.encode(setlist.toDocument())
        fileStorage.writeText(directory = StorageDirectory.SETLISTS, name = setlist.fileName, text = text)
        // Every platform writes the text as UTF-8 with nothing before it, so this is the file's size without asking
        // the storage for it again.
        return setlist.copy(size = text.encodeToByteArray().size.toLong())
    }
```

and that is what the cache keeps
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SetlistRepositoryImpl.kt:113-118`):

```kotlin
    /** Callers hold [writeMutex]. Returns the setlist as it was written, carrying its file's size. */
    private suspend fun write(setlist: Setlist): Setlist {
        val saved = setlistLocalSource.saveSetlist(setlist)
        updateData { current -> current.orEmpty().filterNot { it.fileName == saved.fileName } + saved }
        return saved
    }
```

So until the next full rescan the app's setlist names the song twice, while its file does not. The screens key on
that name:

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt:356-361`:

```kotlin
                    items(
                        items = rows,
                        key = { row -> SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = row.entry.songFileName).string.orEmpty() },
```

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt:342`:

```kotlin
                key = { songs[it].fileName },
```

**The second route**, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1032-1041`:

```kotlin
                destination is CampfireDestination.SongDetails && song.fileName in destination.songFileNames -> {
                    backStack[index] = destination.copy(
                        songFileNames = destination.songFileNames.map { if (it == song.fileName) fileName else it },
                        // The page the reader is on, so that a rename leaves them looking at the song they renamed.
                        initialIndex = destination.songFileNames.indexOf(song.fileName),
                    )
                }
```

A destination opened on a setlist that named both files carries both names, and the map turns them into the same
one. `SongDetailsScreen.kt:138-141` then resolves both to the same `Song`:

```kotlin
    val songs = remember(destination, allSongs) {
        val songsByFileName = allSongs.associateBy { it.fileName }
        destination.songFileNames.mapNotNull { songsByFileName[it] }
    }
```

## The change

Invoke the **`code-style`** skill before the first edit. Both files are `commonMain` and must stay JVM-free.

### 1 — Recommended: make a save hand back the setlist its file now holds

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`, in `saveSetlist`:

```kotlin
    /**
     * Returns [setlist] as the file now holds it: carrying the size of what was written, and naming each song once.
     * A model built in memory can name one twice - a song renamed onto the name of an entry whose file this device
     * does not have, see `RenameSongFileUseCaseImpl` - while the document never can (`toDocument`), and the caller
     * caches what comes back from here. The first mention wins, its transposition with it, which is the rule a file
     * edited by hand is read by (`toModel`).
     */
    override suspend fun saveSetlist(setlist: Setlist): Setlist {
        val deduplicated = setlist.copy(entries = setlist.entries.distinctBy { it.songFileName })
        val text = SetlistDocumentFormat.encode(deduplicated.toDocument())
        fileStorage.writeText(directory = StorageDirectory.SETLISTS, name = deduplicated.fileName, text = text)
        // Every platform writes the text as UTF-8 with nothing before it, so this is the file's size without asking
        // the storage for it again.
        return deduplicated.copy(size = text.encodeToByteArray().size.toLong())
    }
```

This is the right place because it is the one function where the model and the document part ways, and its own
contract — "returns [setlist] carrying the size of what was written" — is exactly what is false today. One edit
covers every caller: `SetlistRepositoryImpl.write` (so the cache), `createSetlist`, `renameSetlist` and
`importSetlist`, all of which cache or return what `saveSetlist` gives them.

**Why not the rename transform.** `distinctBy { it.songFileName }` inside `RenameSongFileUseCaseImpl` fixes the one
caller that can build the duplicate today and leaves the invariant unstated for the next one. (`addSongToSetlist`
guards by hand, `setSetlistSongs` and `reorderSetlist` build from the setlist itself — so the guards are already
scattered, which is the argument for having the rule once, at the write.)

**Why not `SetlistRepositoryImpl.write`.** It would leave `renameSetlist` and `importSetlist` returning a model
their file does not hold, and would put a rule the mappers already state into a second layer.

### 2 — Keep the back stack from naming the same song twice

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, in
`updateSongFileName`, replace the `SongDetails` branch with:

```kotlin
                destination is CampfireDestination.SongDetails && song.fileName in destination.songFileNames -> {
                    // A destination opened on a setlist that named both files - the old name and the one the song is
                    // moving to, whose file this device did not have - would otherwise name the same song twice, and
                    // the pager keys its pages by that name.
                    val songFileNames = destination.songFileNames.map { if (it == song.fileName) fileName else it }.distinct()
                    backStack[index] = destination.copy(
                        songFileNames = songFileNames,
                        // The page the reader is on, so that a rename leaves them looking at the song they renamed.
                        initialIndex = songFileNames.indexOf(fileName),
                    )
                }
```

`indexOf(fileName)` on the rewritten list rather than `indexOf(song.fileName)` on the old one: with a name removed
the two indices differ, and it is the new list the screen reads.

## Tests

- **`:data:source:local:implementation`, `desktopTest`** — a unit test is possible and is where this belongs: the
  module already drives the real local sources over a real `JvmFileStorage` in a temporary directory
  (`src/desktopTest/.../source/RenameTest.kt`, `UserPreferencesLocalSourceTest.kt`). Add
  `src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceTest.kt`
  (MPL header, `JvmFileStorage(Files.createTempDirectory(...).toFile())` and an `@AfterTest` that deletes it, the way
  `RenameTest` does), with:
  - `a setlist that names a song twice is saved and handed back naming it once` — save a `Setlist` whose `entries`
    are `[Entry("a.cho", transposition = 2), Entry("b.cho"), Entry("a.cho", transposition = -1)]`; assert the
    returned model's `entries.map { it.songFileName } == listOf("a.cho", "b.cho")`, that
    `entries.first().transposition == 2` (the first mention wins), and that `loadSetlist(fileName)` reads back the
    same two.
  - `a setlist renamed while it names a song twice is handed back naming it once` — the same through
    `renameSetlist`, which goes through `saveSetlist`.
- **`:domain:implementation`** — the rename use case is testable here (`commonTest` already declares its own fake
  repositories inside `useCases/GetScreenDataUseCaseImplTest.kt`; a new file declares its own). It is **not** worth a
  test for this bug once the fix is at the write: a fake `SetlistRepository` would have to mirror the local source's
  rule for the assertion to mean anything, and the test would then be testing the fake. Skip it.
- **`:presentation`** — the back stack change is view model code, which is untested by policy. Manual check only
  (below).

Run: `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`

## Verification

Desktop is enough; nothing here is platform-specific. macOS library directory:
`~/Library/Application Support/Campfire/library/`.

1. With the app closed, write `library/songs/hello.cho`:

   ```
   {artist: Adele}
   {title: Hello}

   [Am]Hello, it's [F]me
   ```

   and `library/setlists/gig.setlist.json` naming the missing file first:

   ```json
   {"title":"Gig","description":"","priority":1,"isArchived":false,"songs":[{"file":"adele-hello.cho"},{"file":"hello.cho"}]}
   ```

   (Open the app once first and copy the shape of a setlist it wrote, in case the format has moved on.)
2. `./gradlew :app:desktop:run`. The Setlists screen shows "Gig" with one missing song and "Hello".
3. Songs screen → the song's overflow menu → **Update file name**.
4. Before the fix: the Setlists screen throws `Key ... was already used`. After: the setlist shows a single "Hello"
   row, the missing row is gone, and the file `library/setlists/gig.setlist.json` names `adele-hello.cho` once.
5. The second route: open the song **from the setlist** (so the destination names both files) and take **Update file
   name** from the details header's menu. Before the fix the pager throws the same; after, the screen stays on the
   song.
6. Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`) only if you want to confirm the same on OPFS; the library
   there is in the browser's storage, so build it by importing the two files.

## Docs

- `CLAUDE.md`, the sentence "A rename reaches **sync** as a deletion and a new file, since `SyncPlanner` is keyed by
  name and knows no moves." — unchanged, but the paragraph above it, "taking it moves the file and everything that
  named it — every setlist entry, the saved transposition, the open screens (`RenameSongFileUseCaseImpl`)", is worth
  one clause: "…, a setlist that already named the file under its new name keeping the one entry it had."
- `data/source/local/implementation/CLAUDE.md`, the `model/` + `mapper/` bullet (line 102-103), currently says:

  > A setlist file naming a song twice is read as naming it once (the first mention wins) and written back that way,
  > since the screens key their rows by the song's file name.

  True of the file and false of what a save hands back, which is what the caller caches. Extend it to: "A setlist
  naming a song twice is read as naming it once (the first mention wins), written back that way, **and handed back
  that way from a save**, since the screens key their rows by the song's file name and the caller caches the model
  the save returns rather than reading the file again."
- `presentation/CLAUDE.md`, line 46, in the `updateSongFileName` sentence ("what is left here are the two places the
  old name lives in memory — the text read from the file, and the back stack entries opened on it, which are
  rewritten rather than popped…"): add "…, and a rewritten entry names each song once, since a destination opened on
  a setlist that held both names would otherwise key two of the pager's pages alike".

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceImpl.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SetlistLocalSourceTest.kt` (new)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on

Nothing. **33** edits the same `updateSongFileName` function (it wraps the body in a `try`/`finally` and adds a state
around it); land 32 first and 33 on top of it, or merge the two edits by hand.
