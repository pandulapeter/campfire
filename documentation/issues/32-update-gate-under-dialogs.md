# 32 — Android: the "update required" screen does not cover open dialogs, sheets and menus

**Severity:** a required update can be worked around (Android, Play builds with an update priority of 4–5) ·
**Area:** `:presentation` (`AppUpdateGate.kt`, `CampfireApp.kt`, `components/OverflowMenu.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The flow can only be exercised from a Play internal testing track (see the root `CLAUDE.md`, Updates), and the
"Verification" section below says how.

## What the user sees

A Play release published with update priority 4 or 5 puts a blocking "Update required" screen over the app. If a
bottom sheet (the song picker, the setlist picker, sort and filter), a dialog (new song, delete, the import conflicts
question) or an overflow menu is open at that moment, it stays on top of the blocking screen and can still be used —
ticking songs into setlists, deleting a song, confirming an import. A file shared to Campfire while the screen is up
can bring the import conflicts dialog up over it as well.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppUpdateGate.kt:93-105`:

```kotlin
    Box(modifier = modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = isRequiredScreenVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            AppUpdateRequiredScreen(
                onUpdate = controller::startUpdate,
                onClose = controller::closeApp,
            )
        }
    }
```

The screen is drawn after `content()` in the same `Box`, which covers everything the app draws *in its own window*.
`content()` is `CampfireContent`, which renders `CampfireDialogs` (`CampfireApp.kt:502-505`), and every
`AlertDialog`, every `ModalBottomSheet` (`Dialogs.kt:1312`) and every `DropdownMenu` (`OverflowMenu.kt:49`) is a window
of its own on Android — a `Dialog` or a `PopupWindow` above the activity's content view, and so above the `Surface`
that is meant to cover the app. (On the other three platforms the gate is never up: `AppUpdateState.Required` only
exists on Android.)

## The change

Invoke the **`code-style`** skill before the first edit.

**Corrected from the reviewer's suggestion**, which was to call `viewModel.dismissDialog()` when the screen goes up.
That throws away what the dialog held (a typed setlist name, a parked import plan and its question) although the gate's
own design is that "an update that turns out not to install leaves the library where the user was"
(`AppUpdateGate.kt:59-61`), and it does nothing about an overflow menu or about a dialog that arrives *after* the screen
is up (the import queue puts its question up the moment `visibleDialog` is null). Instead: nothing that opens a window
is composed while the screen is up. `visibleDialog` and each menu's state are left alone, so if the update does not
happen and the gate goes down, the app is exactly where it was — its sheet included.

### `AppUpdateGate.kt`

Add, above `AppUpdateGate`:

```kotlin
/**
 * True while the "update required" screen covers the app. That screen is drawn over the app's content, but a dialog,
 * a bottom sheet and a dropdown menu are windows of their own on Android, above the activity's content and so above
 * the screen, where they could still be used. Whatever opens one reads this and composes nothing while it is true;
 * what it was showing stays in its state, so an update that does not install leaves it where it was.
 */
internal val LocalIsCoveredByRequiredUpdate = compositionLocalOf { false }
```

and provide it around `content()` (`:94`):

```kotlin
        CompositionLocalProvider(LocalIsCoveredByRequiredUpdate provides isRequiredScreenVisible) {
            content()
        }
```

(imports: `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.runtime.compositionLocalOf`).

### `CampfireApp.kt`

In `CampfireContent`, around the dialogs (`:502-505`):

```kotlin
        // Not while the update required screen covers the app: every one of these is a window of its own on Android,
        // which that screen, drawn inside the activity's content, cannot cover.
        if (!LocalIsCoveredByRequiredUpdate.current) {
            CampfireDialogs(
                viewModel = viewModel,
                urlOpener = urlOpener,
            )
        }
```

### `OverflowMenu.kt`

Every dropdown in the app goes through `OverflowMenu` (the song and setlist menus, `NewItemMenu`, the editor's
revert). In it (`:34-54`):

```kotlin
    // A menu is a window of its own, which the update required screen cannot cover; it is left open in its state and
    // comes back if that screen ever goes, see LocalIsCoveredByRequiredUpdate.
    val isShown = state.isExpanded && !LocalIsCoveredByRequiredUpdate.current
    if (isShown) {
        DisposableEffect(Unit) {
            openOverflowMenuCount++
            onDispose { openOverflowMenuCount-- }
        }
    }
    Box {
        button(state::open)
        DropdownMenu(
            expanded = isShown,
            onDismissRequest = state::dismiss,
        ) {
            content(state::select)
        }
    }
```

(import `com.pandulapeter.campfire.presentation.ui.LocalIsCoveredByRequiredUpdate`).

What this leaves: the system's own windows (the SAF picker, a Custom Tab) are other activities and are covered by
the Play flow the gate starts, not by this screen. Snackbars and the launch screen are drawn in the content and are
already under the `Surface`.

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check: `./gradlew :presentation:compileDebugKotlinAndroid :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileKotlinIosSimulatorArm64`

## Verification

The real flow needs a Play internal testing track: install version N from it, publish N+1 with
`<!-- play-store update-priority: 5 -->`, then open N.

1. Before the check answers, open Setlists → a setlist's ⋮ → Song assignments (a sheet), and on another run a song
   row's ⋮ menu, and on a third the "New song" dialog with a name typed.
2. **Before:** the blocking screen appears behind the sheet / menu / dialog, which can still be used.
   **After:** the sheet, menu or dialog disappears as the screen fades in; only the screen can be touched; Back
   closes the app.
3. While the screen is up, share a `.cho` file whose name collides with a library song to Campfire: no conflicts
   dialog appears over the screen.
4. Without a track, the mechanism can be checked on a debug build by temporarily forcing the controller's state to
   `AppUpdateState.Required` in `AppUpdate.android.kt` (do not commit): same observations, and restoring the state to
   `NotAvailable` from the debugger brings the sheet / menu / dialog back as it was.
5. Regression, every platform: dialogs, sheets and menus open as before (the local is false everywhere but here).

## Docs

- `presentation/CLAUDE.md`, the `ui/platform/AppUpdate.kt` / `ui/AppUpdateGate.kt` bullet, after "That screen is drawn
  *over* the app rather than in place of it, so an update that turns out not to install leaves the library where the
  user was." add: "Dialogs, sheets and menus are windows of their own on Android, which that screen cannot cover, so
  none of them is composed while it is up (`LocalIsCoveredByRequiredUpdate`, read by `CampfireContent` and
  `OverflowMenu`); what they were showing stays in their state and comes back if the screen goes."
- Root `CLAUDE.md`, Updates: after "The blocking screen is drawn **over** the app rather than in place of it, …" add
  "Nothing that is a window of its own — a dialog, a sheet, a menu — is shown while it is up."

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppUpdateGate.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/OverflowMenu.kt`
- `presentation/CLAUDE.md`
- `CLAUDE.md`

## Depends on

Nothing. `CampfireApp.kt` is also touched by 35, 41 and 44 (different lines); lane D order puts this one first of
those four.
