<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/navigation

The destinations and the back stack.

### `CampfireDestination.kt`

- Songs and setlists are addressed by **file name** everywhere (their identity on disk).
- `SongDetails` is keyed by an `id` generated once per push, not by its file names, so a rename rewrites the entry
  without Navigation 3 taking it for a new one.
- `SongEditor`'s `contentKey` is its file name behind its own prefix, excluding `shouldStartInsideFirstSection` (how it
  opens, not which editor).
- `ImportReport` carries nothing (one import at a time) and is never restored (the report dies with the process);
  `Message.ImportInterrupted` says so where the import had not been written yet (a flag saved beside the back stack).
- Top level selection resets the stack to `[Songs, tab?]`, so Back always returns to Songs. The tools behind one Tools
  item: see `ui/CLAUDE.md`. Feature switches: `isEnabled` with `FeatureSwitches`.

### Saved state and process death

- Every destination is `@Serializable`: `CampfireViewModel` writes the whole stack into its `SavedStateHandle` as JSON
  on every change and restores it, because Navigation 3's per-entry saved state (keyed by `contentKey`, editor text
  included) is only claimed by an entry with the same key.
- Saved state is one Binder transaction, so: the editor saves only text and caret, only up to 50,000 characters
  (`EditorFieldSaver`; the field's own saver stores undo history with the document twice per transposition); the back
  stack only up to 100,000 characters of JSON (`MAX_SAVED_BACK_STACK_LENGTH`), so a huge setlist restores one screen
  short. Search fields cap at 100 characters (`MAX_SEARCH_QUERY_LENGTH`, pickers included) for the same reason.
- The editor field (undo included) survives configuration changes via the view model (`retainEditorField`). A long
  document from a killed process takes its text from the on-disk draft (below); only if that is missing too does it
  open on the file, with a snackbar.
- **Editor draft** (`preferences/editor-draft.json`), for when no process is restored (iOS, desktop, web, a swiped-away
  Android task): written on every pause (`onAppPaused`), removed once nothing is unsaved. On desktop quit it is
  settled before the process ends (`settleSynchronizationBeforeExit`). A start that finds it reopens the editor on it
  behind the launch screen with a snackbar, or hands it to a restored editor (`recoveredEditorField`); kept while it
  holds unsaved text.
- The song filter, both searches, and the open form sheet (`SavedDialog`: kind and file names, reopened through its
  ordinary opener once the library is read) are saved too. Confirmations, the export screen, and the tuner are never
  reopened.
- `NavigationState` is where the user is beyond the stack (searches, settings tab, each details screen's page as
  `initialIndex` via `onSongDetailsPageSettled`); `restoreNavigationState` applies it in one step and refuses while the
  editor holds unsaved text.

### `BrowserRoutes.kt`

- The web's addresses (see `src/wasmJsMain`), pure and tested: `BrowserRoutes` maps `RouteInputs` to the paths of
  every entry, and a path back to a `NavigationState`. Song = file name without `.cho` (other extensions kept),
  setlist without `.setlist.json`, percent-encoded as `encodeURIComponent` (`PercentEncoding.kt`) with `~` escaped
  too, since the site's 404 page carries `&` as `~and~`.
