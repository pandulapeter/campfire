# 46 · An editor whose file disappears turns into an endless spinner and drops the draft

**Severity:** data loss (all platforms; unlikely — the file has to go away, or fail to be read once, while the
editor is open: a sync run carrying out a deletion, a rename in Finder / Files on desktop and iOS, a read error
during a rescan) plus wrong behaviour (an editor whose first read fails spins forever) · **Area:** `:presentation` —
`SongEditorScreen`, `CampfireViewModel` (the invalidation collector)

## Symptom
A. The draft is lost:
1. Desktop: open `song.cho` in the editor and type without saving.
2. Switch to Finder and rename, move or delete the file in the library folder — or let a sync run delete it because
   another device did.
3. Come back to Campfire. The text is replaced by a loading indicator that never ends. There is no app bar, so on a
   touch screen the back gesture is the only way out, and Back leaves without asking: the draft is already gone.
   "An edit beats a deletion" does not help, since the edit never reached the disk.

The same happens when the file is still there but the re-read after a rescan fails once, for whatever reason.

B. The first read fails: open the editor on a song whose file cannot be read (deleted by a second browser tab on
the web, which never rescans; made unreadable on desktop; a storage error of any kind). The editor shows the loading
indicator forever, where the details screen shows "This song could not be loaded" with a Retry.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:140-160`
decides between the editor and the loading pane from `songTexts` on **every** emission, not only while opening:

```kotlin
val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
LaunchedEffect(destination.fileName) { viewModel.loadSongContent(destination.fileName) }
AnimatedContent(
    targetState = songTexts[destination.fileName],
    contentKey = { it != null },
) { initialText ->
    if (initialText == null) LoadingPane(…) else LoadedSongEditor(…)
}
```

and the view model's invalidation collector (`CampfireViewModel.kt:657-665`) removes a text whose re-read returns null
(`SongContentRepositoryImpl.loadSongContent` answers null for a missing file and for any read error alike):

```kotlin
val content = getSongContent(name)
_songTexts.update { if (content == null) it - name else it + (name to content.text) }
```

So the entry disappears, `AnimatedContent` swaps `LoadedSongEditor` out, and with it go the `rememberSaveable`
`TextFieldState` (`:183`) and — through `ReportDraft`'s `onDispose { viewModel.onEditorClosed() }` (`:615`) — the view
model's draft. `hasUnsavedEditorChanges` is then false, so nothing asks on the way out. Nothing ever loads the text
again, hence the endless spinner.

B: `loadSongContent` (`CampfireViewModel.kt:953-961`) records a failed read in `failedSongFileNames`, which only
`SongDetailsScreen.kt:130` reads.

## Fix
Once the editor has its text it keeps it: what `songTexts` says afterwards only decides whether there is something
unsaved. A file that is gone then reads as unsaved changes all by itself
(`draft.text != songTexts[fileName]`, the right side being null), so Save is enabled, leaving asks, and saving writes
the file back — `FileStorage.writeText` creates it and `SongRepositoryImpl.saveSong` puts the song back into the
list.

It goes back **under its own name**, not under a new one derived from the header: the name is what the setlists and
the saved transposition point at, it is free by definition, and should something else take it in the meantime the
editor is in the already documented "file changed underneath it" state. The user is told when it happens, which is
the offer: nothing is written until they press Save.

1. **`SongEditorScreen.kt`, `SongEditorScreen`** (`:140-160`). Replace the body with:

   ```kotlin
   val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
   val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
   // The text the field starts from, taken once. What the file holds afterwards is only ever compared with what has
   // been typed: an editor that followed songTexts would be taken apart, the field and the draft with it, by a
   // sync run deleting the file or a rescan failing to read it - the two moments the draft is the only copy left.
   var initialText by remember(destination.fileName) { mutableStateOf(viewModel.songTexts.value[destination.fileName]) }
   LaunchedEffect(destination.fileName) {
       viewModel.loadSongContent(destination.fileName)
       initialText = initialText ?: viewModel.songTexts.mapNotNull { it[destination.fileName] }.first()
   }
   AnimatedContent(
       modifier = modifier.fillMaxSize(),
       targetState = initialText,
       transitionSpec = { fadeIn() togetherWith fadeOut() },
       contentKey = { it != null },
   ) { text ->
       if (text == null) {
           SongNotLoadedPane(
               contentPadding = contentPadding,
               hasFailed = destination.fileName in failedSongFileNames,
               onRetry = { viewModel.loadSongContent(destination.fileName) },
               onClose = onBack,
           )
       } else {
           LoadedSongEditor(
               viewModel = viewModel,
               destination = destination,
               initialText = text,
               hasSavedText = songTexts[destination.fileName] != null,
               windowSize = windowSize,
               contentPadding = contentPadding,
               onBack = onBack,
           )
       }
   }
   ```
   New imports: `androidx.compose.runtime.remember`/`mutableStateOf`/`getValue`/`setValue` where missing,
   `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.mapNotNull`. A retry needs nothing more: the effect is
   still waiting on the same flow. `remember` and not `rememberSaveable`: after a recreation `songTexts` answers at
   once (the view model survived), and after process death the text is read again and the field restores itself
   from its own saved state as it does today.

2. **Same file, `LoadingPane`** (`:162-168`) becomes the pane for both states; rename it, since the old name would lie:

   ```kotlin
   /**
    * What the editor shows until it has a text to open with. A read that failed says so and offers the way out as
    * well as the retry, because this state has no app bar to leave by.
    */
   @Composable
   private fun SongNotLoadedPane(
       contentPadding: PaddingValues,
       hasFailed: Boolean,
       onRetry: () -> Unit,
       onClose: () -> Unit,
   ) = Box(
       modifier = Modifier.fillMaxSize().padding(contentPadding),
       contentAlignment = Alignment.Center,
   ) {
       if (hasFailed) {
           EmptyState(
               icon = painterResource(Res.drawable.ic_error),
               title = stringResource(Res.string.song_details_no_data),
               hint = stringResource(Res.string.song_details_no_data_hint),
               actions = listOf(
                   EmptyStateAction(text = stringResource(Res.string.retry), onClick = onRetry),
                   EmptyStateAction(text = stringResource(Res.string.close), onClick = onClose),
               ),
           )
       } else {
           DelayedLoadingIndicator()
       }
   }
   ```
   This is the details screen's own failed state (`SongDetailsScreen.kt:484-497`) with a second action; the four
   strings exist already in both languages and are reused, not duplicated.

3. **Same file, `LoadedSongEditor`**: add `hasSavedText: Boolean` after `initialText`, and use it where Revert is
   offered (`:295`), since there is nothing to revert to while the file is gone and `RevertOnRequest` would silently
   do nothing:

   ```kotlin
   EditorMenu(
       canRevert = hasUnsavedChanges && hasSavedText && !isSaving,
       …
   ```

4. **`CampfireViewModel.kt`, the invalidation collector** (`:657-665`): say so when the text that went away is the
   one being edited. `affected` only holds names that *had* a text, so this fires once per disappearance and not on
   every later rescan.

   ```kotlin
   affected.forEach { name ->
       val content = getSongContent(name)
       _songTexts.update { if (content == null) it - name else it + (name to content.text) }
       // The editor keeps what it has, and with no text to compare it to that now counts as unsaved. It is said
       // out loud because saving is what puts the file back, which the user would otherwise have no reason to do.
       if (content == null && _editorDraft.value?.fileName == name) {
           _messages.send(Message.EditedSongFileGone)
       }
   }
   ```
   Add to `Message` (`:1634`), after `SaveFailed`:

   ```kotlin
   /** The file of the song in the editor is no longer there; the editor's text is, and saving writes it back. */
   data object EditedSongFileGone : Message
   ```
   Extend the comment above the collector: "…and an editor whose file changed underneath it now has unsaved
   changes, which is what asks the user before their draft replaces the new version — an editor whose file is gone
   included, where the draft is all there is."

5. **`CampfireApp.kt`** (`:450`), in the `when`:
   `CampfireViewModel.Message.EditedSongFileGone -> stringResource(Res.string.song_editor_file_gone)`.

6. **Strings**, in the editor group after `song_editor_save_failed`, in both files:
   - `values/strings.xml`: `<string name="song_editor_file_gone">This song\'s file is no longer in the library. Save to put it back.</string>`
   - `values-hu/strings.xml`: `<string name="song_editor_file_gone">A dal fájlja már nincs a könyvtáradban. Mentéssel visszakerül.</string>`

   (Check how the neighbouring strings escape an apostrophe and follow them.)

What must not be changed: `ReportDraft`'s `onDispose { onEditorClosed() }` stays — with step 1 the editor is only
disposed when it is really left. Do not make the collector keep a stale text in `_songTexts` instead: the details
screen and the tag edits build on that map, and for them a file that is gone has to read as gone.

Known limit, accepted: after a *process death* with the file gone, the first read fails and the failure pane is
shown; the text in the field's saved state is not reachable from there. Plan 14 decides what that saved state holds.

## Tests
None (the UI and the view model are untested).

## Verify
`./gradlew :app:desktop:run`:
1. Open a song in the editor, type, then in Finder delete `library/songs/<file>.cho`; click back into Campfire (the
   resume rescan runs). The editor is still there with the text, the snackbar says the file is gone, Save is
   enabled, Revert is disabled. Escape asks about unsaved changes. Save: the file exists again with the draft, and
   the song is in the list.
2. Same, without typing anything first: same result (the file can be put back, or left with Discard).
3. Sync (two devices or the Dropbox web UI): delete the song remotely, run a sync on the device with the editor
   open: same as 1; the next run uploads the saved file.
4. First-read failure: on macOS `chmod a-r` a song file, open it in the editor from the song list's menu: the error
   state with Retry and Close. `chmod u+r`, Retry: the editor opens. Close leaves.
5. Ordinary opening, editing, saving, rotating (Android) still behave as before; an editor opened while a sync run
   *changes* the file still shows Save enabled.
Compile: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`:
- the `CampfireViewModel` bullet, "…a sync run or a rescan has every open text read again, so the details screen
  shows the downloaded version in place and an editor whose file changed underneath it has unsaved changes": add
  "— as has one whose file is gone: the editor keeps the text it opened with rather than following `songTexts`,
  says that the file has gone (`Message.EditedSongFileGone`), and saving writes it back under its own name, which is
  what the setlists point at".
- the editor's paragraph: add one sentence that a first read that fails shows the details screen's failed state with
  Retry and Close instead of the loading indicator.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/CLAUDE.md`

## Depends on
**05** — no code of it is needed here, but it edits the same view model, and it is what makes the dialog's Save
trustworthy in exactly this situation (the write that puts the file back may be the one that fails). Plan **14**
rewrites the `rememberSaveable(… TextFieldState.Saver)` a few lines below step 1's hunk; the two do not overlap.
