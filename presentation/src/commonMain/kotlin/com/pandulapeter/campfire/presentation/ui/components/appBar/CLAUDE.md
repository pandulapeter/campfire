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

### The searchable bar (`SearchableTopAppBar`)

- A row drawn *over* the list, not a `TopAppBar`, whose `Surface` would take every touch meant for the pinned header.
- The pill (`SearchPill`) animates between buttons and field as **lookahead rectangles of each settled state**, so
  each edge travels only one way. Its rectangles are the bar's own, never mirrored (its end is the left edge right to
  left). It moves by `animateBounds` to `CLOSED_FIELD_OFFSET`, not an animated width fraction, which settles short and
  jumps on the last frame.
- **A press fills the button's share of the pill**, not Material's circle (`PillIconButton` turns its ripple off,
  `SearchPillPresses` draws it, closing into a circle as search opens). The divider takes no room on it.
- The pinned header's pill narrows to the room the buttons leave, measured before the list (`ListUnderAppBar`) so the
  first frame is right, and along `AppBarOverlap.coverage` as search opens and closes. The fast scroller starts below
  `AppBarOverlap`'s reach, never under a button.
- Cards fade out under the header row over 24dp (`ListTopFade.kt`), each masking itself, since an overlay would fade the
  sticky headers. Strength grows with scroll, so a list at rest shows its first cards whole. No press inside the
  gradient, but a drag there still scrolls.
- On Songs, as on Setlists, the list moves down under the bar as search opens. The "New" menu yields to the field; the
  selection menu stays. The bar stays filled while the list has no header (placeholder, ranked results).
- New is a filled 48dp circle **beside the pill** that never moves as search opens and closes: the pill's end and the
  gap follow the search's spring, the circle fades and scales in place (a clipped shrink would slice it). The divider
  before it animates on the search action's spring, not in the button's exit, whose clipped shrink would cut it off.
- `SearchBackHandler` uses `NavigationBackHandler` over a hoisted `NavigationEventState`, not `BackHandler` (which
  hears a gesture only on release), and previews the close via `SearchState.backProgress`. The offset is layout, not a
  drawn translation, so `animateBounds` carries on from where the gesture left.
- `SearchAction` is **one button travelling** between the actions and navigation slots (`movableContentOf` +
  `animateBounds` in a bar-only `LookaheadScope`), keeping its icon's state; bounds animate only while opening or
  closing, so it doesn't lag a window resize.
- The field is a hand-laid `BasicTextField` (`SearchBarDefaults.InputField` is a fixed 56dp, no padding control). It
  never leaves its `Box` (emptied, zero width when closed), since `animateBounds` only animates a rectangle it has seen,
  and collapses into its end edge rather than sliding.
- `SearchToCloseIcon` is a `Canvas` (resources read only static vectors), transcribing `avd_search_to_close.xml` on
  **one linear timeline**, not springs, whose overlapping stages would drift.

### The plain bar (`CampfireTopAppBar`)

- Song details and editor: flat, background color, never tints or lifts. In a short window details gives it
  `enterAlwaysScrollBehavior`; pages choose their grid from the settled height (`isAppBarSettled`), so a moving bar
  searches no grid. The editor's is 48dp (`isCompact`) while the keyboard is up.
