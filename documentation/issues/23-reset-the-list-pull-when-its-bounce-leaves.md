# Reset the pull a list passes to its short-content bounce when the bounce leaves

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android, iOS (the platforms with an overscroll effect)
**Challenged:** sound
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/ContentOverscroll.kt`

## Problem

`bounceScrollableContent` (`ContentOverscroll.kt:58-77` at 1c52e5347) only installs its connection while the content
cannot scroll:

```kotlin
val effect = rememberOverscrollEffect()
if (effect == null || state.canScrollForward || state.canScrollBackward) return this
val dispatcher = remember { NestedScrollDispatcher() }
val scope = rememberCoroutineScope()
val ownPull = remember { OverscrollPull() }
val usedPull = pull ?: ownPull
```

The Songs and Setlists screens pass a `pull` of their own (`topFade.overscrollPull`, `SongsScreen.kt:440`,
`SetlistsScreen.kt:427`), which lives in `ListTopFade` (`EdgeFade.kt:74`) and outlives this branch. The connection writes
it while the finger drags (`onPreScroll`, line 130) and springs it back to 0 after release in a job on `scope`
(`onPreFling`, lines 141-148). When the list becomes scrollable during a drag or during that ~0.3 s spring — setlist
reorder's Done, a search query narrowed and widened, a sync run or an import adding songs — the next composition takes
the early return: the `nestedScroll` goes (no `onPreFling` ever comes for a drag still in progress) and the
`rememberCoroutineScope` leaves composition, cancelling the spring. `towardsStart` stays where it was.

`ListTopFade.pullPx` ignores it while the list scrolls (`EdgeFade.kt:82`), but as soon as the list is short again it
counts: the cards are drawn shifted up by that much and the top card is partly faded under the header at rest, until the
next pull and release of the short list spring it back. (The container's `ownPull` has no such problem: it is
remembered inside the branch and is a fresh 0 when the branch comes back.)

The early return before the `remember` calls is legal Compose: the compiler closes the function's group at the return,
and the remembered `dispatcher`, `scope`, `ownPull` and `connection` are simply forgotten when the branch is left and
remembered anew when it comes back — the cancelled scope is the intended consequence, the stale external pull is not.

## Fix

Options:

1. **Reset on dispose (recommended).** Inside the branch, after `usedPull` is known:

   ```kotlin
   // A pull handed in outlives this branch: leaving it mid-drag or mid-release would leave the list carried up.
   DisposableEffect(usedPull) { onDispose { usedPull.towardsStart = 0f } }
   ```

   It runs when the list turns scrollable (the branch leaves), when the screen leaves, and when a different pull is
   handed in. No visible jump: `pullPx` is already 0 while the list scrolls. (`towardsStart` has an `internal set`,
   reachable from here.)
2. Zero it in the early-return path (`pull?.towardsStart = 0f` before `return this`). Rejected: a snapshot write during
   composition, made on every recomposition of a scrollable list.

## Tests

None: the behaviour is a composition lifecycle effect, and `OverscrollPull` holds no logic to test.

## Manual check

On Android (stretch) and iOS (bounce): on the Setlists screen with a setlist short enough not to scroll, enter reorder
mode, pull the list up and tap Done (or, on the Songs screen, search for one song, pull its card up and clear the
search within a moment of letting go); make the list short again: the top card rests fully visible under the header,
not partly faded.
