# 44 — iOS: unsaved editor text is lost when the system ends the app in the background

**Severity:** data loss (iOS; also Android when the task is swiped away, mobile browsers, a desktop crash) · **Area:**
`:presentation` (`CampfireViewModel.kt`, `CampfireApp.kt`, `screens/songEditor/SongEditorScreen.kt`, strings), with a
small new storage path through `:domain` and `:data` (`preferences/editor-draft.json`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced on a device. The
"Verification" section below is how to confirm it, and confirming it is the first step of the work.

**Decided by the user on 2026-09-23 (decision D-D): keep an unsaved editor draft across the system killing the app in
the background — persist it when the app is backgrounded, and offer it back on the next launch.** This plan is the
design for that. The choices inside it that the decision left open (which platforms, which lifecycle event, how it is
offered back) are recommendations and are marked as such.

## What the user sees

On an iPhone: open a song in the editor, type a verse, switch to another app (a message comes in, the camera is
needed at the gig). iOS suspends Campfire and, some minutes later and without a word, ends it to free memory. Opening
Campfire again starts it from scratch on the Songs screen; the verse is gone, and nothing says it ever existed.

The same happens on Android when the user swipes Campfire away from Recents (a removed task keeps no saved state), and
for a document over 50,000 characters even when Android only reclaims the process (`EditorFieldSaver` saves no text
for those and says so with `Message.EditorDraftLost`). A mobile browser that discards a background tab does it to the
web build (`beforeunload` is not fired for that, see `presentation/CLAUDE.md`, `CampfireWebApp`), and a desktop app
that crashes or is killed does it too.

## Cause

The editor's unsaved text lives in two places, both of them memory: the field (`TextFieldState`, in the composition
and, across a configuration change, in the view model — `retainEditorField`) and the draft the view model keeps for
the unsaved changes question (`_editorDraft`, `CampfireViewModel.kt:440`). What outlives the process is only the
`SavedStateHandle` and the composition's saved state (`EditorFieldSaver`, `SongEditorScreen.kt:798-830`;
`persistBackStack`, `CampfireViewModel.kt:882-890`), and only Android restores those: the view model's own KDoc says
the handle is "Empty on every real start, and on the platforms that have no such thing as a process being restored"
(`CampfireViewModel.kt:172-176`). iOS, desktop and the web restore nothing; an Android task that was swiped away
restores nothing either.

Nothing writes the draft anywhere durable: the only writes of editor text are the Save action and the unsaved changes
dialog's Save, both of which write the song file itself, and only when asked.

## The design

Invoke the **`code-style`** skill before the first edit.

### Where the draft is kept

`preferences/editor-draft.json`, through `FileStorage` (`StorageDirectory.PREFERENCES`): one JSON document,
`{"fileName": "…", "text": "…"}`, or no file at all when there is no draft.

- **Never in `library/`.** It is not a song: an export would ship it, a sync run would upload it (sync only sees what
  is a song or a setlist *by its extension inside the library*, so `preferences/` is invisible to it by construction),
  and a rescan would list it.
- **Not in the device backup.** Android's backup is an allow-list naming `library/` and
  `preferences/preferences.json` (`app/android/src/main/res/xml/full_backup_content.xml`,
  `data_extraction_rules.xml`), so a new file is excluded without a change there. iOS backs up the whole directory, so
  the draft says so itself the way `sync-index.json` does: `keepOutOfDeviceBackup` after every write
  (`SyncStateLocalSourceImpl.kt:43-49`). A draft restored onto another phone would reopen an editor over a song file
  that phone may not have, for text the user abandoned on the old one.
- **One draft.** The app has one editor at a time; the file names the song it belongs to.

### When it is written — recommended: `ON_PAUSE`, on every platform

`CampfireApp` already reacts to its lifecycle (`LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`, `CampfireApp.kt:176`).
Add an `ON_PAUSE` effect that asks the view model to store the draft: the draft if the editor holds unsaved text,
nothing (the file deleted) if not. A write is skipped when it would store exactly what is stored already.

- **`ON_PAUSE` rather than `ON_STOP`.** It always comes first, and on iOS an app swiped away from the app switcher may
  only ever have been paused (resigned active) before being ended; `ON_STOP` would miss exactly that case. On Android it
  also comes with every rotation and multi-window focus change; each costs one small write at most, and only while
  there is unsaved text.
