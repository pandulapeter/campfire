# Twenty-fourth review: the tuner, process death, accessibility

**Reviewed commit:** `b5c8ed3b5` on `master` ("Initial tuner implementation."), clean tree.

**Angle:** the tuner had never been reviewed, so it got three area reviewers: its DSP core and engine, its platform
audio, permissions and packaging, and its screens and glue. Two angles no earlier sweep took as its own looked at the
whole app with fresh eyes: process death and state restoration (Android above all, plus iOS, desktop and web), and
accessibility (screen readers, large font scale, keyboard only) for the UI added since the last pass. Standard budget:
five reviewers, four plan writers who verified every finding against HEAD (lane T with a probe test, since deleted),
and three challengers. No live run.

## Headlines

1. **20 (high, data loss, Android):** a long unsaved editor text (over 50,000 characters) that Android kills in the
   background is on disk in `editor-draft.json`. On return the app deletes that file and tells the user the draft is
   lost.
2. **52 (accessibility):** the tuner reading is a live region holding the exact cents. A screen reader re-announces it
   nearly every 33 ms poll, and the speech goes back into the microphone, so the tuner can't be used with
   TalkBack or VoiceOver.
3. **50:** the tuner sheet over song details can open while a click plays. On iOS the tuner takes over the shared
   audio session under the click and later deactivates it. On Android a string tone's audio focus stops the click as
   "interrupted". The microphone also hears the clicks.
4. **02, 03, 05 (tuner correctness):** each was proved with a probe test. After a reference tone stops, the
   tracker's reading of the speaker is shown as "in tune" for about half a second. Changing strings swings the needle
   by hundreds of cents for about 66 ms. A stalled input keeps its last reading on screen for good.
5. **21:** after process death, or from a web deep link, a song details screen can close itself, or open a setlist on
   the wrong song, because the library is indexed after `isLoading` turns false.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Test the tuner engine, the sample ring and the FFT (tests only) | medium | T |
