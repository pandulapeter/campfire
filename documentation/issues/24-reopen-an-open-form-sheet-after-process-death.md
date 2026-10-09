# Reopen the form sheet that was open when Android killed the process, with what had been typed into it

**Decided:** the user took this plan on 2026-10-09; its condition below no longer applies.
**Challenged:** amended — a file-target edit sheet also waits for the song's text (its openers return silently without it); the restore gives up if the top of the back stack changed or an editor draft does not arrive, instead of waiting forever and putting the sheet up over a later editor; a restore that gives up clears the entry.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android (the only platform that restores a process)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialog.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SavedStateStore.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialogTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

> Conditional on a user decision. **Drop this plan if the user declines**, that is, if a sheet closing after the
> system killed the app in the background is acceptable. It is a medium-sized change (estimate below) for a moment
> most users never notice.

## Problem

An Android user is filling in a sheet: New song with its title and artist typed, the links of a song, Edit setlist
with a description half written, the tags of a song. They switch to another app to copy something, and the system
reclaims Campfire's process. When they come back, the screen underneath is back (the back stack is in the
`SavedStateHandle`, see `ui/navigation/CLAUDE.md`), but the sheet and everything typed into it are gone.

The sheets do save their state. At b5c8ed3b5 every form sheet keeps its fields in `rememberSaveable`:
- `NewSongDialog.kt`: `var values by rememberSaveable(stateSaver = songMetadataSaver) { … }`
- `SetlistDetailsDialog.kt`: `setlistTitle`, `description`, `dateText`, `isCountdownShown`
- `SongLinksDialog.kt`: `rows`
- `SongMetadataDialog.kt`: `values`
- `SongTagsDialog.kt`: `selectedTags`, `createdTags`, `query`
- `SongLanguagesDialog.kt`: `selectedCodes`, `query`
- `SongPlayingDialog.kt`: `values`
- `CoverArtSearchSheet.kt`: the query fields and the address
- `SongPicker.kt`: `selectedSongFileNames`, `query`
- `SetlistPicker.kt`: `query`, `isNamingNewSetlist`

That state does go into the Activity's saved state, and it does come back in a restored process. Nothing composes the
sheet to claim it, though, because what decides which sheet is up, `DialogHost.visibleDialog`, is a `MutableStateFlow`
in the view model that `SavedStateStore` never writes (it writes the back stack, the song filter, the picker chips
and the two searches). Today those saved values only help across a configuration change, where the view model, and
with it `visibleDialog`, survives anyway.

`DialogType` itself can't simply be serialized. Its variants carry `Song` and `Setlist` domain models (not
`@Serializable`, and they have no reason to be), and snapshots taken as the sheet opened: `SongMetadata.values`,
`SongLinks.links`, `SongPlaying.values`.

## Fix

Save a small descriptor of the visible form sheet next to the back stack. Once the library has been read, rebuild the
`DialogType` from it through the same openers the menus use, at the same composition position (the `when` in
`CampfireDialogs`), so the sheet's saved values reattach to it.

**`dialogs/SavedDialog.kt`** (new): a `@Serializable sealed interface SavedDialog` holding only file names, plus the
two mappings:

```kotlin
@Serializable
internal sealed interface SavedDialog {
    @Serializable data object NewSong : SavedDialog
    @Serializable data object NewSetlist : SavedDialog
    @Serializable data class EditSetlist(val setlistFileName: String) : SavedDialog
    @Serializable data class DuplicateSetlist(val setlistFileName: String) : SavedDialog
    @Serializable data class SongPicker(val setlistFileName: String) : SavedDialog
    @Serializable data class SetlistPicker(val songFileName: String, val setlistFileName: String?) : SavedDialog
    @Serializable data class SongEdit(
        val kind: Kind,
        val songFileName: String,
        val isEditorDraft: Boolean,
        val setlistFileName: String?,
    ) : SavedDialog {
        enum class Kind { TAGS, LANGUAGES, METADATA, LINKS, PLAYING, COVER_ART }
    }
}

/** What of [this] is worth reopening in a restored process: the sheets something is typed or picked into, and only those. */
internal fun DialogType.toSavedDialog(): SavedDialog? = when (this) { … }
```

Deliberately left out, with the reasons in the KDoc: every confirmation (`Delete*`, `Remove*`, `DisconnectSync`,
`ConfirmImportReplace`, `RevertChanges`, `ConfirmExit`, `UnsavedChanges`), since reopening a question the user never
answered is worse than losing it; `DeleteLibrary`, whose typed confirmation must never outlive the moment; `Export`
(a screen with a PDF pipeline of its own); `Tuner` (it must only ever listen from a tap); `Welcome` and `WhatsNew`
(start-up already decides those); and `SongInfo`, `ChordShapes` and `SongFilters`, which hold nothing typed.
Optionally `underlyingSongInfo` can be saved as well (the Song info sheet under an edit sheet opened from it), as a
second `songFileName`. Without it the restored edit sheet closes back to the screen rather than to Song info, which is
acceptable.

**`SavedStateStore`**: add `const val VISIBLE_DIALOG_KEY = "visibleDialog"`. In `CampfireViewModel`'s `dialogHost`
setup, add an `addAfterDialogChange { _, dialog -> savedStateStore.persist(VISIBLE_DIALOG_KEY, dialog?.toSavedDialog()) }`.
Every change is written, so a sheet that closes clears the entry. The descriptor is a few hundred bytes. It adds
nothing to the Binder budget that the sheets' own `rememberSaveable` values don't already use today.

