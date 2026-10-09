# Keep keyboard focus inside the export screen while it covers the app

**Kind:** accessibility  ·  **Severity:** medium  ·  **Platforms:** desktop, web, and Android / iPadOS with a hardware keyboard
**Challenged:** amended — the onExit trap is conditional on the export still being the visible dialog: ExportHost keeps the screen composed while it slides away, and an unconditional cancel refuses the song's own requestFocus then, leaving pedal/arrow keys dead after closing
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`

## Problem

The export screen is drawn in the same window as the app, over the `NavDisplay`. While it covers it, the app is only
taken out of the semantics tree
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt:240` at b5c8ed3b5):

```kotlin
                .then(if (isExportCovering) Modifier.clearAndSetSemantics { } else Modifier),
```

which a screen reader respects but the focus system does not. The export screen's root is a plain focus target
(`screens/export/ExportScreen.kt:336`):

```kotlin
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    // A Surface, so that nothing of the app under it can be pressed through it.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .saveShortcut { if (canSaveNow && exportProgress == null) requestExport(ExportRequest.SAVE) }
            .focusRequester(rootFocus)
            .focusTarget(),
```

The `NavDisplay` comes before the export screen in the tree, so Shift+Tab from the export screen's first control
moves into the song screen hidden under it, and Tab past the last control wraps around into it
(`FocusOwnerImpl.moveFocus` clears focus and takes it again from the start). Focus is then on the invisible app bar
of the song being exported: Enter presses its buttons, Space or a pedal steps the song, and Ctrl / Cmd + S no longer
reaches the export screen.

## Fix

Make the export screen a focus group that refuses to let focus out. Compose 1.12.1's `FocusProperties.onExit`
(`FocusEnterExitScope.cancelFocusChange()`) is consulted whenever focus leaves a group through a focus transaction —
`performCustomClearFocus` runs the exit of every `ActiveParent` on the way from the focused node to the common
ancestor — but not for the group's own node when that node itself is the one focused (`Active` returns `None`). So the
group has to be a node *around* the existing root target, not the target itself:

```kotlin
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .saveShortcut { if (canSaveNow && exportProgress == null) requestExport(ExportRequest.SAVE) }
            // The screen under this one is still in the same window and still focusable, before this one in the
            // order, so Tab and Shift + Tab would walk into it - onto the buttons of a screen nobody can see. The focus
            // group around the root target keeps every move inside; a group is consulted only when focus leaves a
            // child of it, which is why the root target is a node of its own inside it.
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .focusRequester(rootFocus)
            .focusTarget(),
```

(`androidx.compose.ui.focus.focusProperties`, `androidx.compose.foundation.focusGroup`.) `focusProperties` applies to
the next focus target in the chain, the group; `focusRequester` to the next one after it, the root target, so
`rootFocus.requestFocus()` is unchanged.

What this changes: Tab at the last control and Shift+Tab at the first one stay where they are instead of wrapping
(a cancelled move does not wrap). That is the price of the trap; the alternative of blocking entry into the
`NavDisplay` (`onEnter = { cancelFocusChange() }` on a group around it in `CampfireScreens.kt`) is worse, because the
wrap-around clears focus *before* it tries to take it again, and a cancelled entry then leaves nothing focused — the
export screen's Ctrl / Cmd + S included. `focusProperties { canFocus = false }` around the `NavDisplay` does not work
either: it only applies to the nearest focus target under it, not to the many inside the screens.

**Challenged — the trap must end when the export stops being the dialog, not when the screen leaves the tree.**
`ExportHost` keeps `ExportScreen` composed for the whole slide-away (`shown` is cleared only after
`transition.progress.animateTo(0f)`), but the song under it becomes `isUncovered` the moment `visibleDialog` turns null
(`SongDetailsScreen.kt:230`), and `songKeyboardShortcuts` then calls `focusRequester.requestFocus()`
(`SongKeyboardShortcuts.kt:92`). With an unconditional `cancelFocusChange()` that request is refused while the screen
slides away, and when the screen is finally removed the focus goes to nothing — the song never retakes it (its
`onFocusChanged` retry only runs when *it* loses focus), so a pedal, the arrows and Space do nothing on the song until it
is clicked. Make the exit conditional on the export still being on screen:

```kotlin
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    // Read when focus tries to leave, not at composition: the screen stays drawn while it slides away, and by then the
    // song under it is taking the focus back.
    val isTrappingFocus by rememberUpdatedState(visibleDialog is DialogType.Export)
    ...
            .focusProperties { onExit = { if (isTrappingFocus) cancelFocusChange() } }
```

(`ExportScreen` already has `val isOpen = visibleDialog == dialog` at `ExportScreen.kt:234`: use
`rememberUpdatedState(isOpen)` rather than collecting the dialog again. One export replacing another is the same screen
with a new `dialog`, so the trap holds.) Add to the manual check: close the export screen over a song on the desktop with its close button and
with Escape, and press Down / a pedal at once, without clicking: the song steps.

Closing the export screen removes the group from the tree, which moves focus out by force, without consulting
`onExit`. The sheets opened from the export screen (song chooser, …) are `ModalBottomSheet`s in a layer of their own
and are not affected; check them anyway (below).

Add to `ui/screens/export/CLAUDE.md`, after the sentence at line 24 that it is "an opaque `Surface` under the host's
`NavigationBackHandler`": "It is a focus group that cancels every
exit, so Tab never reaches the screen under it (which `clearAndSetSemantics` only hides from a screen reader)."

## Tests

None: focus behaviour of a Composable.

## Manual check

- Desktop (`./gradlew :app:desktop:run`): open a song → Export. Press Tab repeatedly: focus cycles through the export
  screen's controls and stops at the last one; Shift+Tab stops at the first. At no point does Enter or Space change
  the song under it (scroll it, step a page, open its menu). Ctrl / Cmd + S still saves.
- The same in the web build (Chrome) and on an Android tablet with a keyboard.
- From the export screen, open the Choose songs sheet (a setlist's export), type in its search, close it: Tab works in
  the export screen again.
- Close the export screen with its close button and with Escape (desktop) or Back (web): focus returns to the song screen and Tab works there.
