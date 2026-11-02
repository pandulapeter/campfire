<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/chrome

The navigation chrome (bar, rail, transitions, and the Tools item).

## Chrome

- Kind (`navigationChromeKind`): bar under 600dp, rail above, expanded `WideNavigationRail` where three Songs columns
  still fit beside it, the fast scroller counted (from `minSongColumnWidth`, not the sorted grid's).
- **Transitions** (`NavigationTransitions.kt`, every platform): tabs fade through in place, no slide; cards
  (`SongDetails`, `SongEditor`, and the export screen's hand-rolled slide) enter and leave at the end edge (the left in
  RTL) whatever the predictive gesture's edge, the screen beneath moving 12% under a scrim (theme `scrim` at 32%,
  animated per entry so it follows the gesture). Predictive pops use linear specs; going back from Setlists/Settings to
  Songs is a cross fade (a fade through held halfway would show neither screen). `navigationTransition` decides push vs.
  pop from scene depth itself, because Navigation 3's detection is wrong after an interrupted transition and leaves an
  invisible screen swallowing clicks. A finished onboarding is the one exception: zoomed away, no scrim.
- Cards covering the chrome take no touches while moving (lifecycle below `RESUMED` in a `RESUMED` host): a double tap
  on the editor's Close otherwise hit the song's Back.
- **Chrome kind changes hand over rather than switch** (`NavigationChromeTransition`), the room interpolated so screens
  travel with it. Screens *decide* from the settled width (`settledRailWidth`, passed as `ListLayout` /
  `PageWidthLayout`), so a resize recomposes them only when a breakpoint flips and column counts never change
  mid-handover. The interpolated room (`NavigationChromeSize`) is read only in layout (`ChromeRoomPadding`,
  `KeyboardAwarePadding`), so the handover relayouts without recomposing; never read it while composing.
- A label that does not fit shrinks to 75% before it is cut (`NavigationItemLabel`). The collapsed rail is at least 96dp
  and keeps 4dp beside its widest label, so every language fits at the default text size; a larger one grows the rail
  rather than cutting the label.
- `NavigationChromeScaffold` measures the chrome before composing the screens: reporting its size back as state is one
  pass late, and the first frame would be laid out without chrome.
- **The chrome moves with the screen under a card**: while a card is dealt (until its pop has settled, two frames on),
  each top level screen draws its own chrome copy so it rides the 12% parallax; tabs share the one chrome so its
  selection animates. The export screen does the same until its `ExportTransition` is back at zero.
- **No top level screen has a title bar**; the rail reaches the top of the window. `TopLevelScreenSurface` is opaque,
  inset by the chrome and at least `MIN_TOP_EDGE` below the top.

## The Tools item

- A bar holds at most five items (`MAX_NAVIGATION_BAR_ITEMS`); with six or more screens on, Metronome, Tuner, Chords,
  and Scales go behind one Tools item (`areToolsGrouped`, tested), on the bar and on any rail too short to show every
  item whole at the reader's text size (`railCapacityIn` repeats Material's item heights, paddings, and gaps per rail,
  so a rail spreads the tools out the moment they fit). Five or fewer screens never group, nor does a lone tool.
- The tools are switched by a connected button group (`ToolsButtonGroup`), not tabs, so it does not read as pages to
  swipe; only the selected button is named and wider, or "Metronome" is cut at 360dp. **The tools switch, never page**,
  so a swipe never brings the tuner (and its microphone) on screen in passing.
- The tools stay top level destinations (addresses, restored stack, tuner listening unchanged). The item opens the tool
  on top, else `Navigator.lastTool`, else the first enabled; pressed again it scrolls to top, then goes to the first
  tool (`ScrollToTopRequest.destinationWhenAtTop`).
- One `SharedToolsButtonGroup` is laid over all tool screens (each keeps room via `ToolScreenFrame`) so its selection
  morphs; under a card or the export screen each draws its own copy, like the chrome copies.
