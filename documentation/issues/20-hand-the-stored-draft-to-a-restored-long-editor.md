# Hand the draft on disk to an editor Android restored without its long text, instead of deleting it

**Kind:** bug  ·  **Severity:** high (data loss)  ·  **Platforms:** Android
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSession.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorField.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/LoadedSongEditor.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSessionTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`

## Problem

An Android user types into a song longer than 50,000 characters, leaves with Home, and the system kills the process in
the background. When they come back, the text they typed is gone: the editor opens on the file and a snackbar says the
draft was lost. But the app *did* write that text to disk (`preferences/editor-draft.json`), and then deleted the copy
itself during start-up.

The steps, at b5c8ed3b5:

1. **The pause writes the draft.** `AppExitController.onAppPaused` stores `editorSession.currentEditorDraftToStore()`,
   the whole unsaved text, whatever its length.
2. **The saved state keeps only a flag for a long document.** `EditorFieldSaver.save` in `EditorField.kt`:

   ```kotlin
   return if (text.length <= LONG_DOCUMENT_LENGTH) {
       listOf(text, textFieldState.selection.start, textFieldState.selection.end)
   } else {
       // Whether there was anything to lose, so that an untouched long file does not come back with an apology.
       listOf(text != fileText())
   }
   ```

3. **Recovery skips any stack that already has an editor.** The restored process rebuilds the back stack from the
   `SavedStateHandle` in `Navigator`'s constructor, which runs before `startRecovery()` in `CampfireViewModel.init`.
   `EditorSession.recoverEditorDraft`:

   ```kotlin
   editorDraftStoreMutex.withLock { storedEditorDraft = draft }
   if (draft == null || backStack.any { it is CampfireDestination.SongEditor }) return false
   ```

4. **The collector then deletes the draft.** `startRecovery` goes on to

   ```kotlin
   hasUnsavedEditorChanges.collect { if (!it && !hasUnsavedEditorText()) storeEditorDraft(null) }
   ```

   No editor has composed yet, so `_editorDraft` is null and both checks say "nothing unsaved". `storeEditorDraft(null)`
   differs from `storedEditorDraft` (the draft just read), so `saveEditorDraft(null)` deletes the file.
5. **The editor restores from the file.** `EditorFieldSaver.restore` gets `listOf(true)`, finds no `retained()` field
   (nothing was retained in this new process), and builds `EditorField(TextFieldState(fileText()), isDraftLost = true)`.
   `LoadedSongEditor` then calls `viewModel.onEditorDraftLost()`, which shows `Message.EditorDraftLost`.

The snackbar is the only one telling the truth here. `navigation/CLAUDE.md` describes the draft file as the fallback
"where no process is restored". A restored process that could not hold the text is the one case where the file is the
only copy, and it is thrown away.

**The order matters for the fix.** `CampfireContent` composes once the preferences are read, and it does not wait for
the recovery: only the launch screen does (`hasLibraryToShow`). The restored `SongEditorScreen` therefore composes
early. Its `initialText` is null at first, because `songTexts` is empty and nothing is retained, so it shows
`SongNotLoadedPane`. It composes `LoadedSongEditor`, where the saver's `restore` runs, only after
`loadSongContent(fileName).join()` in its `LaunchedEffect`. That song read and the recovery's draft read
(`getEditorDraft()`) are two independent IO reads started at nearly the same moment. Today nothing makes the recovery
finish first, so handing the draft over is not enough unless the editor also waits for it.

## Fix

Three parts. Each one is needed.

**1. Give the restored editor the draft, and keep the file.** In `EditorSession`, add a second slot next to
`retainedEditorField`, used only by the saver's "long text lost" branch. A long field's restore then gets the draft,
while a short field still restores its own saved text and caret: if the draft went through `retainedEditorField`,
`restore` would prefer it every time and drop the caret. Don't pick by comparing lengths with `LONG_DOCUMENT_LENGTH`:
the stored draft went through `fileTextOf`, so a German `B`/`Bb` respelling can move its editor-notation length across
the 50,000 boundary.

```kotlin
/**
 * The draft a previous run left, for the editor an Android process was restored on. Its saved state holds the
 * text and the caret, except for a long document, which comes back with only whether anything was unsaved (see
 * EditorFieldSaver): that one is given this instead of its file. Written at the same pause as the saved state,
 * so it is the same text.
 */
private var recoveredEditorField: Pair<String, TextFieldState>? = null

