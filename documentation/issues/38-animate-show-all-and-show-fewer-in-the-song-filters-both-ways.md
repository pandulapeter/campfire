# Animate "Show all" and "Show fewer" in the song filters both ways: the group's height and everything under it glide, the extra chips fade, and the toggle rides the edge with its label crossfading

**Kind:** animation  ·  **Severity:** medium (user requested)  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CollapsibleChipFlow.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Controls.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/CollapsibleChipFlowTest.kt, presentation/CLAUDE.md

**Challenged:** amended — kept the design (one `LookaheadScope` inside the scroll is the only way to decide the fold under a budget in lookahead while animating the height without it; no simpler fix gets both directions, because `animateBounds` and `animateContentSize` both clamp to the budget constraint); added: leaving chips are also placed in the lookahead pass so their own `animateBounds` has a lookahead placement to read, the first pass and an ended transition draw with no layers or fades (first frame right, nothing narrated on open), explicit handling of an unchanged height (sort change), why both springs must stay default (the Languages title and the chips that now animate in the shared scope move on the same curve as `expandVertically`), the lookahead-size behaviour of the `AnimatedVisibility` parts, and manual checks for the "any / every" choice, the side panel shrunk until it collapses, and the sheet's own height chasing the content.

## Problem

Live frames (scratchpad live/expand_frames.png, collapse_frames.png, expand.mp4, collapse.mp4; 360×640dp, Songs → filter sheet, 20 tags):

- **Expanding** ("Show all 20"): in the first frame the toggle vanishes and the extra rows appear at full opacity, uncovered by a ~130 ms height grow whose edge cuts through a half-drawn row; the Languages group under it moves down in steps; "Show fewer" pops in at its final place about five frames later.
- **Collapsing** ("Show fewer"): nothing animates — the extra rows vanish, the label flips and Languages jumps up two rows in one frame; only the sheet's own height settles afterwards.

Why, at 800ebde0b:

1. `FilterGroupChips` (Controls.kt:464–500) switches its slot in the same frame as the flag:
   ```kotlin
   modifier = Modifier.layoutId(if (isExpanded) FilterSlot.EXPANDED_CHIPS else FilterSlot.CHIPS),
   ```
   and `FilterGroupsLayout` (CollapsibleChipFlow.kt:101–105) measures a `CHIPS` child with its budget as the maximum height:
   ```kotlin
   val maxHeight = if (measurable.layoutId == FilterSlot.EXPANDED_CHIPS) null else budgets?.get(slot)
   placeables[index] = measurable.measure(Constraints(maxWidth = width, maxHeight = maxHeight ?: Constraints.Infinity))
   ```
2. The group's height is animated by `animateContentSize()` on the flow's `Layout` (CollapsibleChipFlow.kt:156–159). That modifier coerces its animated size into the incoming constraints, so on collapse the budget cuts the height down in the first frame (no animation at all), and it clips its content to the animated size, so on expand the new rows are revealed by a hard edge.
3. The measure policy (CollapsibleChipFlow.kt:193–221) switches the shown set and the toggle's place at once: chips past the fold are simply placed or not (no fade), and the toggle is placed at the *new* bottom (`toggle?.placeRelative(0, chipsHeight)`), outside the clip until the grow ends — hence the toggle vanishing and popping back.
4. The flow has a `LookaheadScope` of its own (CollapsibleChipFlow.kt:147), used only for the chips' `animateBounds`; the parent and the group below are outside it, so nothing under the group can follow the height as a lookahead target.

Also reported: the collapsed group showed 12 tags on first open and 15 after selecting one and tapping Reset all filters. `maxLines` is derived from the momentary `constraints.maxHeight` (CollapsibleChipFlow.kt:201), i.e. from the budget computed on every measure. I could not confirm a different count in the evidence (every collapsed frame in live/81, 83, 86 and both strips shows the same 16 chips on 6 lines), so this plan removes the mid-animation inputs to the count and asks for a check rather than guessing at another cause.

Other places checked: the song cards' tag chips do not expand or collapse (no such control in `ListItems.kt`); the setlist description's tap-to-open (SetlistsScreen.kt:496–506) is `animateContentSize()` on a `Text` in an unbounded lazy-grid item, so it is never clamped and animates both ways — a different mechanism, not part of this plan.

## Fix

Use lookahead for what it is for: decide *what* is shown in the lookahead pass, and let the approach pass animate the height while the chips that join or leave fade.

1. **One `LookaheadScope` for the filter column, inside the scroll.** In `SongFilters` (Controls.kt:241–250) move the scroll, fade and padding modifiers to a wrapping `Box`, and put a `LookaheadScope { }` between it and `FilterGroupsLayout`:
   ```kotlin
   Box(modifier = Modifier.fillMaxWidth().fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding)) {
       // Inside the scroll: animateBounds follows positions in the scope, and a scope around the scroll would read
       // every scrolled pixel as a move to animate.
       LookaheadScope {
           FilterGroupsLayout(modifier = Modifier.fillMaxWidth(), availableHeight = availableHeight) { … }
       }
   }
   ```
   Pass the scope down: `TagFilters`, `LanguageFilters` and `FilterGroupChips` take a `lookaheadScope: LookaheadScope` parameter (or become `LookaheadScope.` extensions) and hand it to `CollapsibleChipFlow`, which takes `lookaheadScope: LookaheadScope` and drops its own `LookaheadScope { }`; the chips keep `Modifier.animateBounds(lookaheadScope)`.

