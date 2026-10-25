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

The shared building blocks of the screens. Each subpackage with something non-obvious to say has a `CLAUDE.md` of its
own (`songCard`, `list`, `appBar`, `menu`, `actions`, `songFilters`, `labels`, `controls`, `coverArt`); this file
covers the generic pieces at the root.

### `SaveShortcut.kt`

- `Modifier.saveShortcut`: Ctrl / Cmd + S, **not with Alt** (AltGr arrives as Alt). In a `CampfireBottomSheet` it presses
  the header's `BottomSheetConfirmButton` (`LocalSheetSaveShortcut`; library deletion opts out with `isSaveShortcut`),
  nothing while disabled or closing.
- Keys travel only along the focus path, so each surface takes focus with a bare focus target as it opens — a sheet a
  frame in and only if nothing has focus yet, so a form's first field keeps the caret. The key is always consumed.

### `AddressTextField.kt`

Every field for a web address (a song's links, a cover's address) selects all of it whenever it gains the focus, a tap
included: an address is pasted over, not edited by hand. A tap's caret arrives in the frame the focus does and is
dropped; later ones go through.

### `WarningCard.kt`

What a switch flipped elsewhere has done to a whole screen, on the onboarding note's card with a warning icon: the
everything-off note in Settings → Features, and the import screen's warning about songs or setlists switched off.

### `DelayedLoadingIndicator.kt`

Song details, the editor, and the export screen and its preview: fades in after 300ms, since one local file is nearly
always read before then. Lists keep the plain indicator (the launch screen covers their first read). A `label` under
it (the Link format's "Creating link…") comes and goes with it.

### Edge fades (`EdgeFade.kt`)

- `fadingTopEdge`: over a **known opaque color**, paint that color over the edge rather than masking (a mask costs a
  viewport-sized offscreen buffer every frame). The color must be the one directly behind (`sheetContainerColor()` for
  sheets). Masks are kept only for alert dialogs, `ListTopFade`, `fadingUnderStartOverlay`, and overscroll.
- `fadingVerticalEdges`: lists inside a dialog; never a sheet's content.

### `PlacementAnimation.kt`

- `Modifier.animatePlacement`: a child of a wrapping row (chips, color discs) travels to where its parent now places it
  instead of jumping, as a resized window or a wider neighbour sends it to another line. Only moves inside the parent
  travel, so a start-aligned row follows a dragged window edge frame by frame. First in the child's modifiers; never in
  lazy list items, whose reused nodes would travel from the previous item's places.

### Scrolling performance

- Overscroll is Compose's own (none on desktop/web). Foundation skips it when content fits, so short containers use
  `bounceScrollableContent` / `bounceVerticalScroll` / `bounceHorizontalScroll` (`ContentOverscroll.kt`),
  offering drags to parents first so a fitting sheet still drags down. Installed whether or not the content fits (only
  a measure knows, so a decision while composing would compose every list and label row twice); the connection asks at
  each drag, and the clip is drawn only while it fits, since a scrolling container must not clip its cross axis. Never
  a layer's clip: on Android it can keep the layer's first size while the content grows. The vertical form draws its own top fade while
  pulled (scroll position stays zero, so `fadingTopEdge` is off); list screens pass their `ListTopFade`'s
  `OverscrollPull` instead.
