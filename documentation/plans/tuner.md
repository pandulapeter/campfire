<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Tuner — implementation plan

Written 2026-10-06 against `fbd742803`. A fourth tab that hears one note at a time through the microphone and says how
far it is from the note it should be, and plays that note for tuning by ear. Nothing about it reaches the network, and
nothing it hears is kept.

## 1. What is being built

1. **A Tuner tab**, after the Metronome: Songs, Setlists, Metronome, Tuner, Settings. Address `/tuner` on the web.
2. **A chromatic tuner and instrument presets**: the note, its octave, the cents it is off by on a meter, and the
   frequency heard. With a preset the target is the nearest string of that instrument rather than the nearest semitone.
3. **A reference pitch** (A4 = 440 Hz, 415–466) and note names in the reader's chord notation (`H` for B in German).
4. **Reference tones**: every string of a preset, and the A of the reference pitch, is a chip that plays its note until
   it is tapped again. This is what the page is still good for with no microphone at all.
5. **The microphone is asked for on the page, by a button**, never at launch and never by opening the tab. A refusal,
   a missing microphone and a microphone somebody else holds each say what happened and what to do, with a way into the
   system's settings wherever the platform has one.
6. **A Tuner switch in Settings → Features**, after Metronome. Off takes the tab away, and with it every way the app
   could ever ask for the microphone.

### What is already there, and what is not

- The Metronome tab is the model for all of it: a module pair that depends on nothing of the app's, an engine that never
  throws and says why it stopped, an instrument pinned over a `SettingsPage`, a feature switch that cuts the tab out of
  the chrome and out of a restored back stack, settings saved like `metronomeSettings`.
- `SyncNotificationPermission.kt` is the one permission the app asks for today, and the precedent for where an Android
  request lives (`:presentation`'s `androidMain`, since it needs the Activity's result launcher).
- **The metronome's `AudioOutput` is not reusable for the tones**, which I said it would be before reading it: it is
  `internal` to `:metronome:implementation`, typed to `ClickStream`, and the web one schedules single clicks on the
  audio clock rather than playing a stream. Making it shared is a refactor of timing code that is verified by ear. The
  tones get a small output of their own instead (§4.2), which is cheap for a different reason: a steady tone is a
  looped buffer, and needs no feed thread, no heard-frame and no background playback.
- Nothing in the app records: no permission, no usage string, no entitlement, no capability.

### Defaults this plan assumes — veto any of them before the work starts

1. **On by default**, like every other feature. The tab is there after an update; the microphone is still only asked
   for by a tap on it.
2. **Listening starts by itself once the permission is there**, whenever the tab is on screen and the app is in front,
   and stops the moment either ends. There is no start button after the first grant, so the system's recording indicator
   is lit exactly while the tab is showing.
3. **Nothing of the tuner outlives its screen**: no background mode, no service, no notification. Leaving the tab, the
   app going to the background or the screen locking stops the listening and the tone.
4. **A tone and the meter are not shown together.** While a tone sounds the microphone stays open (reopening it asks
   again in Firefox and Safari) but what it hears is ignored, and the display names the note being played.
5. **Presets in v1**: Chromatic, Guitar, Guitar drop D, Bass, Ukulele, Violin, Mandolin, Banjo. No custom tunings.
6. **Names**: sharps in chromatic mode, the preset's own spelling for its strings, with `#` and `b` as the chords on a
   page have them, the octave in scientific numbering (`E2`, `A4`).
7. **In tune is ±5 cents**, held for a moment before it is said. A constant, to be settled by ear.
8. **Read only mode changes nothing here**, as on the Metronome tab: nothing on the page writes a file.
9. **The preset, the reference pitch and nothing else are remembered** (`UserPreferences.tunerSettings`), never
   exported or synced.

## 2. Modules

```
tuner:api / :implementation      the tuner: the Tuner contract, the note arithmetic and the instrument presets, and the
                                 engine with one audio input and one tone output per platform. Depends on nothing
                                 of the app's; used by :presentation
```

Both apply `campfire-library`; `:tuner:implementation` also the Koin compiler plugin. `settings.gradle.kts` includes
them, `:presentation` depends on `:tuner:api`, `:app:di` on `:tuner:implementation` and names `TunerModule` as the
seventh module object. No app shell needs the api: there is no service and no Now Playing.

### 2.1 `:tuner:api`

