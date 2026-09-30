# Take a held Up or Down key as one step, not as a step per repeat

**Challenged:** amended — two ways the held set could keep a key it no longer has, and the pedal would then lose a
press. The handler returns early for Alt, Meta or Ctrl, so a release with one of them down (a Down let go of after Ctrl
was pressed) would never be seen: the release is now handled before that check. And `onFocusChanged` does not run when
the *window* loses the focus — the node inside keeps it — which is exactly the case the plan cites `resetEscapeKey` for
(that one is wired to AWT's `windowLostFocus`, not to Compose focus): the set is now also cleared when
`LocalWindowInfo.current.isWindowFocused` turns false. Either miss heals itself on the next press, which is swallowed
and then released, but that one swallowed press is a lost page on stage. Taking a held pedal as one step is right:
a foot resting on a pedal is not a request to page, and every page turner that auto-repeats also sends a release when
the foot comes up.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all with a keyboard or a pedal (desktop, web, Android, iOS)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongKeyboardShortcuts.kt`,
`presentation/CLAUDE.md` where it describes `songKeyboardShortcuts`

## Problem

`Modifier.songKeyboardShortcuts` (at 9ab7ca54e) takes every KeyDown as a press on purpose:

```kotlin
.onPreviewKeyEvent { keyEvent ->
    // Key repeats arrive as further KeyDown events, which is what makes a held arrow scroll continuously.
    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    …
    val action = when (keyEvent.key) {
        Key.DirectionUp -> onStepBack ?: onScrollUp
        Key.DirectionDown -> onStepForward ?: onScrollDown
```

That is right for the scroll actions, and wrong for the step actions, which the same branch sends the repeats to.
The live run held Down on the 400-line song (20 KeyDown events 33 ms apart, which is what the OS sends once its
repeat delay of about half a second has passed): 20 `step` calls chained from each other's target
(`SongStepper.step` queues a press behind the one in flight, which is meant for two deliberate taps), the targets ran
151 → 629 → … → 9485 while the scroll had reached 420, and the song then animated through 19 pages in one run. No
press was lost or doubled, but pages 2–19 were only on screen during the animation, never at rest: for a musician
they are skipped lines. A pedal held a moment too long, or a foot resting on it, does this.

## Fix

Act on the first KeyDown of a step key only, until its KeyUp, the way the desktop window already swallows Escape
repeats (`handlePreviewKeyEvent` in `CampfireDesktopApp.kt` keeps an `isEscapeHeld` flag set on KeyDown and cleared on
KeyUp, and `resetEscapeKey` clears it from AWT's `windowLostFocus`). In `songKeyboardShortcuts`, keep
`val heldStepKeys = remember { mutableSetOf<Key>() }` (plain, not state: only the handler reads it):

- **first**, before the Alt / Meta / Ctrl check: on KeyUp of any key, `heldStepKeys.remove(keyEvent.key)` and return
  false, as now;
- on KeyDown of Up / Down / PageUp / PageDown *when the action is a step* (`onStepBack` / `onStepForward` is not null):
  if the key is in the set, consume the event and do nothing; otherwise add it and step;
- clear the set in the existing `onFocusChanged` branch where the screen loses the focus, and when the window does:
  `val windowInfo = LocalWindowInfo.current` and
  `LaunchedEffect(windowInfo) { snapshotFlow { windowInfo.isWindowFocused }.collect { if (!it) heldStepKeys.clear() } }`
  (common Compose, every platform; a release that happens in another window never reaches this one).

The scroll actions (no step buttons) keep taking every repeat, so a held arrow still scrolls continuously, and Left /
Right keep theirs (they are not what a pedal sends). A pedal that sends a KeyDown and a KeyUp per tap chains two taps
as before. Do not use the native event's repeat flag: AWT has none, so the KeyUp set is the one way that works on every
platform. Where AWT on Linux delivers auto-repeat as release / press pairs (X11 without detectable auto-repeat), a
held key still steps per repeat, which is today's behaviour and no worse; note it in the KDoc. Update the comment
quoted above and the sentence in `presentation/CLAUDE.md` about held keys.

## Tests

None practical: the guard is a `Set` inside a modifier. If the executor wants a pin, pull the decision into a pure
`internal fun stepKeyAction(isHeld: Boolean, type: KeyEventType): StepKeyAction` and test the four cases; not required.

## Manual check

Desktop build, a song taller than the screen: hold Down for two seconds. The song moves one step and stops. Release
and press again: it moves one more. Hold Down, switch to another window, let go, come back: the next press steps. A held Up or Down on a song that fits the screen (no step buttons) still scrolls.
