<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Draw the one navigation chrome (and the tools' button group) into a covered screen from a recorded layer, instead of composing a copy of it in that screen on every push and pop

**Challenged:** amended — the recording chrome and button group are placed out of the window (not behind a press-eating `pointerInput`), kept out of focus and semantics: the button group sits in the overlays above NavDisplay, so placed at rest it would take every touch on the bottom of a song opened from a tool, and a placed chrome is hovered (tooltips, state layers recorded into the copy) and reached by Tab; two tests for that.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all  ·  **Run on:** any machine
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt` (must not grow: it is past the 120-line function limit)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireContent.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireOverlays.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationChromeScaffold.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/TopLevelScreenSurface.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/ToolScreenFrame.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/SharedToolsButtonGroup.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/ChromeLayers.kt` (new: the recorded layers and where they sit)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/CLAUDE.md`
- `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/ChromeUnderCardTest.kt` (new)

## Problem

So that the bar or the rail rides the covered screen's 12 % parallax and scrim, each top level screen composes a copy
of the navigation chrome while a card covers it (`chrome/CLAUDE.md`: "each top level screen draws its own chrome copy
so it rides the 12% parallax"). `CampfireScreens.kt:201-219`:

```kotlin
val screenChrome: (CampfireDestination.TopLevel) -> (@Composable () -> Unit)? = { destination ->
    if (chromeInScreens) {
        {
            NavigationChrome(
                kind = chromeKind,
                …
```

handed to `TopLevelScreenSurface(chrome = screenChrome(destination), …)` (`:248`), which composes it
`if (chrome != null)`. The tool screens do the same with their button group: `isButtonGroupDrawn = chromeInScreens`
(`:229`), while `CampfireOverlays.kt:51` drops the shared one: `if (!chromeInScreens) { SharedToolsButtonGroup(…) }`.
`chromeInScreens` turns on in the push's first frame and off two frames after the pop settles (`CampfireContent.kt:60-68`),
so:

- the push frame composes a whole `NavigationChrome` (items, labels, a tooltip per item) inside the covered list, on
  top of composing song details: in the probe's push samples `NavigationChrome` was 9 % and `NavigationItemTooltip` 7 %
  of the compose phase;
- the pop frame composes the list again with its copy (Navigation 3 dropped the covered entry), and the settle frame
  disposes the copy and, on a tool screen with grouped tools, composes `SharedToolsButtonGroup` from scratch;
- `screenChrome(destination)` returns a new lambda on every call, so `TopLevelScreenSurface` never skips when its
  screen's frame recomposes.

## Fix

Options:

- **A. Keep the copies composed, only skip placing and drawing them** (as `NavigationChromeScaffold` does with the
  shared one: "still measured … but not placed … it stays composed"). Since Navigation 3 drops a covered screen and
  composes the next one fresh, a copy kept composed is composed with every screen, every tab switch and pop included:
  it moves the push frame's cost onto the tab-switch and pop frames, the two this review is trying to shrink.
- **B. Draw the one shared chrome into the covered screen from a recorded layer** (recommended). The shared chrome is
  always composed and laid out by `NavigationChromeScaffold`; it records its drawing into a `GraphicsLayer`
  (`rememberGraphicsLayer()`, `drawWithContent { layer.record { this@drawWithContent.drawContent() } … }`) on every
  frame it is drawn, which keeps the metronome beat and the selection animation live. While `chromeInScreens`:
  - the scaffold keeps drawing the chrome (so it records), but **places it out of the window** (e.g. one window width
    past the end edge) and writes the position it *would* rest at into the holder. Not a consuming `pointerInput` at its
    resting place: a placed chrome under a card's empty areas is still hit — hover reaches its items, which then show
    their tooltip and draw a hover state layer into the very recording the covered screen shows, and a consumed press
    still blocks what is under it. Compose does not cull by bounds, so a node placed outside the window is still drawn,
    and so still recorded. While covered it is also `clearAndSetSemantics { }` and kept out of focus
    (`Modifier.focusProperties { onEnter = { cancelFocusChange() } }` on a `focusGroup()` around it): today it is
    unplaced, which does both, and a placed one is reached by Tab from a song on the desktop and the web;
  - `TopLevelScreenSurface` draws that layer (`drawLayer`) at the chrome's resting position in its own coordinates
    (the scaffold writes the position it places the chrome at into a small `ChromeLayers` holder; the screens start at
    the window's origin, so the position needs no conversion), under the screen's content as the copy is now, so the
    parallax translation and the `veilOut`/`coveredScreenScrim` of its entry apply to it, the export screen's slide of
    the deck included;
  - `ToolScreenFrame` does the same with `SharedToolsButtonGroup`'s recorded layer in its room, and
    `SharedToolsButtonGroup` stays composed (records, does not draw, while `chromeInScreens`), placed out of the window,
    out of semantics and focus the same way. This one matters most: it is laid out in `CampfireOverlays`, **above**
    `NavDisplay`, so a group left at its resting place while a song opened from a tool's search is up would take every
    touch on the bottom of the song (its setlist pager bar, a tap that turns the page) for as long as the song is open.

  No composition happens on push, pop, or settle for the chrome; `screenChrome`, `isButtonGroupDrawn`, and the
  `if (!chromeInScreens)` around `SharedToolsButtonGroup` go away, which also shrinks `CampfireScreens`.
  `chromeInScreens` itself is read only while placing and drawing (pass a `() -> Boolean` or a `State`), so flipping it
  relayouts and redraws without recomposing `CampfireScreens` (see also plan 10 b).

  Check: the layer is a render node on every platform (Android's `RenderNode`, skiko's `org.jetbrains.skiko.node.RenderNode`
  in `SkikoGraphicsLayer`, CMP 1.13), so a screen that drew it shows the chrome's next recording without drawing
  again, and a layer drawn by two screens at once (a tab fade under a card) is allowed. A layer released with the
  scaffold draws nothing (`GraphicsLayer.draw` returns once released). A layer recorded in one frame and drawn by another node in the same frame — the scaffold places and draws
  the chrome before the content (`NavigationChromeScaffold.kt:110-122` places it first), so the screens draw the current
  recording. During the handover of chrome kinds (`NavigationChromeTransition`) two chromes are placed; record each into
  its own layer, or keep today's copies only while `transition.outgoingKind != null` (rare: a window resized across a
  breakpoint while a card is up).
- **C. Minimum, independent of A/B:** remember the per-destination chrome lambda
  (`remember(destination, chromeKind, topLevelDestinations, railCapacity, metronomeBeat, isKeyboard) { … }`) so
  `TopLevelScreenSurface` can skip. B removes the lambda; do C only if B is not taken.

Update `chrome/CLAUDE.md`'s "The chrome moves with the screen under a card" and the `SharedToolsButtonGroup` bullet:
one chrome, drawn into the covered screen from its recorded layer while a card or the export screen covers it.

## Tests

`chrome/PredictiveBackTest.kt`, `LayoutDirectionTest.kt` (cards dealt from the right / from the left in Hebrew),
`ToolsItemTest.kt`, `components/tabChrome/TabSwapNewButtonTest.kt` must pass unchanged. New `ChromeUnderCardTest.kt`
(pixels, since the drawn chrome has no semantics of its own while covered):
- `` `the navigation bar slides with the list under a song being opened` ``: pause the clock mid-push, find the
  selected item's indicator color in the bar's row; it is shifted toward the start by the parallax and darkened by the
  scrim, compared with a frame before the push.
- `` `a tool's button group stays above the bar under a song and is pressable again once the song is closed` ``: on a
  window that groups the tools, open a song from a tool screen's search, go back, tap another tool's button.

**Measure:** plan 01's `push:*` (the warm trigger frame's compose part should lose the chrome's share, ~10–15 %) and
`pop:*` (the pop frame and the settle frame ~25 frames later should both drop); `tab:*` unchanged.

- `` `a song opened from a tool's search takes the taps on its bottom edge` ``: on a window that groups the tools, open
  a song from Chords' search, tap where the tools' group rests; the song reacts (a page turn or its pager bar), and no
  tool button was pressed.
- `` `the keyboard never reaches the navigation under an open song` ``: desktop window, open a song, press Tab through
  every focusable; none is a navigation item or a tool button.

## Manual check

On Android with gesture navigation, a predictive back from a song half-way and released: the bar rides the list and
is tappable once the list has settled. On the web, the same with the browser's back.