- `Tuner` — the one stateful interface, every call returning at once and never throwing:
  - `state: StateFlow<TunerState>`;
  - `listen(config: TunerConfig)`, `update(config)`, `stopListening()`;
  - `playTone(note: Int, referencePitch: Int)`, `stopTone()`;
  - `setStartable(isStartable: Boolean)`, the web's gesture rule, as on `Metronome`.
- `model/`:
  - `TunerConfig(referencePitch: Int, tuning: InstrumentTuning?)`, null being chromatic.
  - `TunerState(listening: TunerListening, tone: Int?)`.
  - `TunerListening`: `Stopped(reason: TunerStopReason?)`, `Starting`, `Hearing(reading: TunerReading?, issue:
    TunerInputIssue?)`.
  - `TunerReading(note: Int, cents: Float, frequency: Float, isInTune: Boolean)`, `note` a MIDI number and already the
    target the config asks for.
  - `TunerStopReason`: `PERMISSION_DENIED`, `NO_MICROPHONE`, `MICROPHONE_BUSY`, `MICROPHONE_DISCONNECTED`,
    `NOT_SUPPORTED` (a page outside a secure context, a browser without `getUserMedia`), `FAILED`.
  - `TunerInputIssue`: `SILENT` (see §3.2), `WAITING_FOR_GESTURE`.
  - `InstrumentTuning(id: String, strings: List<Int>)` and `InstrumentTuning.entries`, the presets of default 5.
- `Pitch` — pure arithmetic: `frequencyOf(note, referencePitch)`, `noteOf(frequency, referencePitch)` as a note and
  its cents, `nearestString(frequency, tuning, referencePitch)`, and `REFERENCE_PITCH_RANGE`.

### 2.2 `:tuner:implementation`

`TunerImpl` (`@Single`) runs every call on one confined coroutine, like `MetronomeImpl`. While listening it polls the
input for its latest window about thirty times a second, runs the detector and the tracker, and publishes a state only
when it differs. `AudioInput` and `ToneOutput` are one annotated class per platform source set, the Android ones taking
the `@Provided` `Context`.

## 3. Hearing a note (`commonMain`, all of it tested)

### 3.1 `PitchDetector`

The McLeod pitch method, which is what makes the result steady on a plucked string and resistant to octave errors.

- **Window**: about 85 ms of the input (4096 frames at 44.1 or 48 kHz, the next power of two for another rate), which
  holds two periods of anything above 24 Hz. The mean is taken out; no window function.
- **The normalized square difference** `2·r(τ) / m(τ)`: the autocorrelation `r` through one forward and one inverse
  FFT of the window padded to twice its length (`Fft`, a small radix-2 one of its own), `m` from running sums. That is
  two 8192-point transforms per reading, which costs nothing on any platform, Wasm included.
- **The peak**: the first maximum between positive-going zero crossings that reaches 0.9 of the highest one, refined
  by a parabola through its neighbours. Its height is the clarity.
- **The range searched**: 30–2100 Hz in chromatic mode (B0 to C7), and from four semitones under a preset's lowest
  string to an octave over its highest, which is most of what keeps a bass from being read an octave up.
- **Nothing is reported** below a clarity of 0.8 or below the gate: an absolute floor, or twice a slowly tracked
  noise floor, whichever is higher.

All buffers are allocated once per sample rate and reused.

### 3.2 `PitchTracker`

What turns thirty raw answers a second into a display that can be read. Pure, driven by a frame count rather than a
clock.

- The first 60 ms after an onset (the level jumping by 6 dB) are skipped: a plucked string starts sharp and noisy.
- The median of the last five answers, then the cents smoothed with a time constant of about 80 ms.
- **The target note changes only once the new one has held for three answers**, so a name never flickers between two.
- After the signal falls under the gate the last reading is held for half a second, then let go.
- `isInTune` once the smoothed value has stayed within the in-tune range for 300 ms.
- `SILENT` once every sample has been exactly zero for two seconds, which a live microphone never is.

### 3.3 `ToneSynthesizer`

`loopOf(frequency, sampleRate): ShortArray` — about a second of the note, written so that the buffer loops without a
seam: a whole number of periods, the frequency moved to the one that fits the buffer exactly, which is never more than
0.02 cents off. The fundamental plus three falling harmonics, because a phone's speaker cannot play the fundamental
of a guitar's or a bass's low strings and the ear finds the pitch from the harmonics.

