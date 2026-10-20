<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Make sure a touch 48 dp tall reaches each half of the 32 dp instrument pill, and say so

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** Android and iOS (touch), the touch web
**Challenged:** amended — recorded what Compose's source (ui 1.12.1, `NodeCoordinator.hitTest`) says, so step 2 is the expected outcome; step 3's outset now comes from `LocalViewConfiguration`'s `minimumTouchTargetSize` (what touch uses), not `LocalMinimumInteractiveComponentSize`; tied it to plan 10's `Layout`.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPill.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerInstrumentPillTest.kt`
(new, or the file plan 10 adds)

## Problem

Since 557e58f52, the instrument and tuning pickers on the Tuner, Chords, and Scales tabs and the onboarding are the
halves of one `InstrumentPill`. These used to be 40 dp `PickerChip`s (`PickerChip.kt`: `height: Dp = 40.dp`). The pill
is `modifier.height(songControlHeight(style))` (`InstrumentPill.kt:81`, at 396acf923), 32 dp with `titleSmall`, and
each half is a plain `.clickable(…)` filling it (`:158`). On a song's page 32 dp is the design (it matches the
`Stepper` under it), but the tool screens and onboarding drew a larger target before.

The drawn size may not be the touch size. `Stepper.kt`'s KDoc relies on "Compose extends a small target's touch area
to the minimum touch target size; only their drawn size shrinks". Compose's hit testing extends a pointer-input node
smaller than `ViewConfiguration.minimumTouchTargetSize` (48 dp) on a touch event, even past a clip, as long as nothing
else is hit directly. If that holds for the pill's halves inside the `Surface` (which clips to `CircleShape` and adds a
pointer-input blocker of its own), they already take a 48 dp tall touch, and only the documentation is missing.

## Fix

What Compose does (read from `ui-desktop-1.12.1-sources.jar`, `NodeCoordinator.hitTest` / `hitNear` /
`distanceInMinimumTouchTarget`): for a `PointerType.Touch` event, any pointer-input node (a plain `.clickable`
included) whose coordinator is smaller than `ViewConfiguration.minimumTouchTargetSize` in either direction is hit
"near" up to half the shortfall beyond its edge, and that holds past a clipping layer: a miss of the clip calls
`hitNear` on the clipped node and carries on into its children. It has nothing to do with Material's
`LocalMinimumInteractiveComponentSize`, which only sizes Material components' layout. The default
`minimumTouchTargetSize` is 48 × 48 dp in common code; the desktop, iOS (`ComposeSceneMediator.ios.kt`), and web
(`ComposeWindowInternal.web.kt`) configurations override only the touch slop (and the web the fling velocity), and
Android's keeps the default. The `Surface`'s own `pointerInput` blocker is an ancestor on the same path, not a
competitor. A near hit loses to any direct hit and, between two near hits (the pill and the stepper under it on a
song's page), the nearer edge wins, so the touch areas never overlap. Compose UI tests on the desktop
(`InputDispatcher.skiko.kt`) send `PointerType.Touch`, so the test below exercises the same path. Expect step 2.

**Decision:** expand the halves' touch area to 48 dp (without moving anything), or accept 32 dp and document it.
Recommended: **48 dp everywhere the pill stands**, reached the way the Stepper reaches it: by Compose's own extension,
proven by a test.

1. Write the UI test below first, on today's code.
2. If it passes, Compose already extends the halves. The change is the test plus the documentation: add a sentence
   to `InstrumentPillHalf`'s KDoc ("On a touch screen a half still takes a touch 48 dp tall, since Compose extends a
   small target's touch area to the minimum touch target size, as the `Stepper`'s buttons do; only its drawn height
   is the song's control height."), and to `ui/instrument/CLAUDE.md`'s pill bullet: "drawn `songControlHeight` tall,
   touched 48 dp tall (Compose's minimum touch target)".
3. If it fails (the `Surface`'s clip or its pointer blocker stops the near hit), expand the hit area without moving
   anything, the way `SongDetailsTitle.kt`'s `titleTouchTarget` does it:
   - Draw the pill as a `Box` with `.background(color, CircleShape)` instead of a clipping `Surface` (keep the alpha
     and the height), so nothing clips the halves' presses.
   - Give each half a `Modifier.layout` outset before its `clickable`: measure with `constraints.offset(vertical =
     outset * 2)`, report the original height, and place the child at `-outset`. `outset = ((minimumTouchTargetSize.height - pillHeight) /
     2).coerceAtLeast(0.dp)`, the size read from `LocalViewConfiguration.current`, which is what a touch is measured
     against (not `LocalMinimumInteractiveComponentSize`). Then add `.padding(vertical = outset)`
     inside, so the text stays centred.
   - Clip each half's ripple to the pill's shape: the start half to a shape rounded on its start, the end half on its
     end, the middle edges square. That way a press still lights only the pill.
   - On a song's page the pill sits right above the transposition stepper. Check the outset doesn't take presses
     meant for it: on the song page, outset only up to half the gap to the next control, or skip it there and rely on
     the extension, which loses to a direct hit on the stepper anyway.
   Document it as in step 2, naming the outset. The halves are the children of plan 10's `Layout` by then; an outset
   half reports its original height, so that measure policy is unaffected.

## Tests

UI test (`tools/screenshots`, package `…ui.screens.tuner`, `runAppTest` in `TestWindow.PHONE`): open the Tuner
(`openTuner()`), find the instrument half by its text (`instrumentLabel` of the default instrument, read from the
resources) and press with `performTouchInput { click(Offset(centerX, top - 6.dp.toPx())) }`, 6 dp above the drawn
pill. Assert the instrument sheet opened (its title string is displayed). Do the same 6 dp below the tuning half
("Standard") and assert the tuning sheet opened. On today's code, the result decides between step 2 and step 3.