- **Every platform rather than iOS only (recommended).** The decision names iOS, and iOS is where it is certain to
  matter, but the mechanism is `commonMain` code with no platform branch, and the other three have their own holes it
  closes: an Android task swiped away from Recents, an Android document over the saved state's 50,000 characters, a
  background tab a mobile browser discards, a desktop crash or `kill -9`. One path also means it can be exercised on the
  desktop, where killing the process is one command, rather than only on an iPhone. The cost elsewhere is the write on
  pause. If the user prefers iOS only, gate the `ON_PAUSE` effect on a platform `expect val` — nothing else changes.
- **Removed as soon as there is nothing unsaved**, not only on the next pause: once the draft has been read on launch,
  the view model follows `hasUnsavedEditorChanges`, and every way the editor ends up with nothing unsaved — Save, the
  dialog's Save or Discard, Revert, the song deleted, the file changed to match — deletes the stored draft. So a
  foreground crash after a save never brings back text that was already saved. (The reverse — text typed after the last
  pause — is lost to a crash in the foreground, as today; the store is for the background, where the system gives no
  notice, not a journal of keystrokes.)

### How it is offered back — recommended: the editor is reopened with the draft, and a snackbar says so

On a start that finds a stored draft, the view model reopens the editor on it before the launch screen goes: the stack
becomes `[Songs, SongEditor(fileName)]`, the field opens holding the draft, and a snackbar says "The unsaved changes
were restored". The draft is unsaved against the song file, so the editor is in exactly the state it was left in: Save
writes it, Close asks the unsaved changes question (Save / Discard / Cancel), and Discard is how the user throws it
away.

- **Why not a question on launch.** Reopening is what Android already does for a reclaimed process (it comes back *on the
  editor*, with the text, `presentation/CLAUDE.md`, `CampfireDestination`), and D-D asks for iOS to keep the draft the
  same way. A question at start up would be a new dialog, asked before the user has looked at anything, with a "no"
  that destroys the text; reopening asks nothing until the user leaves the editor, and then asks with the question the
  app already has for exactly this.
- **Not over a restored editor.** An Android process restored on the editor already has the text (from the saved
  state); a second editor on top of it would be the same text twice. When the restored back stack holds an editor, the
  stored draft is left alone and is overwritten or deleted by the ordinary rules. (A long document restored without its
  text keeps today's `EditorDraftLost` message; using the stored draft there would need the draft read before the
  restored editor composes, which is a wait on the composition this does not add.)
- **Not when there is nothing to restore.** A draft whose text equals the file's (a save that happened after the last
  pause, and a crash before the next) is deleted instead of reopened.
- **A file that is gone** (deleted by a sync run while the app was not running): the editor still opens on the draft,
  like an editor whose file went while it was open, and `Message.EditedSongFileGone` is sent too — saving writes the
  file back under its name, which is what the setlists point at.
- **The launch screen waits** for this decision, the way it waits for the demo library and the web's address
  (`hasLibraryToShow`, `CampfireViewModel.kt:323-327`): the editor is on the stack before the app is uncovered, so it is
  not seen sliding in (`hasShownApp` turns the transition off meanwhile). It is one small file read.
- **The other start-up navigations defer to it.** The web's address (`navigateOnLaunch`) waits for the decision and,
  as it already does, only acts on an untouched `[Songs]` stack; the sync consent jump only acts on an untouched stack
  after plan 30. Unsaved text wins over both.

### Consistency with `hasUnsavedEditorChanges` and the update gate

The reopened editor reports its text through `ReportDraft` like any other, and the view model sets `_editorDraft`
itself before pushing the editor, so from the first frame `hasUnsavedEditorChanges` is true: the update gate
(`AppUpdateGate.kt:84-86`, `:117`) holds the required update screen and the Restart offer back, `navigateBack` asks,
the web's `beforeunload` is registered. Nothing new is needed there. The stored draft does not make the text "saved":
it is a copy against the process ending, not a write of the song, so the gate still waits — a required update ends the
process and the draft would survive it, but ending the process under an editor the user is looking at is still not the
gate's to do.

## The change

### `:data:source:local` — the file

`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/EditorDraftLocalSource.kt`:

