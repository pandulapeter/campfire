# Clear the web media session when the click stops while the tab is hidden

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Files:** presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt, presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/BackgroundMetronome.wasmJs.kt, presentation/CLAUDE.md (one clause, shared file)

## Problem

The media session the web build shows for a playing click is driven by `MetronomeNotificationEffect` in
`presentation/src/commonMain/.../ui/CampfireApp.kt`, which reads the engine lifecycle-aware:

```kotlin
val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
...
LaunchedEffect(notification) {
    if (notification != null) { hasShown = true; notifier.onMetronomeNotificationChanged(notification) }
    else if (hasShown) { hasShown = false; notifier.onMetronomeNotificationChanged(null) }
}
```

and `WebMetronomeNotifier` (`BackgroundMetronome.wasmJs.kt`) only clears the session when it is handed `null`.
Compose for web moves the window's lifecycle to `CREATED` when the tab is hidden — `ComposeWindowInternal.web.kt`
(compose ui-wasm-js 1.12.1) handles `visibilitychange` with `ON_START` / `ON_STOP` — and
`collectAsStateWithLifecycle` stops collecting below `STARTED`. So in a hidden tab, which is exactly when the browser's
media controls are the way to reach the click, pressing the session's pause (or stop) does stop the click —
`LaunchedEffect(viewModel) { WebMetronomeNotifier.forEachStopRequest(viewModel::stopMetronome) }` in `CampfireWebApp`
is not lifecycle-bound — but the composition never sees `Stopped`, so the session keeps saying "playing" with the old
title until the tab is shown again. The Android service and `IosMetronomeNotifier` follow the engine themselves for the
same reason.

## Fix

Have the web shell clear the session from the engine's own state, not from the composition. In `CampfireWebApp`, next
to the `forEachStopRequest` effect:

```kotlin
// The composition stops collecting while the tab is hidden (Compose moves the lifecycle to CREATED), and a hidden tab
// is when the browser's media controls are used: the session follows the engine itself, as Android's service does.
LaunchedEffect(viewModel) {
    viewModel.metronomePlayback.collect { if (it !is MetronomePlayback.Playing) WebMetronomeNotifier.onMetronomeNotificationChanged(null) }
}
```

Clearing twice is harmless (`clearMediaSession` only writes `metadata = null` and `playbackState = 'none'`), and when the
tab is shown again `MetronomeNotificationEffect` sees `Stopped` and clears once more. Setting the session stays with the
common effect, since only the composition has the strings in the app's language, and a click can only start from a
visible tab. Update `WebMetronomeNotifier`'s KDoc to say the shell clears it on the engine's own stop as well.

No change to `CampfireApp.kt` (lane D) is needed. In `presentation/CLAUDE.md`'s `CampfireWebApp.kt` entry, add one
clause that the media session is cleared from the engine's playback, not only from the composition, since a hidden tab's
composition stops collecting.

## Tests

None: a browser media-session effect, outside the pure logic that is unit tested.

## Manual check

In Chrome on the desktop (the media hub in the toolbar shows a session for Web Audio only sometimes; Chrome on Android
shows it more reliably), start the click on the Metronome tab, switch to another tab, open the browser's media controls
and press pause. The click stops and the media control disappears (or shows nothing playing) at once, instead of only
after returning to Campfire's tab.
