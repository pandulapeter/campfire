# 33 — Renaming a song from its details screen can close the screen, or leave the setlist on the next song

**Severity:** wrong behaviour, intermittent (all platforms) · **Area:** `:presentation`
(`CampfireViewModel`, `screens/songDetails/SongDetailsScreen.kt`)

**How likely:** every **Update file name** taken from the song details screen is a race. It is lost more often the
more there is to write after the file has moved — a song in two or three setlists, or one with a saved transposition
— and on the platforms where a write is slowest (the web's OPFS, a phone's storage under load).

## What the user sees

Reading a song, its header menu offers **Update file name**. Taking it:

- on a song opened from the library, the details screen sometimes **closes itself** and drops the reader back on the
  Songs list — where the song is, correctly renamed;
- on a song opened from a setlist, the pager sometimes **turns to the next song** of the setlist and stays there.

Nothing is lost either way; the file is renamed correctly. It just looks as though the app lost its place, and it
does so unpredictably: the same gesture on the same song works the next time.

## Cause

The screen resolves its songs from the library, and the library drops the old name before the back stack is
rewritten.

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt:138-149`:

```kotlin
    val songs = remember(destination, allSongs) {
        val songsByFileName = allSongs.associateBy { it.fileName }
        destination.songFileNames.mapNotNull { songsByFileName[it] }
    }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    LaunchedEffect(songs.isEmpty(), isLoading) {
        // Every song this screen was opened on is gone from the library, and the library has been read: there is
        // nothing left to show, so the screen goes the way it would have if the song had been deleted from here. Only
        // while it is still the screen on top, since one that has just been popped goes on being composed for as long
        // as its exit transition runs, and going back from there would close the screen underneath it too.
        if (songs.isEmpty() && !isLoading && viewModel.backStack.lastOrNull() == destination) onBack()
    }
```

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1025-1049`:

```kotlin
    fun updateSongFileName(song: Song) = launchLibraryChange {
        val rename = renameSongFile(song) ?: return@launchLibraryChange
        val fileName = rename.fileName
        _songTexts.update { texts -> texts[song.fileName]?.let { texts - song.fileName + (fileName to it) } ?: texts }
        …
        backStack.forEachIndexed { index, destination -> … }
```

What happens between those two statements:

1. `renameSongFile(song)` calls `songRepository.renameSong(song)`, which moves the file and then
   `updateData { … filterNot { it.fileName == song.fileName } + renamed }`
   (`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt:90-97`).
   The library no longer holds the old name.
2. Only then does `RenameSongFileUseCaseImpl` follow the references — every setlist that names the song, and the
   saved transposition — each of which is a file write, and each of which emits again
   (`domain/implementation/.../useCases/RenameSongFileUseCaseImpl.kt:42-46`).
3. The use case returns, and the view model rewrites the back stack.

The emission from step 1 reaches the screen through `GetScreenDataUseCaseImpl`, whose pipeline ends with
`.flowOn(Dispatchers.Default).distinctUntilChanged()`
(`domain/implementation/.../useCases/GetScreenDataUseCaseImpl.kt:103`) and is then `stateIn`ed on the view model's
scope, so it crosses two dispatchers on its way to the composition. Any recomposition in the window between step 1
and step 3 sees `allSongs` without the old name while `destination.songFileNames` still holds it. `isLoading` is
false throughout (the cache was updated, not reloaded, so the state is `DataState.Idle`), so:

- a destination naming one song resolves to an empty list and `onBack()` fires;
- a destination naming a setlist loses one page, `pagerState`'s page count drops, and the pager settles on whatever
  is now at that index — which `onSongDetailsPageSettled` then records as where the reader is
  (`SongDetailsScreen.kt:166-171`), so the jump sticks even after the back stack catches up.

## The change

Invoke the **`code-style`** skill before the first edit. `commonMain` stays JVM-free.

**Chosen answer: a map of the songs whose files are being renamed, which the screen's resolution falls back on.**
The map is written *before* the rename starts and cleared *after* the back stack has been rewritten, so it always
covers the whole window whatever order the emissions arrive in. It is state, not a delay: no debounce, no
"wait for a settled emission", no animation added or removed. The value is the `Song` the rename was asked for,
which is exactly what the screen was already drawing, so the page does not even redraw.

The alternative the finding offers — requiring the empty state to hold for a settled emission — is rejected: "settled"
is a timing notion in disguise (there is no emission that says "this one is the last"), and it would fix only the
screen closing itself, leaving the setlist pager still dropping a page and jumping.

### 1 — `CampfireViewModel`

Next to `_songTexts` (line 407-408), add:

```kotlin
    /**
     * The songs whose files are being renamed right now, under the names they had when it started.
     *
     * A rename moves the file - and with it the song in the library - before the screens that were opened on the old
     * name have been rewritten, since every setlist naming the song and its saved transposition are written in
     * between (see [updateSongFileName]). For those few writes the details screen's destination names a song the
     * library no longer holds, and a screen that resolved that as "gone" closed itself, or dropped a page and left a
     * setlist on the next song. Resolved through rather than waited out: the song is here from before the file moves
     * until after the back stack names it by its new name, so there is no window to miss and no delay to tune.
     */
    private val _songsBeingRenamed = MutableStateFlow(emptyMap<String, Song>())
    val songsBeingRenamed: StateFlow<Map<String, Song>> = _songsBeingRenamed.asStateFlow()
```

