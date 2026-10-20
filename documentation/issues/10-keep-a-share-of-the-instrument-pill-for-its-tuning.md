<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep a share of a narrow instrument pill for its tuning, cutting the instrument's name short once the tuning's is

**Kind:** bug (regression from 557e58f52)  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the UI test reads the unmerged tree (the merged node is the half's padded clickable, wider than 0 dp on today's code, so the test as written passed without the fix) and uses the measured 100% case (English, 12-string guitar), since at 200% the Tuner's pill is estimated at ~60 dp, under the ~57 dp of fixed padding its two halves take, so no share can give the tuning's text room there (now said under the fix); the measure policy measures both halves with `minWidth = 0` and constrains its own size, since `Surface` hands its min constraints to its content; noted that the default intrinsics answer `GroupedRows`/`FlowRow` correctly through the unbounded branch.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPill.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPillWidths.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPillWidthsTest.kt` (new),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerInstrumentPillTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/CLAUDE.md`

## Problem

`InstrumentPill.kt` (at 396acf923) lays its halves out in a `Row`: the instrument half unweighted (`:100`), then the
tuning half in

```kotlin
AnimatedVisibility(
    modifier = Modifier.weight(1f, fill = false),
    visibleState = tuningState,
    ...
```

A `Row` measures unweighted children first, so the instrument's name takes all the width it wants and the tuning half
gets what is left. In a tool's line (`ToolInstrumentSetting.kt:62-63`, `InstrumentPill(modifier = Modifier.weight(1f,
fill = false).padding(end = NEW_ITEM_BUTTON_GAP), …)` followed by `trailing?.invoke()`), the trailing controls are
measured before the pill, which shrinks it further. Once what is left is under the tuning half's own padding and the
divider (~29 dp), its text gets 0 dp: it is not cut short but gone, and a screen reader skips a node with no size.
Measured on the Tuner tab of a 360 dp phone (bundled Inter Medium, pill at most ~151 dp):

- English, 100%, "12-string guitar | Standard": ~21 dp left for "Standard", so the text is gone.
- Hungarian "5 húros basszusgitár", 100%: 0 dp.
- Any instrument at 200% text size: 0 dp.

The same applies on a song's page and the onboarding wherever the pill is narrow. `ui/instrument/CLAUDE.md` promises
"one line (the tuning name is cut short first)", and `InstrumentPill`'s KDoc says "the tuning … is cut short where the
line is too narrow". Neither allows it to disappear.

## Fix

Two options:

- **A (recommended): a minimum share for the tuning half, inside `InstrumentPill`.** Wherever both names fit, nothing
  changes. Where they don't, the tuning's name is cut short first, but only down to a fixed share of the pill (40%).
  From there the instrument's name is cut instead. This fixes every caller whose pill is wider than its halves' fixed padding (song page, onboarding, the tools at
  ordinary text sizes; see below for the Tuner at large text), moves nothing, and keeps the documented order.
- B: in `ToolInstrumentSetting`, move the pill onto its own line under the trailing controls when both can't fit,
  animated (`components/PlacementAnimation.kt`). This only helps the Tuner (the one caller with `trailing`), leaves the
  song page and onboarding broken at 200%, and moves the Tuner's reference pitch out of line with `TunerModeLine` under
  it. Not recommended.

Option A, step by step:

1. New `InstrumentPillWidths.kt` with the pure decision (KDoc on it; the code-style skill wants decisions as pure
   functions with a test):

   ```kotlin
   /**
    * The widest the tuning half of an [InstrumentPill] may be, out of [available] for both halves, where the instrument
    * half would be [instrumentWidth] wide uncut: whatever the instrument leaves, but never less than [TUNING_SHARE] of
    * the pill, so that a narrow pill cuts the tuning's name short first and then the instrument's rather than leaving
    * the tuning no room at all.
    */
   internal fun tuningHalfMaxWidth(available: Int, instrumentWidth: Int) =
       maxOf(available - instrumentWidth, (available * TUNING_SHARE).roundToInt()).coerceIn(0, available)

   internal const val TUNING_SHARE = 0.4f
   ```

2. In `InstrumentPill`, replace the inner `Row(verticalAlignment = Alignment.CenterVertically)` holding the two halves
   with a `Layout` (the same children, in the same order; drop the tuning's `Modifier.weight`). In its measure policy:
   - `instrument = measurables[0]`, `tuning = measurables.getOrNull(1)`. `AnimatedVisibility` emits nothing once it
     has fully left, so there may be one measurable.
   - Without a tuning measurable, measure the instrument with the incoming constraints (but `minWidth = 0`, below).
   - Otherwise take `instrumentWidth = instrument.maxIntrinsicWidth(constraints.maxHeight)` (Box, padding,
     `AnimatedContent`, and `Text` all report intrinsics) and measure the tuning first with `maxWidth =
     tuningHalfMaxWidth(constraints.maxWidth, instrumentWidth)` and `minWidth = 0`. Then measure the instrument with
     `maxWidth = constraints.maxWidth - tuningPlaceable.width`.
   - Measure both halves with `minWidth = 0` and the incoming height constraints. `Surface` passes its own min
     constraints on to its content (`propagateMinConstraints`), so a caller's min width would otherwise reach the
     instrument with a `maxWidth` below it and throw. The layout is `constraints.constrainWidth(sum of the widths)` by
     `constraints.constrainHeight(the larger height)`; place both centered vertically, the instrument first
     (`placeRelative`, so RTL mirrors as the `Row` did).
   - Where the width is unbounded (`!constraints.hasBoundedWidth`), measure both halves at their natural width, as
     the `Row` did. This is also what answers a parent's intrinsic widths: the song page's `GroupedRows` and the
     pill's `FlowRow` ask for them, and the default intrinsics of a `Layout` run the policy with unbounded width (the
     sum of the halves' intrinsics, as the `Row` gave). Intrinsic heights never reach it: the pill's `height` answers
     them. No intrinsic overrides are needed.
   - It has no state of its own and reads none, so the lookahead and the approach pass (an `AnimatedVisibility` under a
     lookahead reports its target width first) each get a consistent answer from the same policy.
   Measuring the tuning against its animated width keeps the enter and exit smooth: while it expands, the instrument
   gives up width only as the tuning actually takes it.
3. Update the KDoc of `InstrumentPill` ("…keeps to one line: the tuning … is cut short first, down to a share of the
   pill, and then the instrument's name…") and the comment above the old `Row` about what animates the width (still
   true; keep it with the `Layout`).
4. `ui/instrument/CLAUDE.md`: "one line (the tuning name is cut short first)" → "one line (the tuning's name is cut
   short first, down to 40% of the pill, then the instrument's: `tuningHalfMaxWidth`)".

What this does not fix: a pill narrower than its two halves' fixed room (12 + 4 + 12 dp around the instrument's name,
12 + 12 + 4 dp and the divider around the tuning's, ~57 dp together) has no text room to share. Estimated from
`ReferencePitchControls` (a `songControlHeight` stepper whose buttons and value grow with the text, and the 48 dp play
button), the Tuner's pill at 200% text is about 60 dp on a 360 dp phone, so its tuning stays at 0 dp there, as the
Problem's third bullet says. The song page (the pill's `FlowRow` puts the tuner under it), the onboarding, and the
Chords and Scales tabs (no `trailing`) give the pill its whole line and are fixed. Fixing the Tuner at large text
needs the trailing controls to go under the pill (option B, animated through `components/PlacementAnimation.kt`), a
separate decision for the user; say so in the report rather than widening this plan.

Keep `InstrumentPill.kt` under 500 lines and the Composable under 120. If the measure policy pushes `InstrumentPill`
near that, make it a `private fun instrumentPillMeasurePolicy()` in the same file.

## Tests

- `InstrumentPillWidthsTest` (commonTest): both fit → the tuning may take everything the instrument leaves (available
  200, instrument 120 → 80); the instrument too wide → the share holds (available 150, instrument 140 → 60); the
  instrument wider than the pill → the share (available 100, instrument 300 → 40); never more than `available` and
  never negative.
- UI test (`tools/screenshots`, package `…ui.screens.tuner`, `TunerInstrumentPillTest`): `runAppTest` in
  `TestWindow.PHONE`, English, 100% text, then `openTuner()`; tap the instrument half (`hasText(string(
  Res.string.instrument_guitar)) and hasClickAction()`), pick `string(Res.string.instrument_guitar_strings, 12)` in
  the sheet, and wait for idle. Find the tuning's text in the **unmerged** tree,
  `onNodeWithText(string(Res.string.instrument_tuning_standard), useUnmergedTree = true)`, and assert
  `fetchSemanticsNode().size.width > 0`. The merged node is the half's clickable `Box`, at least its 28 dp of padding
  wide whatever its text gets, so asserting on it would pass on today's code. With the unmerged `Text` it fails on
  today's code (~21 dp for the half, 0 dp for the text) and passes with the fix (~60 dp for the half).
