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

## Destinations

- **The editor always opens over its song's details screen** (`songEditorEntries`), so leaving it lands on the song, as
  the web's `song/{song}/edit` address does. Except a pasted or shared song with no file yet
  (`SongEditor.NEW_SONG_FILE_NAME`): its editor goes alone, so a discard lands where it was opened, and its first Save
  puts the details screen under it (`Navigator.followNewSongCreated`). It has no web address of its own (a reload
  reopens it from the draft).
- Songs and setlists are addressed by **file name** everywhere.
- `SongDetails` is keyed by an `id` generated once per push, so a rename rewrites the entry without Navigation 3 taking
  it for a new one. `SongEditor`'s `contentKey` excludes `shouldStartInsideFirstSection` (how it opens, not which
  editor); a new song's editor is keyed by its `entryKey`, so the first Save, rewriting its file name in place, is not a
  new editor sliding in.
- `ImportReport` carries nothing (one import at a time) and is never restored; `Message.ImportInterrupted` says so where
  the import had not been written yet (a flag saved beside the back stack). A `SongDetails` with an `importPreview` is
  dropped on restore the same way.
- Top level selection resets the stack to `[home, tab?]`, so Back always returns there. **The bottom of every stack is
  the home screen** (`TopLevel.home`: the first top level screen switched on, so Settings once everything is off);
  `Navigator.followFeatureSwitches` swaps it whenever the switches change (`rootedAt`), so code asking whether nothing
  has been built on the stack asks for one entry, never for `[Songs]`. A switch never navigates: an old root that is the
  tab on screen stays on top of the new one.
- A disabled tab leaves the chrome (animated by `chrome/NavigationItemPresence.kt`); a restored stack or opened address
  is cut at the first screen of a disabled feature (`NavigationState.withoutDisabledFeatures`), so `/metronome` with the
  metronome off opens the home screen and rewrites the address.
- **Interrupted transitions**: every entry reports `transition.isRunning` (`ReportNavigationTransition`); a back stack
  change during a running transition bumps `navigationGeneration` into the entry metadata so the new scene never equals
  the one it started from. Without it, Navigation 3 takes its "predictive back cancelled" path and gets stuck halfway.
- Top level screens keep their scroll in the view model (`ScrollPosition`, `rememberRetainedLazyGridState` /
  `rememberRetainedScrollState`): Navigation 3 drops an entry's remembered state and tab selection rebuilds the stack.

## Saved state and process death

- `CampfireViewModel` writes the whole stack into its `SavedStateHandle` as JSON on every change and restores it,
  because Navigation 3's per-entry saved state (keyed by `contentKey`, editor text included) is only claimed by an entry
  with the same key.
- Saved state is one Binder transaction, so: the editor saves only text and caret, up to 50,000 characters
  (`EditorFieldSaver`; the field's own saver stores undo history with the document twice per transposition); the back
  stack only up to 100,000 characters of JSON (`MAX_SAVED_BACK_STACK_LENGTH`), so a huge setlist restores one screen
  short; search fields cap at 100 characters (`MAX_SEARCH_QUERY_LENGTH`, pickers included).
- The editor field (undo included) survives configuration changes via the view model (`retainEditorField`). A long
  document from a killed process takes its text from the draft; only if that is missing too does it open on the file,
  with a snackbar.
- **Editor draft**, for when no process is restored (iOS, desktop, web, a swiped-away Android task): written on every
  pause (`onAppPaused`), removed once nothing is unsaved. On desktop quit it is settled before the process ends
  (`settleSynchronizationBeforeExit`). A start that finds it reopens the editor on it behind the launch screen with a
  snackbar, or hands it to a restored editor (`recoveredEditorField`); kept while it holds unsaved text.
- Confirmations, the export screen, and the tuner are never reopened.
- `NavigationState` is where the user is beyond the stack (searches, settings tab, each details screen's page);
  `restoreNavigationState` applies it in one step and refuses while the editor holds unsaved text.

## `BrowserRoutes.kt`

- Every top level screen has an address, the songs too (`songs`); the bare address opens the home screen and is
  rewritten to its address, so `/` and the old `search` keep working whichever screen the app opens on.
- Song = file name without `.cho` (other extensions kept), setlist without `.setlist.json`, percent-encoded as
  `encodeURIComponent` with `~` escaped too, since the site's 404 page carries `&` as `~and~`.
