<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/components

The shared building blocks of the screens; subpackages with something to say have their own `CLAUDE.md`.

- **`saveShortcut`**: Ctrl / Cmd + S, **not with Alt** (AltGr arrives as Alt); in a `CampfireBottomSheet` it presses
  the header's `BottomSheetConfirmButton` (library deletion opts out), nothing while disabled or closing. Keys travel
  only along the focus path, so each surface takes focus with a bare focus target as it opens — a sheet a frame in and
  only if nothing has focus yet, so a form's first field keeps the caret. The key is always consumed.
- **`AddressTextField`**: an address is pasted over, not edited, so the field selects all of it on every focus, a tap
  included (the tap's caret arriving with the focus is dropped; later ones go through).
- **`DelayedLoadingIndicator`** (song details, editor, export): fades in after 300 ms, since one local file is nearly
  always read before then; lists keep the plain indicator (the launch screen covers their first read).
- **Edge fades** (`EdgeFade.kt`): over a **known opaque color**, `fadingTopEdge` paints that color (the one directly
  behind, `sheetContainerColor()` in sheets) rather than masking, since a mask costs a viewport-sized offscreen buffer
  every frame; masks only for alert dialogs, `ListTopFade`, `fadingUnderStartOverlay`, and overscroll.
  `fadingVerticalEdges`: lists in a dialog; in a sheet only content above a pinned row, never content ending at the
  window's bottom.
- **`ShrinkToFit`**, never `TextAutoSize.StepBased`, which never shrinks on Skiko (no ellipsized line reported).
- **`animatePlacement`**: a wrapping row's child travels to its new place instead of jumping; only moves inside the
  parent travel (so a dragged window edge is followed frame by frame), a new layout direction places at once. First in
  the child's modifiers; never in lazy list items, whose reused nodes would travel from the previous item's places.
- **Overscroll** is Compose's own (none on desktop/web), which Foundation skips when content fits, so short containers
  use the `bounce…` modifiers (`ContentOverscroll.kt`), offering drags to parents first so a fitting sheet still drags
  down. Installed whether or not the content fits (deciding while composing would compose every row twice); the clip is
  drawn only while it fits (a scrolling container must not clip its cross axis), and never as a layer's clip, which on
  Android can keep the layer's first size. The vertical form draws its own top fade while pulled (`fadingTopEdge` is
  off at scroll zero); list screens pass their `ListTopFade`'s `OverscrollPull`.
- **Every floating control that comes and goes uses `FloatingControlVisibility`**, which draws and fades the shadow
  itself (the control's elevation is zero); never `AnimatedVisibility` with `fadeIn` + `scaleIn`, whose buffer cuts the
  shadow off until it ends.
