<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Make a song card cheap to compose: create its animations only once a value it animates first changes, and share its icons' painters across the list

**Challenged:** amended — the gate moves the resting content into the transition with `movableContentOf` (re-composing it cut the overflow menu's exit on the card that starts the selection mode); the star's first change starts from the resting value (a fresh `animateFloatAsState`/`rotatedFor` would skip both the fade and the turn); the drag handle's state is per row; painters keep a fallback for song details' star; one more test.

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** all  ·  **Run on:** any machine
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChangeAnimations.kt` (new: the two gated wrappers)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongListRow.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt` (hoisted painters)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songCard/SongListItem.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songCard/CenteredSongCardContent.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/actions/SetlistAssignmentsButton.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/labels/SongLabels.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistSongRow.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md` (one rule)
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChangeAnimationsTest.kt` (new, if the gate is a pure function) and the existing UI tests listed under Tests

The tooltip half of the same cost is plan 07.

## Problem

A pop from song details and a tab switch compose the whole list at once (Navigation 3 drops the covered entry, see
the decision at the end): the 59th review's trace counted ~17,400 composable bodies for 22 cards in that one frame,
`SongListRow` ×44, `AnimatedVisibility` ×153–300, `Transition.animateTo` ×208–293, `rememberVectorPainter` ×217, and the
layout phase (66–82 % of the frame, mostly under the grid) spent 13–21 % in `CenteredSongCardContent.measure`. Nearly
every animation object on a card exists for a change that, on most cards, never happens while they are on screen:

- `SongListRow.kt:170-174`, per card: `= AnimatedContent(targetState = isSelecting, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentAlignment = Alignment.CenterEnd)`.
- `SongListItem.kt:221-227`, the artist line: `AnimatedVisibility(visible = isArtistDrawn, enter = fadeIn(…) + expandVertically(…), exit = …)`.
- `SongListItem.kt:246-257`, the details line: two `SongCardNote`s and `AnimatedVisibility(visible = note != null || tempoNote != null, …) { SongCardGap() }`.
- `SongListItem.kt:346-364`, each `SongCardNote`: `= AnimatedContent(targetState = note, …)` holding another
  `AnimatedVisibility(visible = hasGapBefore, …)`.
- `CenteredSongCardContent.kt:81-84`, on every card, cover or none:
  `val coverSize by animateDpAsState(targetValue = if (isThreeLine) … else …, animationSpec = searchTravelSpec(…))`,
  read only inside `if (coverArtUrl != null)` (`:106-111`).
- `SetlistAssignmentsButton.kt:68-101`, the star on every Songs card: an `Animatable`, a `LaunchedEffect`, an
  `animateFloatAsState`, a layer for the turn and one per icon, and two `painterResource` calls, each a
  `rememberVectorPainter` of its own per card.
- `SongLabels.kt:65-66`: two more `painterResource` per card with labels, and `sortedLanguages` worked out again on
  every composition (`:63`, not remembered).
- `SetlistSongRow.kt:298-302`: `AnimatedVisibility(visible = hasActions, …)` per setlist row, with the drag handle's
  `AnimatedVisibility(visibleState = handleVisibility, …)` inside.

## Fix

**1. Two gated wrappers** in `components/ChangeAnimations.kt`, each with KDoc saying why:

- `AnimatedContentOnChange(targetState, modifier, transitionSpec, contentAlignment, content)`: composes `content(targetState)`
  plainly (in a `Box` with the same `contentAlignment`, so the layout is the one `AnimatedContent` gives at rest) until
  `targetState` first differs from the value it was composed with; from then on, for as long as it is composed,
  `AnimatedContent` driven by a `MutableTransitionState` that starts at the previous value, so the very first change
  already animates.
- `AnimatedVisibilityOnChange(visible, enter, exit, content)`: `if (visible) content()` until `visible` first changes,
  then `AnimatedVisibility(visibleState = remember { MutableTransitionState(previous) }.apply { targetState = visible })`.

The "has it changed" record is `remember { ChangeGate(initial) }` updated in composition; a value that comes back is
still "changed" (the wrapper never goes back to plain, so a running animation is never cut off). This keeps the rule
that every user-caused change is animated: the first change is the one that arms the animation, in the same frame.

**The arming frame must not compose the resting content afresh.** `SongRowActions` holds the card's `OverflowMenu`,
and the selection mode is started from that very menu ("Select multiple" closes it and sets `isSelecting` in one tap):
composed again inside the transition, `DropdownMenu`'s own `MutableTransitionState` restarts at `false` and the menu
vanishes instead of fading out, and the overflow button's ripple is cut too. So the plain branch composes the content
through `val resting = remember { movableContentOf { value: T -> currentContent(value) } }` (`currentContent` from
`rememberUpdatedState(content)`, or the movable would keep the first lambda's captures), and once armed,
`AnimatedContent`'s content lambda calls `resting(value)` for the resting value and `currentContent(value)` for any
other: the resting composition, its menu's exit, ripple, and focus included, moves into the transition's slot in the
same recomposition. `AnimatedVisibilityOnChange` does the same for its content. Then no call site needs checking for
state of its own.

**2. Apply them** at every site listed above except the star. `SongCardNote`'s outer `AnimatedContent` becomes
`AnimatedContentOnChange`, its inner gap `AnimatedVisibilityOnChange`; the artist line, the duration's gap,
`SongRowActions`, and `SetlistSongRow`'s actions likewise. The drag handle's `AnimatedVisibility(visibleState = handleVisibility)`
(one `MutableTransitionState` per row, remembered above the reorder branch, `SetlistSongRow.kt:110`): compose it only
while `handleVisibility.currentState || handleVisibility.targetState` (nothing to show at rest otherwise); the state is
already there when the mode starts, so the grip still fades and expands in.

**3. The cover size**: move `animateDpAsState` into a `SongCardCover` composable composed only where `coverArtUrl != null`
(same file, or `SongCardCover.kt`), so a card without a cover has no animation; a card with one keeps it.

**4. The star**: until `isInSetlist` first changes, draw the one icon it rests on with no layer
(`Icon(painter = if (isInSetlist) filled else outline, contentDescription = …)`); on the first change, switch to today's
animated pair, whose `rotation` starts at 0 and turns from there, so the first turn looks as every later one does.
The pair must start from the value the star rested on, not the new one: composed fresh on the arming frame, today's
`remember { mutableStateOf(isInSetlist) }` would already hold the new value (no turn) and `animateFloatAsState` would
start at its new target (no fade). So `rotatedFor` stays remembered above the gate (it is the record the gate compares
with: armed once `isInSetlist != rotatedFor`), and the fade becomes an `Animatable(if (rotatedFor) 1f else 0f)` created
in the armed branch, animated in the same `LaunchedEffect` as the turn (same tween and delay as today) before
`rotatedFor` is updated.
Options: (a) this gate inside `SetlistAssignmentsButton`; (b) one `Animatable` for the turn read in a single draw
modifier that draws both painters with the cross-fade alpha computed from the rotation's progress (no second layer, no
`animateFloatAsState`). Recommended: (a), smaller and the same pixels; (b) only if the benchmark still shows the star.

**5. Painters shared by the list**: `SongList` (and the setlists' list) already hands `selectSongsIcon: Painter` to every
row for this reason. Do the same for the star's two icons and the label pills' two (`ic_label`, `ic_language`): one
painter each per list, passed down (a small `SongCardPainters` holder, `@Immutable`, remembered in the list), so a card
no longer builds four `VectorPainter`s. `SetlistAssignmentsButton` has a second caller, song details' header
(`SongDetailsActions.kt:166`), which keeps its own `painterResource` (the painters a nullable parameter, or the holder
built there); setlist cards draw no labels (`shouldShowLabels = false`), so only `SongList` builds the label painters. Remember `sortedLanguages` in `SongLabels` on `languages` and the app language.

**6. Rule** in `components/CLAUDE.md`, next to the other list rules: in a card of a list, an animation that only plays
when a value changes is composed once it first changes (`ChangeAnimations.kt`), and icons are painted with painters the
list shares.

**Decision for the owner (not part of this plan's change):** keep the covered list composed under song details, so a
pop composes nothing. Navigation 3 composes only the scene's entries and drops an entry's remembered state when it
leaves (`ui/navigation/CLAUDE.md`: "Navigation 3 drops an entry's remembered state and tab selection rebuilds the
stack", which is why scroll lives in the view model). Ways: a `SceneStrategy` whose song details scene is an
`OverlayScene` with the list as its overlaid entry (Navigation 3 keeps drawing those), or the two list screens composed
outside `NavDisplay`. Cost: a hidden list keeps recomposing on library and preference changes and keeps its memory, and
the card transition specs (`navigationTransition`) assume the covered screen leaves. With items 1–5 and plan 07 the pop
should already be far cheaper; measure before deciding.

## Tests

The UI tests that see cards must pass unchanged: `screens/songs/SongCardFitTest.kt`, `SongListAccessibilityTest.kt`,
`SongFilterSearchTest.kt`, `components/tabChrome/TabSwapNewButtonTest.kt`, `spreadTheWord/SpreadTheWordTest.kt`, and
`desktopTest`. If the gate's bookkeeping is a plain class (`ChangeGate`), a `commonTest` for it:
`` `a value that changes and comes back stays changed` ``. Add one UI test in `screens/songs`:
`` `starting the selection mode fades every card's star and menu into its box` `` (start the mode from a card's menu,
take a frame mid-animation with the clock paused, and see both the box and the menu button present; at the end only
the box), which fails if the gate swallows the first change; in the same frame, the card's overflow menu, closed by its
"Select multiple" entry, is still in the tree (fading out, not gone), which fails if the arming frame composes the
menu afresh. And `` `a song's first filing turns and fills its star` `` (tap a card's star, tick a setlist in the
sheet, close it; mid-animation the star is rotated and both icons drawn), which fails if the pair starts from the new
value.

**Measure:** plan 01's `pop:*`, `tab:songs>setlists`, `tab:setlists>songs`: the warm trigger frame's compose and layout
parts and its allocated MB should all drop (the trace's `AnimatedVisibility` / `Transition.animateTo` counts toward the
number of cards whose values actually changed, i.e. near zero); `push:*` unchanged; `sheet:open` slightly lower (it
recomposes the cards behind it, see plan 11).

## Manual check

None beyond the tests: the animations are drawn the same on every platform.
