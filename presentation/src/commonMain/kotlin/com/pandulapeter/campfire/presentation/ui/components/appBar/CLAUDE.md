<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/components/appBar

The top app bars of the list screens.

### The searchable bar (`SearchableTopAppBar`)

- Not a `TopAppBar`: a row drawn *over* the list, because a `TopAppBar` is a `Surface` and would take every touch meant
  for the pinned header under it.
- Closed, it holds the actions on a tonal pill (`SearchPill`), the pinned `SectionHeader` standing where a title would.
  The pill animates between the buttons' row and the field as **lookahead rectangles of each settled state**, so each
  edge only travels one way.
- The pinned header's pill narrows to the room the buttons leave (`onReachChanged`, measured before the list via
  `ListUnderAppBar` so the first frame is right) and along `AppBarOverlap.coverage` as the search opens and closes.
- Cards fade out under the header row and back in over 24dp (`ListTopFade.kt`): each card masks itself, since an overlay
  would fade the sticky headers (items of the same list). Strength grows with scroll, so a list at rest shows its first
  cards whole. A card takes no press inside the gradient, but a drag there still scrolls.
- The fast scroller starts below `AppBarOverlap`'s reach so its thumb is never under a button.
- On Songs, opening search collapses all section headers (even with an empty query); section boundaries stay so rows
  keep their columns. `SongSearchScrollAnchor` keeps the visible section stationary and restores the exact scroll on
  closing an unchanged search; scrolling or new results release it. It checks for an active scroll both on mode change
  and before each correction, so an interrupted transition never resumes a stale one (tested). On Setlists the list
  moves down instead and headers pin under the bar.
- The "New" menu yields to the field (`closedSearchActions`); the selection menu stays
  (`areClosedSearchActionsKeptInSearch`). New is a filled 48dp circle **beside the pill**
  (`areClosedSearchActionsDetached`): the pill's end is cut by the closed actions' lookahead width and the divider's room
  widens into a gap as wide as the circle's from the edge, both on the search's spring, so the button never moves. A
  clipped shrink would slice the circle, so it fades (quickly) and scales in place while its room closes. The circle
  ends on the cards' scroller-less keyline, as does the pill without it (`UNDETACHED_PILL_END_INSET`); with the
  scroller, a card's menu is centered in the gap and its setlist button under the pill's last action, without it the
  menu is centered under the circle (`listItemTrailingKeylineAdjustment`, following the scroller's column). The bar stays filled while the list has no header (placeholder, ranked
  results). It spans the list only, never the side panel.
- `SearchBackHandler` uses `NavigationBackHandler` over a hoisted `NavigationEventState`, not `BackHandler` (which only
  hears a gesture on release), and previews the close via `SearchState.backProgress` (`SearchRecession`). The offset is
  layout, not a drawn translation, so `animateBounds` carries on from where the gesture left.
- `SearchAction` is **one button travelling** between the actions slot and the navigation slot (`movableContentOf` +
  `animateBounds` in a bar-only `LookaheadScope`), keeping its icon's animation state; bounds animate only while opening
  or closing, so it doesn't lag a window resize. `SearchActionSlot` reserves its room on the same spring.
- The field is a hand-laid `BasicTextField`, not `SearchBarDefaults.InputField` (fixed 56dp, no padding control). It
  never leaves its `Box` (emptied, zero width when closed) because `animateBounds` only animates a rectangle it has
  seen. It collapses into its end edge rather than sliding. The pill moves by `animateBounds` to `CLOSED_FIELD_OFFSET`,
  not an animated width fraction, which settles short and jumps on the last frame.
- `SearchToCloseIcon` is a `Canvas` because Compose Multiplatform resources read only static vectors; it transcribes
  `avd_search_to_close.xml` on **one linear timeline**, not springs (overlapping stages would drift), played faster than
  authored.

### The plain bar (`CampfireTopAppBar`)

- Song details and editor bar: flat, background color, never tints or lifts. In a short window the details screen gives
  it `enterAlwaysScrollBehavior`; pages choose their grid from the settled height (`isAppBarSettled`, `settledHeight`)
  so a moving bar searches no grid. The editor's is `isCompact` (48dp) while the keyboard is up.
- `bottomContent` holds a second row inside the same surface (the editor's toolbar).