2. **Share the room out in the lookahead pass only.** In `FilterGroupsLayout`'s measure block, compute `budgets` only when `isLookingAhead`, and measure a chip child with
   ```kotlin
   val maxHeight = if (isLookingAhead && measurable.layoutId != FilterSlot.EXPANDED_CHIPS) budgets?.get(slot) else null
   ```
   with a comment: the lookahead pass is where each group decides which chips it shows; in the approach pass a group reports the height it is animating through, and a budget there would cut it off — which is what made "Show fewer" jump. Update the composable's KDoc accordingly. The layout's own height in the approach pass is the sum of the approach heights, so the groups below and the sheet follow the animation.

3. **Animate the group's bounds instead of its content size.** Replace `modifier.animateContentSize()` on the flow's `Layout` with `modifier.animateBounds(lookaheadScope)` (no clip). Use the same motion the chips use (default `boundsTransform`), so a chip moving and the group growing travel together. That default is `spring(DampingRatioNoBouncy, StiffnessMediumLow)`, the same spring `expandVertically` uses by default, so a part that is placed by the layout rather than by its own `animateBounds` (the Languages title under the tags, which follows the tags group's approach height, or anything under the "any / every" choice appearing) moves along the same curve as the chips beside it, which now also travel by their `animateBounds` in the shared scope when the group above them changes height. Do not give either a different spec. Note why step 2 is needed even with this: `animateBounds`' outer node clamps the animated size into the incoming constraints (`chosenConstraints.constrain(animatedSize)` in `AnimateBoundsModifier.kt`), so a budget left on the approach pass would still cut the collapse down in its first frame.

4. **Split the flow's measure policy by pass.** Keep a holder remembered in `CollapsibleChipFlow` *outside* the policy (the policy is re-created when `isExpanded` or `isPinned` changes):
   ```kotlin
   /** What the lookahead pass decided, for the approach pass to animate towards, and what was shown before it. */
   private class ChipFlowTarget {
       var shown: List<Int> = emptyList(); var positions: List<IntOffset> = emptyList()
       var isToggleShown = false; var height = 0
       var previousShown: List<Int>? = null; var previousPositions: List<IntOffset> = emptyList()
       var previousToggleShown = false; var previousHeight = 0
   }
   ```
   (Key positions by item index; the chips' keys keep their nodes.)
   - **Lookahead** (`isLookingAhead`): compute `shown`, `lines`, positions, `isToggleShown` and the height exactly as today. If `shown` (or the toggle) differs from the holder's, copy the holder's current values into `previous…` first — a reversal mid-animation then starts from the last target, which is good enough. On the flow's very first lookahead pass (the holder never filled) nothing is previous: leave `previousShown = null`, so the first frame is drawn as it is today, with no layer and no fade — the sheet opening, or the panel appearing, narrates nothing. Store, then `layout(width, height)` placing as today, **and also place every chip of `previousShown` that is not in `shown`, plainly, at its previous position**: nothing a lookahead pass places is drawn, but each chip carries its own `animateBounds`, which reads the chip's lookahead placement while the approach pass places it — a chip the approach places that the lookahead left unplaced would be read at a stale lookahead position, which works by accident at best.
   - **Approach**: the constraints are the animated size `animateBounds` fixed. Let `h = constraints.maxHeight`, `edge = h - toggleHeight` (or `h` when no toggle is shown in either state), `progress = if (height == previousHeight) 1f else ((h - previousHeight).toFloat() / (height - previousHeight)).coerceIn(0f, 1f)`. Place every chip of `shown` at its target position — plainly if it was also in `previousShown`, otherwise with `placeRelativeWithLayer(x, y) { alpha = chipTransitionAlpha(…, isEntering = true) }`; place every chip of `previousShown` not in `shown` at its previous position with the leaving alpha; place the toggle at `y = h - toggleHeight` whenever it is shown in either state (alpha 1 when shown in both, `progress` when appearing, `1 - progress` when leaving). While `previousShown` is null (the first pass, or a transition that has ended), there is nothing to fade: place `shown` plainly and the toggle at `h - toggleHeight` with alpha 1 if it is shown. When `h == height`, set `previousShown = null` in that same pass and do not place the leaving chips in it, which ends their placement (and with it their semantics, as today); the next lookahead pass, whenever it comes, no longer places them either. A change of `shown` with no change of height (a collapsed group sorted the other way) gives `progress = 1`, so the chips that leave go and the ones that join appear in that frame while the chips that stay travel by their `animateBounds` — as today.
   - Add the pure helper, internal so it can be tested:
     ```kotlin
     /**
      * How opaque a chip that joins or leaves the group is while its height animates: a row fades in as the group's
      * edge passes it, never ahead of the animation, so a chip joining a line already in sight (the rest of the last
      * collapsed line) fades in over the animation rather than appearing at once. Leaving is the same in reverse.
      */
     internal fun chipTransitionAlpha(chipTop: Int, chipHeight: Int, edge: Int, progress: Float, isEntering: Boolean): Float {
         val sweep = ((edge - chipTop).toFloat() / chipHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
         return minOf(sweep, if (isEntering) progress else 1f - progress)
     }
     ```
   - Intrinsic measurements are unchanged.

5. **The toggle's label crossfades in place.** In `FilterGroupChips`' `toggle`, put the label in
   ```kotlin
   AnimatedContent(targetState = isExpanded, transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) }) { expanded ->
       Text(text = if (expanded) stringResource(Res.string.songs_filters_show_less) else stringResource(Res.string.songs_filters_show_all, items.size))
   }
   ```
   so "Show all 20" turns into "Show fewer" where the button is, while the button rides the group's edge (step 4).

5b. **Inside the shared scope, `AnimatedVisibility` reports its target size in the lookahead pass.** The group parts, the "any / every" choice and Reset all filters are `AnimatedVisibility`s; in the lookahead pass each measures at its final size at once, so the budgets are decided once per change rather than once per frame of an `expandVertically` (which today re-decides the tags' fold on every frame of the Languages group appearing). That is intended; check it on the device: a second language arriving by a sync run while the sheet is open makes the tags group give up its rows in one animated step, not row by row.

6. **The collapsed count.** With budgets computed only in the lookahead pass (step 2), no in-between height of a group or of the sheet reaches `maxLines`. After implementing, check on the 360×640dp emulator: open the sheet fresh and count the collapsed tags; select a tag, tap Reset all filters, count again; close and reopen. If the counts still differ, log `availableHeight`, the non-chip `fixedHeight` and the budgets from `FilterGroupsLayout`'s lookahead pass at both moments and report which input moved rather than patching around it (a suspect is `availableHeight = maxHeight - uncoveredTopInset() - …` in `SongFilters`, read in composition while the sheet slides).

7. **Docs.** In presentation/CLAUDE.md:122 (the "Where the fold falls is decided by the room" passage), replace "(`animateBounds` in a `LookaheadScope` of the flow's own, each chip composed under its key)" with "(`animateBounds` in the filter column's one `LookaheadScope`, inside the scroll, each chip composed under its key)" and add after the "Show all" sentence: "Opening and closing a group is animated both ways: the room is shared out in the lookahead pass only, where each group decides its chips, and the approach pass animates the group's bounds, the groups below following; a chip that joins or leaves fades as the group's edge passes it (`chipTransitionAlpha`), and the toggle rides that edge, its label crossfading." Update the KDocs of `CollapsibleChipFlow` ("The chips past the fold arrive and leave in one step" comment goes) and `FilterGroupsLayout`.

## Tests

`CollapsibleChipFlowTest` (desktop): add cases for `chipTransitionAlpha`:
- a chip wholly below the edge is invisible whatever the progress (`edge = 100, chipTop = 120` → 0);
- a chip the edge has passed shows as far as the animation has come (`edge = 200, chipTop = 0, chipHeight = 32, progress = 0.3, entering` → 0.3);
- half-passed at the end of the animation is half visible (`edge = 116, chipTop = 100, chipHeight = 32, progress = 1, entering` → 0.5);
- leaving mirrors entering (`progress = 0.3, leaving`, edge past → 0.7);
- a zero chip height does not divide by zero.
The existing `collapsedChips`, `chipLines` and `chipBudgets` tests stay green unchanged.

Run `./gradlew :presentation:desktopTest --offline`.

## Manual check

On the 360×640dp emulator with a library of ~20 tags and several languages, open Songs → filter. Tap "Show all 20": the group grows smoothly, the new chips fade in row by row as the edge passes, the Languages group glides down with it, and the button travels down with the edge while its label crossfades to "Show fewer" — it never disappears. Tap "Show fewer": the reverse, with Languages gliding back up and no frame where rows vanish at once. Select a tag that sits past the fold while collapsed, then toggle: the pinned chip glides between its collapsed and expanded places. Scroll the sheet while nothing is animating: the chips do not lag behind the scroll. Select a second tag so that "any / every" appears under the tags: the Languages title and its chips move down together, on one curve. Repeat on a desktop window (side panel, ~1400dp tall), where the groups are usually shown whole, and shrink the window until the tags collapse, then use Show all there. Watch the sheet's own top edge on the phone while a group opens and closes: the sheet is outside the scope and follows the content's height every frame; if it visibly lags behind and then catches up (its own settling animation chasing a moving height), report it with a recording rather than adding another animation to cover it. Do the count check of step 6.
