# Stop a click that cannot sound (volume at zero, or every beat muted) once the app has been out of sight for a few seconds, and say why

**Kind:** store-policy · **Severity:** low · **Platforms:** iOS, Android
**Challenged:** amended — the grace is three seconds rather than one (the catch-up `ON_START` that cancels it on an
Android recreation only arrives with the new activity's first composition, which a slow phone with a song open can
bring close to a second); the Hungarian string is reworded; the platform lifecycle sources, the snackbar timing and the
shells' teardown were checked and are recorded under *Verified*; an output that could not open is left out on purpose.
**Files:**
- `metronome/api/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/api/model/MetronomePattern.kt`
- `metronome/api/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/api/MetronomePatternTest.kt` (new)
- `metronome/api/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/CLAUDE.md` (the Metronome section's "The platform's side" bullet)
- `app/ios/CLAUDE.md` (the `IosMetronomeNotifier.kt` paragraph, `UIBackgroundModes` sentence)
- `app/android/CLAUDE.md` (the `metronome/CampfireMetronomeService` paragraph)
- `CLAUDE.md` (Metronome section: the "Playback is media" bullet and the "there is no mute of its own" sentence)

## Problem

A click keeps running with nothing audible in two cases, both on purpose in the foreground, where the flash and the
haptics follow the beats.

The volume, `MetronomePattern.kt`:

```kotlin
 * @param volume From 0 to 1, on top of the system's own volume; at 0 the clock and the beats go on with nothing
 *   sounding, for the visual beat alone.
```

and every beat muted — a muted beat mutes its subdivisions too, so the subdivision cannot make such a bar audible
(`MetronomeSequencer.kt`):

```kotlin
     * @param level The level of its beat; a subdivision carries its beat's, so that a muted beat mutes them too.
...
        /** The voice that sounds, or null for a click that only counts. */
        val voice
            get() = when {
                level == BeatLevel.MUTED -> null
```

`ClickStream.schedule` hands an output nothing for such a tick (`tick.voice?.let { voice -> onClick(...) }`), while the
output, its session and the service stay up. Root `CLAUDE.md` says so outright:

```
  `UserPreferences.metronomeSettings`; there is no mute of its own, since a volume of zero leaves the click running
  with nothing sounding.
```

And nothing ends such a click when the app leaves the front, by design (root `CLAUDE.md`):

```
  app taking the audio, and a click that stopped on its own says why. **A click outlives the app being sent to the
  background and not the app being left**: the screen locked or another app in front is a phone on a music stand and
  keeps it, ...
```

- **iOS**: `IosAudioOutput` keeps `AVAudioSessionCategoryPlayback` active for the whole click
  (`session.setCategory(if (isPreview) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback, error.ptr) &&
  session.setActive(true, error.ptr)`, deactivated only in `deactivateSession()` when the click stops), the engine keeps
  feeding the player buffers of zeros, and `app/ios/iosApp/iosApp/Info.plist` declares:

  ```xml
  <key>UIBackgroundModes</key>
  <array>
      <string>audio</string>
  </array>
  ```

  So with the screen locked or another app in front, the process stays alive playing silence, with a Now Playing entry
  for it. That is App Store Review Guideline 2.5.4's case (background audio used for something that is not audible
  content), and `app/ios/CLAUDE.md` itself says the mode is justified by "the session being active only while a click
  plays, which is what App Review checks the mode against".
- **Android**: `CampfireMetronomeService` (a `mediaPlayback` foreground service with a media notification) keeps the
  process up and the notification posted for silence, which is what Play's foreground service type policy asks
  `mediaPlayback` not to be used for.

Out of sight such a click does nothing at all: the flash is not drawn (the composition stops), and the haptics are
"driven from the heard beats while the app is resumed" (`presentation/CLAUDE.md`, `BeatHaptics.kt`).