| 02 | Stop the tracker learning from the speaker while a tone sounds, and blank readings briefly after | medium | T |
| 03 | Keep the previous reading while a new target candidate is pending | medium | T |
| 04 | Re-arm the one-time reopen once the reopened input has run for two seconds | low | T |
| 05 | Blank the reading when the input stops delivering new frames | medium | T |
| 06 | Catch a failing input start, and never leave the web's microphone tracks live | medium | T |
| 07 | End the iOS tone on an engine configuration change or a media services reset | medium | T |
| 08 | Close the desktop input's reopen race that can orphan a line holding the microphone | medium | T |
| 09 | Report an Android input silenced from its start as busy, not silent | low | T |
| 10 | Localize the iOS microphone prompt in Hungarian | low | T |
| 11 | Add low-G ukulele and five-string bass presets  | low | P (last) |
| 20 | Hand the draft on disk to an editor Android restored without its long text | **high** | L |
| 21 | Don't let a restored or deep-linked song details screen close itself or open on the wrong page | medium | L |
| 22 | Reload a back/forward-cached web page when another tab launched the app meanwhile | low | L |
| 23 | Keep the click when Android destroys the activity without finishing it | low | L |
| 24 | Reopen the form sheet that was open when Android killed the process  | low | L |
| 30 | Make every Stepper button carry the value it steps | medium | A |
| 31 | Mark section titles and sheet titles as headings | low | A |
| 32 | Let SelectableChip grow with a large text size | medium | A |
| 33 | Let the "Defined in this song" line grow past the stepper's height | low | A |
| 34 | Let a Chord shapes cell widen to fit its stepper, and read "Shape 1 of 5" | medium | A |
| 35 | Stop the Chord shapes sheet reading each name twice | low | A |
| 36 | Read the tempo slider as BPM rather than percent | medium | A |
| 37 | Read a beat block as its number with its level as state | medium | A |
| 38 | Outline every resting beat block so it meets 3:1 | low | A |
| 39 | Keep navigation labels on one line at large text | low | A |
| 40 | Keep keyboard focus inside the export screen | medium | A |
| 41 | Announce a settings-style notice that appears on its own | low | A |
| 42 | Group single-choice chip rows with selectableGroup() | low | A |
| 43 | Stack a setting's label above its stepper when a word would break | medium | A |
| 50 | Stop the metronome when the tuner sheet opens  | medium | P |
| 51 | Close the tuner sheet when the screen under it changes | medium | P |
| 52 | Announce only coarse, settled tuner readings | medium | P |
| 53 | Describe the tuner display when nothing is heard or a tone plays | low | P |
| 54 | Make the cents sentences plurals, and read 0 cents as the note alone | low | P |
| 55 | Speak sharps as sharps in the tuner's descriptions | low | P |
| 56 | Announce the tuner's notices and input issues | low | P |
| 57 | Stop the Tuner tab listening under the required update screen | low | P |
| 58 | Read the per-reading tuner state below the screen root | low | P |
| 59 | Keep the web's microphone status current after the browser prompt | low | P |
| 60 | Reword the refusal notice, which promises strings chromatic mode doesn't show | low | P |
| 61 | Remove the unused `tuner_in_tune` string | low | P |
| 62 | Give the compact tuner note label a fixed width | low | P |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|------|------|---------------------------|-------------|
| T | tuner modules, platform audio, iOS packaging | 01, 03, 05, 02, 04, 06, 07, 08, 09, 10 | `tuner/**` (except what 11 touches later), `app/ios/iosApp/**` (Info.plist strings, `project.pbxproj`), `app/ios/CLAUDE.md`, one line of `presentation/CLAUDE.md` (10), the `:tuner:*` tests clause of the root `CLAUDE.md` (01) |
| L | process death and restoration | 20, 21, 22, 23, 24 | `ui/screens/songEditor/**`, `ui/screens/songDetails/SongDetailsScreen.kt` + new `SongDetailsSongs.kt`, `ui/state/LibraryState.kt`, `ui/state/AppExitController.kt`, `ui/metronome/MetronomeController.kt`, `ui/navigation/SavedStateStore.kt`, new `ui/dialogs/SavedDialog.kt`, `presentation/src/androidMain/.../CampfireAndroidApp.kt`, `app/web/src/wasmJsMain/resources/index.html`, `app/web/tests/offline.test.cjs`, `app/web/CLAUDE.md`, and `CampfireViewModel.kt` (its own hunks) |
| A | shared accessibility and large text | 30 … 43 in number order | `ui/components/**` (Stepper, SelectableChip, SettingsSectionTitle, SectionHeader, SettingsSubsection, new LabeledControlRow), `ui/dialogs/ChordShapesSheet.kt`, `ui/dialogs/CampfireBottomSheet.kt`, `ui/metronome/BeatRow.kt`, `TempoStepper.kt`, `TimeSignaturePicker.kt`, `ui/screens/metronome/**`, `ui/screens/export/ExportScreen.kt`, `PrintOptions.kt`, `ui/screens/settings/SettingsMessage.kt`, `SyncSettings.kt`, `ui/screens/songDetails/SectionTitle.kt`, `ui/screens/importReport/SectionTitle.kt`, `NavigationChrome.kt`, `ui/tuner/InstrumentChoice.kt`, `ui/tuner/ReferencePitchSetting.kt` |
| P | tuner presentation | 50, 51, 52, 53, 54, 55, 62, 58, 56, 57, 59, 60, 61, then 11 | `ui/tuner/**` (except the two lane A files, whose call sites only it changes), `ui/screens/tuner/TunerScreen.kt`, `ui/dialogs/TunerSheet.kt`, `ui/tuner/CLAUDE.md`, `MicrophonePermission.wasmJs.kt`, `CampfireViewModel.kt` (its own hunks), the tuner block of both `strings.xml`; and for 11, `tuner/api` presets, `PitchDetector.kt` and `InstrumentChoice.kt` |

