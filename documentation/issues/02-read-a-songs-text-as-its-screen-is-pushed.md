# Ask for a song's text as its screen is pushed, not after the screen's first frame

**Kind:** performance · **Severity:** medium · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/SongTextStore.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPagerEffects.kt`
(KDoc of `LoadSongTexts` only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/SongTextStoreTest.kt`
**Challenged:** amended — the read ahead centres on the page the screen is on (`navigator.currentSongFileName`), not
`initialIndex`, since the listener also fires when a paged pager comes back on top (the editor or a tool popped, a web
Back); the honest trade-off for a cold JVM and for a setlist open (two in-place builds in frame 0) is now stated, and the
tests follow the new signature.

## Problem

A song's text is only asked for from inside the screen, once it has composed:

```kotlin
// SongDetailsPagerEffects.kt, LoadSongTexts
val currentFileName = songs.getOrNull(currentPage)?.fileName
LaunchedEffect(currentFileName) { currentFileName?.let(viewModel.songTextStore::loadSongContent) }
LaunchedEffect(currentPage, songs) {
    listOfNotNull(songs.getOrNull(currentPage - 1), songs.getOrNull(currentPage + 1))
        .filter { it.fileName !in songTexts && it.fileName !in failedSongFileNames }
        .forEach { viewModel.songTextStore.loadSongContent(it.fileName) }
}
```

`SongTextStore` drops every text no back stack screen names once a transition ends (`pruneSongTexts`), so a song opened
from a list never has its text held when its screen is pushed. The sequence on every open is therefore:

1. Frame 0: the back stack change composes `SongDetailsScreen`; the page gets `text = null` and composes the loading
   branch of `SongDetailsPage`'s `AnimatedContent(targetState = text, contentKey = { it != null })`; the navigation
   transition starts its clock on the next frame.
2. The effects launch; the text comes back one or more frames later, **while the screen is sliding in**.
3. In that frame the page swaps to its lyrics: `rememberSongLyricsModel` builds the first model in place on the main
   thread (`remember { mutableStateOf(if (buildsInPlace) inputs to prepare(inputs, false) else null) }` in
   `songView/RememberSongLyricsModel.kt`), the whole song is measured and the grid searched, and the lyrics cross-fade
   in over the loading box — all inside the running transition.

Measured on the desktop (offscreen probe, demo library, 360x640dp): ~40–50 ms of main-thread work per open, warm, and
~360 ms on the first open of a JVM session, most of it in that frame. A long frame in the middle of a slide is a
visible hitch; the same work done in frame 0, before the transition's clock starts, delays the slide's start a little
but lets it run smoothly, and skips the loading-to-lyrics cross-fade (two contents composed for its duration).

The read itself is usually fast enough to land before frame 0: `SongContentRepositoryImpl.loadSongContent` returns a
cached text from inside an uncontended `mutex.withLock` without suspending, and `InferredSongKeys.inferFor` returns at
once for a text it has seen (`if (changed.isEmpty()) return`), so on `viewModelScope` (`Dispatchers.Main.immediate`) a
song opened before in the session gets its text into `songTexts` synchronously, inside the click. A song never read
this session still needs a file read and an inference on `Dispatchers.Default`, a few milliseconds that usually beat
the next vsync but are not guaranteed to.

