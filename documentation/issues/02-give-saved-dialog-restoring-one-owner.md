<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Give saving and reopening the form sheet a process death left open one owner, `SavedDialogRestorer`

**Kind:** structure · **Severity:** low · **Platforms:** all (the behaviour matters on Android)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialogRestorer.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongMetadataEditing.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialogTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialogRestorerTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CLAUDE.md`,
`.github/scripts/size_allowances.json`

Lands after plan 01 (it calls `SongFilesController.duplicateSongDialogOf`). Drop the `songFilesController` lambda below
and keep `duplicateSongDialogOf` where it is if plan 01 was not carried out.

## Problem

The one feature "a form sheet up when Android ended the process comes back" is split across three places of the view
model, none of them owned by anyone:

- The save half is a line inside the `DialogHost` listener in the view model's `dialogHost` initializer:

  ```kotlin
  addOpenDialogsChange { before, after ->
      // Every change is written, so that a sheet that goes takes its entry with it. The topmost one worth reopening
      // rather than the one on top, so that a sheet with a question over it is what a restored process opens.
      savedStateStore.persist(SavedStateStore.VISIBLE_DIALOG_KEY, after.asReversed().firstNotNullOfOrNull { it.toSavedDialog() })
      coverArtSearchController.onOpenDialogsChanged(before = before, after = after)
      songDetailsLookupController.onOpenDialogsChanged(before = before, after = after)
  }
  ```
- The reopen half is `restoreVisibleDialog()` and `reopenSavedDialog(saved, top)` at the bottom of
  `CampfireViewModel.kt` (about 60 lines with KDoc): a coroutine racing the reopening against the screen on top
  changing, then a `when` over every `SavedDialog` that waits for the library, the song text or the editor draft and
  calls the right opener.
- It is started from `init` by `restoreVisibleDialog()`, between `editorSession.startRecovery()` and
  `songSelection.startPruning(screenData)`.

Its policy (wait for the library and the text, give up when the top screen changes, clear the entry if nothing could be
reopened, let What's new and import questions go first) is untested.

## Fix

One commit: "Give saving and reopening the sheet a process death left open one owner."

1. In `dialogs/SavedDialog.kt`, add the pure choice the save half makes, so it can be tested:

   ```kotlin
   /**
    * The sheet of [openDialogs] (bottom first) a restored process should reopen: the topmost one worth reopening rather
    * than the one on top, so that a sheet with a question over it is what comes back.
    */
   internal fun savedDialogOf(openDialogs: List<DialogType>): SavedDialog? = openDialogs.asReversed().firstNotNullOfOrNull { it.toSavedDialog() }
   ```
2. In `state/SongMetadataEditing.kt`, add the opener the restorer needs for the six song edit sheets, so the restorer
   does not need six lambdas and the `when` does not stay in the view model:

   ```kotlin
   /** Reopens the song edit sheet [saved] names over [song], through the opener its menu uses, see `SavedDialogRestorer`. */
   fun showSavedSongEdit(saved: SavedDialog.SongEdit, song: Song) = when (saved.kind) {
       SavedDialog.SongEdit.Kind.TAGS -> showSongTagsDialog(song, saved.target)
       SavedDialog.SongEdit.Kind.LANGUAGES -> showSongLanguagesDialog(song, saved.target)
       SavedDialog.SongEdit.Kind.METADATA -> showSongMetadataDialog(song, saved.target)
       SavedDialog.SongEdit.Kind.LINKS -> showSongLinksDialog(song, saved.target)
       SavedDialog.SongEdit.Kind.PLAYING -> showSongPlayingDialog(song, saved.setlistFileName, saved.target)
       SavedDialog.SongEdit.Kind.COVER_ART -> showSongCoverArtDialog(song, saved.target)
   }
   ```

   (These are exactly the methods the view model's `showSong…Dialog` members delegate to today; check each name and
   signature in `SongMetadataEditing.kt` before writing it.)
3. New `dialogs/SavedDialogRestorer.kt`:

   ```kotlin
   internal class SavedDialogRestorer(
       private val scope: CoroutineScope,
       private val savedStateStore: SavedStateStore,
       private val dialogHost: DialogHost,
       private val backStack: List<CampfireDestination>,         // the navigator's snapshot list
       private val songLookup: StateFlow<SongLookup>,
       private val setlists: StateFlow<List<Setlist>>,
       private val editorDraft: StateFlow<SongContent?>,
       private val songTexts: StateFlow<Map<String, String>>,
       private val loadSongContent: (fileName: String) -> Job,
       private val duplicateSongDialogOf: suspend (Song) -> DialogType?,
       private val showSavedSongEdit: (SavedDialog.SongEdit, Song) -> Unit,
   )
   ```

   with:
   - `fun start()` — the body of today's `restoreVisibleDialog()`, KDoc moved with it (`restore<SavedDialog?>(…) ?: return`,
     the `top` captured **synchronously before** the launch, the `merge(flow { emit(reopen(saved, top)) },
     snapshotFlow { backStack.lastOrNull() }.filter { it != top }.map { false }).first()` race, and the
     `persist<SavedDialog?>(…, null)` when it was not reopened).
   - `private suspend fun reopen(saved: SavedDialog, top: CampfireDestination?): Boolean` — today's `reopenSavedDialog`,
     unchanged in order: the editor draft check against `top`; `songLookup.first { it.isLibraryRead }`; for a
     `SongEdit`, the draft wait or `loadSongContent(…).join()` and the `songTexts` check; then
     `if (dialogHost.visibleDialog.value != null) return false` ("What's new or an import question go first"); then the
     `when`, calling `dialogHost.showDialog(…)`, `duplicateSongDialogOf(…)` and, for `SavedDialog.SongEdit`,
     `showSavedSongEdit(saved, song)`; finally `return dialogHost.visibleDialog.value != null`.
   - `fun onOpenDialogsChanged(after: List<DialogType>)` — `savedStateStore.persist(SavedStateStore.VISIBLE_DIALOG_KEY, savedDialogOf(after))`,
     with the "Every change is written…" comment.
4. In `CampfireViewModel.kt`:
   - Declare `savedDialogRestorer` after `songFilesController` (plan 01) — every holder it reads exists by then. It
     starts nothing in its constructor.
   - The `addOpenDialogsChange` listener becomes
     `savedDialogRestorer.onOpenDialogsChanged(after)` followed by the two controllers' lines, **in that order**.
     The listener is registered in the `dialogHost` initializer, before `savedDialogRestorer` exists, which is fine
     only because the lambda reads the property when called; the first call can only come from a `showDialog`, and
     none happens before `init`. Keep the persist first.
   - In `init`, replace `restoreVisibleDialog()` with `savedDialogRestorer.start()` **at the same position** (after
     `editorSession.startRecovery()`, before `songSelection.startPruning(screenData)`): the back stack's top is read
     there, and the editor draft recovery started just before it may already have put an editor on top.
   - Delete `restoreVisibleDialog`, `reopenSavedDialog`, and the imports that only they used (`merge`, `flow`, `filter`,
     `map`, `snapshotFlow`, `SavedDialog`, `toSavedDialog` — check each).
   - The `restore` helper at the bottom stays if `_songFilter` still uses it.
5. Run `python3 .github/scripts/check_sizes.py --update` (the view model shrinks).

Docs: in `dialogs/CLAUDE.md`, the two places that describe it (the `SavedDialog` bullet near the top, and the line about
the topmost sheet with a `SavedDialog` being kept by `DialogHost.addOpenDialogsChange`) name `SavedDialogRestorer`
instead; in `navigation/CLAUDE.md`'s "Saved state and process death" the open form sheet's sentence may name it too.
Neither may grow (both packages are near or over budget: check with `check_sizes.py`).

## Tests

- `SavedDialogTest`: `the topmost sheet worth reopening is the one saved` — `savedDialogOf(listOf(NewSetlist, a
  confirmation over it))` is `SavedDialog.NewSetlist`; `savedDialogOf(listOf(SongPicker(setlist), SetlistPicker(...)))`
  is the setlist picker's; a stack of nothing reopenable is `null`.
- New `SavedDialogRestorerTest` (desktop `runTest`, `SavedStateStore(SavedStateHandle(), backgroundScope)`,
  `DialogHost(backgroundScope)`, a `mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)`,
  `MutableStateFlow`s for the lookup, setlists, draft and texts, lambdas that record calls):
  - `a saved new setlist sheet is reopened once the library is read`: persist `SavedDialog.NewSetlist`, `start()`, set
    the lookup to `SongLookup(emptyMap(), isLibraryRead = true)`; `dialogHost.visibleDialog.value` is
    `DialogType.NewSetlist` and the saved entry is unchanged.
  - `a saved sheet is given up once another screen is on top`: persist `SavedDialog.NewSetlist`, `start()`, push
    `CampfireDestination.Settings` while the lookup is unread, then read it; nothing is shown and the saved entry is
    `null`. (The `snapshotFlow` needs a snapshot apply: call `Snapshot.sendApplyNotifications()` after the push.)
  - `a saved sheet waits behind a dialog already up and is then forgotten`: show `DialogType.WhatsNew` first; nothing
    else is shown and the entry is `null`.
  - `a sheet over a song that is gone is forgotten`: persist an `EditSetlist("missing.setlist.json")`; entry `null`.

Then the root CLAUDE.md test command, the four platform compile tasks listed in the brief, `spotlessCheck`, and the
size check.

## Manual check

On Android with "Don't keep activities" off and the process killed in the background (`adb shell am kill
com.pandulapeter.campfire.debug` after pressing Home): open a song's **Tags** sheet, tick something, background and
kill, return — the sheet is back with the tick. Repeat with **New setlist** from the Setlists tab, and with Edit song
details opened from the editor (the editor-draft path).
