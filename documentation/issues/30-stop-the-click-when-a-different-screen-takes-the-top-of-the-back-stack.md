# Stop the click whenever a different screen comes to the top of the back stack, not only when the top is neither a song nor the Metronome tab

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (desktop and Android "Open with" / drag and drop most likely)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (`updateBackStack`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeContext.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeContextTest.kt`,
`presentation/CLAUDE.md` (only if it describes the stop rule; it currently does not), root `CLAUDE.md` (Metronome section, "The click belongs to the screen it is played from" bullet — optional wording addition)

Lane D, first of 30–38. Apply before 31 and 32, which edit other functions of `CampfireViewModel.kt`.

## Problem

Root `CLAUDE.md`: "a click never outlives the screen it was started on … so there is never a click playing with nothing
on screen to stop it with." The rule is implemented in `CampfireViewModel.updateBackStack` by looking at the new top
alone:

```kotlin
if (backStack.lastOrNull().let { it !is CampfireDestination.SongDetails && it != CampfireDestination.Metronome }) {
    metronome.stop()
}
```

So any back stack change that lands on *a* song details screen or on the Metronome tab keeps the click, even when it is
a different screen from the one the click was started on. `openImportedSong` does exactly that:

```kotlin
updateBackStack {
    if (current is CampfireDestination.SongDetails) removeAt(lastIndex)
    add(CampfireDestination.SongDetails(songFileNames = songFileNames, setlistFileName = null, initialIndex = 0))
}
```

It is reached from a file opened with the app or dropped on the window (single-song import, `shouldOpenSong` in the
import flow, ~3104) and from the import snackbar's Open (`CampfireApp.kt` ~877, `viewModel.openImportedSong(songToOpen)`),
none of which require the user to leave the Metronome tab first. Then:

- **Over the Metronome tab:** the tab's click keeps going; `metronomeContext` (`metronomeContextOf(backStack)`) turns
  from `Standalone` into `Song(...)`, and the pattern collector (`metronome.update(pattern, restartBar = context != last)`)
  retargets the tab's click to the imported song's tempo. Back returns to the tab with the click still going, retargeted
  back. The user never started a click on that song.
- **Over an open song (replaced):** the click started on song A moves to the imported song B, whose screen it was never
  started from.

The navigation chrome is not composed over a song details screen (`isTopLevelScreenCovered` in `CampfireApp.kt`), so
selecting the Metronome tab from a song is not a second trigger today; the fix below covers it anyway.

## Fix

Remember the top before the update and stop the click whenever the top screen's identity changes, as well as whenever
the new top holds no metronome. Identity is `CampfireDestination.contentKey`: a song details screen keeps its `id`
(and so its content key) across `copy()` — which is how `updateSongFileName` rewrites it, and that rewrite does not go
through `updateBackStack` anyway — and paging inside the pager never touches the back stack, so neither stops a click.

1. In `metronome/MetronomeContext.kt` add a pure helper next to `metronomeContextOf`:

   ```kotlin
   /**
    * Whether a back stack change leaves the screen a click is played from: the new top holds no metronome, or it is
    * another screen than the one that was on top - a song opened over the Metronome tab, or over another song.
    */
   internal fun isMetronomeScreenLeft(previousTop: CampfireDestination?, top: CampfireDestination?) =
       (top !is CampfireDestination.SongDetails && top != CampfireDestination.Metronome) || top?.contentKey != previousTop?.contentKey
   ```

2. In `updateBackStack`, capture `val previousTop = backStack.lastOrNull()` before `backStack.update()` and replace
   the condition above with `if (isMetronomeScreenLeft(previousTop, backStack.lastOrNull())) metronome.stop()`. Update
   the comment above it to say that a different screen arriving on top (a song opened over the tab or over another
   song) stops it too.

Check while applying: every `updateBackStack` caller (`restoreNavigationState`, `selectTopLevelDestination`,
`openSongDetails`, `openImportedSong`, `popBackStack`, the two editor pushes, `ImportReport` push and
`closeImportReport`) — none should keep a click across a change of top. `restoreNavigationState` with an unchanged top
keeps it, which is right.

## Tests

In `MetronomeContextTest.kt` (same style as the existing tests there):
- same `SongDetails` (same `id`) before and after → false;
- the same `SongDetails` after `copy(songFileNames = …)` → false;
- `Metronome` → `Metronome` → false;
- `Metronome` → a `SongDetails` → true; `SongDetails(id = a)` → `SongDetails(id = b)` → true;
- `SongDetails` → `SongEditor` / `Songs` → true; `SongDetails` → `Metronome` → true.

## Manual check

Desktop: start the click on the Metronome tab, drop a single `.cho` file onto the window (or open one with the app) →
the click stops as the song opens; Back returns to the tab with nothing playing. Start a click on a song, open another
`.cho` with the app → the click stops. Paging a setlist's songs with a click playing still moves the click (from beat
one), and Update file name on a playing song keeps it going.