fun recoveredEditorField(fileName: String) = recoveredEditorField?.takeIf { it.first == fileName }?.second
```

In `recoverEditorDraft`, replace the early return with:

```kotlin
editorDraftStoreMutex.withLock { storedEditorDraft = draft }
if (draft == null) return false
if (backStack.any { it is CampfireDestination.SongEditor }) {
    // An Android process restored on the editor. Its own saved state decides what the field holds, but the draft is
    // reported as the editor's text before the editor composes, so that the collector below does not take the
    // stored draft for a stale one and delete the only copy of a long document's text.
    if (backStack.any { it is CampfireDestination.SongEditor && it.fileName == draft.fileName }) {
        arePreferencesLoaded.first { it }
        val text = editorTextOf(draft.text)
        onEditorTextChanged(fileName = draft.fileName, text = text)
        recoveredEditorField = draft.fileName to TextFieldState(initialText = text)
    }
    return false
}
```

The value stays `false`: the editor was not *reopened*, and `FirstRunController.navigateOnLaunch` and the other
start-up navigations already step aside for any stack that has more than the songs on it. Clear
`recoveredEditorField` wherever `retainedEditorField` is cleared (`onBackStackChanged`). Once the field has been taken
the slot has done its job; leaving it set is harmless, because the saver only reads it in the `size == 1` branch.

If the draft names a different file from the editor on the stack (which should not happen, since the draft is always
the open editor's), the behaviour stays as it is today: the draft is not adopted, and the collector removes it.

**2. Use it in the saver.** Add a `recovered: () -> TextFieldState?` parameter to `EditorFieldSaver`, and use it in the
long branch only:

```kotlin
override fun restore(value: Any): EditorField {
    retained()?.let { return EditorField(it) }
    val saved = value as List<*>
    return if (saved.size == 1) {
        recovered()?.let { EditorField(it) }
            ?: EditorField(textFieldState = TextFieldState(initialText = fileText()), isDraftLost = saved[0] as Boolean)
    } else {
        // unchanged
    }
}
```

In `LoadedSongEditor`, pass `recovered = { viewModel.recoveredEditorField(destination.fileName) }`, and add the
delegating `fun recoveredEditorField(fileName: String)` to `CampfireViewModel` next to `retainedEditorField`. Update
the saver's KDoc: "A new process gets the text and the caret, or for a long document the draft the pause put on disk,
or failing that the file."

**3. Don't let a restored editor restore before the recovery has answered.** In `CampfireViewModel`, expose
`internal val isEditorDraftRecoveryPending get() = editorSession.isEditorDraftRecoveryPending` and
`internal suspend fun awaitEditorDraftRecovery() = editorSession.editorDraftRecovery.await()`. In `SongEditorScreen`:

```kotlin
var initialText by remember(destination.fileName) {
    mutableStateOf(
        // Not before the draft a previous run left has been looked at: an Android process restored on a long document
        // gets its text from there, and the field's saved state is only read once, as the loaded editor composes.
        if (viewModel.isEditorDraftRecoveryPending.value) {
            null
        } else {
            viewModel.songTexts.value[destination.fileName]?.let(editorTextOf)
                ?: viewModel.retainedEditorField(destination.fileName)?.let { "" }
        }
    )
}
LaunchedEffect(destination.fileName) {
    viewModel.loadSongContent(destination.fileName).join()
    viewModel.awaitEditorDraftRecovery()
    // ...the existing lines, unchanged
}
```

Normally the recovery finished long before any editor is opened, so it costs nothing. In a restored process the
launch screen is up for the whole wait anyway: `hasLibraryToShow` waits for `isEditorDraftRecoveryPending`. The
existing `if (initialText == null && hasOpened)` line then opens the restored editor over the file text, or over `""`
for a file that is gone, and the saver's restore takes the recovered field.

Leave `LONG_DOCUMENT_LENGTH`, the saver's `save` and `onAppPaused` as they are.

Docs:
- `ui/navigation/CLAUDE.md`, in the paragraph on the editor's saved state: replace "and a long document that came back
  from a killed process without its unsaved text says so in a snackbar" with "and a long document that came back from a
  killed process takes its unsaved text from the draft the pause put on disk (below), which the editor waits for. Only
  when that is missing too does it open on the file and say so in a snackbar". At the end of the draft sentence,
  replace "unless the restored stack already has an editor" with "unless the restored stack already has an editor, which
  is handed the draft instead (`recoveredEditorField`), and keeps the file for as long as it holds unsaved text".
- `screens/songEditor/CLAUDE.md`, last paragraph: one sentence saying that an editor composed in a restored process
  waits for the draft recovery before it opens (`awaitEditorDraftRecovery`), so a long document gets its draft back.

## Tests

Extend `EditorSessionTest` (commonTest, run with `:presentation:desktopTest`):

```kotlin
@Test
fun `a stored draft is handed to an editor the restored stack has for its file, and kept`() = runTest {
    files["a.cho"] = "[C]One"
    storedDraft = SongContent(fileName = "a.cho", text = "[C]Two")
    backStack += CampfireDestination.SongEditor(fileName = "a.cho")
    val fixture = fixture()
    fixture.session.startRecovery()
    assertFalse(fixture.session.editorDraftRecovery.await())
    runCurrent()
    assertEquals("[C]Two", fixture.session.recoveredEditorField("a.cho")?.text?.toString())
    assertEquals("[C]Two", fixture.session.editorDraft.value?.text)
    assertEquals(SongContent(fileName = "a.cho", text = "[C]Two"), storedDraft)
    assertEquals(emptyList(), fixture.messages)
}
```

Also add, to the existing `a stored draft is not recovered over an editor the stack already has` (the `b.cho` one), an
assertion that `recoveredEditorField("a.cho")` and `recoveredEditorField("b.cho")` are both null.

Add a saver test next to it (a new `EditorFieldSaverTest.kt` in the same package, MPL header). `restore` does not need
a `SaverScope`:
- `EditorFieldSaver(retain = {}, retained = { null }, recovered = { TextFieldState("draft") }, fileText = { "file" })`
  `.restore(listOf(true))` gives a field holding `"draft"` with `isDraftLost == false`.
- With `recovered = { null }`, `restore(listOf(true))` gives `"file"` with `isDraftLost == true`.
- `restore(listOf("short", 1, 2))` with a non-null `recovered` gives `"short"` with the selection `1..2`, which shows
  the short branch ignores the draft.

## Manual check

On an Android phone or emulator, with Developer options → "Don't keep activities" **off**:

1. Open a song, paste text into the editor until it is over 50,000 characters, and type a word at the end. Don't save.
2. Press Home, then `adb shell am kill com.pandulapeter.campfire.debug` (or the release id) to end the process the way
   the system does in the background.
3. Open the app from Recents. The editor comes back holding the pasted text and the word, with Save enabled and no
   "draft lost" snackbar. `preferences/editor-draft.json` is still in the app's data directory (`adb shell run-as <id> find . -name editor-draft.json`).
4. Save. The draft file goes away.
5. Repeat with a short song (under 50,000 characters). The text and the caret position come back as before.
