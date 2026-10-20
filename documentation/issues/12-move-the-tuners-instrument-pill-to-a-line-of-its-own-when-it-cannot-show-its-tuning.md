<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give the Tuner's instrument pill its line to itself, with the reference pitch sliding under it, where the two cannot share one with the tuning shown

**Kind:** bug (large text)  ·  **Severity:** low–medium  ·  **Platforms:** all
**Decided:** option B of plan 10, by the user on 2026-10-20. Runs after plans 10 and 11 in lane U, on 10's `Layout`
inside `InstrumentPill` and its `tuningHalfMaxWidth` / `TUNING_SHARE` in `InstrumentPillWidths.kt`.
**Challenged:** amended — the measure policy now overrides all four intrinsics, so a parent's intrinsic query (which
runs a default policy at unbounded width) neither creates the `Animatable` at the wrong target, launches a slide, nor
calls `layout(Constraints.Infinity, …)`, which throws; a real unbounded measure lays out at the pill's natural width
plus the gap and the controls'; `pillMaxWidth` is kept at 0 or more (negative `room` mid-slide on a window narrower
than the controls threw in `Constraints`) and the height is `constrainHeight`ed; the play circle is a fixed 48 dp, not
growing with the text; the `ui/instrument/CLAUDE.md` edit keeps plan 11's touch sentence. The rest held: no
measure-time feedback (the stepper's room is reserved from its widest values, so typing never changes its width),
lookahead, first frame, RTL, short window, and process death all check out.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/ToolInstrumentSetting.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/ToolInstrumentLines.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPillWidths.kt` (plan 10's),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPill.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/ToolInstrumentLinesTest.kt` (new),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerInstrumentPillTest.kt` (plan 10's),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerScreen.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/ReferencePitchControls.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

**Drop this plan if** plan 10's `TunerInstrumentPillTest` case below at 200% (step "Tests", first UI test, without
its line assertions) already passes on the code plans 10 and 11 leave. That would mean the Tuner's pill is wider at
200% than estimated here. Say so in the report.

## Problem

On the Tuner tab the instrument pill shares a line with the reference pitch controls (`ToolInstrumentSetting.kt:48-71`
at 0876be233):

```kotlin
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .then(
            if (trailing == null) {
                Modifier.padding(top = (LIST_APP_BAR_HEIGHT - songControlHeight(MaterialTheme.typography.titleSmall)) / 2)
            } else {
                Modifier.height(LIST_APP_BAR_HEIGHT)
            },
        )
        .padding(start = 16.dp, end = NEW_ITEM_BUTTON_END_INSET),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    InstrumentPill(
        modifier = Modifier.weight(1f, fill = false).padding(end = NEW_ITEM_BUTTON_GAP),
        ...
    )
    trailing?.invoke()
}
```

The `Row` measures `trailing` (`ReferencePitchControls`: a `songControlHeight` stepper whose buttons and value grow
with the text, and the fixed 48 dp play circle) first, and the pill gets what is left. Plan 10 makes the pill keep 40% of whatever it gets
for the tuning half. On a 360 dp phone the line is 336 dp (16 dp start, `NEW_ITEM_BUTTON_END_INSET` 8 dp end). Plan
10 estimates the pill there at ~151 dp at 100% text and ~60 dp at 200%. The tuning half has 29 dp of fixed room (12 dp
either side of its name, the 4 dp edge, the 1 dp divider). So at 200% its 40% share is ~24 dp, the name gets 0 dp,
and a screen reader skips it. Plan 10 says no share can fix that, and the user chose to move the pill to a line of its
own there (option B).

Which callers need it:

- **The Tuner**: yes. It is the only `ToolInstrumentSetting` caller with `trailing` (`TunerScreen.kt:218-236`,
  `TunerInstrumentRow`).
- **Chords** (`ChordLibraryControls.kt:38`) and **Scales** (`ScaleLibraryControls.kt:35`): no. They pass no
  `trailing`, so the pill already has the whole 336 dp line and plan 10's share gives the tuning room at 200%. Keep
  their branch exactly as it is.
- **Onboarding** (`InstrumentStep.kt:72`, `InstrumentPill` directly) has no trailing controls.
- **The song page** (`SongMetadataSection.kt`, `InstrumentPillRow`, a `FlowRow`) already sends the tuner whole under
  the pill whenever the pill does not fit beside it. No change.

## Fix

### Where the controls go: under the pill

When the line is too narrow, the pill keeps the top line, at the same spot as today and as on the Chords and Scales
tabs. `ReferencePitchControls` slides down to a second line, still end-aligned, directly above `TunerModeLine`.
This beats the other arrangement (controls on top, pill under them) for two reasons:

- The Strings / Chromatic toggle (`TunerModeToggle`, in `TunerModeLine` 8 dp under the row, its end at
  `NEW_ITEM_BUTTON_END_INSET`) stays right under the play circle, and its end stays at the circle's end, as
  `ui/tuner/CLAUDE.md` requires. With the pill in between, the toggle would hang under the pill instead.
- The pill stands where it stands on every tool tab (`ToolInstrumentSetting`'s KDoc: "the pill stands at one spot
  whichever tool is picked").

The cost is that the reference pitch leaves the New button's corner in that case.

Putting the pill on `TunerModeLine`'s line, beside the toggle, was rejected. The toggle's labels at 200% leave the pill
about as little room as the stepper does, and that line is 32 dp tall (`TUNER_MODE_TOGGLE_HEIGHT`), under the pill's
52 dp at 200%.

**Height (pinned-height budget).** At 200% the row grows from 56 dp to ~116 dp. The second line is the 8 dp gap, the
52 dp stepper, and line 1's 2 dp bottom slack. All of it is pinned (the Tuner pins the row, the mode line, and the
display). It is paid only where the line wraps (large text on a narrow window, estimated from ~120% on a 360 dp
phone). It is also the price of the tuning being visible at all. The strings scroll under it in the `SettingsPage`, as
they already do.

The wrap depends on width only, never on height. Otherwise opening the keyboard to type a reference pitch (the
stepper's entry, which would sit on line 2 then) would bring the stepper back up to line 1 while the player types into
it. In a short window with the keyboard up, the field typed into stays pinned on line 2, which the budget rule allows
("a field typed into throughout").

### 1. The decision and the geometry, pure (`ToolInstrumentLines.kt`, new, in `ui/instrument`)

The code-style skill requires decisions to be pure functions next to their Composable, with a commonTest. Pixels
throughout. KDoc on each declaration.

```kotlin
/**
 * Whether a tool's instrument pill, [pillWidth] wide uncut, stands beside its trailing controls ([trailingWidth],
 * parted by [gap]) on a line [lineWidth] wide, rather than on a line of its own over them: where it fits whole, or
 * where cutting it to the room left still shows [minNameWidth] of the name that is cut first. That is the tuning's,
 * at the share `tuningHalfMaxWidth` keeps for it (the least it is given, whatever the instrument's name), past its
 * [nameRoom] of padding and divider, or, with no tuning half ([hasTuning] false), the instrument's past its own.
 * A share showing the tuning leaves the instrument's name more than [minNameWidth] as well (60% of the room against
 * 40%), so the tuning is the one to ask about.
 */
