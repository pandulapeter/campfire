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

### `ui/navigation/CampfireDestination.kt`

`ui/navigation/CampfireDestination.kt` — `NavKey`s: `Songs`, `Setlists`, `Metronome`, `Settings` (top level),
`SongDetails(songFileNames, setlistFileName, initialIndex, id)` (keyed by the `id` generated once per push rather than
by its file names, so renaming a song from the details screen rewrites the entry without Navigation 3 taking it for a
new one) `SongEditor(fileName, shouldStartInsideFirstSection)` and `ImportReport`, which carries nothing since there is
only ever one import to report on, and is never restored (the view model's report is gone with the process).
`SongEditor`'s `contentKey` is its file name behind a prefix of its own, leaving `shouldStartInsideFirstSection` out,
since that says how the editor opens rather than which editor it is. Top level selection resets the stack to
`[Songs, tab?]` so back always returns to Songs. Every destination is
`@Serializable`, because `CampfireViewModel` writes the whole stack into its `SavedStateHandle` as JSON on every change
(`updateBackStack`, and the rename that rewrites entries in place) and reads it back when it is created: a process
Android killed in the background comes back on the screen it was showing, and Navigation 3's per-entry saved state —
keyed by `contentKey`, the editor's typed text included — only ever goes back to an entry with the same key, so a stack
that restarted on Songs would leave it all unclaimed.

The editor saves its text and caret only, and only up to 50,000 characters (`EditorFieldSaver`): the Activity's saved
state is one Binder transaction, and the field's own saver writes an undo history that holds the whole document twice
per transposition. For the same reason the view model saves a back stack only as far as it fits 100,000 characters of
JSON (a song opened from a setlist of thousands names every one of them, and a restored process then comes back one
screen short). The field itself, undo history included, is kept by the view model across a configuration change
(`retainEditorField`), and a long document that came back from a killed process without its unsaved text says so in a
snackbar. Where no process is restored — iOS, the desktop, the web, an Android task swiped away — the editor's unsaved
text is put in `preferences/editor-draft.json` whenever the app is paused (`onAppPaused`, which also starts an automatic
sync run that is still waiting, see the root `CLAUDE.md`) and removed as soon as nothing is unsaved — on a desktop quit,
written or removed before the process ends (`settleSynchronizationBeforeExit`), since the removal otherwise trails the
editor by a few hops; a start that finds it reopens the editor on it behind the launch screen, with a snackbar, unless
the restored stack already has an editor.

The song filter and the two searches are saved the same way; a search field takes at most 100 characters
(`MAX_SEARCH_QUERY_LENGTH`, the pickers' fields included), since its text goes into that saved state as well; on every
real start, and on the platforms that never restore a process, the handle is empty. Songs and setlists are addressed by
**file name** everywhere, which is their identity on disk. `NavigationState` next to it is where the user is as more
than the stack — the two searches, the settings tab, and each details screen's page as its `initialIndex` (the page the
pager settled on, reported through `onSongDetailsPageSettled`) — which `CampfireViewModel.navigationState` reads and
`restoreNavigationState` puts back in one step, refusing while the editor holds unsaved text.
