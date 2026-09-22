# 26 — Answer the macOS quit request instead of cancelling it

## What the user sees

On macOS, with Campfire running and nothing unsaved, the user chooses **Log Out**, **Restart** or **Shut Down**.
The logout stops, and macOS says *"Campfire interrupted restart"* (older wording: *"The application Campfire
canceled restart"*). The user has to quit Campfire by hand and start again. The same thing happens on every
logout, every restart and every shutdown, whatever is on screen.

What actually happens is that Campfire quits a moment later — the quit handler cancels the system's request and
then asks itself to exit — but by then the system has already abandoned the shutdown, because the answer it got was
"no".

## Cause

`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt:82-89`, verified at HEAD
`984861e4`:

```kotlin
        DisposableEffect(Unit) {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_QUIT_HANDLER) } else null
            desktop?.setQuitHandler { _, response ->
                response.cancelQuit()
                SwingUtilities.invokeLater { requestExit() }
            }
            onDispose { desktop?.setQuitHandler(null) }
        }
```

`java.awt.desktop.QuitResponse.cancelQuit()` is `replyToApplicationShouldTerminate:NO`, which is
`NSTerminateCancel`. During a logout, restart or shutdown that is the veto the system honours: it stops the whole
sequence and blames the application that said it. The `QuitResponse` is then discarded, so the exit that follows is
Campfire quitting itself, with the shutdown already called off.

The comment above it describes the intent, and is the part that is right:

```kotlin
        // Quitting from the macOS application menu or with Cmd+Q never reaches onCloseRequest: without a handler of
        // its own the JDK answers it with System.exit. The quit is cancelled and asked for the way closing the window
        // is, which ends in exitApplication all the same once there is nothing left to lose.
```

The two things it has to do — wait for a save, ask about unsaved text — are `CampfireViewModel.requestExit`
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:985-995`):

```kotlin
    fun requestExit(exit: () -> Unit) {
        viewModelScope.launch {
            currentSaveJob?.join()
            if (hasUnsavedEditorText() && backStack.lastOrNull() is CampfireDestination.SongEditor) {
                pendingExit = exit
                showDialog(DialogType.UnsavedChanges)
            } else {
                exit()
            }
        }
    }
```

and it has **no way to say that the user chose to stay**: `exit` is called on Save and on Discard, and on every
other path the exit is simply forgotten, at `CampfireViewModel.kt:1844-1848`:

```kotlin
    private fun setVisibleDialog(dialogType: DialogType?) {
        if (dialogType !is DialogType.ImportConflicts) pendingImport = null
        if (dialogType != DialogType.UnsavedChanges) pendingExit = null
        _visibleDialog.update { dialogType }
    }
```

That is why the handler cancels up front: without a "the user stayed" callback there is nothing else it could do
with the response. So the fix is in two halves — the view model learns to report staying, and the shell answers the
system with it.

Three ways out of the dialog mean staying, and all three go through `setVisibleDialog`: the dialog's own dismissal
(`dismissDialog`), a write that failed (`saveEditorChangesAndLeave`'s `!isSaved -> dismissDialog()`,
`CampfireViewModel.kt:1155`), and another dialog replacing it.

## The change

### 1. The view model reports a cancelled exit

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`.

`pendingExit` becomes a pair of callbacks rather than one:

```kotlin
    /**
     * The exit that asked the `UnsavedChanges` question, run once it is answered with Save or Discard. Any other
     * way the dialog goes away is staying, and that is reported too: on macOS the exit may be the system's own
     * quit request, which has to be answered either way (see the desktop app module).
     */
    private var pendingExit: PendingExit? = null

    /** An exit that is waiting for an answer, and what to tell the caller if the answer is staying. */
    private class PendingExit(
        val exit: () -> Unit,
        val onCancelled: () -> Unit,
    )
```

taken rather than read, so that the branch which is about to run the exit cannot also report a cancellation when
`leaveEditor` dismisses the dialog behind it:

```kotlin
    private fun takePendingExit() = pendingExit.also { pendingExit = null }
```

`requestExit` gains the second callback, defaulted so that the window's close button and Escape
(`CampfireDesktopApp.handleKeyEvent`) stay as they are:

```kotlin
    /**
     * Closing the application … (existing KDoc, plus:)
     *
     * @param onCancelled Called instead of [exit] when the user answers the `UnsavedChanges` question by staying -
     *   dismissing it, or a save that failed. The macOS quit handler needs it: a quit request that is not answered
     *   one way or the other leaves the system waiting.
     */
    fun requestExit(exit: () -> Unit, onCancelled: () -> Unit = {}) {
        viewModelScope.launch {
            currentSaveJob?.join()
            // A second request - Cmd+Q pressed again while the question is up - answers the first one, which is
            // still waiting for something.
            takePendingExit()?.onCancelled?.invoke()
            if (hasUnsavedEditorText() && backStack.lastOrNull() is CampfireDestination.SongEditor) {
                pendingExit = PendingExit(exit = exit, onCancelled = onCancelled)
                showDialog(DialogType.UnsavedChanges)
            } else {
                exit()
            }
        }
    }
```

`setVisibleDialog` reports what it used to drop:

```kotlin
        // An exit the question was asked for and that is not being run is an exit that was cancelled: its caller
        // may be waiting to hear so (the macOS quit request is).
        if (dialogType != DialogType.UnsavedChanges) takePendingExit()?.onCancelled?.invoke()
```

and the two answers take the exit instead of reading it — `saveEditorChangesAndLeave`
(`CampfireViewModel.kt:1156-1160`) already takes it before leaving, which now becomes:

```kotlin
                _visibleDialog.value == DialogType.UnsavedChanges -> {
                    // Taken before leaving, which dismisses the dialog and would otherwise report this exit as
                    // cancelled.
                    val exit = takePendingExit()
                    leaveEditor()
                    exit?.exit?.invoke()
                }
```

and `leaveEditorWithoutSaving`:

```kotlin
    fun leaveEditorWithoutSaving() {
        val exit = takePendingExit()
        leaveEditor()
        exit?.let { requestExit(exit = it.exit, onCancelled = it.onCancelled) }
    }
```

### 2. The shell answers the system

`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`:

```kotlin
        // Quitting from the macOS application menu or with Cmd+Q never reaches onCloseRequest: without a handler of
        // its own the JDK answers it with System.exit. The request is kept and answered once the editor's unsaved
        // text has been dealt with - performQuit lets the logout, restart or shut down that asked carry on, and
        // cancelQuit is said only where the user actually chose to stay. Cancelling it up front, which is what this
        // used to do, is NSTerminateCancel: it aborts the whole sequence and macOS reports that Campfire
        // interrupted it, with nothing unsaved anywhere.
        DisposableEffect(Unit) {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_QUIT_HANDLER) } else null
            desktop?.setQuitHandler { _, response ->
                // The handler returns before anything is decided: what follows waits for a save, and may put a
                // dialog on screen.
                SwingUtilities.invokeLater {
                    // The process ends with the system's reply rather than with exitApplication, so the listener
                    // for other instances is closed here the way `exit` closes it.
                    val performQuit = {
                        stopListeningForOtherInstances()
                        response.performQuit()
                    }
                    viewModel.value?.requestExit(onExit = performQuit, onCancelled = response::cancelQuit) ?: performQuit()
                }
            }
            onDispose { desktop?.setQuitHandler(null) }
        }
```

(the parameter is named `exit` in the view model today; rename it to `onExit` there, or call it positionally — the
`handleKeyEvent` KDoc already calls it `onExit`.)

Notes that matter:

- **Which thread.** The JDK dispatches app events on the AWT event thread, and `requestExit` launches on
  `viewModelScope`, whose dispatcher on desktop is the same thread. `performQuit()` and `cancelQuit()` are
  therefore both called from the event thread, which is where AWT wants them. Keep the `SwingUtilities.invokeLater`
  so the handler returns immediately, as it does today.
- **Not answering at once is the supported thing to do.** `QuitResponse` is the JDK's wrapper around
  `NSTerminateLater`: an app that has unsaved work is expected to hold the reply while it asks the user, and macOS
  shows its own "waiting for Campfire" panel if that takes long. That is the correct behaviour for an app with
  unsaved text in an editor, and it is the only case where it happens now.
- **`performQuit()` ends the process**, so `exitApplication()` is not called on this path and the window is not
  closed first. Nothing else is owed at shutdown: the single-instance lock is released by the operating system when
  the process dies (`SingleInstance.kt:87-100`), which is exactly why `stopListeningForOtherInstances` is separate
  from it.
- **A sync run is not waited for**, as it is not today: a run cut short by a quit is found by the index's "a run
  was going" marker at the next start and reported as interrupted. Unchanged, and worth not changing here.

## Tests

None. The desktop shell and the view model's exit path are UI, which is untested by policy, and the behaviour is a
conversation with the operating system that no unit test can hold up its end of. The one thing a test could cover —
that `setVisibleDialog` reports a cancellation exactly once and never alongside an exit — would need a view model
test harness that does not exist, and adding one for this is out of proportion.

What replaces it is the manual check below, which has to be done on a Mac before this is called done.

## Verification

```
./gradlew :app:desktop:run
```

Manual, **needs a Mac** (the quit handler exists nowhere else):

1. `./gradlew :app:desktop:run`, then Cmd+Q with nothing open. The app quits. (Regression: this worked before.)
2. With the app running and no editor open, choose **Restart** from the Apple menu and cancel the restart at the
   confirmation. Then choose **Log Out**. The logout must go through and Campfire must not appear in any
   "interrupted" alert. (Do this on a machine where losing the session is fine, or log out of a test user.)
3. Open a song in the editor, type something, and press Cmd+Q. The unsaved changes question appears. Press
   **Cancel**: the app stays open, and the system's quit is answered with a cancel — verified by repeating step 2
   immediately afterwards and seeing the logout go through, which it would not if the response had been left
   unanswered *and* would not if the app had quit.
4. Same, answering **Save**: the file is written, then the app quits.
5. Same, answering **Discard**: the app quits without writing.
6. Type in the editor, choose **Log Out** from the Apple menu: the question appears over the app, the logout waits,
   and answering Save or Discard lets it carry on. Answering Cancel stops the logout with macOS naming Campfire —
   which is now true, and is the user's own decision.
7. Two processes: start a second Campfire (it hands over and exits), then Cmd+Q the first, answer Save, and start a
   third. It must open normally rather than finding the port still held.

The other three targets are untouched, but the view model changed, so:

```
./gradlew :app:android:assembleDebug
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDistribution
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Manual, Windows or Linux (regression, no quit handler there): close the window with unsaved text, answer each of
the three ways, and confirm nothing changed.

## Docs

`app/desktop/CLAUDE.md:14` — the sentence is right about the path and wrong about the ending, since the macOS quit
no longer ends in `exitApplication`:

> Closing the window is a back-navigation as far as unsaved text is concerned: `onCloseRequest`, the Escape that
> would exit, and the macOS quit (the application menu and Cmd+Q, which never reach `onCloseRequest` and are caught
> with `Desktop.setQuitHandler` instead) all go through `CampfireViewModel.requestExit`, which waits for a save
> still being written, asks the editor's unsaved changes question if there is unsaved text then — a save that failed
> included — and only lets `exitApplication` end the process once the text is in the file or has been discarded.

Rewrite the tail: the macOS quit keeps the system's `QuitResponse` and answers it — `performQuit` once the text is
in the file or has been discarded, `cancelQuit` when the user chose to stay — because cancelling it up front aborts
the logout, restart or shut down it was part of; the other two ways out still end in `exitApplication`.

`presentation/CLAUDE.md` — if it lists `requestExit` among the view model's platform-facing entry points, it gains
the cancellation callback and the one sentence about why it exists.

## Files touched

- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `app/desktop/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on

Nothing. It changes the signature of `CampfireViewModel.requestExit`, whose only callers are
`CampfireDesktopApplication.kt` and `CampfireDesktopApp.handleKeyEvent`; the default argument keeps the second one
compiling unchanged.

## Rules

- Load the `code-style` skill before the first edit: KDoc on the new `PendingExit` and on the new parameter, `//`
  comments inside the handler, "why, not what", trailing commas, no MPL header needed (no new files).
- No new strings.
- The "did the user stay?" question is answered with state — the exit is *taken* out of `pendingExit`, so exactly
  one of the two callbacks can ever run — and not with a flag set on a timer or a window in which a dismissal does
  not count.
- Unit tests are the command in the root `CLAUDE.md`; this plan adds none and says why.
- `app/desktop/CLAUDE.md` is part of the change, not a follow-up.
