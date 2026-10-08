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

The web shell, called by `:app:web`.

- `wasmJsMain/ui/CampfireWebApp.kt` (its effects in files of their own beside it) — `CampfireWebApp()` hosts `CampfireApp` inside `ProvideKeyboardInsets` (`wasmJsMain/ui/platform/KeyboardInsets.wasmJs.kt`), which has a browser with the VirtualKeyboard API lay its keyboard over the page and hands its height, animated, to Compose as the IME inset, so that the canvas keeps its size and the app keeps clear of the keyboard as on Android (see `app/web`); links open in a new tab, the favicon is the icon of the chosen theme color (`FaviconEffect`, which also leaves its name for `index.html` to show on the next visit's loading screen), it clears the browser's media session from the engine's own playback rather than only from the composition, since a hidden tab's composition stops collecting, and files dropped anywhere on the page are imported, a dropped folder standing for the files directly inside it; a file the browser cannot read arrives empty, so the import reports it as skipped rather than the drop failing. It also forwards Escape to Compose (`startForwardingEscapeKey`), which is what makes the key dismiss a dialog here: Compose listens for keys on its canvas, but a text field with the caret in it is a hidden `<input>` placed beside that canvas rather than inside it, so a key pressed there travels up the page without ever passing the canvas — and nearly every dialog holding a field opens with that field focused. Such a press is dispatched on the canvas instead, so it takes the path it would have taken had the canvas been focused and the innermost back handler answers it; deciding here what an Escape means, the way the desktop window's key handler does, could not tell a dialog standing on a bottom sheet from the sheet under it. Only a press Compose has not already seen is forwarded, since the canvas' own listener runs first and calls `preventDefault` on everything it processes. Repeats of a held Escape are stopped in the capture phase before the canvas sees them, for the same reason the desktop swallows them. For the same reason — a key pressed in that hidden input reaches Compose only after the browser has acted on it — Ctrl / Cmd + S has its default prevented on the window in the capture phase, or the browser's "Save page as" would open over the app every time something is saved with the shortcut. Ctrl / Cmd + F is heard by a capture phase listener on the window too (`SearchShortcutEffect`, a Kotlin listener rather than a `js(...)` block since the view model decides it synchronously), which calls the same `openCurrentSearch` and prevents the browser's find bar only when that opened something — the page is one canvas with no text to find, but on a screen with no search the key is left to the browser. The browser's zoom is taken over the same way while the song details screen is on top (`SongTextZoomEffect`): Ctrl / Cmd + plus, minus and zero go to `zoomSongText` instead, matched by `key` rather than `code` as the browser's own zoom is, and a Ctrl + wheel has its default prevented by a non-passive listener (a wheel listener on the window is passive unless it says otherwise) and reaches the screen's own `fontScaleGestures`, so that the song grows rather than the whole page around it. Every browser but Safari also makes a touchpad pinch into a Ctrl + wheel, with Ctrl set although nobody holds it and a pixel delta of −100 × ln of the pinch; one that arrives while the listener has not seen the Ctrl key go down is taken for that pinch, stopped before the canvas hears of it and handed to `magnifyByTouchpad` as `exp(−deltaY / 100)`, since read as notches of a wheel a whole pinch would move the text a few percent — which zooms the page on preview too while the PDF export screen is up, where a real Ctrl + wheel is still the browser's. Everywhere else the browser zooms the page as usual, and so does Safari, which reports a pinch as a `gesturechange` of its own that nothing listens for. While the editor holds unsaved text (`hasUnsavedEditorChanges`) it also keeps a `beforeunload` listener registered, so a reload, a closed tab or a navigation away is asked about by the browser first; the browser's Back is not among them, since it is answered by the app's own unsaved changes question (below). The listener is there only for as long as there is something to lose, because a page that has one is kept out of the back/forward cache. iOS browsers do not reliably fire it when a tab is closed or the browser is swiped away, and no browser can ask when its process is killed.
- `wasmJsMain/ui/navigation/` — the web build's addresses. `BrowserRoutes` (in `commonMain/ui/navigation/`, a pure
  function of `RouteInputs`, which `routeInputs()` here reads off the view model, and tested with its percent codec,
  `PercentEncoding.kt`, which writes what `encodeURIComponent` writes) maps where the user is to the path of
  every history entry that should exist (one per step a back gesture would take: a screen of the back stack, an open
  search, the setlist reorder mode, or a dialog, a sheet or a menu open over them, which repeats the address under it — without an entry of its
  own, a Back on the songs screen of a page opened on it left the page instead of closing the dialog; the unsaved changes
  question has none, since it is asked in answer to a Back and the entry that Back left is gone forward to again), and a path back to a `NavigationState` against the library, a missing song or setlist resolving to nothing.
  A settings tab other than General is an entry of its own on top of `settings/general`, since a Back from it goes to
  General first (`isSettingsBackToGeneral`): the Back then lands on an entry that is already there, where a General
  pushed again after it would have been an entry added without a user gesture, which Chrome's Back skips — taking the
  next Back off the page rather than to the songs.
  Performance mode leaves no way into the editor, an address included: `song/{song}/edit` opens the song alone while it
  is on (the history then writes `song/{song}` over it), and Forward to an editor's entry stops at the song under it.
  A song is named by its file name without `.cho` (a file with another extension keeps it), a setlist without
  `.setlist.json`, both percent-encoded with `~` escaped too, since the site's 404 page carries `&` as `~and~`.
  `BrowserHistoryEffect` keeps the history in step with the app, which decides: a change of the app is written as a
  `pushState`, a `replaceState` (one settings tab after another, a song paged to in a setlist, a renamed file) or a `history.go` back to
  the depth wanted, and every entry is stamped with its depth, which is how a `popstate` says where the browser went. A
  `popstate` to a shallower entry is sent into the navigation event dispatcher through a `DirectNavigationEventInput`,
  exactly as Escape is, so a dialog, a menu, a sheet, a search or the editor's unsaved changes question answers it
  before the back stack does, and the history is then brought back to what the app did: a dialog closed by its own
  button goes back over its entry, and a Forward to that entry is refused, since a dialog is no part of a
  `NavigationState`. **Entries that were just left
  are gone forward to rather than pushed again**: Chrome marks an entry that added another without a user gesture as
  one its Back button skips, so re-pushing the editor after its question was asked had the next Back jump off the page.
  A Back over several entries (the long press menu) is taken one step at a time, two frames apart so the next reaches
  whatever is on top by then. Forward restores the `NavigationState` recorded when the entry was last on top, a search with the text its field still holds, cut short
  at whatever the library no longer holds, with a song read from a setlist paging through what the setlist holds now,
  or opens the entry's address — but only an address exactly as deep as the entry, since it stands for a whole stack
  built up from the songs; one that is not (an editor whose setlist page underneath was paged on) is left again. The
  page starts as depth zero however deep in the tab's history it was loaded: the entries under it belong to a document
  that is gone, and stepping into one reloads the app. The address the page was opened on is handed to `CampfireViewModel.navigateOnLaunch` while the web
  shell is first composed, so the launch screen waits for it; opening a deep address puts the songs under it as an
  entry of its own, which Chrome's Back skips until the user has touched the page — the same rule, and the price every
  single page app pays for a deep link.

## Addresses

- **Every screen has an address, and the browser's history is the app's back stack**: `/` is the songs, then
  `search`, `setlists`, `setlists/search`, `metronome`, `settings/{general,features,songs,library,about}`, `song/{song}`, `song/{song}/edit`,
  `setlist/{setlist}/{song}` and `import`, one history entry per step a back gesture would take — a dialog, a sheet or a
  menu open over a screen is one too, and so is the setlist reorder mode, at the screen's address (`:presentation`'s
  `ui/navigation/BrowserHistory.kt`). The app decides and the history follows — pushed, replaced or gone back through
  to match — and the browser's Back is sent into the navigation event dispatcher like Escape, so it closes a dialog
  or asks about unsaved text before it leaves a screen. An address that is opened is resolved once the library has
  been read, behind the launch screen; one naming nothing the library holds opens the songs. GitHub Pages serves a
  deep address as its site-wide 404 page, which hands it to `index.html` in the query string (`404.html` in the
  `campfire-website` repository does this for addresses under `app/`), and `index.html` writes a `<base>`
  for the folder it lives in, which every relative URL of the page and the app depends on.
