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

- **Every control drawn as an icon alone has a tooltip naming it** (`CampfireTooltip`, its text the content
  description): a plain icon button is a `CampfireIconButton`, never a raw `IconButton`; anything else (a filled or
  floating button, a toggle, a clickable `Surface`) is wrapped in `CampfireTooltip`, a parent's `weight` or `align` on
  the wrapper. Hover and long press are the wrapper's own, Material's off: its `TooltipBox` drops a parent's modifier,
  opens on keyboard focus for good (eating Escape), and opens the moment a pointer passes. Hover waits 500 ms, none
  within 300 ms of another tooltip going (a pointer moving along a toolbar; timed by a coroutine, not a clock); a press
  hides it. A long press that is already the control's (a repeating stepper) leaves hover alone (`isShownOnLongPress`);
  a label the control sometimes shows itself turns it off (`isEnabled`). A toggle's tooltip names what a press does. Drawn as the app's tonal pills are,
  not Material's dark slab. Settings → General's Tooltips switch turns every one off
  (`LocalAreTooltipsEnabled`).
- **Every keyboard shortcut is an entry of `shortcuts/KeyShortcut.kt`**, matched through it (`KeyShortcut.X.matches`),
  never by comparing keys by hand, and the control it presses names it (`shortcut` on `CampfireTooltip`,
  `CampfireIconButton`, `PillIconButton`, `ActionsMenuItem`; `menuShortcutHint` in a `DropdownMenuItem`'s trailing
  slot), so a hint is written from the keys that work. Hints show only with a hardware keyboard
  (`LocalShowsShortcutHints`), Apple's symbols on an Apple keyboard (`⌘S`), names translated elsewhere (`Strg+S`).
  **The table's platform column** (`ShortcutPlatforms`) says where a shortcut works: elsewhere it matches nothing and
  shows no hint. The web has no Ctrl / Cmd + N (Chrome opens a window before the page hears it); Ctrl / Cmd + 1 to 7 are
  the desktop's and the web's only.
- **Its scope column** (`ShortcutScope`) is where the keys are heard and the group the **shortcuts sheet**
  (`dialogs/shortcuts`, Ctrl / Cmd + slash or `?`) and the system lists (Android's Meta + /, iOS's Command-hold
  overlay, `systemShortcutGroups`) show them under (Find and the numbers under the main screens, not "Anywhere", since a song or the onboarding
  has neither); `description` is an exhaustive `when`, so a new entry is described,
  and `rowLead` puts a pair in one row. Keys meaning something else elsewhere (Page Down on a song and in the print
  preview) are an entry each; the root asks only the app-wide ones. Lists show only what works with the features on
  (`ShortcutFeatures`: no song keys with the songs off, nothing whose control read only mode hides, Space's
  auto-scroll only in the continuous song layout), Ctrl / Cmd + Y not on an Apple keyboard, and Ctrl / Cmd + the
  scroll wheel, which is no key, in the sheet only. `?` is Shift + slash
  (`Shift.IN_LABEL`, written as the character): a US layout's key, so elsewhere Ctrl / Cmd + slash, the numpad's
  slash, or Settings → About open the sheet; `?` does nothing while a text field is being typed in.
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
