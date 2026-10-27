# Keep the reader's place current while a song scrolls by itself, so a relayout while playing never sends it back to where Play was pressed

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the same-pass path also sets the flag when its clamped move falls short, the KDoc of `keepReaderInPlace` is updated, and the UI test drives a frozen clock while the song plays (an auto-advancing clock never goes idle under `play()`'s frame loop) and checks the song moved first.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/stepping/RowSnapFlingBehavior.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/autoScroll/SongAutoScroller.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/stepping/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/autoScroll/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/AppTest.kt` (a `songLayout` parameter),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/AutoScrollTest.kt` (new)

## Problem

In the one-column layout that scrolls by itself (`SongLayout.CONTINUOUS`), any relayout of the song while it plays
sends the song back to where the reader last stopped scrolling it themselves, which is usually where they pressed Play.
A performer folds a section, pinches the text, transposes, toggles chords, or resizes the window 40 seconds into a song,
and the song jumps back to the first verse and plays on from there.

Why: `RowSnapFlingBehavior.keepReaderInPlace` takes the reader's place (`anchor`) only when the scroll comes to rest:

```kotlin
snapshotFlow { scrollState.isScrollInProgress }.collect { isScrolling ->
    if (isScrolling || isMoving) return@collect
    if (scrollState.value != movedTo) anchor = readingAnchorOf(scrollState.value, rows, readingWindow.top)
    movedTo = null
}
```

But `SongAutoScroller.play` moves the song with a raw delta, which never sets `isScrollInProgress`:

```kotlin
val speed = currentTrack.speedAt(readingLineAt(scrollState.value).toFloat(), secondsPerLineAt(pace.secondsPerLine(currentTrack.lineCount), speedPercent()))
scrollState.dispatchRawDelta(speed * seconds)
```

So while playing, `anchor` stays wherever the reader last left the song. When the rows change
(`snapshotFlow { rows }.collectLatest { … }`), after `LAYOUT_SETTLE_MILLIS` the collector computes
`anchoredScrollOffset(currentAnchor, …)` from that stale anchor and `animateScrollTo`s there; `play()` skips the frames
of that animation (`if (scrollState.isScrollInProgress) continue`) and then plays on from the old place.

`onRowsPlaced`'s same-pass path has the same flaw: on a height-only viewport change (the metronome panel, the short
window's title row) with changed rows, it `dispatchRawDelta`s to the stale anchor in the layout pass itself, an instant
jump with no animation. (Whether the rows of an unmeasured single column change with the viewport height at all is not
certain; the fix covers it either way.)

This breaks `autoScroll/CLAUDE.md` ("A pinch, a resize, or a wrapped line changes the speed on screen, never the pace
through the song") and the keep-in-place promise of `stepping/CLAUDE.md` ("Keeping the reader in place").

## Fix

Let the auto scroller's movement count as the reader's own, except while a new layout is still waiting to be followed,
when the song holds still.

The subtlety: after a relayout the scroll still holds the *old layout's* pixels, which are somewhere else in the new
one, until `keepReaderInPlace` moves it. An anchor taken from the scroll in that window (from the new `rows`) would
record the wrong place, and the move would then keep the reader at the wrong place. So the anchor may follow the
playing scroll only while it describes the current rows.

In `RowSnapFlingBehavior`:

1. Add `var isFollowingLayout by mutableStateOf(false)` with `private set`, KDoc'd as: a new layout has been placed
   that the reader has not been put back into yet, so the scroll's pixels say nothing about where they are.
2. In `onRowsPlaced`, where `placedRows != rows`: if the same-pass height-only branch re-placed the reader (it ran the
   `dispatchRawDelta` and `scrollState.value` is then the target), leave the flag alone; otherwise, with
   `anchor != null`, set `isFollowingLayout = true` before `rows = placedRows`. A dispatch clamped short of its target
   (the previous layout's extent, a reader near the end) counts as not re-placed: the rest is `keepReaderInPlace`'s, and
   an anchor taken from the clamped pixels meanwhile would be kept instead of the reader's. Setting it here, in the layout pass that writes the rows, rather than in the `snapshotFlow`
   collector, is what keeps a frame of `play()` from moving the song between the new rows and the collector seeing
   them.
3. In `keepReaderInPlace`'s `collectLatest` body, wrap everything after the `anchor == null` branch in
   `try { … } finally { if (rows == currentRows) isFollowingLayout = false }`, so every exit (nothing to move, a
   reader's scroll in progress, the target missing, the move done or cut short by a drag) clears it, and a newer layout
   that cancelled this one keeps it set. The initial `anchor == null` branch clears it too.
4. Add

   ```kotlin
   /** Takes where the reader is from where the song was moved to by something that is not a scroll gesture: the song playing by itself. */
   fun noteReaderMoved() {
       if (isFollowingLayout) return
       val value = scrollState.value
       if (value == anchoredValue) return
       anchoredValue = value
       anchor = readingAnchorOf(value, rows, readingWindow.top)
   }
   ```

   with a `private var anchoredValue = -1` reset wherever `anchor` is otherwise written (set it to the value the anchor
   was taken at, or -1). The check skips the frames in which a slow scroll's fraction of a pixel did not change the
   whole-pixel value, which is most frames at a slow pace. `readingAnchorOf` is one pass over `stepOffsets` and up to
   two over `lineTops` (a few hundred entries for a long song) and one small allocation: fine once per changed pixel,
   no cheaper form needed.

In `SongAutoScroller.play`:

```kotlin
if (scrollState.isScrollInProgress || flingBehavior.isFollowingLayout) continue
…
scrollState.dispatchRawDelta(speed * seconds)
flingBehavior.noteReaderMoved()
```

Extend `play`'s KDoc: a frame in which a new layout is still to be followed is skipped too, since the reader is put back
on their place in it first.

Effect: a fold, transposition, or resize while playing holds the song still for the settle wait (250 ms) and the
glide, then plays on from the reader's place; a pinch holds it for as long as the fingers move. Rejected alternatives:

- Keep playing through the settle wait and carry the anchor forward in the old layout's terms: needs the old rows kept
  and mapped through every frame, for a quarter second of motion.
- Move the song in `scrollState.scroll { scrollBy(…) }` so `isScrollInProgress` flips each frame and the at-rest
  collector retakes the anchor: it would cancel nudges and flings (same mutex), flip `SongStepper.headedOffset` to null
  every frame, and churn every `snapshotFlow` reading `isScrollInProgress`.

KDoc: `keepReaderInPlace`'s "taken whenever the scroll comes to rest" gains "and, while the song scrolls by itself,
after each frame it moves ([noteReaderMoved]), never while a new layout waits to be followed ([isFollowingLayout])".

Docs: in `stepping/CLAUDE.md`'s "Keeping the reader in place", replace "Anchors are taken only at rest and never from
its own moves" with: anchors are taken at rest, and after each frame a song scrolling by itself moves (`noteReaderMoved`),
never from its own moves nor while a new layout waits to be followed (`isFollowingLayout`). In `autoScroll/CLAUDE.md`'s
"What moves and what stops it", add: a relayout while playing holds the song until the reader is back in place, then
plays on from there.

## Tests

A UI test, since the bug is the wiring of a running scroll and a relayout: add a `songLayout: SongLayout =
SongLayout.PAGES` parameter to `runAppTest` (`AppTest.kt`), written by `writeLibrary` as `put("songLayout",
songLayout.id)`, and a new `AutoScrollTest` in `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/`:

`` `a section folded while the song plays leaves the reader where the song had played to` ``: `runAppTest(window =
TestWindow.PHONE, songs = listOf("campfire_tests-the_long_road_north.cho"), songLayout = SongLayout.CONTINUOUS)`, open the
song (`AppTestNavigation.kt`'s way to it), then set `mainClock.autoAdvance = false` **before** pressing Space and keep
it off until the song is stopped again: `play()` awaits a frame forever, and Skiko's `waitForIdle` (which every finder
and action calls) with an auto-advancing clock waits for frame work to drain, timing out. Press Space, advance
`mainClock` 20 s (at the song's 112 BPM, two bars a line, about five lines), note the topmost lyric line wholly on
screen and assert it is not the song's first line (so the test can fail) (semantics bounds against
the song's viewport, as `PedalCoverageTest` reads lines; move any helper it needs out of `PedalCoverageTest` into a file
both use rather than copying it). Then fold a section whose heading pill is on screen below the reading line (click its
fold toggle's semantics node), advance the clock two seconds (the 250 ms settle wait, the section's spring, and the
glide back), and assert the topmost line is that one or a later one,
never an earlier one. Before the fix the song returns to the top. Stop playing (Space) at the end, advance a frame, and only then
turn `autoAdvance` back on, so the test leaves no running frame loop.

Run `./gradlew :tools:screenshots:test --tests '*AutoScrollTest*'` and `./gradlew :presentation:desktopTest --tests
'*RowSnappingTest*'` (unchanged logic, kept green).