```kotlin
/**
 * The editor's unsaved text, kept in a file of its own outside the library so that the process ending in the
 * background does not end the text with it. Never exported, synced or backed up.
 */
interface EditorDraftLocalSource {

    /** Null when there is none, and when the one there cannot be read: a draft is worth keeping, not failing over. */
    suspend fun loadEditorDraft(): SongContent?

    /** Replaces the stored draft; null deletes it. */
    suspend fun saveEditorDraft(draft: SongContent?)
}
```

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/EditorDraftDocument.kt`:
a `@Serializable internal data class EditorDraftDocument(val fileName: String, val text: String)`.

`…/implementation/source/EditorDraftLocalSourceImpl.kt`, `@Single internal class EditorDraftLocalSourceImpl(private val fileStorage: FileStorage)`:

- `loadEditorDraft`: `fileStorage.readText(PREFERENCES, FILE_NAME)` decoded with `Json { ignoreUnknownKeys = true }`;
  any exception but cancellation (unreadable storage, a document that does not decode) is logged by its class name —
  the text is the user's — and answered with null.
- `saveEditorDraft(null)`: `fileStorage.delete(PREFERENCES, FILE_NAME)`. Otherwise `writeText` the encoded document,
  then `fileStorage.keepOutOfDeviceBackup(PREFERENCES, FILE_NAME)` (the attribute is lost with every atomic write,
  as the `sync-index.json` comment explains).
- `FILE_NAME = "editor-draft.json"`.

### `:data:repository` — the ordering

`data/repository/api/.../EditorDraftRepository.kt` with the same two functions, and
`data/repository/implementation/.../EditorDraftRepositoryImpl.kt`, `@Single`, passing through to the local source under
a `Mutex`, so that a pause's write and the deletion that follows a save land in the order they were asked for. It holds
no cache and does not extend `BaseLocalDataRepository` (like `SyncRepositoryImpl`, it is not a cached `DataState`).

### `:domain` — two use cases

`domain/api/.../useCases/GetEditorDraftUseCase.kt` (`suspend operator fun invoke(): SongContent?`) and
`SaveEditorDraftUseCase.kt` (`suspend operator fun invoke(draft: SongContent?)`), with `@Factory` implementations in
`domain/implementation/.../useCases/EditorDraftUseCaseImpls.kt` over `EditorDraftRepository`. The Koin compiler plugin
finds all four annotated classes by the existing `@ComponentScan`s; no module object changes.

### `CampfireViewModel.kt`

Constructor: add `private val getEditorDraft: GetEditorDraftUseCase` and `private val saveEditorDraft:
SaveEditorDraftUseCase` (the plugin writes the call).

State, next to `_isLaunchNavigationPending` (`:308`) — declared before `hasLibraryToShow`, which reads it:

```kotlin
    /**
     * True until the draft a previous run left behind has been read and, where there was one, the editor reopened on
     * it (see [recoverEditorDraft]). The launch screen waits for it, so that the app is uncovered on the editor rather
     * than on the songs a moment before the editor slides in over them.
     */
    private val _isEditorDraftRecoveryPending = MutableStateFlow(true)

    /** Completed with whether the editor was reopened on a stored draft, which the other start-up navigations defer to. */
    private val editorDraftRecovery = CompletableDeferred<Boolean>()
```

and next to `_editorDraft` (`:440`):

```kotlin
    /**
     * The draft as it was last put on disk, so that a pause that finds the same text writes nothing. Only read or written
     * under [editorDraftStoreMutex], and only once [recoverEditorDraft] has read what a previous run left.
     */
    private var storedEditorDraft: SongContent? = null
    private val editorDraftStoreMutex = Mutex()
```

`hasLibraryToShow` (`:323-325`) gains the fourth flow:

```kotlin
    val hasLibraryToShow = combine(screenData, isDemoLibraryPending, _isLaunchNavigationPending, _isEditorDraftRecoveryPending) { state, isDemoLibraryPending, isLaunchNavigationPending, isEditorDraftRecoveryPending ->
        !isDemoLibraryPending && !isLaunchNavigationPending && !isEditorDraftRecoveryPending && (state !is DataState.Loading || state.data?.songs?.isNotEmpty() == true)
    }