internal fun pillSharesLine(
    lineWidth: Int,
    trailingWidth: Int,
    gap: Int,
    pillWidth: Int,
    hasTuning: Boolean,
    nameRoom: Int,
    minNameWidth: Int,
): Boolean {
    val room = lineWidth - trailingWidth - gap
    if (room >= pillWidth) return true
    val shownName = if (hasTuning) (room * TUNING_SHARE).roundToInt() - nameRoom else room - nameRoom
    return shownName >= minNameWidth
}

/** Where a tool's instrument line puts its pill and its trailing controls, [progress] of the way to the pill's own line. */
internal data class ToolLinePlaces(val pillMaxWidth: Int, val trailingY: Int, val height: Int)

/**
 * The line at [progress] (0 beside, 1 under) of the way from the trailing controls standing beside the pill, centred
 * in the [lineHeight] line, to standing under it, [gap] below the pill and end-aligned as before: the controls travel
 * straight down, the line grows with them by exactly as much, so that what stands under the line (the Tuner's mode
 * toggle under the play circle) keeps its distance to them throughout, and the pill widens into the room they leave
 * only once they have cleared it, so that it never reaches under them.
 */
internal fun toolLinePlaces(
    progress: Float,
    lineWidth: Int,
    lineHeight: Int,
    gap: Int,
    pillHeight: Int,
    trailingWidth: Int,
    trailingHeight: Int,
): ToolLinePlaces
```

`toolLinePlaces`, with `p = progress.coerceIn(0f, 1f)` (the expressive spring overshoots, as `SectionColumns` in
`SettingsPage.kt` notes):

- `pillTop = (lineHeight - pillHeight) / 2` and `pillBottom = pillTop + pillHeight`. The pill never moves.
- `besideY = (lineHeight - trailingHeight) / 2` and `underY = pillBottom + gap`. `trailingY = lerp(besideY, underY, p)`.
- `slack = lineHeight - besideY - trailingHeight`, the controls' distance to the line's bottom beside the pill.
  `height = maxOf(lineHeight, trailingY + trailingHeight + slack)`. Both ends are linear in `p`, so the distance from
  the controls' bottom to the line's bottom is the same at every progress.
- `room = lineWidth - trailingWidth - gap`. `cleared = ((trailingY - pillBottom).toFloat() / gap).coerceIn(0f, 1f)`.
  `pillMaxWidth = lerp(room, lineWidth, cleared).coerceAtLeast(0)`. The pill grows over the last `gap` of the travel
  and shrinks over the first `gap` on the way back. The floor matters: a slide that starts beside the pill on a window
  narrower than the controls and the gap (a desktop edge dragged fast) has a negative `room`, which `Constraints` throws
  on.

Also put these in `ToolInstrumentLines.kt`:

```kotlin
/** The least of a cut name a tool's line keeps beside its trailing controls, in ems of the pill's text: about a letter and the ellipsis. */
internal const val MIN_SHOWN_NAME_EMS = 1.5f
```

Why 1.5 em: at 100% the tuning's 40% share of ~151 dp leaves its name ~31 dp, over the 21 dp bar, so the default
size keeps one line. At 200% it leaves less than nothing. The bar asks only that the tuning is in sight, not in full,
since each wrap costs pinned height.

In `InstrumentPillWidths.kt` (plan 10's), make the fixed room the decision needs internal. Move
`INSTRUMENT_PILL_HALF_PADDING` and `INSTRUMENT_PILL_EDGE_PADDING` there from `InstrumentPill.kt` (they become
`internal`; `InstrumentPill.kt` keeps using them) and add:

```kotlin
/** What the tuning half of an [InstrumentPill] takes besides its name: its padding, the pill's edge, and the divider. */
internal val INSTRUMENT_PILL_TUNING_ROOM = INSTRUMENT_PILL_HALF_PADDING * 2 + INSTRUMENT_PILL_EDGE_PADDING + DividerDefaults.Thickness

