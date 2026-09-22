# 43 · Browser Forward after paging a setlist loses the setlist, and pressing Forward again repeats it

**Severity:** wrong behaviour, minor (web only. Unlikely: it takes a song opened from a setlist, its editor opened and
closed, the pager moved to another song, and then the browser's Forward) · **Area:** `:presentation` `wasmJsMain`
(`ui/navigation/BrowserHistory.kt`, `ui/navigation/BrowserRoutes.kt`)

## Symptom
1. Songs → Setlists → tap song `a` of setlist `s`. The history is `""`, `setlists`, `setlist/s/a`.
2. Open the editor on `a` from the song's menu (`song/a/edit`), then close it (Close or the browser's Back).
3. Swipe the pager to song `b`. The current entry becomes `setlist/s/b`.
4. Press the browser's Forward.

The app now shows the editor of `a` over a new details screen of `a` alone, straight on top of the songs. The
Setlists screen and the setlist's pager are gone, so Back leads to song `a` and then to the song list. The browser is
also moved back an entry, and the entry above is still there, so Forward stays enabled. Every further Forward does
the same again, each time with a new details screen under the editor.

## Cause
Rewriting the current entry forgets the recorded state of every entry above it
(`BrowserHistory.kt:216-221`):

```kotlin
if (entries[current].path != paths[current]) {
    replaceHistoryEntry(depth = current, path = paths[current])
    ...
    for (depth in current + 1..entries.lastIndex) entries[depth] = entries[depth].copy(state = null)
}
```

So `goForward` (`BrowserHistory.kt:177-190`) falls back to the address:

```kotlin
val state = entry?.state?.let { BrowserRoutes.validate(state = it, songs = songs, setlists = setlists) }
    ?: (path ?: entry?.path)?.let { BrowserRoutes.resolve(path = it, songs = songs, setlists = setlists, current = viewModel.navigationState) }
```

`BrowserRoutes.resolve` (`BrowserRoutes.kt:72-136`) always builds a whole stack under a fresh `[Songs]`, as deep as
the address says: `song/a/edit` is `[Songs, SongDetails(a), SongEditor(a)]`, three history entries, while the entry
being entered is the fourth (depth 3). `restoreNavigationState` (`CampfireViewModel.kt:847-861`) accepts it, since
nothing is unsaved, and `SongDetails` gets a new random `id` (`CampfireDestination.kt:80`), so the back stack is
replaced every time. `synchronize` (`BrowserHistory.kt:203-234`) then finds `target (2) < current (3)`, traverses the
browser back one entry and rewrites depth 2 to `song/a/edit` (the editor is now the third entry). The depth-3 entry
keeps its address and a null state (line 220 again), which is why the next Forward replays all of it.

An address resolved for Forward is only a faithful stand-in for the entry when it makes exactly as many history
entries as the entry is deep. The entries it is meant for, the ones written "in passing" by a multi-level push (a deep
address opened at launch: `""`, `setlists` under `setlist/s/a`), always do.

## Fix
Accept an address-resolved state only when it is exactly as deep as the entry; otherwise refuse, which already makes
`synchronize` take the browser back to where the app stayed.

1. `BrowserRoutes.kt`, after `paths(viewModel)` (after `:65`), add:

   ```kotlin
   /**
    * How many history entries [state] makes, which is the size [paths] has once the app is there: one per screen of
    * the back stack, and one more for each list screen whose search is open.
    */
   fun entryCount(state: NavigationState) = state.backStack.size + state.backStack.count { destination ->
       destination == CampfireDestination.Songs && state.isSongsSearchOpen || destination == CampfireDestination.Setlists && state.isSetlistsSearchOpen
   }
   ```

2. `BrowserHistory.kt`, `goForward` (`:181-182`), replace the `state` declaration with:

   ```kotlin
   val state = entry?.state?.let { BrowserRoutes.validate(state = it, songs = songs, setlists = setlists) }
       ?: (path ?: entry?.path)
           ?.let { BrowserRoutes.resolve(path = it, songs = songs, setlists = setlists, current = viewModel.navigationState) }
           // An address stands for a whole stack built up from the songs, which is only this entry when it is as deep as
           // the entry is. One that is not - the editor of a song whose setlist page underneath was paged on, which
           // forgot what the entry above it was when it was rewritten - would take away the screens under it and leave
           // the browser an entry ahead of the app, for every further Forward to do the same again. It is refused, and
           // the browser is taken back to where the app stayed.
           ?.takeIf { BrowserRoutes.entryCount(it) == to + 1 }
   ```

   No imports change (`BrowserRoutes` and `NavigationState` share the package).

3. In the class KDoc of `BrowserHistoryEffect` (`BrowserHistory.kt:59-61`), replace "an entry the app knows nothing
   about is opened from its address instead, and one that cannot be is left again." with "an entry the app knows
   nothing about is opened from its address instead, as long as that address stands for a place exactly as deep as
   the entry, and one that cannot be is left again."

Do not stop nulling the recorded states in `synchronize`: a rewritten entry (a settings tab picked in place of the
setlists, a renamed file) makes the states above it belong to a different stack. And do not change `resolve`, which
also serves the launch, where rebuilding from the songs up is exactly right.

## Tests
None (UI; `:presentation` is untested).

## Verify
Web dev server (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), a library with a setlist of at least two songs:
1. The scenario above: Forward leaves the app on `setlist/s/b` with the Setlists screen under it, and Back from there
   goes to Setlists. Pressing Forward again changes nothing in the app.
2. Open `http://localhost:8080/setlist/s/a` directly, press Back twice (to the songs), then Forward twice: Setlists,
   then the song in its setlist, as before (entries written in passing still resolve from their address).
3. Open `…/song/a/edit` directly, Back to the song, Back to the songs, Forward, Forward: the song, then its editor.
4. Settings tab switch at depth 1 with a forward entry above it: Setlists → a setlist song → Back → open Settings
   from the rail → Forward: the setlist song over Setlists opens (address depth matches), Back goes to Setlists.
Compile: `./gradlew :presentation:compileKotlinWasmJs` (the change is `wasmJsMain` only).

## Docs
`presentation/CLAUDE.md`, the `wasmJsMain/ui/navigation/` bullet (`:34-35`): replace "Forward restores the
`NavigationState` recorded when the entry was last on top, cut short at whatever the library no longer holds, or
opens the entry's address." with "Forward restores the `NavigationState` recorded when the entry was last on top, cut
short at whatever the library no longer holds, or opens the entry's address — but only an address exactly as deep as
the entry, since it stands for a whole stack built up from the songs; one that is not (an editor whose setlist page
underneath was paged on) is left again."

## Touches
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserHistory.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt`
- `presentation/CLAUDE.md`

## Depends on
None. 44 edits `BrowserRoutes.validate` and the same `presentation/CLAUDE.md` sentence, so run them one after another.