### 3.4 Tests (`desktopTest`)

- `PitchDetectorTest`: sines and harmonic-rich strings (Karplus-Strong from a seeded `Random`) at every string of every
  preset and across the chromatic range, within ±1 cent up to 1 kHz and ±3 above; the same at 20 dB of noise; a string
  whose fundamental is 20 dB under its second harmonic (a low E through a phone's microphone) read at the right
  octave; silence, noise and a chord answer nothing; 16, 44.1 and 48 kHz.
- `PitchTrackerTest`: the attack skipped, no flicker at a boundary, the hold and the release, in tune after the delay,
  `SILENT`.
- `ToneSynthesizerTest`: the loop's seam, the frequency by zero crossings, peaks under full scale.
- `PitchTest` and `InstrumentTuningTest` in `:tuner:api`.
- Recordings are worth more than synthesis: a reader for 16-bit mono WAVs in `desktopTest/resources/tuner/`, named by
  the note they hold (`E2.wav`), so that a string recorded on a real phone becomes a test by being dropped in.

## 4. The platforms

### 4.1 `AudioInput`

```kotlin
internal interface AudioInput {
    /** Opens the microphone. Suspends since the web's answer is a promise; never throws and never asks for a permission. */
    suspend fun start(listener: AudioInputListener): AudioInputStart   // Started(sampleRate) | Refused(reason)
    /** Copies the latest window.size frames into [window] and answers whether that many have arrived yet. */
    fun latest(window: FloatArray): Boolean
    fun stop()
    fun setGestureListening(isEnabled: Boolean) = Unit
}
```

A pull of the latest window rather than a stream of chunks, which is what the web has natively and what a ring buffer
gives the other three. Every platform turns its voice processing off, since gain control and noise suppression are
written to remove exactly a held note.

- **Android** — `AudioRecord`, mono 16-bit at 48 kHz, source `UNPROCESSED` where
  `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` says so and `VOICE_RECOGNITION` elsewhere, read by a thread of its own
  into the ring. No permission is `Refused(PERMISSION_DENIED)` from `checkSelfPermission`, before anything is opened.
  An `AudioRecordingCallback` reports the client being silenced (a call) as `MICROPHONE_BUSY`. No Bluetooth SCO is
  ever started, so a headset never becomes the input.
- **iOS** — `AVAudioEngine`'s input node with a tap of 4096 frames, whose block runs on a queue of the engine's and not
  on the real-time thread, so Kotlin may run in it. The session is `playAndRecord` in `measurement` mode with
  `defaultToSpeaker` and no Bluetooth option, activated on start and deactivated with `notifyOthersOnDeactivation`.
  Started only when `recordPermission` is granted, since touching the input otherwise makes the system ask on its own.
  Interruptions, a route change and a media services reset stop it with their reason, as in `AudioOutput.ios.kt`. The
  deployment target is 15.3, so this is `AVAudioSession`'s permission API, not iOS 17's `AVAudioApplication`.
- **Desktop** — a `TargetDataLine` (mono 16-bit, 48 kHz) read by a daemon thread. No line is `NO_MICROPHONE`. The JVM
  cannot ask macOS or Windows whether it may record, and a refusal there arrives as digital silence rather than as an
  error, which is what `SILENT` is for; a line that stays silent is reopened every few seconds, since macOS answers
  its own prompt after the line is already open.
- **Web** — `getUserMedia` with `echoCancellation`, `noiseSuppression` and `autoGainControl` off, into an
  `AnalyserNode` of an `AudioContext` of the tuner's own, connected to nothing. `latest` is one
  `getFloatTimeDomainData` and a copy. The rejection's name is the reason: `NotAllowedError`, `NotFoundError`,
  `NotReadableError`; no `mediaDevices` is `NOT_SUPPORTED`. A context that is not running is `WAITING_FOR_GESTURE`,
  resumed by the same capture-phase listener the metronome's output uses. `stop` ends the stream's tracks, which is
  what puts the browser's recording indicator out. A track's `ended` is `MICROPHONE_DISCONNECTED`. All through
  `js(...)` blocks; no new static file, so the distribution's file list and the service worker's cache are untouched.

### 4.2 `ToneOutput`

`play(samples: ShortArray, sampleRate: Int)` and `stop()`, with a fade of a few milliseconds at both ends so that
neither clicks.

- **Android** — a static `AudioTrack` with `setLoopPoints`, a `VolumeShaper` for the fades, transient audio focus
  whose loss stops the tone.
- **iOS** — an `AVAudioPlayerNode` with the one buffer scheduled looping, on the engine the input runs in (one class
  is both definitions there, since the two share the engine and the session); with no microphone permission the
  session is `playback` instead.
- **Desktop** — a `Clip` looped continuously.
- **Web** — an `AudioBufferSourceNode` with `loop`, through a gain node that ramps.

### 4.3 Permissions and packaging

| Where | What is added |
| --- | --- |
| `app/android` manifest | `RECORD_AUDIO`, and `<uses-feature android:name="android.hardware.microphone" android:required="false" />`, without which Play hides the app from every device that has no microphone |
| `app/ios` `Info.plist` | `NSMicrophoneUsageDescription`; no new background mode |
| `app/desktop/build.gradle.kts` | `NSMicrophoneUsageDescription` in `extraKeysRawXml`, without which macOS ends the process at the first read |
| `app/desktop/app-store.entitlements` | `com.apple.security.device.audio-input`, with its line in the comment; the runtime's entitlements stay as they are |
| `app/desktop/AppxManifest.xml` | `<DeviceCapability Name="microphone" />`, last in `Capabilities`, so that Windows lists Campfire by name under its microphone privacy settings |
| `app/web` | `tuner` in the `ROUTES` of `routes.js` and of `service-worker.js`, and in their Node tests |

The usage strings are in English, as the store listings are, and say what §5.4's first notice says.

## 5. `:presentation`

### 5.1 The feature and the destination

- `UserPreferences.isTunerEnabled` (true) and `tunerSettings: TunerSettings(instrumentId = "chromatic", referencePitch
  = 440)` in `:data:model`, with `UserPreferencesDocument`, `TunerSettingsDocument`, the mappers and their two tests.
  An id this version does not know reads as chromatic.
- `CampfireDestination.Tuner` (`contentKey = "tuner"`), in `TopLevel.entries` after `Metronome`. `entries(…)`,
  `isEnabled` and `NavigationState.withoutDisabledFeatures` take `isTunerEnabled`; `FeatureDestinationsTest` grows with
  them. `BrowserRoutes` gets `tuner` in `paths` and `resolve`.
- `CampfireApp`: the entry, laid out like the Metronome's; `ic_tuner` and the `tuner` label; the tab leaves and joins
  the chrome through `NavigationItemPresence` like the other two.
- Settings → Features: a **Tuner** switch after Metronome (`setTunerEnabled`), described as the tab and the
  microphone it listens through.
- **Five items in the bar**: on a 360dp phone each gets 72dp, and "Beállítások" is the longest label. To be looked at
  on the smallest screen before anything else is built (§8).

### 5.2 The view model

`Tuner` is injected into `CampfireViewModel` next to `Metronome`.

- `tunerState`, and `tunerSettings` with a pending overlay saved once it has held still, exactly as
  `metronomeSettings` is (and written with the others in `onCleared` and before a desktop quit).
- `setTunerListening(isListening: Boolean)`, called by the screen; `toggleTunerTone(note)`; `updateTunerSettings`,
  which also sends the new config to a listening tuner.
- **Two rules stop it outright**, the metronome's own: `updateBackStack` whenever the top is not `Tuner`, and
  `onCleared`. The screen's lifecycle effect is the third, for the app leaving the front.
- A tuner that stops on its own is not a snackbar: the page it stopped on is showing the reason already.

### 5.3 The screen (`ui/screens/tuner/`)

The Metronome tab's shape: an instrument pinned at the top, a `SettingsPage` scrolling under it.

- **`TunerDisplay`**, pinned, capped at the page's width: the note in the display size with its octave beside it; under
  it a horizontal cents meter from −50 to +50 with a marker that moves on a spring, a centre notch, and flat and sharp
  marks at its ends; under that the frequency heard and the target's. In tune, the marker and the note take the second
  accent color *and* the notch closes into a check, so it is never said by color alone. With nothing heard the meter
  rests and the note's place says "Play a note". While a tone sounds it names that note and the meter rests.
  In a short window (`SHORT_WINDOW_HEIGHT`) the note and the meter share one row of about 96dp.
- **The page**, one section:
  - the notice, where there is one (below);
  - **Instrument**: the presets as chips;
  - **Strings**, with a preset: one chip per string, low to high, named and numbered, the one being heard marked; a tap
    plays its tone, a second tap or another chip ends it. Hidden in chromatic mode;
  - **Reference pitch**: the `Stepper` the tempo uses, `A4 = 440 Hz`, a tap on the value resetting it, and a play chip
    for that A.
- The screen is kept on while it listens (`keepScreenOn`, as the Metronome tab does while it plays).
- `ui/tuner/NoteNames.kt`, pure and tested: a MIDI note to its name in the reader's `ChordNotation`.
- A `LifecycleStartEffect` tells the view model to listen while the screen is started and the permission allows it,
  and to stop otherwise; it also sets `setStartable`.

**The first frame is already right**: the permission is read synchronously where the platform answers that way, so a
tab opened with the permission granted never shows the notice for a frame. The web's answer is a promise, asked once
as the app starts and kept.

### 5.4 The microphone, asked on the page

`ui/platform/MicrophonePermission.kt`: `rememberMicrophonePermission(): MicrophonePermission`, with a `status`
(`GRANTED`, `NOT_ASKED`, `DENIED`, `UNKNOWN`), `request()` and `openSettings: (() -> Unit)?`.

| Platform | `status` | `request()` | `openSettings` |
| --- | --- | --- | --- |
| Android | `checkSelfPermission`, read again on resume | the Activity result launcher | the app's page of the system settings |
| iOS | `AVAudioSession.recordPermission`, read again on resume | `requestRecordPermission` | `UIApplicationOpenSettingsURLString` |
| Web | `navigator.permissions.query`, `UNKNOWN` where the browser has none | starts listening, which is what asks | none: the notice says where the browser keeps it |
| Desktop | `UNKNOWN` | starts listening | macOS's and Windows' microphone privacy pages by their URLs, none on Linux |

The notice is the page's first item, in place of the display, which has nothing to show without a microphone. It
scrolls with the page, so it costs a short window nothing. Each is a sentence, and at most two buttons:

| State | It says | Buttons |
| --- | --- | --- |
| Not asked yet | Campfire listens through the microphone to hear the instrument. The sound is analyzed on this device, and is never recorded or sent anywhere. | **Use the microphone** |
| Refused | The microphone is switched off for Campfire. The strings below still play their notes. | **Open settings**; **Ask again** on Android while the system would still show its dialog, and on the web |
| Refused, web | The same, and that the browser's site settings, next to the address, are where it is allowed. | **Try again** |
| No microphone | No microphone was found. | **Try again** |
| Busy | The microphone is in use by a call or another app. | **Try again** |
| Not supported | This browser does not let a page use the microphone. | none |
| Failed | The microphone could not be opened. | **Try again** |

Two more are a line above the Instrument row while the display stays up, as the Metronome tab's `audioIssue` is:
`SILENT` — "Nothing at all is heard. Check that the microphone is not muted and that Campfire may use it.", with
**Open settings** where there is one — and `WAITING_FOR_GESTURE`.

Coming back from the system's settings is a resume: the status is read again and listening starts without a tap.
Android never keeps a "was asked" flag: a request whose answer is a refusal with no rationale owed before or after it
is one the system no longer shows, and that is when **Open settings** becomes the first button.

Every string is added to both languages.

## 6. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| Tuner switched off | No tab, no `/tuner` (it opens the songs), nothing that could ask. The stored settings stay. |
| Tab opened by its address on the web | No gesture yet: "Tap anywhere to start listening", then as usual. |
| Permission revoked while the app runs | Android restarts the process; iOS and the web end the input, and the page shows Refused. |
| A call arrives | The input is interrupted: Busy, and listening again on the next resume. |
| Headphones plugged in or pulled | The input restarts once on the new route. |
| Bluetooth headset connected | The built-in microphone stays the input on both phones; a tone plays from the phone's speaker on iOS. |
| A metronome click | Cannot be playing: every way onto another tab stops it. The two never hold the audio together. |
| A chord, a voice, a room | No clear pitch, so no reading. A loud room is where a microphone tuner stops working. |
| A string more than a semitone from every string of the preset | Still read against the nearest one, the meter pinned at its end with the direction. |
| The preset changed while a tone plays | The tone ends. |
| The reference pitch changed while a tone plays | The tone moves to the new pitch. |
| Read only mode | Everything stays. |
| Sync, export, import, device backup | Nothing to carry but the two settings, which go where the preferences go. |
| Flexible update's Restart, a sync run, an import | Unaffected by the tuner and the other way round. |
| An "Open with" or a drop while tuning | The import's own screens come on top, which stops the tuner like any other screen. |
| Baseline profile journey | Visits the tab; nothing there asks for anything without a tap. |

## 7. Order of work

Each step builds and tests green on its own, one commit each. Step 1 goes first because it is the one thing that could
change the plan.

1. **A spike, thrown away**: twenty lines reading a `TargetDataLine` inside the sandboxed Mac build signed ad hoc with
   the new entitlement and usage string, to see the system's prompt appear and samples follow it. If a sandboxed JVM
   cannot be given the microphone, the Mac build ships the tones without the listening and says so in the notice;
   nothing else in the plan moves.
2. `:tuner:api` and the pure half of `:tuner:implementation` (§3), with their tests.
3. `TunerImpl` and the desktop input and tone output, verified with a real instrument through `:app:desktop:run`.
4. The feature switch, the destination, the routes, the screen and the permission contract, on the desktop.
5. Android: the input, the tone, the manifest, the permission actual.
6. iOS: the same, and the session.
7. Web: the same, and the routes' Node tests.
8. Packaging: the Mac entitlement and usage string, the MSIX capability.
9. Docs: the root `CLAUDE.md` (the architecture block, Features, a Tuner section, the test list and command, "the six
   module objects"), `tuner/*/CLAUDE.md`, `presentation/CLAUDE.md`, `data/model`, `app/android`, `app/ios`,
   `app/desktop`, `app/web`, `app/di`, `app/baselineprofile`; the README's feature list; the journey in
   `BaselineProfileGenerator`.

## 8. Checks owed by hand

None of this can be done on an emulator or a simulator's microphone.

- **Before step 2**: five navigation items on the 360 × 640 dp screen, in Hungarian.
- **Accuracy**, on every platform, against a hardware tuner: all six guitar strings, a bass's low E, a ukulele; the
  needle steady on a ringing string; no octave jumps as a note dies away.
- **Each notice** on each platform: first ask, refuse, refuse for good, grant from the system's settings and come back,
  revoke, no microphone (a desktop with none), a call during listening.
- Android: the recording indicator on only while the tab shows; the app still offered to a device with no microphone.
- iOS: the orange dot gone on leaving the tab and on locking; the tone's loudness in `measurement` mode (if it is too
  quiet, the session switches category for a tone instead); the silent switch; a metronome click right after tuning.
- Mac App Store build: the prompt, a refusal read as `SILENT` with a working settings link, the same in TestFlight.
- Windows: Campfire listed by name under the microphone privacy page; the toggle off read as `SILENT` or as Failed.
- Linux: PipeWire and PulseAudio defaults; a machine with no input.
- Web: Chrome, Firefox and Safari (iOS included): the prompt, a block, the indicator out on leaving the tab, `/tuner`
  opened cold.
- The tones by ear against the hardware tuner, and with no microphone permission at all.

## 9. Outside the repository

- `campfire-website`: one sentence in the privacy policy (the microphone is used by the tuner, on the device, and
  nothing is recorded or sent), and a support answer for "Why does Campfire ask for the microphone?".
- Play Console: the Data safety form stays "no data collected" (audio is processed on the device and not kept), to be
  confirmed against the form's wording; the listing gains the permission.
- App Store Connect, both apps: the privacy label is unchanged; a line in the review notes saying where the microphone
  is used.
- Partner Center: the listing shows the microphone capability; nothing to fill in.
- A What's new message, by the prepare-release skill.

## 10. Not in this plan

- Tuning all strings from one strum (polyphonic detection), which is the hard problem this plan stays out of.
- Custom tunings, and tunings read from a song (a `{capo}` or a tuning in its header setting the preset).
- A tuner inside the song details screen.
- A strobe display, a haptic tick on reaching the note, keyboard shortcuts for the strings.
- Transposing instruments and temperaments other than equal.
- A shared audio module under the metronome and the tuner. Worth doing if a third thing ever plays sound (backing
  tracks); not worth disturbing the click's timing code for a looped tone.