/** What the instrument half of an [InstrumentPill] takes besides its name where it is the only half. */
internal val INSTRUMENT_PILL_LONE_ROOM = (INSTRUMENT_PILL_HALF_PADDING + INSTRUMENT_PILL_EDGE_PADDING) * 2
```

`StepperDivider` is a default `VerticalDivider`, so `DividerDefaults.Thickness` (1 dp) is its width. Also move
`InstrumentPill`'s `val shownTuning = tuning?.takeIf { instrument.tunings.size > 1 }` into an `internal fun
shownTuning(instrument: UserPreferences.Instrument, tuning: InstrumentTuning?)` in `InstrumentPill.kt`, so
`ToolInstrumentSetting` asks the same question rather than repeating it.

### 2. The line (`ToolInstrumentSetting.kt`)

- Keep the `trailing == null` branch exactly as today: the `Row`, its top padding, and `Modifier.weight(1f, fill =
  false)` on the pill. Chords and Scales are unchanged.
- With `trailing`, drop `Modifier.height(LIST_APP_BAR_HEIGHT)` and the `Row`. Keep `fillMaxWidth()` and the
  `padding(start = 16.dp, end = NEW_ITEM_BUTTON_END_INSET)`, and lay out a `private fun ToolInstrumentLine` instead.
  It is a `Layout` of two children: the pill (`modifier = Modifier`, since the layout keeps the gap) and `trailing()`.
  The two branches can share the `InstrumentPill(...)` call through a local `pill: @Composable (Modifier) -> Unit`.
- Remembered by `ToolInstrumentLine`: `val motion = remember { ToolLineMotion() }`, a private plain class holding
  `var progress: Animatable<Float, AnimationVector1D>? = null` (no `State`, as `PlacementAnimationNode` keeps its
  `Animatable`). Also `rememberCoroutineScope()`, `MaterialTheme.motionScheme.defaultSpatialSpec<Float>()` (the spec
  `SectionColumns` slides on), the `titleSmall` font size, and `hasTuning = shownTuning(instrument, tuning) != null`.
- The measure policy:
  1. `lineWidth = constraints.maxWidth` and `lineHeight = LIST_APP_BAR_HEIGHT.roundToPx()`. Measure the trailing child
     with `Constraints(maxWidth = lineWidth, maxHeight = lineHeight)`, the same in both arrangements, as the `Row`
     measured it.
  2. `pillWidth = measurables[0].maxIntrinsicWidth(lineHeight)`. Plan 10's `Layout` answers this through its unbounded
     branch: the halves' natural widths, including the tuning half's animated width.
  3. `shares = !constraints.hasBoundedWidth || pillSharesLine(lineWidth, trailing.width, NEW_ITEM_BUTTON_GAP.roundToPx(),
     pillWidth, hasTuning, (if (hasTuning) INSTRUMENT_PILL_TUNING_ROOM else INSTRUMENT_PILL_LONE_ROOM).roundToPx(),
     (fontSize.toPx() * MIN_SHOWN_NAME_EMS).roundToInt())` (`fontSize` read from `titleSmall` while composing).
     `target = if (shares) 0f else 1f`.
  4. `progress`: in the lookahead pass (`isLookingAhead`), `target`, so that it is laid out where it is headed, as
     `PlacementAnimation.kt` does. Otherwise `val animatable = motion.progress ?: Animatable(target).also {
     motion.progress = it }`. If `animatable.targetValue != target`, run `scope.launch { animatable.animateTo(target,
     spec) }`, then read `animatable.value`. The first measure creates it at its target. That makes the first frame
     right with nothing animating: no slide on opening the tab, or on coming back to it, which composes a new
     `ToolInstrumentLine`. Two measures before the launched coroutine runs both launch, and the second `animateTo`
     cancels the first. That is harmless.
  5. `val places = toolLinePlaces(progress, lineWidth, lineHeight, gap, pillHeight = songControlHeight(titleSmall)
     in px, trailing.width, trailing.height)`. Measure the pill with `Constraints(maxWidth = places.pillMaxWidth,
     maxHeight = lineHeight)` (plan 10's policy handles `minWidth = 0` itself). Then `layout(lineWidth,
     constraints.constrainHeight(places.height))`, placing the pill at `(0, (lineHeight - pill.height) / 2)` and the
     trailing at `(lineWidth - trailing.width, places.trailingY)`, both with `placeRelative` so RTL mirrors as the
     `Row` did.
  6. A real measure at unbounded width (never reached under `fillMaxWidth()` in the Tuner's `Column`, but the policy
     must not throw on it) skips steps 3 to 5: it measures the pill unbounded too and lays out at `pill.width + gap +
     trailing.width` by `lineHeight`, both centred, as the `Row` did, and touches no `Animatable`.
- **Intrinsics.** Write the policy as a `MeasurePolicy` object overriding all four intrinsics, not a bare lambda. A
  bare `Layout`'s default intrinsics run its measure block at unbounded width, so a parent asking (an
  `IntrinsicSize`, a `FlowRow`, a future caller) would create the `Animatable` at that pass's target (0, sharing) and
  have the first real measure slide from there, launch a slide from an intrinsic pass, and call `layout` with
  `Constraints.Infinity`, which throws. The overrides answer as the line beside the pill and never touch the
  `Animatable`: widths `pill + gap + trailing` (max) and `trailing + gap` (min, the pill can be cut to nothing), from
  the children's own intrinsics; heights `maxOf(lineHeight, trailing's)` for both. Nothing asks them today; they keep
  the measure block the only place the motion is touched.
- **No measure-time feedback.** This is the 34th review's hang: a layout reading state that the same node writes each
  measure never settles. This layout reads three things: the pill's intrinsic width, the trailing child's measured
  width, and the `Animatable`'s `targetValue` and `value`. It writes none of them while measuring. The only writes are
  `animateTo`'s, from a coroutine, and only when the decision changes. The decision cannot depend on the progress: the
  trailing child is measured against the whole line in both arrangements, and the pill's intrinsic width does not
  depend on the width it is given. So a change settles in one measure, and the slide's frames only re-measure until it
  ends. Do not move the decision into a `State` set from the measure, and do not `snapTo` there.
- **What animates, and why it never jumps.** Everything follows the one `progress`:
  - A window resized across the threshold, a text size change, or a newly picked instrument whose names no longer fit
    (a user-caused change) each slide the controls down or up. The row's height grows or shrinks with them, carrying
    `TunerModeLine` and the display along.
  - The pill widens or narrows into the room the controls leave or need.
  - Within one arrangement, a dragged window edge is followed frame by frame. The controls' `x` is `lineWidth -
    trailing.width` in both arrangements, so they stay at the end, and nothing is sent travelling. For the same reason
    the controls are not given `Modifier.animatePlacement`: an end-aligned child of it would trail a dragged edge.
  - The tuning half fading in or out changes `pillWidth` over its frames. The line may change arrangement during that
    fade, and slides when it does.
- Update `ToolInstrumentSetting`'s KDoc. Replace "The pill gives up width before it reaches [trailing], cutting its
  tuning short." with "The pill gives up width before it reaches [trailing], cutting its tuning short, and where it
  could no longer show the tuning beside them it keeps the line to itself and [trailing] slides under it, still at the
  end, the line growing with it (`pillSharesLine`, `toolLinePlaces`)."

Keep `ToolInstrumentSetting.kt` under 500 lines and each function under 120. The `Layout` goes in its own
`private fun ToolInstrumentLine`.

### 3. Docs

- `TunerScreen.kt` KDoc: "with the reference pitch ([ReferencePitchControls]) on its line, in the corner the list
  screens' pill and New button stand in" → "…on its line, in the corner the list screens' pill and New button stand
  in, or under the pill, at the same end, where the line is too narrow for both (`ToolInstrumentSetting`)".
- `ReferencePitchControls.kt` KDoc: after "where they stand on the Tuner tab (see `ToolInstrumentSetting`)" add ",
  under the instrument where the line is too narrow for both".
- `ui/tuner/CLAUDE.md`, the Layout paragraph: after "the instrument pill gives up width (its tuning cut short) before
  it reaches them." add "Where it could then no longer show its tuning (large text on a phone), the pill keeps the line
  and they slide under it, still at the end, so the mode toggle stays under the play circle; the line grows with them
  and only on width, never on the keyboard (`ui/instrument/ToolInstrumentLines.kt`)." Plan 05 edits the Lifecycle
  section of the same file. Keep both.
- `ui/instrument/CLAUDE.md`, the pill bullet: after plan 10's "(…then the instrument's: `tuningHalfMaxWidth`)" add
  "; on a tool's line with trailing controls (the Tuner's reference pitch) the pill takes the line to itself where its
  share could not show the tuning (`pillSharesLine`)". Plan 11 adds its touch sentence to the same bullet; keep both.

## Tests

- `ToolInstrumentLinesTest` (commonTest, pixels at density 1):
  - `pillSharesLine`:
    - The pill fits whole, so the line is shared (line 336, trailing 177, gap 8, pill 140, tuning: true).
    - Cut, but its share still shows the tuning, so the line is shared (line 336, trailing 177, gap 8, pill 260, name
      room 29, min 21: room 151, share 60, name 31, so true).
    - The 200% case puts the pill on its own line (line 336, trailing 268, gap 8, pill 300, name room 29, min 42:
      false).
    - Exactly the minimum shares the line, one pixel less does not.
    - Without a tuning half, the instrument's name is what is asked (room 60, lone room 32, min 42: false; room 100:
      true).
    - An unbounded line (`Constraints.Infinity`) shares it.
  - `toolLinePlaces`:
    - At 0, the controls are centred in the line, the height is the line's, and the pill may take `room`.
    - At 1, they are `gap` under the pill, and the pill may take the whole line.
    - Out-of-range progress (−0.2, 1.2) gives the 0 and 1 answers.
    - Controls and gap wider than the line (line 200, trailing 268) give `pillMaxWidth` 0 at progress 0, never
      negative.
    - For every progress from 0 to 1 in steps of 0.05:
      - `height - trailingY - trailingHeight` equals the slack at 0 (±1 for rounding), so the toggle under the line
        keeps its distance.
      - `pillMaxWidth > room` only where `trailingY >= pillBottom`, so the pill never reaches under the controls.
      - `trailingY` and `pillMaxWidth` never decrease.
- UI tests, added to plan 10's `TunerInstrumentPillTest` (`tools/screenshots`, package `…ui.screens.tuner`), in
  `TestWindow.PHONE` (360 dp):
  - `at the largest text size the tuning is shown, with the reference pitch under the instrument`:
    - Set up with `runAppTest(appearance = TestAppearance(textScale = 2f))`, then `openTuner()` and `waitForIdle()`.
    - Find the tuning's text in the unmerged tree, `onNodeWithText(string(Res.string.instrument_tuning_standard),
      useUnmergedTree = true)`, and assert `fetchSemanticsNode().size.width > 0`. The merged node is the half's
      padded clickable, so it would pass on today's code (plan 10's lesson).
    - The stepper's increase button (`hasContentDescription(string(Res.string.tuner_reference_pitch_increase))`) has
      its top at or below the bottom of the instrument half (`hasText(string(Res.string.instrument_guitar)) and
      hasClickAction()`, `boundsInRoot`).
    - The play circle (`hasContentDescription(string(Res.string.tuner_play_reference, "A"))`, the default notation's
      A, as `ReferencePitchEntryTest` names "A4") ends within 1 dp of where the Chromatic segment
      (`hasText(string(Res.string.tuner_mode_chromatic)) and hasClickAction()`) ends, and that segment's top is
      below the circle's bottom. This proves the toggle is still under the circle.
    - It fails on plans 10 and 11 alone, where the text is 0 dp wide, and passes with this fix.
  - `at the default text size the reference pitch keeps the instrument's line`: the same window at 100%. The increase
    button's vertical centre lies inside the instrument half's bounds. This pins that the ordinary size pays no height,
    and fails if the threshold is set too high.
  - Run the class alone with a capped run first (`--tests '*TunerInstrumentPillTest*'`, the task's 20-minute
    `timeout` as the backstop). A measure that never settles shows up as a hang, not a failure (the 34th review).
- The first frame (no slide on opening the tab) is right by construction: the `Animatable` is created at the first
  measure's target. No test pins it, since the navigation helpers settle the clock after every tap. The slide itself
  is the user's to look at, as with every UI iteration.
