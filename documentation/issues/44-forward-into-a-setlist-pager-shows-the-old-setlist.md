# 44 · Browser Forward into a song of a setlist pages through the setlist as it was, not as it is now

**Severity:** wrong behaviour, minor (web only. Unlikely: it takes leaving a setlist's song with Back, editing that
setlist, and then the browser's Forward) · **Area:** `:presentation` `wasmJsMain` (`ui/navigation/BrowserRoutes.kt`)

## Symptom
1. Songs → Setlists → tap song `a` of setlist `s` (songs `a`, `b`, `c`). Press Back to the Setlists screen.
2. On the Setlists screen, remove `c` from `s` or reorder its songs.
3. Press the browser's Forward.

The pager opens on the old song list: `c` is still a page, in the old order, under the setlist's bar. Transposing `c`
there does nothing, without a word. Reloading the same address gives the setlist as it is now, so the two disagree.

## Cause
Forward restores the state recorded when the entry was last on top, checked by `BrowserRoutes.validate`
(`BrowserRoutes.kt:143-161`), which only asks whether the details screen still has *a* song and its setlist file
still exists:

```kotlin
is CampfireDestination.SongDetails -> destination.songFileNames.any { it in songFileNames } &&
        (destination.setlistFileName == null || destination.setlistFileName in setlistFileNames)
```

Editing the setlist does not change the Setlists screen's own address, so nothing rewrites the entry and its
recorded state keeps the old `songFileNames`. The page list is never compared with the setlist's current entries,
while `resolve` (`:117-129`) builds it from them. A page that is no longer in the setlist has no entry to write a
transposition to: `setTransposition` (`CampfireViewModel.kt:1255-1260`) only maps the entries that exist, and
`Transpositions.get` (`:2070-2074`) answers 0 for it.

## Fix
`BrowserRoutes.kt`, replace `validate` (`:138-161`, KDoc included) with:

```kotlin
/**
 * [state] with its back stack cut short at the first screen that names a song or a setlist the library no longer
 * holds, which is what a history entry the browser returns to may do: the library changes while the entry waits.
 * A song read from a setlist pages through what the setlist holds now, as it does when the setlist's row is tapped
 * or its address is opened ([resolve]), and it is cut short too once the song it was on is gone from the setlist.
 */
fun validate(
    state: NavigationState,
    songs: List<Song>,
    setlists: List<Setlist>,
): NavigationState {
    val songFileNames = songs.mapTo(mutableSetOf()) { it.fileName }
    val setlistsByFileName = setlists.associateBy { it.fileName }
    val backStack = mutableListOf<CampfireDestination>()
    for (destination in state.backStack) {
        backStack += when (destination) {
            is CampfireDestination.SongDetails -> if (destination.setlistFileName == null) {
                destination.takeIf { it.songFileNames.any { fileName -> fileName in songFileNames } }
            } else {
                val pages = setlistsByFileName[destination.setlistFileName]?.entries?.map { it.songFileName }?.filter { it in songFileNames }.orEmpty()
                val index = destination.songFileNames.getOrNull(destination.initialIndex)?.let(pages::indexOf) ?: -1
                if (index < 0) null else destination.copy(songFileNames = pages, initialIndex = index)
            }

            is CampfireDestination.SongEditor -> destination.takeIf { it.fileName in songFileNames }
            else -> destination
        } ?: break
    }
    return state.copy(backStack = backStack)
}
```

`initialIndex` of a recorded state is already the page the pager was on (`CampfireViewModel.navigationState`,
`:828-842`), which is why it names the song to keep. The destination keeps its `id`, as the recorded state already
did. No imports change.

Behaviour that changes on purpose: a setlist pager whose current song was deleted used to be kept (showing whichever
page the old index now fell on); it is now cut, which is what `resolve` does for the same address on a reload. A
library pager (`setlistFileName == null`) holds one song and is unchanged.

## Tests
None (UI; `:presentation` is untested).

## Verify
Web dev server (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), a setlist `s` of three songs `a`, `b`, `c`:
1. The scenario above, removing `c`: Forward opens `a` with two pages, `a` and `b`, and the setlist bar counts two.
2. Reorder to `c`, `b`, `a` instead: Forward opens `a` as the third page; swiping goes back through `b`, `c`.
3. Page to `b`, Back, remove `b` from the setlist, Forward: nothing opens and the browser stays on Setlists.
Compile: `./gradlew :presentation:compileKotlinWasmJs`.

## Docs
`presentation/CLAUDE.md`, the `wasmJsMain/ui/navigation/` bullet (`:34-35`): after "cut short at whatever the library
no longer holds" add ", with a song read from a setlist paging through what the setlist holds now". (If 43 has landed
first, insert it at the same spot in 43's new sentence.)

## Touches
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt`
- `presentation/CLAUDE.md`

## Depends on
None. 43 edits `BrowserRoutes.kt` and the same `presentation/CLAUDE.md` sentence, so run them one after another.
