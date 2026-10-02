<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Metronome — implementation plan

Written 2026-10-02 against `8c267e01a`. Nothing in the app plays a sound today, so this adds a new capability
(audio output, background playback) as well as a new screen.

## 1. What is being built

1. **A Metronome tab**, the third top-level destination (Songs, Setlists, **Metronome**, Settings): the full
   instrument — BPM, tap tempo, time signature, per-beat accents, subdivisions, sound, volume, visual beat, haptics.
2. **A mini metronome on the song details screen**: one tap starts a click in the song's tempo and time signature
   (120 BPM and 4/4 where the song says nothing), with a tempo stepper and tap tempo beside the transposition.
3. **Tempo overrides that mirror transpositions**: a song opened from the library keeps its override in the
   preferences, a song opened from a setlist keeps it in that setlist's entry. The song file is never written.
4. **Setlist paging retargets a running click** to the next song's tempo and signature without a gap.
5. **Background playback as media**: the click carries on with the screen locked, with the platform's own
   play/stop controls, and stops for a call, an unplugged headset or another app taking the audio.

### Decisions already taken (2026-10-02)

| Question | Answer |
| --- | --- |
| Where a BPM change from a song is stored | Preferences (library) and the setlist entry (setlist), exactly like transposition. `{tempo}` in the file only changes in the editor. |
| Background | Yes, as media playback: Android foreground service + media session, iOS audio background mode + Now Playing. |
| Paging while playing | Keep playing, retarget to the new song. |
| Extras in the first version | Subdivisions; visual beat, mute (visual only) and haptics. **Not** count-in, **not** a tempo trainer. |

### Defaults this plan assumes — veto any of them before the work starts

1. **Sounds are synthesized in Kotlin**, not shipped as audio files: no assets, no decoder per platform, nothing for
   the web build's cache to carry, and every platform sounds identical.
2. **BPM counts the clicks of the bar**: in 6/8 at 120, six clicks a bar at 120 a minute. No dotted-beat reading.
3. **Range 30–300 BPM**, whole numbers. A file's `{tempo: 97.5}` is rounded; one outside the range is clamped for
   playback and shown as written on the page.
4. **Only a song's first `{tempo}` and `{time}` count.** Mid-song tempo changes are out of scope: the app does not
   know where in the song the band is.
5. **A retarget happens on the next click, which becomes beat one** of the new song's bar — not at the end of the
   old bar, which at 60 BPM in 4/4 could be four seconds away and would not read as "immediately".
6. **Leaving the song details screen stops a click that was playing for a song.** Backgrounding the app does not.
   A click started on the Metronome tab carries on across tabs; opening a song while it runs retargets it to the song.
7. **Performance mode keeps play/stop and hides the per-song tempo stepper**, as it hides the transposition. The
   Metronome tab stays fully usable there: it changes no song and no setlist.
8. **The metronome takes the audio like any player** (audio focus on Android, a non-mixable session on iOS), which
   is what the lock screen controls require. Clicking along with another app's backing track is a later switch
   ("Play alongside other audio"), see §12.
9. **A setlist does not inherit the library override**: setlist entry → the file's `{tempo}` → 120. Verified
   against the code: `Transpositions[song, setlist]` reads only the setlist's entry when a setlist is given and
   only the preferences when not, and `addSongToSetlist` creates a plain `Setlist.Entry` without copying the
   library's transposition. Tempo follows both rules, which keeps a setlist reading the same on every device.
10. **The editor gets no metronome.** `{tempo}` is typed there as before.

## 2. Architecture

### 2.1 A new module pair: `:metronome:api` and `:metronome:implementation`

The click is neither data nor UI: it is a real-time service that belongs to the **app**, as a sync run does, and has
to outlive the screen that started it and (on Android) the activity. It gets its own layer rather than being bent into
repositories and single-method use cases.

