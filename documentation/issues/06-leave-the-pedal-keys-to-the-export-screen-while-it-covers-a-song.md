# Leave the arrow and page keys alone while the export screen covers a song, so a pedal never steps the song under it

**Challenged:** amended — the covered song now *consumes* its six keys without acting instead of returning `false`
(an unconsumed arrow is spent by Compose on a two-dimensional focus search, which can move the focus to a chip or a step
button of the covered song and scroll it into view); the export screen takes the focus with a bare `focusTarget()` on
every platform (a pedal pairs with a phone too), not a `focusGroup()`, which would hand the focus to the first button.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** every platform with a keyboard or a page-turner pedal (desktop, web on a computer, Android and iOS with a pedal)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongKeyboardShortcuts.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportScreen.kt`

## Problem

Since ed54c7da9 the PDF export is a screen composed over the app in the same window (`PrintExportHost`, a sibling
after `NavDisplay` in `CampfireApp.kt`'s `CampfireScreens`), not a window of its own, so it no longer takes the focus
away from the song details screen. The song screen's handler (`Modifier.songKeyboardShortcuts`, on the root `Column` of
`SongDetailsScreen`, its only caller) keeps the focus and never asks whether it is covered:

```kotlin
.focusable()
.onPreviewKeyEvent { keyEvent ->
    // Before the check for modifiers below, so that a key released with one of them down is still released.
    if (keyEvent.type == KeyEventType.KeyUp) heldStepKeys.remove(keyEvent.key)
    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    ...
```

`isUncovered` (`visibleDialog == null && backStack.lastOrNull() is SongDetails` in `SongDetailsScreen.kt`) only decides
whether it takes the focus back. The export screen moves the focus only from `PrintPages`, once a preview is composed
and only where `isDesktopPlatform`: `LaunchedEffect(Unit) { if (isDesktopPlatform) focusRequester.requestFocus() }`.
While the source loads, when it fails, when no song is ticked — and on Android and iOS always — no part of the export
screen has the focus: a pedal press steps the song under it (or, in a setlist, turns to the next song), and the
export's option list cannot be reached with the keys.

Every other `DialogType` is a window of its own (a Material dialog or a modal bottom sheet), which takes the focus, and
overflow menus are popups, so the export screen is the only in-window cover today.

## Fix

Both, since each covers what the other cannot:

1. In `songKeyboardShortcuts`, after the `KeyUp` line and the `KeyDown` and modifier checks, when `!latestIsUncovered`
   **consume** the six keys the handler knows (Up, Down, Page Up, Page Down, Left, Right) and do nothing with them:
   `return@onPreviewKeyEvent keyEvent.key in setOf(…)`. Do **not** return `false` for them: the KDoc above the function
   already records that an arrow nobody consumes is spent by Compose on a focus search of its own, which here would move
   the focus from the covered song's `focusable()` to a chip or step button of the song (scrolling it into view — the very
   movement this plan stops) or into the export screen at random. Every other key, Escape and Tab included, is left
   alone as now. Also clear `heldStepKeys` when `isUncovered` turns false (`LaunchedEffect(isUncovered) { if
   (!isUncovered) heldStepKeys.clear() else focusRequester.requestFocus() }`), so a key held as the cover went up is not
   taken for held when it comes down. Extend the `isUncovered` KDoc by a sentence: while it does not hold, the keys are
   swallowed, since something drawn in the window over the screen (the export screen) does not take the focus by
   being there.
2. In `PrintExportScreen`, make the root `Surface` a focus target that takes the focus when the screen opens, whatever
   it shows: `Modifier.focusRequester(rootFocus).focusTarget()` on the `Surface` plus
   `LaunchedEffect(Unit) { rootFocus.requestFocus() }`, on every platform. `focusTarget()` draws no indication, so the
   focus-ring concern that keeps the preview's own request desktop-only does not apply; do not use `focusGroup()` or
   `focusable()` with an interaction source, which would move the focus onto (and possibly ring) the first button.
   Keep `PrintPages`' desktop request as it is: it is inside the root, so it simply moves the focus further in once the
   preview is composed, which is what lets its arrows turn pages. There are no text fields in the options to fight
   with. Keyed by `Unit` is right: an export replacing another is the same composable, and the focus is already inside.

Closing needs nothing: `isUncovered` turns true as the dialog is cleared, while the screen is still sliding away, and
`LaunchedEffect(isUncovered)` in `songKeyboardShortcuts` takes the focus back to the song. The song's own retake loop in
`onFocusChanged` is gated by `latestIsUncovered`, which is already false by the frame after the export screen takes the
focus (the host composes the screen a frame after the dialog changes), so the two do not fight. A window dialog put
up in place of the export takes the focus itself and hands it back the same way when it goes.

Nothing else in the window competes for these keys: the desktop window's `handleKeyEvent` only answers Ctrl / Cmd
shortcuts and Escape, and on the web the page does not scroll (`overflow: hidden` in `index.html`).

## Tests

None: focus and key routing are Compose UI, which the project does not test by code.

## Manual check

Desktop: open a song in a setlist, Export to PDF, untick every song (or open one whose source fails), press Down, Page
Down and Right: neither the song nor the setlist's pager moves under the screen, and Tab reaches the export's controls.
With a preview up, the arrows still turn its pages. Close the export: the arrows step the song again. Same on the web in
Chrome on a computer, and on Android with a Bluetooth keyboard or pedal.
