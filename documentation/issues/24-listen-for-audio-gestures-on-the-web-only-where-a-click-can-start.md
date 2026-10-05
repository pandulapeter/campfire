# Listen for the web audio gesture only while a screen that can start a click is on top

**Decided (user, 2026-10-05):** arm the listener only where a click can start (the recommended fix).

**Kind:** bug (resource use / behaviour beyond what is documented)  ·  **Severity:** low  ·  **Platforms:** web
**Files:** metronome/api/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/api/Metronome.kt, metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/AudioOutput.kt, metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeImpl.kt, metronome/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/metronome/implementation/AudioOutput.wasmJs.kt, presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt, metronome/api/CLAUDE.md, metronome/implementation/CLAUDE.md, presentation/CLAUDE.md (one clause, shared file)

## Problem

`WebAudioOutput` installs its gesture listener in its constructor:

```kotlin
@Single
internal class WebAudioOutput : AudioOutput {
    ...
    init {
        installAudio()
    }
```

and `installAudio()` registers, in the capture phase on `window`, for `pointerdown`, `pointerup`, `touchend` and
`keydown`, a handler that creates an `AudioContext` on the first press and `resume()`s it on every one, suspending it
again three seconds after the last. `WebAudioOutput` is built as `MetronomeImpl`'s dependency, and `MetronomeImpl` is a
constructor parameter of `CampfireViewModel` (`private val metronome: Metronome`), which every launch creates. So from
the first tap of every session — on the song list, in Settings, in the editor, with the Metronome feature switched off
in Settings → Features — every press opens the audio device for three seconds. That is a running audio context the
user never asked for: on some systems it keeps the output device awake, can switch a Bluetooth headset's profile, and
on iOS Safari a running Web Audio context may interrupt or duck audio from other apps. `metronome/implementation/CLAUDE.md`
describes the listener ("resumes … the context on every press and key") but not that it runs app-wide and regardless of
the feature switch, and a switch that "only hides" the metronome still leaves this on.

## Fix

Arm the listener only while a click can be started from what is on screen, or one is playing.

1. **`:metronome:api`** — add to `Metronome`:
   ```kotlin
   /**
    * Whether a screen from which a click can be started is showing. Only the web uses it: a page may only start its
    * audio inside a user gesture, so the output listens for presses while this is true and leaves the audio device
    * alone otherwise. Elsewhere it does nothing.
    */
   fun setStartable(isStartable: Boolean)
   ```
   (name open; `setOutputWanted` would do too.)
2. **`AudioOutput`** — add `fun setGestureListening(isEnabled: Boolean) {}` with an empty default body, so the Android,
   iOS, desktop and silent outputs need no change. `MetronomeImpl.setStartable` forwards it on the engine's confined
   coroutine (`onEngine { output.setGestureListening(isStartable) }`).
3. **`WebAudioOutput`** — remove the `init { installAudio() }`; `installAudio()` keeps creating the shared
   `window.__campfireMetronome` object but no longer adds the listeners. Add `armGestures()` / `disarmGestures()`
   `js(...)` functions that add and remove the same `onGesture` (store it on `window.__campfireMetronome.onGesture` so
   removal finds the same function; `removeEventListener` with `true` for capture). Call `installAudio()` from both
   `setGestureListening` and `openContext`'s path so the object exists either way. `setGestureListening(false)` while a
   click plays leaves the context alone (the listener only resumes; `closeContext` suspends on stop as today).
4. **`CampfireWebApp`** (wasmJsMain, this lane) — inject the engine with `koinInject<Metronome>()` (presentation already
   depends on `:metronome:api`) and add:
   ```kotlin
   LaunchedEffect(viewModel, metronome) {
       snapshotFlow { viewModel.backStack.lastOrNull() }
           .combine(viewModel.userPreferences.map { it?.isMetronomeEnabled != false }) { top, isEnabled ->
               isEnabled && (top == CampfireDestination.Metronome || top is CampfireDestination.SongDetails)
           }
           .distinctUntilChanged()
           .collect(metronome::setStartable)
   }
   ```
   A plain `collect`, not lifecycle-aware, like the other web effects here. Those two destinations are exactly where
   `toggleMetronomeByKey` and the Play buttons live (the song details app bar's Play is there whenever the feature is
   on, panel open or not), and the listener is armed before the tap that starts a click because the screen composes
   first. No change to `presentation/src/commonMain`.

Docs: in `metronome/implementation/CLAUDE.md`'s Web bullet, say the capture-phase listener is installed only while the
UI reports a screen that can start a click (`Metronome.setStartable`), so the rest of the app never opens the audio
device; add `setStartable` to `metronome/api/CLAUDE.md`'s description of the contract; in `presentation/CLAUDE.md`'s
`CampfireWebApp.kt` entry, one clause that it reports the startable screens to the metronome.

Open decision: arm the listener as above (recommended), or keep the eager listener and only document that every press
anywhere opens the audio context for three seconds on the web. The second costs nothing but leaves the behaviour.

## Tests

None: browser listener wiring and a Compose effect, outside the pure logic that is unit tested.

## Manual check

In Chrome, open the web build with `chrome://media-internals` (Audio Focus / Players tab) or DevTools' WebAudio panel
open. Tap around the song list and Settings: no `AudioContext` is created (before the fix one appears on the first tap
and toggles running/suspended). Open the Metronome tab, tap Play: the click is heard at once. Open a song, tap the app
bar's Play: heard at once. Switch the Metronome feature off, tap around a song: no context. On iOS Safari with music
playing in another app, tapping around the song list no longer affects the music.