```
metronome:api              campfire-library; depends on nothing
  Metronome                the one stateful contract (below)
  model/                   MetronomePattern, TimeSignature, BeatLevel, Subdivision, MetronomeSound,
                           MetronomePlayback, MetronomeBeat
  TapTempo                 pure
metronome:implementation   campfire-library + Koin compiler plugin; depends on :metronome:api
  MetronomeImpl            @Single, owns the audio thread / scheduler and the state
  MetronomeSequencer       pure: where in the sample stream every click falls
  ClickSynthesizer         pure: the PCM of each sound
  ClickMixer               pure: sequencer ticks -> PCM chunks
  AudioOutput              internal interface; one annotated class per platform source set
  Module.kt                @Module @ComponentScan object MetronomeModule
```

- `:presentation` depends on `:metronome:api` next to `:domain:api` and `:chordpro`; `CampfireViewModel` gets
  `Metronome` injected. `:app:android` and `:app:ios` depend on it too, for the service and the Now Playing shell.
- `:app:di` names `MetronomeModule` as the sixth module object of the `@KoinApplication`; `app/di/CLAUDE.md` and
  the root one say "six".
- Settings do **not** flow into the module from the repositories: the view model reads `UserPreferences` and hands
  the engine a complete `MetronomePattern`. The module stays free of every other one.
- `settings.gradle.kts` includes both; the convention plugin derives namespace and `archivesName` as for the rest.

```kotlin
interface Metronome {
    val playback: StateFlow<MetronomePlayback>      // Stopped | Playing(pattern) | Unavailable(reason)
    val beats: SharedFlow<MetronomeBeat>            // one per click, emitted when it is *heard*
    fun start(pattern: MetronomePattern)
    fun update(pattern: MetronomePattern, restartBar: Boolean)   // applied on the next click
    fun stop()
}

data class MetronomePattern(
    val bpm: Int,
    val timeSignature: TimeSignature,               // beats 1..16, unit 1/2/4/8/16
    val beatLevels: List<BeatLevel>,                // ACCENT, NORMAL, MUTED; one per beat
    val subdivision: Subdivision,                   // NONE, EIGHTHS, TRIPLETS, SIXTEENTHS
    val sound: MetronomeSound,
    val volume: Float,                              // 0..1, on top of the system volume
    val isMuted: Boolean,                           // visual only: the clock runs, nothing sounds
)
```

### 2.2 Timing: the audio clock, never a timer

A `delay()` loop drifts and jitters audibly. Every click is placed **by sample count** in the output stream, so the
tempo is exactly as accurate as the sound card's clock.

- `MetronomeSequencer(sampleRate)` holds the position in frames and answers `ticksUntil(frame)`: a list of
  `Tick(frame, level, isSubdivision, beatIndex, barIndex)`. A pattern change replaces the pattern for every tick not
  yet handed out; `restartBar` makes the next beat boundary beat one. Pure, and the most tested class of the feature.
- `ClickSynthesizer` renders each sound's three voices (accent, normal, subdivision) once per sample rate into a
  `FloatArray`: 20–60 ms bursts of a decaying sine, filtered noise or two detuned partials. Sound sets for v1:
  **Click**, **Woodblock**, **Beep**, **Sticks**, **Cowbell**. Deterministic, so golden-tested by checksum.
- `ClickMixer` asks the sequencer for the ticks of the next chunk and mixes the voices into a 16-bit PCM buffer,
  carrying a voice's tail across chunk boundaries.
- Three platforms **push PCM** from one dedicated thread; the web **schedules events** on the audio clock (§6.4).
  `AudioOutput` therefore has two shapes behind `MetronomeImpl`: `PcmAudioOutput` (Android, desktop, iOS) and the
  web's `ScheduledAudioOutput`, both driven by the same sequencer.
- **`beats` is emitted when a click is heard, not when it is rendered**: each output reports its playback position
  (`AudioTrack.getTimestamp`, `SourceDataLine.getLongFramePosition`, the player node's render time plus
  `outputLatency`, `AudioContext.currentTime` and `outputLatency`), and a small coroutine releases the queued ticks
  as that position passes them. The UI flash and the haptic tick are driven from this flow alone.
- Chunk size ~20 ms and at most ~60 ms queued, so a tempo change or a stop is felt at once.
- An output that cannot open (no audio device on a Linux box, a refused `AudioContext`) leaves the engine in
  `Unavailable`; the UI offers the visual beat, driven by a monotonic-clock fallback, and says why in one line.

### 2.3 Tap tempo

