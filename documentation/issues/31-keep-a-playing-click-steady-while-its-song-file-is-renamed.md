# Keep a playing click on its tempo and in its bar while Update file name moves its song

**Challenged:** amended — the song-lookup fallback alone leaves two glitches: the overrides (`tempos`, setlist entry tempos) move to the new name inside `RenameSongFileUseCase` before the view model knows that name, and `_songsBeingRenamed` is cleared (a StateFlow, at once) before `snapshotFlow { metronomeContext }` sees the rewritten back stack (on the next apply notification), and the plan's `distinctUntilChanged` would then drop a change held back meanwhile; the collector now holds updates for the click's song while it is being renamed and until the context reaches the new name, tracking the applied pattern itself.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (the pattern collector in `init`, `currentMetronomePattern`, `updateSongFileName`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeContext.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeContextTest.kt`,
`presentation/CLAUDE.md` (the sentence about `songsBeingRenamed`)

Lane D: apply after 30 (same file, different function) and before 32.

## Problem

Update file name is offered from the song details menu, which a playing click does not stop. During the rename the
library no longer holds the old name for a few writes (`songsBeingRenamed`'s whole reason to exist, see the comment
above the dialog collector: "never for a song this app is renaming, which is missing from the library for a few writes
on purpose"). The screen resolves its pages through `songsBeingRenamed`, but the metronome does not:

```kotlin
combine(snapshotFlow { metronomeContext }, tempos, metronomeSettings, songsByFileName) { context, tempos, settings, songs ->
    context to metronomePatternOf(context = context, settings = settings, songOf = songs::get, tempos = tempos)
}.distinctUntilChanged().collect { (context, pattern) ->
    ...
    if (last != null) metronome.update(pattern, restartBar = context != last)
}
```

1. While the old name is missing, `songOf(old)` is null, so `metronomePatternOf` falls back to the song-less tempo
   (`MetronomePattern.DEFAULT_BPM` unless an override is stored under the name) and `TimeSignature.COMMON_TIME`: a song
   at 92 in 6/8 briefly clicks at 120 in 4/4 and the beat row redraws.
2. When the screen then reports the new name (`SongDetailsScreen`'s `snapshotFlow { latestSongs.getOrNull(pagerState.targetPage)?.fileName }`
   → `onSongDetailsPageChanged`), `metronomeContext` changes from `Song(old)` to `Song(new)` and `restartBar = context != last`
   restarts the bar, as if the user had paged to another song.

`metronomeContext` reads `songDetailsTargetSongs` first
(`songDetailsTargetSongs[destination.id] ?: currentSongFileName(destination)`), and `updateSongFileName` only rewrites
`songDetailsCurrentSongs`:

```kotlin
songDetailsCurrentSongs.entries.filter { it.value == song.fileName }.forEach { songDetailsCurrentSongs[it.key] = fileName }
```

so between the back stack rewrite and the screen's next report the context still names the old file. `currentMetronomePattern`
(`songOf = songsByFileName.value::get`), used when a click is started, has the same blind spot.

## Fix

Two more windows than the ones above make a fallback for the song alone not enough, and both are in the order the rename
already runs in:

- `RenameSongFileUseCaseImpl` moves the file and then `followSongReferences` moves the library's `tempos` / `capos` and
  every setlist entry to the new name — all before `renameSongFile` returns the new name to the view model. Meanwhile the
  context still names the old file, so `tempos[old]` is gone and a click at an override (stepped to 100 over a file's
  120) plays the file's 120 until the context follows.
- After it returns, `updateSongFileName` rewrites the back stack and the two maps (snapshot state, which
  `snapshotFlow { metronomeContext }` only sees once the global snapshot's apply notification has been dispatched) and
  then, in `finally`, clears `_songsBeingRenamed` (a `StateFlow`, whose emission reaches the `combine` at once). The
  collector can therefore see the old context with an empty renaming map and a library without the old name: the
  song-less pattern again, for a frame.

So the click is held, not re-resolved, while its own song is being renamed:

1. `currentMetronomePattern` (a click *started* during the rename): `songOf = { songsByFileName.value[it] ?: _songsBeingRenamed.value[it] }`.
2. In `updateSongFileName`, rewrite `songDetailsTargetSongs` exactly like `songDetailsCurrentSongs` (same line, for the
   other map), so the context follows the file in the same snapshot as the back stack.
3. Keep a private `metronomeRenames = mutableMapOf<String, String>()` (old → new; read and written on the main thread
   only, as the collector and `launchLibraryChange` both run on `viewModelScope`). In `updateSongFileName`, once
   `renameSongFile` has returned a rename and before the back stack and the maps are rewritten, record
   `song.fileName to fileName` **only if** `(metronomeContext as? MetronomeContext.Song)?.songFileName == song.fileName`
   — then the top of the back stack is the screen being rewritten, so the context is certain to move off the old name
   and the entry is certain to be consumed (a rename that returns null records nothing).
4. Rewrite the collector:

   ```kotlin
   var previous: MetronomeContext? = null
   var applied: MetronomePattern? = null
   combine(snapshotFlow { metronomeContext }, tempos, metronomeSettings, songsByFileName, _songsBeingRenamed) { context, tempos, settings, songs, renaming ->
       Triple(context, metronomePatternOf(context = context, settings = settings, songOf = { songs[it] ?: renaming[it] }, tempos = tempos), renaming.keys)
   }.collect { (context, pattern, renaming) ->
       // Held while the click's own song is being renamed: its overrides move to the new name before the screen does,
       // and the screen follows a moment after the rename is over. Whatever changed meanwhile is applied once it has.
       if (context is MetronomeContext.Song && (context.songFileName in renaming || context.songFileName in metronomeRenames)) return@collect
       val last = previous
       previous = context
       if (last != null) {
           val isMoved = isMetronomeContextMoved(last = last, context = context, renames = metronomeRenames)
           if (last is MetronomeContext.Song && context != last) metronomeRenames.remove(last.songFileName)
           if (context != last || pattern != applied) metronome.update(pattern, restartBar = isMoved)
       }
       applied = pattern
   }
   ```

   The `distinctUntilChanged()` goes: a pattern that changed while held (a setting, a synced `{tempo}`) is emitted
   again unchanged once the hold ends, and would be dropped as a repeat although it was never applied — `applied` is the
   comparison instead. The first value is still only remembered.
5. Add the pure helper to `metronome/MetronomeContext.kt`:

   ```kotlin
   /** Whether [context] is another song than [last] rather than the same one under the name a rename gave it. */
   internal fun isMetronomeContextMoved(last: MetronomeContext, context: MetronomeContext, renames: Map<String, String>) =
       context != last && !(last is MetronomeContext.Song && context is MetronomeContext.Song &&
           last.setlistFileName == context.setlistFileName && renames[last.songFileName] == context.songFileName)
   ```

   (The setlist file name does not change in a song rename, so comparing it is right.)
6. Extend the `songsBeingRenamed` sentence in `presentation/CLAUDE.md` with: the metronome resolves through it too and
   holds a playing click's changes while its own song is being renamed, so the click follows the rename on its tempo
   and without a restarted bar.

Plan 30 is unaffected: the rename rewrites the back stack in place and never goes through `updateBackStack`.

## Tests

`MetronomeContextTest.kt`: `isMetronomeContextMoved` is false for `Song(a, s)` → `Song(b, s)` with `renames = {a: b}`,
true without the rename, true for a different setlist, true for `Standalone` → `Song`, false for equal contexts.

## Manual check

Open a song whose header no longer matches its file name (rename the `.cho` in the library folder on desktop, or edit
its `{title}` so the menu offers **Update file name**), give it `{tempo: 92}` and `{time: 6/8}`, open its panel and start
the click, then take Update file name: the click keeps 92 in 6/8 and the accent stays on the bar's first beat, with no
flicker in the beat row. Repeat with the tempo stepped to 100 on the page first (a library override): the click stays at
100 through the rename, and the stepper still reads 100 afterwards.
