# Split SongDetailsScreen so that its method is well below HotSpot's 8,000-byte JIT limit

**Kind:** performance (desktop)  ·  **Severity:** low  ·  **Platforms:** desktop (JVM)
**Lane:** U  ·  **Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`

**Order:** last of lane U's song details plans. It goes after 32, 33 and 34, which all edit `SongDetailsScreen.kt`
(32 and 34 add code to `SongDetailsScreen` itself), so that the split is measured on the method as it ends up.

**Challenged:** amended — dropped the reference to a lane D CI check for huge methods (no such plan exists); named
what plans 32–34 add to `SongDetailsScreen` and where each has to stay once the app bar moves out; corrected the JVM flag to `+DontCompileHugeMethods` (the default that keeps huge methods interpreted).

## Problem

HotSpot never JIT-compiles a method whose bytecode is longer than 8,000 bytes (`-XX:+DontCompileHugeMethods` is the
default). Such a method is interpreted for the life of the process. At 491c4254a the composable
`SongDetailsScreen` (`SongDetailsScreen.kt:201-935`) compiles to 10,070 bytes of bytecode unshrunk
(`presentation/build/classes/kotlin/desktop/main/.../SongDetailsScreenKt.class`, method
`SongDetailsScreen-jIwJxvA`), and to 8,179 bytes after the desktop release's ProGuard pass. So it is just over the
limit even in the shipped build. It is the only Compose function of the app in that situation. The other methods
over 8,000 bytes are static initializers of the localized strings, which run once.

The method runs on every recomposition of the screen: every page change (`pagerState.currentPage`/`targetPage` are
read in it), every state the screen collects (tempos, capos, playback, preferences, song texts) and every song text
that arrives. Interpreted, its body costs tens of µs on a fast machine and up to ~0.5 ms per recomposition on a
Celeron-class 2-in-1 (estimated), where a compiled one would cost a small fraction of that. It also keeps the JIT
from inlining anything it calls.

## Fix

Move a self-contained block out of `SongDetailsScreen` into a `private` composable of its own, until the method is
well under the limit. Aim for **≤ 6,500 bytes unshrunk**, which leaves room for later additions.

The natural first cut is the **app bar**: the whole `CampfireTopAppBar(…)` call (`SongDetailsScreen.kt` ~465-742)
with its `navigationIcon`, `title`, `actions` and `bottomContent` lambdas. It becomes
`SongDetailsAppBar(…)`, taking:

- the values it reads: `currentSong`, `songs`, `isReadOnly`, the `appBarButtons` decisions, `metronomeButton` /
  `metronomeAction`, `openCurrentSongInfo`, `currentPageScrollState`, `scrollBehavior`, `contentPadding`;
- the callbacks it uses;
- from plan 34, the panel's `MutableTransitionState` (`panelState`), which the bar's `bottomContent` hands to
  `MetronomePanel` as `visibleState`. It is created in `SongDetailsScreen` and stays there, since the pages read its
  idleness through `isViewportSettled` too: pass the state object down, never `panelState.isIdle` as a value, and keep
  `isViewportSettled` (and the app bar scroll behaviour it reads) in `SongDetailsScreen`;
- from plan 32, `openCurrentSongInfo` as already computed in `SongDetailsScreen` (its `hasSongInfo` `remember` stays
  in the screen, next to `currentSongText`).

If the second cut (the pager) is needed, plan 33's per-page `buildsInPlace` `derivedStateOf` moves with the page
lambda unchanged, and plan 34's `isViewportSettled` lambda is passed into it.

The lambdas themselves are already separate synthetic methods. What stays in `SongDetailsScreen` today is building
them: capturing a dozen values each, the `remember`/`changed` bookkeeping, and `rememberComposableLambda`. That is
what moves.

If that is not enough, the second cut is the pager block, the `if (songs.isEmpty()) … else Box { HorizontalPager …
StepButtons … }` together with `SongPagerControls`, as `SongDetailsPager(…)`.

Pass `State`/lambda reads down rather than values where the original read was deferred, as with
`currentPageScrollState` and `isScrolledToTop`'s `derivedStateOf`, so that no read moves into a wider recomposition
scope than it had. Behaviour must not change at all. This is a pure move of code, with its comments kept next to it.

Measure the method after the change:

```
./gradlew :presentation:compileKotlinDesktop
javap -c -p presentation/build/classes/kotlin/desktop/main/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreenKt.class \
  | awk '/^  [a-z].*\(/{name=$0} /^ *[0-9]+: /{split($1,a,":"); last[name]=a[1]} END{for(n in last) print last[n], n}' | sort -rn | head
```

The first number per method is the offset of its last instruction, within a few bytes of its length. Every method of
the file, the new composable included, must be under 8,000, and `SongDetailsScreen-…` ≤ 6,500.

## Tests

None. It is a pure move of composable code. The existing `:presentation:desktopTest` suite must still pass, which
covers `appBarButtons`, `showsFontScaleInPerformanceBar` and the setlist slots that stay in this file.

## Manual check

On the desktop build, open a song from a setlist and check everything the app bar does, plus paging:

1. The title opens About the song at the top and scrolls back up when scrolled.
2. The metronome button opens the panel.
3. The overflow and editing menus.
4. In Read only, the text size stepper and the metronome button in the bar, as wide as the window allows.
5. The pager bar's numbering and arrows.
6. In a short window, the bar leaving and coming back with the scroll.

All of these behave exactly as before. On a low-end Windows 2-in-1, paging through a setlist feels at least as quick
as before. A JFR recording should no longer show `SongDetailsScreen` as interpreted.