It cannot *become* silent in the background, and a silent one cannot be *started* there: the volume and the accents
are `UserPreferences.metronomeSettings`, which no sync run carries (only `transpositions`, `tempos`, `capos` and
`chordVoicings` are synced); the page being read, which decides the time signature, does not move out of sight; every
start is a tap or a key in front (Android's media session only answers `onPause`/`onStop` with `metronome.stop()`,
iOS's `playCommand.enabled = false` and pause/stop/toggle stop). So the moment the app leaves the front is the one
place to check.

## Fix

### Recommended: one common rule in `:presentation`, decided on `ON_STOP`, with a three second grace

1. **A pure answer in `:metronome:api`**, `MetronomePattern.kt`, next to `beatLevel`:

   ```kotlin
   /**
    * Whether anything of this pattern is ever heard: a volume above zero and at least one beat of the bar not muted. A
    * subdivision carries its beat's level, so it never makes a muted bar audible.
    */
   val canSound: Boolean
       get() = volume > 0f && (0 until timeSignature.beats).any { beatLevel(it) != BeatLevel.MUTED }
   ```

   `beatLevel(index)` already pads a short `beatLevels` with the signature's defaults, and iterating the signature's
   beats cuts a long one, exactly as the engine reads it. Extend the `@param volume` KDoc: "...for the visual beat
   alone - which is why such a click does not outlive the app leaving the front, see `canSound`."

2. **The view model**, `CampfireViewModel.kt`, next to `onAppPaused()`:

   ```kotlin
   private var silentClickStopJob: Job? = null

   /**
    * Called whenever the app is out of sight (ON_STOP). A click that cannot sound - the volume at zero, every beat
    * muted - is only there for the flash and the haptics, neither of which reaches a screen nobody sees, and keeping it
    * up would keep the phone's background audio (iOS's audio mode, Android's media playback service) going for
    * silence, which is not what either platform allows it for. A few seconds' grace, since an Android activity
    * recreated in front (a system theme or language change) stops, and is only started again once its new composition
    * is up, and a click playing on its own screen is not to be stopped by that.
    */
   fun onAppStopped() {
       silentClickStopJob?.cancel()
       silentClickStopJob = viewModelScope.launch {
           delay(SILENT_CLICK_GRACE_MILLIS)
           val playing = metronome.playback.value as? MetronomePlayback.Playing ?: return@launch
           if (!playing.pattern.canSound) {
               metronome.stop()
               sendMessage(Message.SilentMetronomeStopped)
           }
       }
   }

   /** Called whenever the app is in sight again (ON_START), which takes back a stop [onAppStopped] has not made yet. */
   fun onAppStarted() {
       silentClickStopJob?.cancel()
       silentClickStopJob = null
   }
   ```

   with `private const val SILENT_CLICK_GRACE_MILLIS = 3_000L` beside the view model's other constants, and in `Message`:

   ```kotlin
   /** A click that could not sound was stopped as the app left the front, see [onAppStopped]. */
   data object SilentMetronomeStopped : Message
   ```

   The engine's own pattern (`playback.value`) is read rather than the view model's, since it is what is actually
   playing. `metronome.stop()` ends in `Stopped(null)`, so the existing `playback` collector sends nothing of its own;
   the message is the view model's, queued in `_messageQueue`, so the snackbar shows when the user comes back. The
   delay runs in the background on both phones because the click itself is what keeps the process running there (the
   active playback session on iOS, the foreground service on Android), and once it stops, `IosMetronomeNotifier` and
   `CampfireMetronomeService` follow the engine's `playback` to take Now Playing, the session and the service down -
   no shell code changes.

   A new `MetronomeStopReason` was considered and is not recommended: that enum is "Why a click ended without being
   asked to" as the *engine* reports it, and the engine knows nothing of the app's lifecycle; adding a reason would
   mean a `stop(reason)` on the `Metronome` contract for one caller.

3. **The composition**, `CampfireApp.kt`, beside the existing `ON_PAUSE` effect (outside the
   `if (isLibraryEditableOutsideApp)` block, unconditionally):

   ```kotlin
   // A click that cannot sound is stopped once the app has been out of sight for a moment, see
   // CampfireViewModel.onAppStopped. ON_STOP rather than ON_PAUSE: Control Center or a notification shade pulled over
   // the app pauses it with the click still in view.
   LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppStopped() }
   LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppStarted() }
   ```

   A recreated Android activity's new composition adds its observer to an already started lifecycle and is handed the
   catch-up `ON_START` (the `addObserver` replay), which cancels the pending stop. That replay only comes once the new
   activity's first composition has been applied, after its `onResume`, which on a slow phone with a song open can take
   most of a second: hence three seconds rather than one.

   In the message `when` (after `MetronomeStopped`):

   ```kotlin
   CampfireViewModel.Message.SilentMetronomeStopped -> stringResource(Res.string.metronome_stopped_silent)
   ```

4. **Strings**, after `metronome_stopped_failed` in both files:

   - `values/strings.xml`: `<string name="metronome_stopped_silent">The metronome stopped when Campfire went out of sight, since with the volume at zero or every beat muted there was nothing to hear.</string>`
   - `values-hu/strings.xml`: `<string name="metronome_stopped_silent">A metronóm leállt, amikor a Campfire a háttérbe került, mert nulla hangerőn vagy minden ütést elnémítva semmi sem hallatszott.</string>`

   No user-written text, so `stringResource`, not `textResource`.

5. **Documentation**:
   - Root `CLAUDE.md`, the "Playback is media" bullet, after "...nothing is left to look at the notification it keeps
     up.": "A click that cannot sound — the volume at zero or every beat muted — is the exception: it is stopped a
     few seconds after the app goes out of sight, and says so, since out of sight it has nothing left to show and the
     phones' background audio is not for silence (`MetronomePattern.canSound`, `CampfireViewModel.onAppStopped`)."
     And the last bullet's sentence becomes: "there is no mute of its own, since a volume of zero leaves the click
     running with nothing sounding — on screen, for the flash and the haptics."
   - `metronome/api/CLAUDE.md`: in the `model/` entry, after "why there is no mute of its own": "; `canSound` says
     whether a pattern is ever heard, for the app to stop a silent click it can no longer show".
   - `presentation/CLAUDE.md`, "The platform's side" bullet: add that `CampfireApp`'s `ON_STOP`/`ON_START` effects stop
     a click that cannot sound a few seconds after the app goes out of sight (`onAppStopped`), on every platform.
   - `app/ios/CLAUDE.md`, `IosMetronomeNotifier.kt` paragraph, after "...what App Review checks the mode against.":
     "A click that cannot sound is stopped by `:presentation` a few seconds after the app leaves the front, so the mode
     never keeps a silent session alive."
   - `app/android/CLAUDE.md`, the service paragraph: "A click that cannot sound is stopped a few seconds after the app goes
     out of sight (`:presentation`), which stops the service with it, so `mediaPlayback` never runs for silence."

### Verified (challenge)

- **Every platform emits the events** (Compose Multiplatform 1.12.1 sources): iOS `ComposeContainerLifecycleDelegate`
  is `CREATED` (so `ON_STOP`) once the scene is in the background or the view controller has disappeared, `STARTED`
  while it is only inactive (Control Center, the app switcher, a system alert); the desktop `ComposeContainer` is
  `CREATED` while the window is minimized or detached; the web window answers `visibilitychange` with `ON_STOP` /
  `ON_START` (and focus/blur with resume/pause). Android is the activity's own lifecycle.
- **The shells' teardown needs no composition**: `CampfireMetronomeService` and `IosMetronomeNotifier` each collect
  `metronome.playback` themselves and stop on anything but `Playing`. (`MetronomeNotificationEffect` collects with
  lifecycle and is idle in the background, so it could not have done it.)
- **The snackbar waits for the return**: `messageQueue` is read with `collectAsStateWithLifecycle`, so the message is
  only taken, and its duration only starts, once the app is started again.
- **`viewModelScope` keeps running**: the click itself (the active session on iOS, the foreground service on Android)
  is what keeps the process and its main thread up for the grace; on the web a hidden tab's timers are only
  throttled to once a second, well inside it.
- **Left out on purpose**: a click whose output could not open (`MetronomeAudioIssue.UNAVAILABLE`, on the silent
  output) is just as inaudible, but it is a failure the panel already reports, on iOS no session is active for it, and
  the message would name the wrong cause. A sync run that changes the open song's `{time}` to a signature whose stored
  accents are all muted could make a click silent in the background; that is not checked again (vanishingly rare, and
  harmless beyond the policy reading).

### Desktop and web

The rule is common, and harms neither: on the desktop `ON_STOP` comes with a minimized window, on the web with a hidden
tab — both places where a silent click has nothing to show, so stopping it (with the snackbar waiting on return) is
right there too, and not a policy matter. A click with any sound in it is untouched on every platform.

### Alternatives (not recommended)

- **Keep the volume above zero and refuse an all-muted bar** (slider minimum, a beat row that will not mute the last
  beat): changes foreground behaviour that is intended (the visual-only click, "whose zero is the mute"), and the docs
  built on it.
- **iOS only, in `IosAudioOutput`**: deactivate the session or pause the engine while the pattern is silent. Without
  an active session iOS suspends the app, so the clock and the beats stop anyway, and the output would need to know the
  app's lifecycle, which `:metronome` does not; the Android service case would remain.
- **Per shell**: Android `CampfireMainActivity.onStop` (`!isChangingConfigurations`) and iOS `scenePhase` in
  `iOSApp.swift`. Two implementations of one rule, each needing to reach the engine and the message queue; the common
  effect with a grace covers the recreation case the activity callback would have handled.
- **`ON_PAUSE` instead of `ON_STOP`**: stops the click whenever Control Center, the notification shade or a system
  dialog is pulled over the app in front, which is still the foreground case the click exists for.

## Tests

`metronome/api/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/api/MetronomePatternTest.kt` (new; existing
tests in that module are `TapTempoTest` and `TimeSignatureTest`, none covers `MetronomePattern`):

- `MetronomePattern(bpm = 120).canSound` is true (defaults: volume 1, default accents).
- `volume = 0f` is false, whatever the accents.
- `TimeSignature(4, 4)` with all four `BeatLevel.MUTED` is false, also with `subdivision = Subdivision.SIXTEENTHS`
  (a subdivision never sounds a muted beat).
- One `NORMAL` among muted beats, volume `0.01f`, is true.
- A short list is padded from the defaults: `TimeSignature(4, 4)` with `beatLevels = listOf(BeatLevel.MUTED)` is true
  (beats 2–4 take the signature's `NORMAL`).
- A long list is cut: `TimeSignature(3, 4)` with `listOf(MUTED, MUTED, MUTED, NORMAL)` is false.

The view model wiring is not unit tested, as no view model logic is here. Run
`./gradlew :metronome:api:desktopTest :presentation:desktopTest` and compile-check
`./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Manual check

On an iPhone (or the simulator) and an Android phone:

1. Metronome tab, volume at zero, start: the flash runs in front. Lock the screen, wait five seconds, unlock: the
   click is stopped, the snackbar says why; on iOS the lock screen's Now Playing entry is gone while locked, on
   Android the media notification is gone.
2. Same with the volume up and every beat of the bar tapped to muted (try a subdivision too): stopped the same way.
3. Volume up, one beat audible: lock, wait, unlock — the click plays throughout, as before.
4. Silent click, pull down Control Center (iOS) or the notification shade (Android) and put it away: still playing.
5. Android, silent click on a song's panel, switch the system dark theme from the quick settings tile without leaving
   the app (the activity is recreated): still playing.
6. Silent click, switch to another app and back within a second: still playing; stay away five seconds: stopped.
7. Switch the app language to Hungarian and repeat 1: the Hungarian message.
8. Desktop: minimize the window with a silent click, restore — stopped with the message; with an audible one, still
   playing. Web: hide the tab the same way.