```

(and its KDoc: "…nor while a draft a previous run left is being reopened ([_isEditorDraftRecoveryPending])").

In `init`, a new coroutine:

```kotlin
        // The editor's unsaved text as a previous run left it, when the process ended in the background (see
        // onAppPaused). Once that is settled, whatever leaves the editor with nothing unsaved takes the stored
        // draft with it - a save, a discard, a revert, the song deleted - so a crash after a save never brings back
        // text that was saved. Asked of the draft itself rather than of hasUnsavedEditorChanges alone, which may not
        // have caught up yet with a draft this has just reopened.
        viewModelScope.launch {
            try {
                editorDraftRecovery.complete(recoverEditorDraft())
            } finally {
                editorDraftRecovery.complete(false)
                _isEditorDraftRecoveryPending.value = false
            }
            hasUnsavedEditorChanges.collect { if (!it && !hasUnsavedEditorText()) storeEditorDraft(null) }
        }
```

The functions, in the editor section:

```kotlin
    /**
     * Called by the app whenever it stops being the one in front (ON_PAUSE), which is the last moment it is certainly
     * running: iOS ends a process in the background without a word, and so does Android to a task swiped away and a
     * mobile browser to a tab it wants the memory of. Stores the unsaved text, or removes what was stored when there
     * is none. Nothing before the draft a previous run left has been read, or it would be written over.
     */
    fun onAppPaused() {
        if (_isEditorDraftRecoveryPending.value) return
        val draft = _editorDraft.value?.takeIf { hasUnsavedEditorText() }
        viewModelScope.launch { storeEditorDraft(draft) }
    }

    /** Not cancellable once started: a pause is often the last thing the process does. A write that fails is only a copy lost. */
    private suspend fun storeEditorDraft(draft: SongContent?) = withContext(NonCancellable) {
        editorDraftStoreMutex.withLock {
            if (draft == storedEditorDraft) return@withLock
            try {
                saveEditorDraft(draft)
                storedEditorDraft = draft
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not store the editor's draft: ${exception::class.simpleName}")
            }
        }
    }

    /**
     * Reopens the editor on the draft a previous run left, answering whether it did. Not over a stack that already has
     * an editor - an Android process restored on the editor has its text from the saved state - and not for a draft the
     * file already holds. A file that is gone is reopened all the same: the draft is all there is, and saving puts the
     * file back, which is what an editor whose file goes while it is open does too.
     */
    private suspend fun recoverEditorDraft(): Boolean {
        val draft = try {
            getEditorDraft()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the editor's draft: ${exception::class.simpleName}")
            null
        }
        editorDraftStoreMutex.withLock { storedEditorDraft = draft }
        if (draft == null || backStack.any { it is CampfireDestination.SongEditor }) return false
        val content = getSongContent(draft.fileName)
        if (content?.text == draft.text) {
            storeEditorDraft(null)
            return false
        }
        content?.let { _songTexts.update { texts -> texts + (it.fileName to it.text) } }
        // The draft is the editor's before the editor exists, so that nothing asking whether there is unsaved text in
        // the moments before it composes - a pause, the update gate - hears "no".
        onEditorTextChanged(fileName = draft.fileName, text = draft.text)
        updateBackStack { add(CampfireDestination.SongEditor(fileName = draft.fileName)) }
        // Taken by the editor as its field, see LoadedSongEditor, the same way a field it retained across a rotation is.
        retainedEditorField = draft.fileName to TextFieldState(initialText = draft.text)
        sendMessage(Message.EditorDraftRestored)
        if (content == null) sendMessage(Message.EditedSongFileGone)
        return true
    }
