# 02 · Unsaved editor text is lost when the phone is rotated after the song's file disappeared

**Severity:** data loss (Android: any configuration change - rotation, fold, dark mode, language - and process death.
Needs the edited song's file to go away underneath an open editor, by a sync run or a rescan that cannot read it, so
uncommon; but when it happens the draft is the only copy of the text and it is thrown away without a question) ·
**Area:** `:presentation` (`screens/songEditor/SongEditorScreen.kt`, `CampfireViewModel.onEditorClosed`)

## Symptom
1. Open a song in the editor and type something.
2. While the editor is open, the song's file is removed by a sync run (deleted on another device) or cannot be read
   by a rescan. The snackbar says the file is gone and that saving writes it back (`Message.EditedSongFileGone`);
   the editor keeps the text, as intended.
3. Rotate the phone (or fold/unfold, switch dark mode). The editor now shows the "Nothing could be loaded" pane with
   Retry and Close in place of the text. Retry fails again. Close leaves without the unsaved changes question, and
   the draft is gone.

The same happens after process death: an editor restored over a file that was deleted while the app was in the
background opens on the error pane, and the text saved in the Bundle is never used.

## Cause
The editor takes the text it starts from out of `songTexts`, in a plain `remember`
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:158-162`):

```kotlin
var initialText by remember(destination.fileName) { mutableStateOf(viewModel.songTexts.value[destination.fileName]) }
LaunchedEffect(destination.fileName) {
    viewModel.loadSongContent(destination.fileName)
    initialText = initialText ?: viewModel.songTexts.mapNotNull { it[destination.fileName] }.first()
}
```

Once the file is gone, the invalidation collector has already removed it from `songTexts`
(`CampfireViewModel.kt:741`: `if (content == null) it - name`). A recreated composition therefore starts with
`initialText == null`, `loadSongContent` fails (`failedSongFileNames`), and the `first()` suspends for good. With a
null `initialText` the `AnimatedContent` (`:163-186`) shows `SongNotLoadedPane`, so `LoadedSongEditor` is never
composed and its `rememberSaveable(saver = EditorFieldSaver(...))` (`:233-253`) never runs: neither the field the
view model retained across the configuration change (`retainedEditorField`, `CampfireViewModel.kt:1090-1096`) nor the
text saved for process death is ever claimed.

Leaving is then unguarded: the old editor's `ReportDraft` disposed with `viewModel.onEditorClosed()`
(`SongEditorScreen.kt:693`), which nulls the draft unconditionally (`CampfireViewModel.kt:1084`:
`fun onEditorClosed() = _editorDraft.update { null }`), so `hasUnsavedEditorChanges` is false and `navigateBack`
(`:946-952`) pops without asking. That unconditional clearing is also a window of "no unsaved text" during every
configuration change of an editor, which 14's fix would otherwise act on.

## Fix
Two parts, both needed.

### 1. The editor remembers that it had opened, and opens again without a file

`SongEditorScreen.kt`, replace `:155-162` (the comment, `initialText` and the `LaunchedEffect`) with:

```kotlin
    // The text the field starts from, taken once. What the file holds afterwards is only ever compared with what has
    // been typed: an editor that followed songTexts would be taken apart, the field and the draft with it, by a
    // sync run deleting the file or a rescan failing to read it - the two moments the draft is the only copy left.
    //
    // An editor that is composed again over a file that has gone - after a rotation, or in a process the system
    // restored - still has its field to come back to, retained by the view model or saved with the screen, so it
    // opens on that rather than waiting for a file that is not coming back. Whether it had opened is saved for the
    // same reason: in a new process that flag is all there is to tell a draft worth restoring from a song that was
    // never loaded. The empty text it opens with is only what the field is compared with, which is what the file
    // holds now.
    var hasOpened by rememberSaveable(destination.fileName) { mutableStateOf(false) }
    var initialText by remember(destination.fileName) {
        mutableStateOf(viewModel.songTexts.value[destination.fileName] ?: viewModel.retainedEditorField(destination.fileName)?.let { "" })
    }
    LaunchedEffect(destination.fileName) {
        viewModel.loadSongContent(destination.fileName).join()
        if (initialText == null && hasOpened) initialText = viewModel.songTexts.value[destination.fileName] ?: ""
        initialText = initialText ?: viewModel.songTexts.mapNotNull { it[destination.fileName] }.first()
        hasOpened = true
    }