Checked against `ui/songView/model/CLAUDE.md` ("Built off the main thread except the first page on screen and those
before it (`buildsModelInPlace`), so a page never opens empty"): this keeps that rule and only moves the in-place build
earlier; nothing waits, nothing is gated.

## Fix

1. `SongTextStore`: add

   ```kotlin
   /**
    * Starts reading the texts a song details screen just pushed will show first - its first page and the pages on
    * either side, which the pager composes too - so that they are usually here for its first frame and the first
    * page's in-place model is built before the navigation transition starts moving, not in the middle of it.
    */
   fun readAhead(destination: CampfireDestination.SongDetails, currentFileName: String?) {
       if (destination.importPreview != null) return
       val index = destination.songFileNames.indexOf(currentFileName).takeIf { it >= 0 } ?: destination.initialIndex
       listOfNotNull(
           destination.songFileNames.getOrNull(index),
           destination.songFileNames.getOrNull(index - 1),
           destination.songFileNames.getOrNull(index + 1),
       ).filter { it !in _songTexts.value }.forEach(::loadSongContent)
   }
   ```

   The current page first, so its read is started first. An import preview's pages name no file
   (`importPreviewFileName`), hence the early return. A name the library lacks is marked failed by `loadSongContent` as
   today; the screen's own effect clears that again before it retries the current page.
2. `CampfireViewModel`: register one more back stack listener **after** `songTextStore` is declared (it is declared after
   `navigator`, so not inside the `navigator`'s `apply { }`; the later `navigator.addOnBackStackChanged { … }` at the
   `firstRunController` line shows the pattern):

   ```kotlin
   navigator.addOnBackStackChanged { previousTop, stack ->
       val top = stack.lastOrNull()
       if (top is CampfireDestination.SongDetails && top != previousTop) {
           songTextStore.readAhead(top, currentFileName = navigator.currentSongFileName(top))
       }
   }
   ```

   `currentSongFileName` is `initialIndex`'s song for a screen just pushed (no page has settled yet) and the page the
   user left for a pager that comes back on top — after an editor, a tool, or the web's Back — whose `initialIndex` is
   where it was opened, possibly far from where it is now. Texts already held are skipped, so a return costs nothing.

   Every way a song screen is pushed passes `updateBackStack` (`openSong`, `openSongInSetlist`, `openImportedSong`, the
   web's addresses), so the listener covers them all; `followSongRename` and `followNewSongCreated` bypass it, correctly
   (nothing was navigated). The pushed destination is on the back stack before `pruneSongTexts` can run, so the text is
   never pruned under it.
3. Keep `LoadSongTexts` as it is (it handles paging, retries, and a pager created before the library was read); amend
   its KDoc with one sentence that the screen's first texts were already asked for as it was pushed
   (`SongTextStore.readAhead`). A second `loadSongContent` of a held text is a cache hit that publishes an equal map,
   which `MutableStateFlow` does not emit.
   What it moves, stated plainly so nobody expects more: the work is the same, it lands in frame 0 instead of mid-slide.
   Warm, that is ~40–50 ms before the slide starts instead of a hitch inside it. Opened from a setlist, frame 0 builds two
   in-place models (the page and the one before it, `buildsModelInPlace`'s `targetPage - 1`), work that was done
   mid-slide before. On the first open of a cold JVM the song was never read this session, so its text needs a file
   read on `Dispatchers.Default` and may still miss frame 0; when it does not, the whole cold cost (~360 ms) delays the
   slide's start rather than stalling it. Shortening that cost is plan 03's, not this plan's. The threading is the click's
   own: the listener runs inside `updateBackStack` on the main thread, and `loadSongContent`'s `launch` on
   `Dispatchers.Main.immediate` runs at once (or, from inside another main-thread coroutine, as soon as that one
   suspends), still before the frame. On the web (one thread, OPFS reads) the text lands later as today.
4. `screens/songDetails/CLAUDE.md`, under "The songs it pages through", one bullet: "Its first page's text and its
   neighbours' are asked for as the screen is pushed (`SongTextStore.readAhead`), so a song read before opens with its
   lyrics in the first frame and its in-place model is built before the transition moves, not during it."

## Tests

In `SongTextStoreTest` (with `FakeSongFiles`, whose reads do not suspend):

- `readAhead` of `SongDetails(songFileNames = listOf("a", "b", "c", "d"), setlistFileName = "s", initialIndex = 2)`
  with `currentFileName = "c"` holds the texts of `b`, `c`, and `d`, and not `a`.
- The same destination with `currentFileName = "a"` (paged back since it was opened) holds `a` and `b` only; with
  `currentFileName = null` or a name it does not list, it falls back to `initialIndex`.
- `readAhead` of a destination with `importPreview` set reads nothing (`store.songTexts.value` stays empty).
- A held text is not read again (count reads with a small wrapper, or check `FakeSongFiles` was not asked; add a read
  counter to `FakeSongFiles` if needed).

Then `./gradlew :presentation:desktopTest` and `:tools:screenshots:test` (the UI tests open songs; their goldens must not
change, since only when the text arrives moves). Compile `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