`TapTempo` in `:metronome:api`: taps as `TimeSource.Monotonic` marks; a gap over two seconds starts a new series;
the tempo is 60 s over the **median** of the last up to eight intervals (one late tap must not move it), available
from the second tap, clamped to the range. Pure and tested with a fake time source.

## 3. Data model

### 3.1 `:chordpro`

- `ChordProTempo.parse(text: String?): Int?` — the first number in a `{tempo}` value (`120`, `120 bpm`, `♩ = 96`,
  `97.5`), rounded; null where there is none or it is not positive. Clamping is the caller's.
- `ChordProTime.parse(text: String?): Pair<Int, Int>?` — `3/4`, `6/8`, also `C` (4/4) and `C|` (2/2); null for
  anything else or for values outside 1–16 over 1/2/4/8/16. `:chordpro` stays dependency-free, so it returns plain
  numbers and `:presentation` maps them to `TimeSignature`.
- Tests next to `ChordProParserTest`.

### 3.2 `:data:model`

- `Song` gains `tempo: Int?` and `time: String?`, read at scan time like `key` and `transpose`
  (`SongLocalSourceImpl`), so a page change knows the next song's tempo **before** its text is loaded — which is what
  "immediately" needs when a setlist is paged faster than the files are read.
- `Setlist.Entry` gains `tempo: Int? = null`: the BPM this setlist plays the song at, null for "the song's own".
- `UserPreferences` gains
  - `tempos: Map<String, Int>` — song file name to BPM, for songs opened from the library. Next to
    `transpositions`, and like it never exported or synced.
  - `metronomeSettings: MetronomeSettings = MetronomeSettings()`, a nested value like `PrintSettings`:

```kotlin
data class MetronomeSettings(
    // What applies everywhere a click is played
    val soundId: String = "click",
    val volume: Float = 1f,
    val subdivisionId: String = "none",
    val isMuted: Boolean = false,
    val isVisualBeatEnabled: Boolean = true,
    val isHapticBeatEnabled: Boolean = false,
    /** Accents the user drew for a signature, keyed "7/8": a song in 7/8 is clicked 2+2+3 if that was set once. */
    val beatLevels: Map<String, List<BeatLevel>> = emptyMap(),
    // The Metronome tab's own state
    val bpm: Int = 120,
    val timeSignature: String = "4/4",
)
```

  `:data:model` does not depend on `:metronome:api`, so the enums it stores are ids (`String`), mapped in
  `:presentation` where both meet — or the three small enums live in `:data:model` and `:metronome:api` depends on
  it. **Recommendation: ids**, keeping `:metronome:api` dependency-free like `:chordpro`.

### 3.3 `:data:source:local:implementation`

- `SetlistSongDocument.tempo: Int? = null`, mapped both ways in `SetlistMappers`. Check `SetlistDocumentFormat`'s
  encoder settings: a null tempo must be **left out** of the JSON rather than written as `"tempo": null`, so that a
  setlist nobody gave a tempo stays byte for byte what it was (no sync churn, the import's "same file" comparison
  unaffected). Out-of-range or non-numeric values read as null, the way a bad `date` does.
- An older installed version carries the unknown `tempo` member through its own saves (`unknownFields`), so a
  mixed-version set of devices does not lose it. Add a test that says so.
- `UserPreferencesDocument`: `tempos` and a `MetronomeSettingsDocument`, every field defaulted, unknown ids falling
  back to the default (as `PrintSettingsDocument` does).

### 3.4 `:domain`

- `followSongReferences` moves or drops `tempos` with `transpositions` and `foldedSections`; the setlist entry's
  tempo already follows, being part of the entry that is copied.
- `DeleteLibraryUseCaseImpl` clears `tempos`.
- `ExportSetlistUseCase`: nothing — the stored document goes in as it is.
- No new use cases: transposition changes go through `UpdateUserPreferencesUseCase` and `UpdateSetlistUseCase`, and
  tempo changes take the same two.

### 3.5 Resolution, in one place

`:presentation`'s `ui/metronome/SongTempo.kt` (pure, tested):

```kotlin
fun effectiveTempo(song: Song, setlistFileName: String?, tempos: Tempos): EffectiveTempo
// EffectiveTempo(bpm, source = SETLIST | LIBRARY_OVERRIDE | FILE | DEFAULT, songBpm = the file's or 120)
```