(`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.)

**Merge order: T, L, A, then P.**
- T, L and A start from the same commit, at most two at a time.
- P is cut from the main checkout's `HEAD` after the other three have merged:
  - its 56 uses the `SettingsMessage` parameter added by A's 41;
  - its 58 changes the call sites of A's `InstrumentChoice` and `ReferencePitchSetting`;
  - its 11 edits T's tuner files.
- P touches the most shared UI files and the strings, so it goes last.

## Shared files

- **`strings.xml` (both languages):**
  - Lane A adds keys next to their screens' existing keys (34, 37) and removes `metronome_beat_description` (37).
  - Lane P adds and changes keys only in the tuner block (52–55, 60, 61, 11).
  - Merge both sides key by key.
- **`CampfireViewModel.kt`:** L (20, 21, 23, 24) and P (50, 51) edit separate listeners and members. Keep both sides'
  hunks.
- **`ui/CLAUDE.md`:**
  - A's 30 creates a `## Accessibility` section that 31, 41 and 42 extend, in that order.
  - L's 21 adds a sentence elsewhere.
  - Word-level three-way merge.
- **`tuner/implementation/CLAUDE.md`:** edited by every lane T plan from 02 on, and later by 11.
- **`ui/tuner/CLAUDE.md`:** edited by lane P only.

## Decisions — taken by the user, 2026-10-09

1. **50 — stop the click when the tuner sheet opens over a song:** yes, as the export screen does. Plan 50 runs.
2. **24 — reopen an open form sheet after process death:** yes, against the recommendation to decline. Plan 24 runs
   at the end of lane L, after 20 and 21. It carries a device risk: it is unknown whether a ModalBottomSheet's
   saveable keys reattach. If they don't, the plan reduces to reopening the sheet empty, as it says.
3. **11 — low-G ukulele and five-string bass presets:** yes, both. If the detector change for B0 regresses its noise
   and chord tests, only low-G ships, as the plan says.

## Challenge

The challenge ran: three fresh read-only agents, one each for lane T, lane L and lanes A+P. Result: 30 plans sound,
13 amended, none dropped. The amendments:

- **Lane T:**
  - **02, 04:** rewritten to build on 05's new loop. 02 keeps polling the input under a tone, so the web still notices
    an ended track and 05's position keeps moving. 04 re-arms only on fresh windows, and has a test for a reopened
    input that stalled.
  - **05:** the iOS tap delivers about 100 ms (Apple asks 100–400 ms), so the 500 ms hold must stay above 400 ms plus
    one poll. The fake input gets `advanceEvery`.
- **Lane L:**
  - **21:** update `SongListStateTest`'s `IndexedSongs(...)`, with no default on the field. Spell out the
    `runningFold` latch.
  - **23:** skip an ON_DESTROY that is a configuration change (locale and dark mode recreate the activity).
  - **24:** load the song text before reopening a file-target sheet. Bound the editor-draft wait, giving up when the
    top of the stack changes.
- **Lane A:**
  - **34:** size the chord cell once, from the widest position string, so neighbours don't slide while stepping.
  - **40:** the focus trap only holds while the export screen `isOpen`. Otherwise it refuses the song's own focus
    request during the slide-away, and the pedal and the arrows go dead.
  - **41:** wrap the `AnimatedSettingsRow` from outside. Never `clearAndSetSemantics` over a row with buttons.
  - **43:** a measured-but-not-drawn `decidingLabel` slot holds the widest tempo marking, and a row stays stacked once
    it stacked at that width. The layout no longer flips while a slider is dragged.
- **Lane P:**
  - **52:** the live node goes first, so exploring reads the exact reading. The hold also clears it when the reading
    goes away.
  - **53:** bind `state.tone` to a local, since it can't smart-cast across modules.
  - **56:** match 41's form, so the notice's buttons aren't erased.
  - **62:** the label width scales with the font scale.

## Checked and found solid

- **Tuner core:**
  - Pitch math: sign, A4 reference, rounding at ±50, no NaN.
  - Every preset's MIDI notes.
  - The FFT and NSDF normalization.
  - Window sizes at 8–96 kHz.
  - Octave choice with a weak fundamental.
  - The `ToneSynthesizer` seam and its accuracy.
  - The tracker's hysteresis, onsets, median and smoothing.
  - `SampleRing`'s single-producer, single-consumer publish order.
  - The engine's confinement and session ids.
  - CPU per poll: about 1 ms.
- **Tuner platforms:**
  - Resources released on every platform.
  - The recording indicator follows the page.
  - Sample rates and formats.
  - Permission flows on all four platforms, and nothing asks on its own.
  - Packaging: `RECORD_AUDIO` with the microphone not required, `NSMicrophoneUsageDescription`, the Mac App Store
    `audio-input` entitlement, the MSIX `microphone` capability in schema order.
  - `/tuner` in both web route lists.
  - Android tone focus.
  - The web context is suspended when idle.
- **Tuner screens:**
  - Tab and sheet lifecycle against Navigation 3 1.1.2's sources.
  - The microphone is asked for only from the button.
  - Tones stop on every stop path.
  - Tuner settings are not synced (intended); old documents decode to the defaults.
  - The Features switch cuts the tab, `/tuner`, the menu entry and the gesture listener.
  - The sheet fits the 360×640dp budget.
  - The animation rule is followed.
- **Process death:**
  - Android intents are not re-handled after recreation.
  - Picker and export results arrive after death.
  - Configuration changes, the back stack's persistence and cap.
  - Form sheets survive configuration changes (`rememberSaveable`).
  - Short-text editor restore.
  - Draft persistence on iOS, desktop and web.
  - The iOS single scene and its `onOpenURL`.
  - Restoring onto the Tuner tab reopens the microphone only where already granted.
- **Accessibility:**
  - Chord-diagram rows in song details do have per-slot descriptions; the code is right, and the TalkBack check stays
    manual.
  - `StepProgressIndicator`.
  - Metronome play/stop labels.
  - Stepper icon labels.
  - 48dp touch areas.
  - The tuner chips' roles.
  - The export screen's labels, roles and Escape.
  - The setlist date and countdown semantics.
  - Bottom sheets trap focus.
  - No hard-coded English.

## Dropped after verification

None. Every finding held against `b5c8ed3b5`; the plan writers amended several fixes (recorded in each plan).

Not raised, as notes:
- `PitchDetector.pickPeak` allocates `IntArray(64)` per call (11 moves it).
- The lowest note of each preset range reads 3–6 cents above the range's start (harmless).
- The desktop half of the Hungarian microphone prompt (10) isn't feasible: the Compose plugin can't add files to
  `Contents/Resources`, and copying them in afterwards breaks the store signature.
- Preferences are not flushed on pause: that only matters if the process dies within 500 ms of leaving.

## Manual checks owed

- **Tuner, live, on every platform:**
  - A real instrument through every preset.
  - Tone on, then off, shows no false "in tune" (02).
  - String changes show no needle swing (03).
  - Headphones plugged in, then pulled minutes later, in one session (04).
  - A Safari interruption blanks the reading (05).
  - A Firefox mic at a mismatched rate (06).
- **iOS:**
  - A route change during a tone (07).
  - Whether the tone's engine posts a configuration change on the input engine (decides how much 04 matters).
  - The Hungarian microphone prompt (10).
  - The click and the sheet together (50).
- **Android:**
  - A start under a call on speaker (09).
  - `am kill` with a long unsaved text (20).
  - A 500-song library restored on a song and on a setlist page (21).
  - "Don't keep activities" with the click playing (23).
  - Sheet restore with typed text after `am kill` (24).
  - The update-required cover on the Tuner tab (57).
- **Desktop:** stop and start the tuner during the 3 s reopen (08).
- **Web:**
  - A bfcache return after another tab edited (22).
  - The microphone status after the browser prompt (59).
- **Screen readers (TalkBack, VoiceOver):**
  - The tuner with a guitar (52–56).
  - Steppers (30, 34).
  - Headings (31).
  - The tempo slider (36).
  - Beat blocks (37).
  - The chord shapes sheet (35).
- **Font scale 1.3 and 2.0 at 360×640dp in Hungarian:**
  - Chips (32).
  - The chord shapes sheet (33, 34).
  - The navigation bar (39).
  - Label rows (43).
  - The compact tuner (62).
- **Tab and Shift+Tab** in the export screen on desktop and web (40).
- **Contrast of the beat blocks** in both themes (38).
- **Regenerate the Baseline Profile** before the next release.
