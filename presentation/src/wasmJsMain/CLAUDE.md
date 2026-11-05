<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — wasmJsMain

The web shell (page-side quirks: `app/web/CLAUDE.md`).

## `ui/CampfireWebApp.kt` and its effects

- Compose reports no IME inset on the web, so `ProvideKeyboardInsets` uses the VirtualKeyboard API's `overlaysContent`
  (Chrome, Edge, Samsung Internet) and its `geometrychange`, animated, as the `ime` inset; the window keeps its size.
  Firefox falls back on `interactive-widget=resizes-content` (the canvas shrinks), a window resize to the app, which can
  change the rails' tools grouping. Safari ignores both and pans.
- The media session is cleared from the engine's own playback state, not only from the composition, since a hidden tab's
  composition stops collecting.
- Drops: a folder is read to the bottom like the desktop's, unknown names peeked at (`fileHead`) and read only as an
  archive or a text; an unreadable file arrives empty and is reported as skipped. A drop with no file is text, a pasted
  song where `isNewSongPasteAvailable`, ignored elsewhere. In read only mode (`refuseDrops`) the listeners still prevent
  the browser's default, which would navigate to the file, but set `dropEffect` to `none` and drop nothing.
- A link the browser refuses to share (anything but the user closing the sheet) throws from `shareText`, so the screen
  says the share failed; a refused file is downloaded instead.
- Shared links come from the list `index.html` keeps of the address's fragment and of what other tabs hand over, one
  entry at a time, removed only after it reached `CampfireApp`'s `shareLinks`.

### Keys

A focused text field is a hidden `<input>` beside the canvas, so keys typed there never pass Compose's canvas listener,
and most dialogs open with a field focused.

- **Escape** (`startForwardingEscapeKey`): a press Compose hasn't seen (its listener `preventDefault`s what it handles)
  is re-dispatched on the canvas, so the innermost back handler answers it. Don't decide its meaning here as the desktop
  does: that can't tell a dialog from the sheet under it. Held repeats are stopped in the capture phase.
- The shortcuts answered here (find, paste, zoom, Space and M) are `LocalKeysLeftToShell`, which the app's own root
  handler (`appKeyboardShortcuts`) leaves alone, so none is handled twice.
- **Ctrl/Cmd + S**: default prevented in the capture phase, or "Save page as" opens on every shortcut save.
- **Ctrl/Cmd + F** (a Kotlin listener, since the view model decides synchronously): prevents the find bar only if
  `openCurrentSearch` opened something. In the editor, F and R are always prevented (Find / Find and replace; R would
  reload), and B, I, and U without Shift (formatting; U would show the page source), so Ctrl + Shift + I still opens the
  developer tools.
- **Ctrl/Cmd + E**: prevented on song details outside read only mode and an import preview, where it opens the editor;
  Chrome would focus its address bar.
- **Ctrl/Cmd + V**: the `paste` event, not the key, since it carries the text with no permission asked; a paste into an
  editable target is left alone.
- **Zoom** (song details only): Ctrl/Cmd + plus/minus/zero matched by `key` like the browser's zoom. Ctrl + wheel is
  prevented by a **non-passive** listener (window wheel listeners are passive by default).
- **Touchpad pinch**: every browser but Safari sends it as Ctrl + wheel with a synthetic Ctrl and a delta of −100 ×
  ln(scale). One arriving without a seen Ctrl keydown is taken as a pinch and passed to `magnifyByTouchpad` as
  `exp(−deltaY / 100)` (on the PDF export preview too, where a real Ctrl + wheel stays the browser's). Safari's
  `gesturechange` is not handled.

### `beforeunload`

Registered only while `hasUnsavedEditorChanges`, since a page with one is kept out of the back/forward cache. The
browser's Back is answered by the app's own unsaved changes question instead. iOS browsers don't reliably fire it on tab
close.

## Addresses and history

- The browser's history is the app's back stack. A dialog, sheet, or menu, the reorder mode, the selection mode, and the
  onboarding each get an entry repeating the address under it; the unsaved changes question gets none (it answers a
  Back). An address naming nothing in the library opens the home screen, resolved behind the launch screen.
- The app decides, the history follows (`pushState`, `replaceState` for settings tabs, setlist paging, and a renamed
  file, or `history.go`). Each entry is stamped with its depth, which is how `popstate` tells where the browser went.
- A `popstate` to a shallower entry is dispatched through a `DirectNavigationEventInput`, like Escape, so modals and the
  unsaved changes question answer first; the history is then reconciled. A Forward to a dialog entry is refused. A
  multi-entry Back is taken one step at a time, two frames apart.
- **Chrome skips entries added without a user gesture**, so:
  - entries just left are gone forward to, never pushed again;
  - a non-General settings tab sits on top of an existing `settings/general` entry (`isSettingsBackToGeneral`);
  - a deep link's songs entry is skipped by Back until the user touches the page (unavoidable).
- Forward restores the `NavigationState` recorded for the entry, trimmed to what the library still holds and never
  reopening a finished, skipped, or closed onboarding; an address is only used if it is exactly as deep as the entry
  once rooted on the home screen the switches leave (`BrowserRoutes.resolveForward`).
- Read only mode allows no editor: `song/{song}/edit` opens the song (address rewritten), and Forward stops at it.
- The page starts at depth zero however deep it was loaded; earlier entries belong to a gone document and reload the
  app. The launch address goes to `LaunchState.navigateOnLaunch` on first composition, so the launch screen waits.
