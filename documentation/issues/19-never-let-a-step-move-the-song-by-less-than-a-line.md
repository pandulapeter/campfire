# Never let a step move the song by less than a line

**Challenged:** amended — part 1 as written skips lines. Going on "to the next stop after it" with `nextStepOffset`
jumps over whatever the stop it passes over still had to page through: a reader resting a few pixels above a section
taller than the screen (a fling, a new layout) was sent to the section after it. The scratchpad probe
(`challenge/C19.kt`, the review's fuzz scenarios with realistic sizes and starts on a stop, a few pixels above one, or
anywhere) found content skipped in 6 732 of the realistic songs with the plan's rule and in none with the current code.
Even going on with `nextStepTarget` from the tiny target is only safe while the result is no more than what can be
read at once past the reader, since the reader has seen up to a line less than that target would have. The rule is
now: take the step from the tiny target, repeatedly, while it stays within `visibleHeight` of the reader; that walk
(`challenge/C19c.kt`, 400 000 songs both ways) skips nothing and removes about 60 % of the sub-line presses forwards and
40 % backwards. The null rule is tightened to "everything down to the end of the song's content is on screen"
(`maxValue - window.end <= scroll`), which is what a hand-off requires, rather than "within a line of `maxValue`".
Part 2 (the overlap cap) is sound: a page stays no larger than `visibleHeight`, which is all the coverage proof needs.
Of the Problem's examples, the 4 px press and the 25 px one are sub-line; the 62 px press at text size 0.5 is about three
lines there (the live run's `ov=82` at 1.0 is a 41 px line, about 20 px at 0.5), a one-line section stepped whole — D2's
one press per section, which this plan leaves alone.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RowSnapping.kt`
(`ReadingWindow.pageHeight`, `nextStepTarget`, `previousStepTarget`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RowSnappingTest.kt`,
`presentation/CLAUDE.md` where it describes paging through a tall section

## Problem

Two ways a press moves the song by so little that it looks as if nothing happened, from the live run at 9ab7ca54e.

**Stops clamped to the end.** Near the end of a song several stops clamp to within a few pixels of `maxValue`, and each
is stepped to on its own: the 400-line song at 800 × 600 and text size 0.5 stepped 15282 → 15344 (62 px), the 300-section
song at 360 × 300 moved 4 px on one press, the tab song 1444 → 1469. `nextStepTarget` returns `next` as soon as the
stop's content is in view,

```kotlin
val next = nextStepOffset(scroll, rows.stepOffsets, maxValue)
…
if (lastContent <= scroll + POSITION_TOLERANCE) return next
```

and `reachableStops` only merges *exact* ties: `rows.stepOffsets.map { it.coerceIn(0, maxValue) }.distinct().sorted()`.

**A page smaller than a line.** `pageHeight` subtracts two lines of overlap and floors the result at half the
visible height:

```kotlin
fun pageHeight(viewportHeight: Int): Int {
    val visibleHeight = visibleHeight(viewportHeight)
    return (visibleHeight - overlap).coerceAtLeast(visibleHeight / 2).coerceAtLeast(MIN_PAGE_HEIGHT)
}
```

At 360 × 300 with text size 2.5 (viewport 353 px, overlap 204 px) the page is 156 px, less than one chorded line, and
the 400-line song took 1 601 presses, four per line. A phone held sideways at a large text size is in the same place.

## Fix

Both in the pure functions.

1. **A press that would move less than a line goes on from where it would have stopped.** With `line = window.overlap / 2`
   (the lyrics line height the window was built with):

   ```kotlin
   // in nextStepTarget, around what it returns today (rename the current body to a private nextStepTargetOnce)
   var target = nextStepTargetOnce(scroll, rows, viewportHeight, window, maxValue) ?: return null
   while (target - scroll < line) {
       val further = nextStepTargetOnce(target, rows, viewportHeight, window, maxValue)
           ?: return if (maxValue - window.end <= scroll + POSITION_TOLERANCE) null else target
       if (further > scroll + window.visibleHeight(viewportHeight)) break
       target = further
   }
   return target
   ```

   and the mirror in `previousStepTarget` (`scroll - target < line`, `further < scroll - window.visibleHeight(viewportHeight)`
   breaks, and a `null` from the further step simply keeps `target`, since only the very top of the song hands off
   backwards). Why it cannot skip a line: every step the walk takes is a valid step from where the previous one would have
   stopped, and the walk only goes as far as a target whose window starts no lower than the reader's window ends
   (`further <= scroll + visibleHeight`), so what lies between the reader's window and the new one is covered however
   many stops the walk passed. The `null` is returned only where everything down to the end of the song's content is
   already on screen (the space the scroll holds after the last line, `window.end`, being nothing to read), which is the
   condition for a hand-off to the next song. A step that is small for a reason — a row whose content ends just below
   (the `limit` of a row stepped by rows), a tall stop the next page of which would be more than a screen away — is
   taken as it is.
2. In `pageHeight`, cap the overlap at a third of the visible height: `(visibleHeight - overlap.coerceAtMost(visibleHeight / 3))`,
   keeping the floor at `visibleHeight / 2` and `MIN_PAGE_HEIGHT`. A page is then at least two thirds of the screen
   where two lines of overlap would take more than a third of it; wherever the overlap is less than a third (every
   ordinary size) nothing changes.
3. `reachableStops` feeds the dots: give it the same `line` (`reachableStops(rows, maxValue, minGap: Int = 0)`, the default keeping `stopsTooCloseToTheEndOfTheSongAreCountedOnceWhereItEnds` as it is; the stepper's
   `stops` reading `flingBehavior.readingWindow.overlap / 2`) and keep only the last of every run of clamped stops closer
   than that to the one before, so that the dots do not count stops the presses walk through. `isStepStop` is unchanged:
   the walk only ever lands on a stop, a page start or `maxValue`, as today.

Note both in the KDocs, and in `presentation/CLAUDE.md`'s paging sentence ("a press moves the song by at least a line
wherever that skips nothing").

## Tests

`RowSnappingTest`, with a window whose `overlap` is 80 (a line of 40):

- three stops at 1000, 1010 and 1020 with `maxValue = 1020`, viewport 600, scroll 990: one press lands on 1020, not
  1000, and the next returns `null`;
- backwards from 1020 over the same stops: the press lands at least a line above 1020 and no further than
  `visibleHeight` above it;
- a stop at 1000 starting a section taller than the screen (the next stop at 5000), scroll 995: the press does **not**
  land on 5000 — it lands on a page of the section no further than `visibleHeight` past 995 (the case the plan's rule
  got wrong);
- the last stop past `maxValue` (so clamped to it), scroll 4 px short of `maxValue`, `window.end = 32`: `null` (today
  it is a 4 px press);
- `ReadingWindow(overlap = 204).pageHeight(353)` is `353 - 353 / 3`; with `overlap = 40` it is `353 - 40` as before;
- the existing `aPageIsNeverLessThanHalfOfWhatCanBeRead` changes with part 2: `ReadingWindow(overlap = 2000).pageHeight(1000)`
  is now `1000 - 1000 / 3 = 667`, not 500 (rename it to say a page is never less than two thirds of what can be read);
  its second assertion (overlap 100 of 800) is unchanged. Every other existing stepping test moves by more than its
  window's line (50) and passes unchanged;
- `reachableStops` merges 1000, 1010, 1020 into 1020 and leaves stops a line or more apart alone.

Run the review's coverage fuzz (`scratchpad/probe/Fuzz.kt`, plus the random-start walk of `scratchpad/challenge/C19c.kt`)
against the changed functions: no segment skipped either way, no early `null`.

## Manual check

Desktop window at 360 × 300 (lift the minimum for the check) and text size 2.5: Down moves at least a chorded line
every press, and no press at the end of a song moves only a few pixels.
