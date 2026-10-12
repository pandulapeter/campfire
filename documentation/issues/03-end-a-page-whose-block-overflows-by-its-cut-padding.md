# Let a page end, running over, where its block overflows the screen only by the padding of its cut

**Challenged:** amended — the premise is confirmed by a probe run of `flowIntoPages` at 21173fed6 (the plan's inputs give `rows = [0, 0, 0]`, one page holding all three units). The "equivalently and simpler" variant is dropped: returning at the first end that fails by its padding cuts off later ends from the same start that do fit, which changes normal songs. Only the deferred form is kept. The tests move to `SectionPagingTest`, where `flowIntoPages` is tested, and a second test pins that case. `ColumnFlow.kt` is left alone, and no `CLAUDE.md` sentence is added.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all; most likely a phone on its side or a large text size
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionCutting.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionPagingTest.kt`

## Problem

`flowIntoPages`'s `walk` (`SectionCutting.kt`) only reports a page end that fits once the bottom padding of a cut is
added:

```kotlin
used += added
onBlock(false, unit, blockEnd)
unit = blockEnd
val height = used + bottomPaddingAt(unit)
if (height <= maxRowHeight) onEnd(unit, height, false)
```

and when the next block then does not fit, it returns without an end unless the page is mostly empty
(`used * 2 < maxRowHeight`). A page whose blocks fit (`used <= maxRowHeight`) but whose cut padding pushes it over
(`used + padding > maxRowHeight`) therefore has no end at all. Traced at 21173fed6 with `sectionStarts = [0, 1, 3]`,
`heights = [80, 95, 40]`, cuttable before unit 2, `piecePadding = [0, 10]`, `sectionGap = 10`, `maxRowHeight = 100`:
from 0 the only end is 1; from 1, unit 1 gives `used = 95`, the end at 2 is 105 and is skipped, unit 2 does not fit
and 95·2 ≥ 100, so `walk` returns with no end. `pageStarts[3]` stays -1, `pageEnds` is `[3]`, and the second pass
walks one page from 0 that stops after unit 0: units 1 and 2 keep the default `rows = 0, columns = 0`, so the rest of
the song is drawn in the first page's column — paging lost for that song.

## Fix

In `walk`, keep `var hasEnded = false` and a pending end (`var pendingEnd = -1`, `var pendingHeight = 0L`):

- Where an end is reported (the pair that fits, and `if (height <= maxRowHeight) onEnd(unit, height, false)`), set
  `hasEnded = true`. In the `else` of the latter, remember `pendingEnd = unit`, `pendingHeight = height`. This is the
  end that only failed by its padding, since `used <= maxRowHeight` there.
- At both `return` points (the pair branch and the block branch), when the page is **not** mostly empty, and only
  there: if `!hasEnded && pendingEnd >= 0`, call `onEnd(pendingEnd, pendingHeight, true)` before returning. The page
  runs over by its padding, it costs the pages `pagesOf` gives its real height (2 for 105 on 100), and it is still
  the last end of the walk, as the comment above `walk` says. The mostly-empty branches already report a run-over end
  and stay as they are.
- Do **not** report it at the failing end itself and `return` there. A later end from the same start can still fit,
  where the next block ends a section and so has no padding: with `sectionStarts = [0, 1, 3, 4]`,
  `heights = [80, 95, 3, 40]`, cuttable before unit 2, `piecePadding = [0, 10, 0]`, `sectionGap = 10` and
  `maxRowHeight = 100`, the page from unit 1 fails at 2 (105) but fits at 3 (98). Returning at 2 would turn three
  pages into four.
- The second pass needs nothing: walking from the page's start, it hands over the blocks up to the pending end, and
  the block after it neither fits nor gets an `onBlock` (the page is not mostly empty), so the walk returns.
- Reword the comment above `walk` to say that a page whose blocks fit, but whose cut's padding does not, runs over too
  when it has no other end. `ColumnFlow.kt`'s `choices` also drops an end that does not fit its padding, but
  `flowLikeAMagazine` only flows a stretch between starts that have an end (`isTooTall`). A start inside a stretch can
  only lose all its ends there if the stretch's last start, a cut, has more top padding than the content between the
  two starts, which is never the case, since a line is taller than a card's padding. So `fill(...)!!` holds. Leave it
  alone. Add no `CLAUDE.md` sentence: the paging paragraph's "runs on past the screen" already covers
  it, and lane H rewrites the next paragraph.

## Tests

In `SectionPagingTest`, where `flowIntoPages` is tested, with `isNarrow = { false }`, `lineHeight = 20`,
`lineCount = { 1 }`, `isStanzaBreakBefore = { false }`, `heightAt = { unit, _ -> heights[unit] }`, and `sectionGap = 10`:

- The plan's inputs (`sectionStarts = [0, 1, 3]`, `heights = [80, 95, 40]`, cuttable before unit 2,
  `piecePadding = [0, 10]`, `maxRowHeight = 100`). Expect `rows = [0, 1, 2]`. This fails today with `[0, 0, 0]`.
- The later-end case above. Expect `rows = [0, 1, 1, 2]`, which passes today and must still pass.

## Manual check

None needed beyond the test; optionally, a song with a long tab run at a large text size on a phone on its side pages
through every row.
