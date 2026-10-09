# Stop the Tuner tab listening while Android's "update required" screen covers it

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerListeningEffect.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/update/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

A Play release with `updatePriority` 4–5 puts a blocking screen **over** the app (`update/AppUpdateGate.kt`); the app
stays composed behind it. Only windows of their own are taken away under it, through
`LocalIsCoveredByRequiredUpdate` (`AppUpdateGate.kt:63`, provided at `:107`), which only `CampfireScreens.kt:382`
(the dialogs) and `OverflowMenu.kt:58` read. So the tuner sheet goes (its `TunerListeningEffect` disposes and stops the
tuner), but the Tuner **tab** keeps listening: `TunerScreen` and `TunerListeningEffect` never read the local
(`tuner/TunerListeningEffect.kt:28-43` at b5c8ed3b5):

```kotlin
LaunchedEffect(isStarted, canListen) {
    if (isStarted && canListen) currentOnListeningChanged(true)
}
LifecycleStartEffect(Unit) {
    onStopOrDispose { currentOnListeningChanged(false) }
}
```

The microphone stays open, the recording indicator lit and the screen kept on (`keepScreenOn` while hearing) under a
screen that says nothing about a tuner. The immediate update flow is an activity of Play's, which stops the tab — but
coming back from it (the user cancels, or the download waits) is an `ON_START`, which listens again under the blocking
screen. That breaks the rule that the recording indicator is lit exactly while a tuner shows.

## Fix

Read the local in the shared effect, so any tuner screen is covered, and stop while covered:

```kotlin
val isCovered = LocalIsCoveredByRequiredUpdate.current
// Started on both, but stopped only by the lifecycle: a permission that turns out to be needed is the notice's to
// say, and a listening the page's own button started is not to be stopped by the status catching up with it. The
// screen a required update draws over the app is the exception: the app behind it is still composed and started,
// and the microphone is not to be open under a screen that is not the tuner.
LaunchedEffect(isStarted, canListen, isCovered) {
    when {
        isCovered -> currentOnListeningChanged(false)
        isStarted && canListen -> currentOnListeningChanged(true)
    }
}
```

Import `com.pandulapeter.campfire.presentation.ui.update.LocalIsCoveredByRequiredUpdate`; update the KDoc ("… and
stops the moment the screen stops or leaves, or a required update covers it"). `setTunerListening(false)` is
`stopTuner()`, which also ends a tone. When the screen goes (the update installed, or Play says it no longer applies),
`isCovered` turns false and the effect listens again where it may without a tap. The sheet is unaffected: it is not
composed while covered.

Docs:
- `ui/update/CLAUDE.md`: "(`LocalIsCoveredByRequiredUpdate`, read by `CampfireContent` and `OverflowMenu`)" → "read by
  `CampfireContent`, `OverflowMenu` and `TunerListeningEffect`, which stops the Tuner tab listening under it".
- `ui/tuner/CLAUDE.md`, the `TunerController` bullet: "… the screens' `TunerListeningEffect` stops it as they stop
  (the app out of sight, the screen locked, a required update drawn over the app) …".

## Tests

None: a Composable effect.

## Manual check

Only reachable from a Play testing track with a release of `updatePriority` 5 above the installed one: open the Tuner
tab with the microphone allowed and listening, and let the required update screen come up (or relaunch onto it). The
status bar's microphone indicator goes off as the screen appears, and stays off after backing out of Play's update
flow onto the blocking screen. A debug build cannot show the screen (`ui/update/CLAUDE.md`).