`Tempos` is the twin of `CampfireViewModel.Transpositions`: one `StateFlow` combining `userPreferences.tempos` with
every setlist's entries that carry one. `isDefault` for the stepper is `source == FILE || source == DEFAULT`.

## 4. View model

`CampfireViewModel` gains, beside the transposition block:

- `tempos: StateFlow<Tempos>`.
- `stepTempo(songFileName, setlistFileName, delta)`, `setTempo(…, bpm)` (tap tempo), `resetTempo(…)` — one private
  `changeTempo` modelled on `changeTransposition`: applied to the stored value at the time of the write inside
  `launchLibraryChange`, a value equal to the song's own removes the override rather than storing it. The setlist
  branch is a setlist write, so it schedules a sync run like a transposition does; the stepper's key repeat (hold
  to run) must therefore write once when it settles, not once per step — keep the live value in the view model and
  debounce the write (~500 ms), flushing on stop of interaction and in `onCleared`.
- `metronomeSettings` updates: `updateMetronomeSettings { … }`, debounced the way `printSettings` is saved "once the
  options have settled", since a BPM dial moves on every frame.
- **The context**: `metronomeContext: MetronomeContext` — `Standalone` or `Song(fileName, setlistFileName)`.
  - `onSongDetailsPageChanged(destination, songFileName)` (from the pager's **target** page, see §5.2) sets
    `Song(…)`; if `playback` is `Playing`, calls `metronome.update(patternOf(song), restartBar = true)`.
  - A back stack whose top is no longer a `SongDetails` returns the context to `Standalone` and stops a click that
    was playing for a song (default 6). Observed where the back stack changes, not from a screen's disposal — a
    popped screen is composed for as long as its exit transition runs.
  - `toggleMetronome()` builds the pattern of the current context and starts, or stops.
  - A change of an effective tempo, of the settings or of a song's file (`{tempo}` saved in the editor, a sync run)
    while playing is pushed with `update(…, restartBar = false)` by one `combine` collector.
- `patternOf(context)`: BPM from §3.5 or the settings; signature from `Song.time` (4/4 where absent) or the
  settings; beat levels from `metronomeSettings.beatLevels[signature]` or the default for it (accent on one; on
  one and four in 6/8, and the like for 9/8, 12/8); everything else from the settings.
- `handleKeyEvent` (desktop) and the web's key listener: **Space** toggles on the Metronome tab while nothing is
  open over it and no field is focused; **M** toggles on the song details screen. Pedals send only arrows, so this
  is for keyboards.
- Saved state: the context is derived from the back stack, so nothing new is saved. Playback is not restored after
  process death.

## 5. UI

Everything is animated per the conventions (the play/stop mark morphs, values cross-fade, bars fade content under
them); every string goes into both `strings.xml` files; `code-style` skill before any edit.

### 5.1 The Metronome tab

`CampfireDestination.Metronome : TopLevel` (`contentKey = "metronome"`), in `TopLevel.entries` between `Setlists`
and `Settings`; icon `ic_metronome`, label `metronome`; a `TopLevelScreenSurface` case in `CampfireApp`; the tab
transition direction logic (`fromContentKey` order) picks it up from the list. Four items fit the navigation bar
and both rails.

`ui/screens/metronome/MetronomeScreen.kt`, plus one file per larger composable (TO_DO: "each top-level Composable
in a separate file"):

- **Tempo**: the BPM large, with −/+ (hold to run), a slider or dial across the range, the Italian tempo marking
  under it (Largo … Presto; not localized, it is notation), and a **Tap** button.
- **Play / stop**: the screen's floating action button, a morphing mark.
- **Beat row**: one block per beat of the bar, lit as each is heard (from `metronome.beats`); a tap cycles a beat
  through accent → normal → muted, which is the accent editor. Stored per signature in `beatLevels`.
- **Time signature**: beats 1–16 over 2/4/8/16, two steppers in a popup, with the common ones as chips (2/4, 3/4,
  4/4, 6/8, 7/8, 12/8).
- **Subdivision**: none, eighths, triplets, sixteenths — a segmented row.
- **Sound** (a row of chips, a tap previews it) and **volume** (slider).
- **Switches**: Flash on the beat; Vibrate on the beat (Android and iOS only, `expect val supportsBeatHaptics`);
  Mute (visual only).
- Narrow windows: one scrolling column, edge-to-edge with the bottom inset + 16 dp, `fadingTopEdge`. Wide windows:
  tempo and beat row on one side, options on the other, following `SettingsLayout`.
- While a click is playing **for a song**, the tab shows that song's title and tempo read-only at the top with the
  shared settings still editable; its own BPM comes back when the context does. (Reachable on wide layouts with the
  rail, and on the web by address.)
- Keeps the screen on while playing (`keepScreenOn`, as the song details screen does).
- Web: address `/metronome`, one history entry, in `BrowserRoutes` both ways; `404.html` in `campfire-website`
  needs nothing, it forwards every path under `app/`.

### 5.2 The mini metronome on the song details screen

Follows the pattern the transposition already set — a control in the bar where there is room, the overflow menu
where not, never a sheet:

- **`MetronomeButton`** in the app bar: an icon button whose mark morphs between a metronome and stop, pulsing on
  each heard beat (a scale and a tint to `LocalSecondAccentColor`, off when Flash is off), with the effective BPM as
  its content description and tooltip. One tap starts, one stops.
  - `appBarButtons(…)` gains `isMetronomeShown`, first in the order the bar gives room out — a control reached
    while playing outranks one "set once for a song". Where it does not fit it is the first entry of the overflow
    menu: "Start metronome · 96 BPM" / "Stop metronome".
  - In performance mode it stays, next to the text size stepper (and in that mode's menu where the bar is too narrow).
- **Tempo row** in the overflow menu's footer, under Transposition: `MenuStepperRow(label = Tempo)` holding a
  `TempoControls` stepper (−1 / +1, hold to run, value "96") and a small **Tap** button.
  - **Reset is the transposition's, gesture for gesture**: `TempoControls` is the same shared `Stepper`
    (`SongDisplayControls.kt`), so the value is highlighted while the tempo is overridden (`isDefault` from §3.5) and
    **one tap on the value** puts it back to the file's tempo (120 where the file has none), with its own
    `song_details_tempo_reset` click label. No dialog, no second control.
  - Reset **removes** the override (`tempos - songFileName`, `entry.copy(tempo = null)`) rather than storing the
    file's number, as `changeTransposition` drops a zero: a `{tempo}` edited later shows through. Stepping or
    tapping back onto the file's value removes it the same way.
  - Reset is written at once and cancels a pending debounced write (§4), so a held step followed by a reset cannot
    land the old value after it. The menu stays open while it is used, as for the other steppers. Hidden in
  performance mode. Shown in lyrics-only mode too: the click is for singers as well.
- **The page's own tempo line** (`SongMetadata.kt`, `song_details_tempo`) shows the **effective** BPM, the way the
  key line shows the transposed key, so the page never contradicts the click. The value as written in the file
  shows where there is no override.
- **Paging**: the context follows `pagerState.targetPage` (the page being headed for — `settledPage` would wait out
  the fling), deduplicated, so the click has the new tempo while the page is still sliding in. `Song.tempo` /
  `Song.time` make that independent of `songTexts`.
- **Setlists screen**: nothing in v1. (A setlist row could show "96 BPM" where the entry overrides it — §12.)
- **PDF export**: `PrintLayout` prints the tempo line from the effective tempo for a setlist entry, as it prints the
  entry's key; a library export prints the file's own value (the preference override is one reader's, like folded
  sections). `PrintLayoutTest` gains the case.

### 5.3 Localization and platform chrome

- A `MetronomeNotifier` contract in `ui/platform/`, the twin of `SyncNotifier`: `onMetronomeNotificationChanged(
  MetronomeNotification?)` with the title ("Metronome" or the song's title), the body ("96 BPM · 4/4"), the stop
  label and the channel name, **resolved in the composition** so the notification and the lock screen are in the
  language chosen in the app. A `rememberMetronomeNotifications(viewModel)` effect in `CampfireApp` next to the
  sync one; `LocalMetronomeNotifier` no-op by default.
- Strings: tab label, every control and its content description, sound names, the notification's words, the
  unavailable line, the What's new message — English and Hungarian.

## 6. Platforms: playback, focus and lifecycle

### 6.1 Android

- **Output**: `AudioTrack` in `MODE_STREAM`, `USAGE_MEDIA` / `CONTENT_TYPE_SONIFICATION`,
  `PERFORMANCE_MODE_LOW_LATENCY`, the device's native sample rate (`AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`) so
  the fast mixer path is taken; a writer thread at `THREAD_PRIORITY_URGENT_AUDIO`. No NDK, no Oboe.
- **`CampfireMetronomeService`** in `:app:android`, `foregroundServiceType="mediaPlayback"` with
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Like `CampfireSyncService` it plays nothing itself — the engine is the Koin
  singleton — it keeps the process alive and owns the system-facing part:
  - a framework `MediaSession` (minSdk 28 needs no compat library) with `PlaybackState` and metadata, answering
    play, pause, stop and the headset / Bluetooth media button;
  - a `Notification.MediaStyle` notification on its own low-importance channel, with Stop. Media session
    notifications are exempt from the Android 13 notification permission, so nothing new is asked;
  - started with `startForegroundService` from the activity when playback starts (always a tap in the foreground,
    so the background-start restrictions do not apply) and stopped with playback;
  - `stopWithTask="true"`, the opposite of the sync service: swiping Campfire away is a request for silence.
  - Recommendation over Media3's `MediaSessionService` + `SimpleBasePlayer`: the framework classes cover a
    play/stop session with no new dependency. Revisit if Android Auto or Wear control is ever wanted.
- **Audio focus**: `AudioFocusRequest(AUDIOFOCUS_GAIN)` on start. Loss or transient loss (a call, another player)
  **stops**; a metronome that comes back by itself after a phone call is a surprise. "Can duck" is left to the system.
- **`ACTION_AUDIO_BECOMING_NOISY`** (headphones pulled) stops.
- **Haptics**: `Vibrator` with `VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK / EFFECT_CLICK)` for accent /
  beat, from the `beats` flow; `VIBRATE` permission (normal, no prompt). Hidden where `hasVibrator()` is false.
- **Activity recreation** (language, dark mode) does not touch the engine; the UI re-collects `playback`.
- The in-app update gate: Restart for a flexible update should wait for a playing metronome, as it waits for a sync
  run; a required update does not.
- `app/android/CLAUDE.md`, the manifest comment ("the one thing Campfire posts a notification for") and
  `presentation/CLAUDE.md`'s notification paragraph are corrected.
- **Play Console**: declare the `mediaPlayback` foreground service type (a form and a short video on first use).

### 6.2 iOS

- **Output**: `AVAudioEngine` with an `AVAudioPlayerNode`, fed by `scheduleBuffer` with the ~20 ms chunks the
  mixer renders on a Kotlin thread, two or three queued ahead and refilled from the completion callback.
  **Not** an `AVAudioSourceNode` render block: that would run Kotlin/Native on the real-time audio thread, where a
  GC pause is an audible dropout.
- **Session**: `AVAudioSessionCategoryPlayback` (so the silent switch does not mute a metronome), activated on
  start and deactivated with `notifyOthersOnDeactivation` on stop.
- **Background**: `UIBackgroundModes: audio` in `app/ios/iosApp/iosApp/Info.plist`. The session is active only
  while a click plays, which is what App Review checks for.
- **Lock screen / Control Center**: `MPNowPlayingInfoCenter` (title, "96 BPM · 4/4", rate) and
  `MPRemoteCommandCenter` play, pause, toggle and stop — in an `IosMetronomeNotifier` in `:app:ios`, fed by the
  `MetronomeNotifier` contract so the words follow the in-app language.
- **Interruptions**: `AVAudioSessionInterruptionNotification` began → stop (no resume, as on Android);
  `AVAudioSessionRouteChangeNotification` with `OldDeviceUnavailable` → stop;
  `AVAudioSessionMediaServicesWereResetNotification` → rebuild the engine.
- **Haptics**: `UIImpactFeedbackGenerator` (heavy / light), prepared ahead; foreground only by the system's rules.
- The sync background task and this are independent; nothing in `IosSyncNotifier` changes.

### 6.3 Desktop

- **Output**: `javax.sound.sampled.SourceDataLine` (part of `java.desktop`, already in the runtime image), 16-bit
  mono at 48 kHz, a small line buffer (~40 ms), a daemon writer thread at max priority.
  `LineUnavailableException` → `Unavailable`.
- Lifecycle: nothing to keep alive. Closing the window stops the click before the "let a sync run finish" wait.
- macOS sandbox: audio **output** needs no entitlement. ProGuard: `javax.sound` service providers are looked up by
  name — verify the release image actually clicks; the Linux, Windows and Mac start checks in CI will not catch a
  silent metronome, so it is a line of the release check.
- Media keys and a macOS Now Playing entry are out of scope (no JDK API; would need native code).

### 6.4 Web

- **Output**: Web Audio, the standard "two clocks" scheduler: an `AudioContext`, each voice rendered once into an
  `AudioBuffer` (the Kotlin synthesizer's samples handed over in one crossing), and every tick within the next
  ~100 ms scheduled with `AudioBufferSourceNode.start(time)` on the context's clock.
- **The scheduler's wake-up comes from a Web Worker timer** (an inline blob worker posting a message every 25 ms):
  a hidden tab's `setTimeout` is throttled to once a second, a worker's is not, so the click survives a tab switch.
  Check `index.html`'s policy allows a `blob:` worker; otherwise ship it as a file, which `finishWebDistribution`
  then lists in `build.json` like the rest.
- **Autoplay policy**: the context must be created or resumed inside a user gesture, and Compose may handle a click
  after the DOM event has returned. A capture-phase `pointerdown` / `keydown` listener on the window resumes a
  suspended context on every gesture — registered through a `js(...)` block, as the other web listeners are.
- **Media Session**: `navigator.mediaSession` metadata and play / pause / stop handlers where the API exists —
  best effort; browsers show it reliably only for media elements.
- iOS Safari mutes Web Audio with the silent switch; the support page says so rather than the app working around it.
- No haptics.

## 7. Work order

Each phase builds and passes the desktop tests by itself; 1–3 are invisible to the user.

1. **Pure core** — the two modules, models, `MetronomeSequencer`, `ClickSynthesizer`, `ClickMixer`, `TapTempo`,
   `ChordProTempo` / `ChordProTime`, with tests. `tests.yml` and the root `CLAUDE.md`'s test command gain
   `:metronome:implementation:desktopTest` (and `:metronome:api:desktopTest`).
2. **Engine and outputs** — `MetronomeImpl`, the four `AudioOutput`s, heard-time `beats`, `Unavailable`; Koin
   module, `:app:di`. Verified with a throwaway button on desktop first, then each platform.
3. **Data** — `Song.tempo` / `time`, `Setlist.Entry.tempo`, `UserPreferences.tempos` and `metronomeSettings`,
   documents, mappers, `followSongReferences`, `DeleteLibrary`; mapper and format tests.
4. **View model** — `Tempos`, `changeTempo`, context, `patternOf`, the debounced saves; `SongTempo` tests.
5. **Metronome tab** — destination, navigation chrome, screen, web route, key handling, strings.
6. **Song details** — `MetronomeButton`, `appBarButtons`, the tempo row, the effective tempo line, paging retarget,
   leaving stops, performance mode; PDF tempo line.
7. **Background playback** — `MetronomeNotifier`; Android service, session, focus, noisy; iOS session, background
   mode, Now Playing, interruptions; web Media Session.
8. **Haptics and polish** — vibration, flash, animations, accessibility (every control labelled; the beat row
   announces "Beat 3, accent"; the flash respects reduced motion where the platform reports it).
9. **Documentation and release** — §10.

## 8. Tests (pure logic only, as everywhere)

- `MetronomeSequencerTest`: tick frames for plain, compound and odd signatures; subdivisions; muted beats; a tempo
  change mid-bar lands on the next click; `restartBar`; no drift over ten thousand bars (integer frame error stays
  below one frame — accumulate in fractions, never in rounded frames).
- `ClickSynthesizerTest`: lengths, peak below full scale, silence at the tail, checksums per sound.
- `ClickMixerTest`: a voice crossing a chunk boundary is identical to the unchunked render.
- `TapTempoTest`: two taps, a steady run, one outlier, the reset gap, the clamps.
- `ChordProTempoTest` / `ChordProTimeTest`.
- `SongTempoTest`: the four sources and their precedence; an override equal to the song's own is no override.
- `SetlistDocumentFormat`: `tempo` round-trips, is absent when null, bad values read as null, an unknown future
  member still survives beside it.
- `UserPreferences` mappers: defaults, unknown ids.
- `BrowserRoutes` has no tests today; the `metronome` path is checked by hand.

## 9. Manual checks (to `release-check.md`)

- Each platform clicks, in time against a reference metronome over five minutes at 120.
- Android: lock the screen, notification Stop, headset button, a phone call stops it, headphones pulled stops it,
  swipe the app away stops it, rotation and language change do not.
- iOS: lock screen controls, silent switch ignored, Siri / a call interrupts, AirPods removed stops it.
- Web: tab in the background keeps time; first tap after load sounds (autoplay); offline launch still clicks.
- Desktop release images (ProGuard) on all three systems make a sound.
- Setlist: page quickly through songs with different tempos while playing; override in one setlist does not show
  in the library or in another setlist; export and import a setlist, the override arrives; sync it to a second device.
- Rename and delete a song with a library override; delete the library.
- Performance mode: play/stop there, no tempo stepper.

## 10. Documentation, stores and release

- `CLAUDE.md` (root): the module tree, a **Metronome** section (timing by sample count, context rules, where a
  tempo lives, per-platform playback), the setlist conventions paragraph (tempo travels like the transposition),
  the test command, "six module objects". New `metronome/api/CLAUDE.md` and `metronome/implementation/CLAUDE.md`;
  updates to `presentation`, `data/model`, `data/source/local/implementation`, `domain/implementation`,
  `app/android`, `app/ios`, `app/web`, `app/di`.
- The root file's opening claim about the network is untouched: nothing here makes a request.
- **Privacy policy / store forms**: no data collected; Android gains `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and
  `VIBRATE`, iOS the audio background mode — both listings' review notes should say "metronome".
- `app/baselineprofile`: add the Metronome tab to the journey and regenerate.
- Website and README: feature list, a screenshot of the tab.
- What's new message in both languages (prepare-release skill); remove "Metronome" from `documentation/TO_DO.md`,
  and "Haptic effects" stays (this adds only the beat).

## 11. Risks

| Risk | Mitigation |
| --- | --- |
| Kotlin/Native GC pauses on the iOS feeder thread cause dropouts | Buffers are queued 40–60 ms ahead; the feeder allocates nothing per chunk (reused buffers). Measure on the oldest supported phone before phase 7. |
| Android low-latency path not taken (wrong sample rate / buffer size) | Use the device's native rate and `getMinBufferSize`; log the track's `performanceMode` in debug builds. |
| Web autoplay: first tap silent | The gesture-resume listener; the button shows `Unavailable` until the context runs. |
| Bluetooth output latency makes flash and click disagree | `beats` is timed from the reported playback position, which includes the route's latency where the platform reports it; otherwise accept it. |
| Setlist writes on every tempo tap churn sync | Debounced write (§4); the sync scheduler already folds bursts into one run. |
| App Review asks why `audio` background mode | The session is active only while playing; say "metronome continues with the screen locked" in the review notes. |
| A fourth tab crowds the bar on small phones | Material's bar holds three to five; check the Hungarian label ("Metronóm") at the largest font scale. |

## 12. Deliberately left for later

- Count-in (N bars, then stop) and a tempo trainer — declined for v1.
- "Play alongside other audio" (no focus / `mixWithOthers`), at the cost of the lock screen controls.
- Mid-song `{tempo}` / `{time}` changes.
- "Save as the song's tempo": writing an override into the file's `{tempo}` from the details screen.
- Showing an entry's overridden tempo on the Setlists screen rows.
- Media3 session (Android Auto / Wear), macOS media keys, next / previous on the lock screen paging the setlist.
- Dotted-beat reading of compound meters, swing, polyrhythms, custom sound import.