```

`navigateOnLaunch` (`:955-963`) waits for the decision before it looks at the stack:

```kotlin
                val destination = resolve(state?.unfilteredSongs.orEmpty(), state?.setlists.orEmpty())
                // A draft reopened on launch is unsaved text on screen, which an address does not get to replace.
                editorDraftRecovery.await()
                if (destination != null && backStack.toList() == listOf(CampfireDestination.Songs)) {
```

`Message` (`:2092-2124`) gains:

```kotlin
        /** The editor was reopened on the unsaved text a previous run left when it ended in the background. */
        data object EditorDraftRestored : Message
```

### `CampfireApp.kt`

Next to the `ON_RESUME` effect (`:170-179`), unconditionally:

```kotlin
    // The editor's unsaved text goes to disk whenever the app stops being the one in front, see
    // CampfireViewModel.onAppPaused. ON_PAUSE rather than ON_STOP: it always comes first, and an app swiped away from
    // iOS's app switcher may never have got any further.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAppPaused() }
```

and in `Messages` (`:554`):

```kotlin
        CampfireViewModel.Message.EditorDraftRestored -> stringResource(Res.string.song_editor_draft_restored)
```

### `SongEditorScreen.kt`

`LoadedSongEditor`'s `rememberSaveable` initializer (`:255-266`) takes a field the view model already holds for the file,
which is where the reopened draft is:

```kotlin
    ) {
        // A field the view model already holds for this file is taken as it is: the draft a previous run left, reopened
        // on launch. Otherwise a new one over the file's text.
        EditorField(
            viewModel.retainedEditorField(destination.fileName) ?: TextFieldState(
                initialText = initialText,
                initialSelection = if (destination.shouldStartInsideFirstSection) {
                    TextRange(initialText.caretInsideFirstSection())
                } else {
                    TextRange.Zero
                },
            )
        )
    }
```

`SongEditorScreen`'s own `initialText` (`:167-169`) already falls back to the retained field when the file's text is
not there, so a draft over a file that is gone opens at once, as a rotated editor over a gone file does. The retained
field is cleared by `updateBackStack` once no editor is on the stack (`:867`), as today.

### Strings

`values/strings.xml`, next to `song_editor_draft_lost`:

```xml
    <string name="song_editor_draft_restored">The unsaved changes were restored</string>
```

`values-hu/strings.xml`:

```xml
    <string name="song_editor_draft_restored">A nem mentett módosítások visszaállítva</string>
```

## Tests

- New `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/EditorDraftLocalSourceTest.kt`,
  built like `UserPreferencesLocalSourceTest` (a `JvmFileStorage` over a temporary directory):
  - no file → `loadEditorDraft()` is null;
  - save then load returns the same `SongContent`, text with newlines, non-Latin letters and a `%` intact;
  - `saveEditorDraft(null)` removes `preferences/editor-draft.json`, and saving null with no file does not throw;
  - a document that does not decode (`"{"`) loads as null and does not throw;
  - the file is written under `StorageDirectory.PREFERENCES`, never under the library directories.
- The view model and the UI are untested by policy.
- Run: `./gradlew :data:source:local:implementation:desktopTest` and the root unit test command; compile check for
  every target: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64 :app:ios:linkDebugFrameworkIosSimulatorArm64`
  (the last also checks the Koin graph gains the two use cases, the repository and the local source).

## Verification

Confirm the loss first on iOS, then the fix everywhere.

1. **iOS** (simulator, the `xcodebuild` recipe in the root `CLAUDE.md`; bundle id `com.pandulapeter.campfire`):
   open a song → ⋮ → Edit, type a line. Go Home (Cmd + Shift + H). `xcrun simctl terminate booted
   com.pandulapeter.campfire` (what the system does to a suspended app). Launch it again.
   - **Before:** Songs screen; the line is gone.
   - **After:** the app opens on the editor with the line in it, the snackbar "The unsaved changes were restored",
     Save enabled. Close → the unsaved changes question → Discard → back on Songs; terminate and relaunch → Songs,
     nothing reopened.
   - Real iPhone: the same with the app swiped away from the app switcher.
2. iOS: type, Home, relaunch without terminating → the app simply resumes (no second editor, no snackbar).
3. iOS: type, Save, Home, terminate, relaunch → nothing reopens (`preferences/editor-draft.json` is gone:
   `xcrun simctl get_app_container booted com.pandulapeter.campfire data`).
4. iOS backup attribute: after step 1's Home, `xattr -l` on the draft file in the container shows
   `com.apple.metadata:com_apple_backup_excludeItem` (what `NSURLIsExcludedFromBackupKey` writes).
5. **Android**: type, Home, swipe Campfire away from Recents, open it → the editor with the text (before: gone). Then
   type, Home, `adb shell am kill com.pandulapeter.campfire.debug`, open → the *restored* editor with the text and no
   second editor stacked on it, no "restored" snackbar (the saved state did it, as today).
6. Android, the update gate on an internal track (or the forced-state trick in plan 32): after a reopened draft,
   neither the required update screen nor Restart appears until the draft is saved or discarded.