**Restoring** (in `CampfireViewModel.init`, or a small `restoreVisibleDialog()` it launches): read
`savedStateStore.restore<SavedDialog>(VISIBLE_DIALOG_KEY)` once. If it holds something:
1. Wait until the library has been read **and indexed**. Use `songLookup.first { it.isLibraryRead }` if plan 21
   landed, or otherwise `isLoading.first { !it }` followed by waiting until `songsByFileName` holds the needed file.
   The openers resolve the song through that lookup, and a song not in the index yet would read as gone.
2. For `isEditorDraft`, also wait until `editorDraft.value?.fileName == songFileName`. The openers read the draft's
   text through `songTextOf(SongEditTarget.EditorDraft)`, and the restored editor reports it only once composed (and,
   with plan 20, once the recovery has run).
   **Bound both waits.** Take `backStack.lastOrNull()` as the restore starts, and give up as soon as the top is
   something else (`snapshotFlow { backStack.lastOrNull() }` raced against the waits, or `navigationGeneration`): a sheet
   reopened over a screen the user has moved to since would be a sheet over the wrong screen. For `isEditorDraft`,
   restore only when that top is the `SongEditor` of `songFileName`; otherwise the wait for `editorDraft` could never end
   (the editor closed from its not-loaded pane, performance mode turned on) and would put the sheet up the next time
   the user opens an editor on that song, minutes later.
2b. For a `SongEdit` with `isEditorDraft = false`, also `loadSongContent(songFileName).join()` and require
   `songTexts.value[songFileName]` to be there: `showSongMetadataDialog`, `showSongLinksDialog` and
   `showSongPlayingDialog` read the text through `songTextOf(SongEditTarget.File)`, which is `songTexts`, and return
   without a word when it is missing. In a restored process nothing has read it until the details screen composes
   and asks, and the library read finishing says nothing about it.
3. Only if no dialog has been shown in the meantime (`dialogHost.visibleDialog.value == null`: the welcome, what's new
   or an import question go first), rebuild through the existing openers:
   - `NewSong` → `showDialog(DialogType.NewSong)`, `NewSetlist` likewise.
   - `EditSetlist` / `DuplicateSetlist` / `SongPicker` → the setlist from `setlists.value` by file name.
   - `SetlistPicker` → the song from the lookup.
   - `SongEdit` → `showSongTagsDialog` / `showSongLanguagesDialog` / `showSongMetadataDialog` / `showSongLinksDialog`
     / `showSongPlayingDialog` / `showSongCoverArtDialog`, with `SongEditTarget.File` or `.EditorDraft`.
   A file name that no longer resolves restores nothing. A restore that gives up, for any of the reasons above, writes
   `null` under `VISIBLE_DIALOG_KEY`, so that the next process death does not try the same stale descriptor again.

Because the openers read the text again, `SongMetadata` and `SongPlaying` take a fresh "offered" snapshot. That is
the right one: `setSongMetadata` / `setSongPlaying` write only the fields that differ from what was offered, and the
sheet's restored `values` hold what the user typed.

The saved values reattach only if the sheet composes at the same compound key as before. `CampfireDialogs` composes
every sheet from one `when (visibleDialog)` without `key(...)`, and the `rememberSaveable` keys inside take only the
song's file name as input, so the keys are deterministic. A `ModalBottomSheet`'s content is a subcomposition seeded
from its parent's key. **Verify this first on a device** with one sheet (New song). If the values do not reattach, the
plan shrinks to reopening the sheet empty, which the user should then decide on again.

**Size, honestly.** Roughly 120 lines of main code: the new file with its KDoc (about 70), the persisting listener
and the restore (about 50), and the openers reused unchanged. Plus about 60 lines of tests and two documentation
paragraphs. It depends on plan 21's `songLookup` for a clean wait, and for `EditorDraft` targets on plan 20.

Docs:
- `ui/navigation/CLAUDE.md`, the paragraph listing what the `SavedStateHandle` holds: add "and the form sheet that
  was up (`SavedDialog`: its kind and file names), reopened through its ordinary opener once the library has been read,
  where the sheet's own saved fields find it again; confirmations, the export screen and the tuner are never reopened".
- `ui/dialogs/CLAUDE.md`: one sentence that a new form sheet with typed input gets a `SavedDialog` variant, and a new
  confirmation deliberately does not.

## Tests

`SavedDialogTest.kt` (commonTest, new):
- `toSavedDialog()` for each form variant gives the descriptor with the right file names, including
  `SongEditTarget.EditorDraft` → `isEditorDraft = true` and `SongPlaying`'s `setlistFileName`.
- `toSavedDialog()` is null for `DeleteLibrary`, `UnsavedChanges`, `ConfirmExit`, `Export`, `Tuner`, `DeleteSong`,
  `SongInfo`.
- Every `SavedDialog` survives `Json.encodeToString` / `decodeFromString` through `SavedStateStore`, the way
  `SavedStateStoreTest` already does for its other keys.

The restore itself waits on view model flows and is not unit-tested. Add to the manual check: open a song's Links sheet from the details screen, kill the process, and on reopening it comes back holding the typed rows (this is the case that needs step 2b); and open an editor's Tags sheet, kill, reopen, close the editor from its loading pane before it opens if you can, then open that song's editor again: no Tags sheet appears.

## Manual check

On an Android phone or emulator, for New song, Edit setlist and a song's Links sheet (from the details screen, and once
from the editor's menu):

1. Open the sheet, type into two fields, press Home, and run `adb shell am kill <application id>`.
2. Reopen from Recents. The screen comes back with the sheet up over it, holding what was typed, and the keyboard is
   not forced up.
3. Save. The change lands as it would have before.
4. Open a Delete song confirmation, kill the process the same way, and reopen. The confirmation does not come back.