and in `updateSongFileName`:

```kotlin
    fun updateSongFileName(song: Song) = launchLibraryChange {
        // Before the file moves, so that the moment the library drops the old name is already covered.
        _songsBeingRenamed.update { it + (song.fileName to song) }
        try {
            val rename = renameSongFile(song) ?: return@launchLibraryChange
            val fileName = rename.fileName
            _songTexts.update { texts -> texts[song.fileName]?.let { texts - song.fileName + (fileName to it) } ?: texts }
            // … unchanged through persistBackStack() …
            if (!rename.haveReferencesFollowed) sendMessage(Message.SongFileRenamedPartly)
        } finally {
            // Cleared once the screens name the new file - and on the paths that never got that far: a rename that
            // found nothing to do, one that threw, and a view model going away mid-rename.
            _songsBeingRenamed.update { it - song.fileName }
        }
    }
```

`return@launchLibraryChange` inside the `try` still runs the `finally`, as does the cancellation that takes the view
model down.

### 2 — `SongDetailsScreen`

```kotlin
    val songsBeingRenamed by viewModel.songsBeingRenamed.collectAsStateWithLifecycle()
    val songs = remember(destination, allSongs, songsBeingRenamed) {
        val songsByFileName = allSongs.associateBy { it.fileName }
        // A song whose file is being renamed is still this screen's song. The library drops the old name as the file
        // moves and the back stack is rewritten a few writes later, and in between the destination names a song the
        // library does not hold: resolving it to the song as it was keeps the pages - and their count - exactly
        // where they are, instead of this screen closing itself or a setlist settling on the next song.
        destination.songFileNames.mapNotNull { songsByFileName[it] ?: songsBeingRenamed[it] }
    }
```

Nothing else on the screen needs to change. `songs` is never empty for the duration, so the `LaunchedEffect` that
pops the screen keeps its meaning — "every song this screen was opened on is gone" — and now only ever says it of a
deletion. The pager's `key = { songs[it].fileName }` goes on returning the old name for that moment and the new one
after, which is a key change on one page, not a duplicate (see **32** for the duplicate).

### What is deliberately left alone

`setlistsWithSongs` draws the renamed song as a missing file for the same window, so a setlist row can blink. It is
one row rather than the whole screen, it is over as soon as the setlist file is written, and wiring the same map into
`setlistsWithSongs` would put a song into a setlist that does not name it yet. Left as it is.

## Tests

- **No unit test is possible.** This is view model and Compose state; `:presentation` has no test source set and the
  UI is untested by policy. The use case underneath (`RenameSongFileUseCaseImpl`) is unchanged by this plan.
- The change must not break the existing suites; run the root command anyway:
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`
- Compile check for every target: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

Manual, and the window must be widened to see the bug reliably before the fix.

1. Desktop: `./gradlew :app:desktop:run`. macOS library directory:
   `~/Library/Application Support/Campfire/library/`.
2. With the app closed, write `library/songs/old name.cho`:

   ```
   {artist: Adele}
   {title: Hello}

   [Am]Hello, it's [F]me
   ```

   so that its name and its header have drifted apart and the menu offers **Update file name**.
3. Start the app, put the song into three or four setlists, and transpose it once from the library (so the
   preferences carry a transposition for it). That is four or five file writes between the move and the back stack
   rewrite.
4. Open the song from the Songs list. Header menu → **Update file name**. Before the fix the details screen closes
   itself most times; after, it stays open, the bar keeps naming the song, and the transposition control keeps its
   value.
5. Open the song from one of the setlists, page to it, take the same action. Before the fix the pager turns to the
   next song; after, it stays on the song, and going back and forward again comes back to it.
6. Web: `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, import the same song and build the same setlists. OPFS is
   slower, so the race is easier to lose there — the clearest place to check before the fix.
7. Android and iOS need no separate check: the code is `commonMain` and the race is not platform-specific. If a
   device is at hand, repeat step 4 once on it.

## Docs

- `presentation/CLAUDE.md`, line 46, the `updateSongFileName` sentence:

  > `updateSongFileName` is the one intent that changes a song's identity: the use case under it follows the
  > references on disk, and what is left here are the two places the old name lives in memory — the text read from
  > the file, and the back stack entries opened on it, which are rewritten rather than popped so that a song renamed
  > from the details screen is still the song being read.

  Add after it: "The file moves before those references are followed, so for the few writes in between the library no
  longer holds the name the details screen was opened on: `songsBeingRenamed` carries the song under its old name
  across that window and the screen resolves through it, which is why a rename neither closes the screen nor turns
  the setlist's pager to the next song. It is a state rather than a wait — there is no emission that says it is the
  last one."
- `presentation/CLAUDE.md` has no bullet of its own for `screens/songDetails/SongDetailsScreen.kt` (only `SongLyrics`,
  `SongDisplayControls` / `FontScaleGestures` and `SongKeyboardShortcuts`, lines 73-76), and the screen closing itself
  on an empty resolution is stated nowhere — so there is no sentence to correct there. The `updateSongFileName`
  sentence above is the one that has to change; do not open a new bullet for this.
- No user-facing behaviour described in `documentation/features.md` becomes untrue.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`
- `presentation/CLAUDE.md`

## Depends on

**32** edits the same `updateSongFileName` function (the `SongDetails` branch of the back stack walk). Land 32 first
and this on top of it; the two edits do not overlap line for line but they are within a dozen lines of each other.