7. **Desktop**: type, `kill -9` the process (`pgrep -f campfire`), relaunch → the editor with the text. Then delete the
   song's `.cho` from the library folder while the app is not running, relaunch → the editor with the text and both
   snackbars (restored; the file is gone); Save → the file is back.
8. **Web**: type, switch to another tab (pause), close the original tab from the browser's task manager
   (Shift + Esc → End process) so no `beforeunload` runs, open the site again → the editor with the text. A page loaded
   on a deep address (`…/setlists`) with a stored draft opens on the editor instead.
9. Sync and export: with a draft stored, run a sync and export the library → neither contains `editor-draft.json`.
10. Regression: a first run, the demo library, the web's address, the consent-page jump (plan 30) and the editor's
    ordinary save / discard / revert paths behave as before with no draft stored.

## Docs

- Root `CLAUDE.md`, the library layout block, after `preferences/sync-index.json`:
  `preferences/editor-draft.json         the editor's unsaved text as the app last left the front, so that the system
  ending it in the background does not end the text too; gone once it is saved or discarded`. And in the paragraph
  under it: "the sync credentials, `sync-index.json` and `editor-draft.json` are not" (in the backup), "iOS with a
  Keychain item bound to the device and `FileStorage.keepOutOfDeviceBackup` on the index and the draft".
- `presentation/CLAUDE.md`, `ui/navigation/CampfireDestination.kt` bullet, after "…and a long document that came back
  from a killed process without its unsaved text says so in a snackbar": add "Where no process is restored — iOS, the
  desktop, the web, an Android task swiped away — the editor's unsaved text is put in `preferences/editor-draft.json`
  whenever the app is paused (`onAppPaused`) and removed as soon as nothing is unsaved; a start that finds it reopens
  the editor on it behind the launch screen, with a snackbar, unless the restored stack already has an editor."
- `presentation/CLAUDE.md`, `screens/songEditor/` bullet (the paragraph on `hasUnsavedEditorChanges`): add "A draft
  reopened on launch is unsaved like any other, so the update gate, the way out and the web's `beforeunload` treat it
  the same."
- `data/source/local/implementation/CLAUDE.md:26-31` (backup): add `editor-draft.json` next to `sync-index.json` as
  a file that calls `keepOutOfDeviceBackup` after every write.
- `data/repository/implementation/CLAUDE.md`, `domain/api/CLAUDE.md`, `domain/implementation/CLAUDE.md`,
  `data/source/local/api/CLAUDE.md`: one line each for the new repository, use cases and local source, in the style
  of their neighbours.
- `documentation/features.md` (if it lists the editor's guarantees about unsaved text): add that it survives the
  system ending the app in the background.
- Testing scripts: a new P0 entry in `documentation/testing/02-ios.md` (steps 1–4), and one each in `01-android.md`
  (step 5), `03-macos.md` / `04-windows.md` / `05-linux.md` (step 7) and `06-web.md` (step 8).

## Files touched

- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/EditorDraftLocalSource.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/EditorDraftDocument.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/EditorDraftLocalSourceImpl.kt` (new)
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/EditorDraftLocalSourceTest.kt` (new)
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/EditorDraftRepository.kt` (new)
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/EditorDraftRepositoryImpl.kt` (new)
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/GetEditorDraftUseCase.kt` (new)
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/SaveEditorDraftUseCase.kt` (new)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/EditorDraftUseCaseImpls.kt` (new)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `CLAUDE.md`, `presentation/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`,
  `data/source/local/api/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `domain/api/CLAUDE.md`,
  `domain/implementation/CLAUDE.md`, the testing scripts named above

## Depends on

- **Plan 30** (the consent jump only on an untouched stack): without it a reopened draft can be replaced by the jump to
  Settings, which is the very loss this plan closes.
- Lands last in lane D: it touches all three shared files — `CampfireViewModel.kt` after 33, 31, 30, 29, 34;
  `CampfireApp.kt` after 32, 35, 41; `SongEditorScreen.kt` after 36 and 42 — and both `strings.xml` (after 43, which
  removes a different key).
- Plan 17 (lane B) touches `CampfireViewModel.kt` only if its option (a) is chosen, and plan 28 (lane C) not at all; no
  overlap with the functions here.
  Plan 46 (lane E, the Safari worker's partial writes) makes OPFS writes whole on older Safari; the draft file goes
  through the same `writeText`, so on the web it benefits from it without depending on it.
