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

The web shell, called by `:app:web` (page-side quirks are in `app/web/CLAUDE.md`).

## `ui/CampfireWebApp.kt` and its effects

- Compose reports no IME inset on the web, so `ProvideKeyboardInsets` uses the VirtualKeyboard API's `overlaysContent`
  (Chrome, Edge, Samsung Internet), reads `geometrychange`, animates it, and provides it as the `ime` of
  `LocalPlatformWindowInsets`; the window keeps its size. Firefox falls back on the viewport's
  `interactive-widget=resizes-content` (the canvas shrinks), which to the app is a window resize: the rails' tools
  grouping can change with it. Safari ignores both and pans.
- The browser's media session is cleared from the engine's own playback state, not only from the composition, since a
  hidden tab's composition stops collecting.
- Dropped files are imported; a dropped folder is read to the bottom like the desktop's (`FilePicker.wasmJs.kt`), its
  files under unknown names peeked at (`fileHead`) and read only as an archive or a text (never a binary name). An
  unreadable file arrives empty and is reported as skipped. A drop with no file is text (`droppedTexts`), a pasted song
  where `isNewSongPasteAvailable`, ignored elsewhere. In read only mode (`refuseDrops`, a page flag) the listeners still
  prevent the browser's default, which would navigate to the file, but set `dropEffect` to `none` and drop nothing.
- Shared song and setlist links (`sharedLinks`, `ShareLinks.wasmJs.kt`) come from the list index.html keeps of the
  address's fragment and of what other tabs hand over (`app/web/CLAUDE.md`), one entry at a time, removed only after
  it reached `CampfireApp`'s `shareLinks`.

### Keys

A focused text field is a hidden `<input>` beside the canvas, not inside it, so keys typed there never pass Compose's
canvas listener — and most dialogs open with a field focused.
- **Escape** (`startForwardingEscapeKey`): a press Compose hasn't seen (its listener runs first and `preventDefault`s
  what it handles) is re-dispatched on the canvas, so the innermost back handler answers it. Don't decide its meaning
  here as the desktop does: that can't tell a dialog from the sheet under it. Held repeats are stopped in the capture
  phase.
- **Ctrl/Cmd + S**: default prevented in the capture phase, or "Save page as" opens on every shortcut save.
- **Ctrl/Cmd + F** (`SearchShortcutEffect`, a Kotlin listener, since the view model decides synchronously): calls
  `openCurrentSearch` and prevents the find bar only if that opened something. In the editor, F and R are
  always prevented (its Find / Find and replace; R would reload), and B, I, and U without Shift (its formatting; U
  would show the page source), so Ctrl + Shift + I still opens the developer tools.
- **Ctrl/Cmd + E** (`EditShortcutEffect`): prevented on song details outside performance mode, where it opens the
  editor; Chrome would focus its address bar.
- **Ctrl/Cmd + V** (`PasteShortcutEffect`): the `paste` event, not the key, since it carries the text with no permission
  asked; a paste into an editable target is left alone, and only `isNewSongPasteAvailable` takes one.
- **Zoom** (`SongTextZoomEffect`, song details only): Ctrl/Cmd + plus/minus/zero go to `zoomSongText`, matched by `key`
  like the browser's zoom. Ctrl + wheel is prevented by a **non-passive** listener (window wheel listeners are passive
  by default) and reaches `fontScaleGestures`.
- **Touchpad pinch**: every browser but Safari sends it as Ctrl + wheel with a synthetic Ctrl and a delta of
  −100 × ln(scale). One arriving without a seen Ctrl keydown is taken as a pinch, stopped, and passed to
  `magnifyByTouchpad` as `exp(−deltaY / 100)` (also on the PDF export preview, where a real Ctrl + wheel stays the
  browser's). Safari's `gesturechange` is not handled.

### `beforeunload`

Registered only while `hasUnsavedEditorChanges`, since a page with one is kept out of the back/forward cache. The
browser's Back is not covered by it; the app's own unsaved changes question answers that. iOS browsers don't reliably
fire it on tab close.

## Addresses and history (`ui/navigation/`)

- Every screen has an address (`BrowserRoutes`, `ui/navigation/CLAUDE.md`) and the browser's history is the app's back
  stack. A dialog, sheet, or menu, the reorder mode, the selection mode, and the onboarding each get an entry repeating
  the address under it; the unsaved changes question gets none (it answers a Back). An address naming nothing in the
  library opens the home screen, resolved behind the launch screen.
- `BrowserHistoryEffect` (`wasmJsMain/.../navigation/BrowserHistory.kt`): the app decides, the history follows via
  `pushState`, `replaceState` (settings tab to tab, setlist paging, renamed file), or `history.go`. Each entry is
  stamped with its depth, which is how `popstate` tells where the browser went.
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
- Performance mode allows no editor: `song/{song}/edit` opens the song (address rewritten), and Forward stops at it.
- The page starts at depth zero however deep it was loaded; earlier entries belong to a gone document and reload the
  app. The launch address goes to `LaunchState.navigateOnLaunch` on first composition, so the launch screen
  waits for it.