```

Notes for the implementer:
- `loadSongContent` returns the `Job` of `viewModelScope.launch` (`CampfireViewModel.kt:1189`), so `.join()` needs no
  signature change. It clears `failedSongFileNames` synchronously (Main.immediate) before its read, so waiting on the
  job rather than on the set cannot see a stale failure.
- The retained-field branch is what keeps a rotation free of a loading frame: `retainEditorField` is called by the
  saver as the old composition saves (`EditorFieldSaver.save`), so on a configuration change it is always set, and
  `LoadedSongEditor`'s `restore` then returns that field (`retained()?.let { return EditorField(it) }`) whatever
  `initialText` is. No `AnimatedContent` change is animated, since it starts on the editor.
- After process death with the file gone, `restore` gets the saved `[text, selectionStart, selectionEnd]` and brings
  the draft back. A document over `LONG_DOCUMENT_LENGTH` saved only `[isDraftLost]`; it now opens empty, with the
  "draft lost" snackbar where there were edits (today it opens on the error pane either way). That is accepted: the
  saved state has no text to give back.
- `hasSavedText = songTexts[destination.fileName] != null` (`:179`) stays false, and `hasUnsavedEditorChanges`
  (`draft.text != songTexts[fileName]`, with nothing on the right) is true once `ReportDraft` reports, so Close asks
  the unsaved changes question and Save writes the file back under its name.
- No new imports: `rememberSaveable`, `remember`, `mutableStateOf`, `getValue`/`setValue`, `mapNotNull` and `first`
  are already imported (`:52-65`, `:130-131`).

### 2. A configuration change does not clear the draft

`CampfireViewModel.kt:1083-1084`, replace `onEditorClosed` with:

```kotlin
    /**
     * Reported by the editor once it is gone, whatever became of the text it had. An editor that is still on the
     * stack is only being composed again - a rotation, the system changing its theme - and its draft stands until the
     * new composition reports it: dropped in between, the moment would read as "nothing unsaved" to everything that
     * asks, the update gate and the way out of the editor included.
     */
    fun onEditorClosed(fileName: String) {
        if (backStack.none { it is CampfireDestination.SongEditor && it.fileName == fileName }) {
            _editorDraft.update { draft -> draft?.takeUnless { it.fileName == fileName } }
        }
    }
```

and in `SongEditorScreen.kt:693` pass the file name:

```kotlin
    DisposableEffect(fileName) { onDispose { viewModel.onEditorClosed(fileName) } }
```

Why this is safe: every way out of the editor removes it from `backStack` before its composition is disposed (the
exit animation runs first), so the draft is still cleared then; `leaveEditor` and `deleteSong` clear it themselves
anyway (`:1024-1026`, `:1144-1146`). The `takeUnless` keeps a later editor's draft from being cleared by an earlier
one's late dispose. A rename rewrites the editor entry to the new name (`updateSongFileName`, `:1015-1017`): the old
name is then off the stack, the old draft is cleared, and the new entry's `ReportDraft` reports it again under the
new name.

Do **not**:
- save `initialText` itself with `rememberSaveable`. It is the whole file, and the field's saver already writes up to
  `LONG_DOCUMENT_LENGTH` characters into the same Binder transaction (see the KDoc of `EditorFieldSaver`).
- have the editor follow `songTexts` again; the comment above it explains why.

## Tests
None (UI).

## Verify
Android `.debug` build (emulator, auto-rotate on):
1. Open a song in the editor and type a word. Delete the file behind the app's back: with sync connected, delete it
   in the remote folder and run a sync from another device/tab; or with `adb shell run-as <package> rm
   files/library/songs/<name>.cho` followed by a pull-to-refresh on the songs list opened in split screen (any
   rescan does). The "file is gone" snackbar appears.
2. Rotate. The editor stays, with the word, the caret and the undo history (retained field). No loading pane flashes.
3. Tap Close: the unsaved changes question appears. Save writes the file back; the song is in the list again.
4. Repeat 1, then put the app in the background and kill it (`adb shell am kill <package>` after enabling "Don't
   keep activities" or with `am kill`), reopen: the editor comes back with the word (saved state), and Close asks.
5. Regression: open the editor on an existing song, rotate: text, caret, undo history kept, no flash. Close without
   changes leaves without a question. Type, rotate, Close: the question is asked.
6. Regression: open the editor from a fresh process (song not yet read): the loading indicator, then the editor, as
   before.

Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
`:app:web:wasmJsBrowserDevelopmentRun` (compile only is enough: `:presentation:compileKotlinWasmJs`).

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireViewModel.kt` bullet, after "…the editor keeps the text it opened with
rather than following `songTexts`, says that the file has gone (`Message.EditedSongFileGone`), and saving writes it
back under its own name, which is what the setlists point at" insert: " — a rotation and a restored process
included: the editor saves whether it had opened, and one composed again over a file that has gone opens on its
retained or saved field rather than waiting for the file, while `onEditorClosed` leaves the draft alone for as long
as the editor is still on the stack, so a configuration change is never a moment with nothing unsaved".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 14 depends on part 2 of this plan.
