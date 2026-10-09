# Read the per-reading tuner state below the screen root, so 30 readings a second recompose only the display

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all (low-end Android first)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/TunerSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerOptions.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerMeter.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

While a note is heard the engine publishes a new `TunerState` about every 33 ms (`TunerEngine.kt:176`), the cents and
frequency differing each time. Both tuner screens read it at their root (at b5c8ed3b5):

`screens/tuner/TunerScreen.kt:73-79`, `:92`, `:108-110`, `:128-135`:

```kotlin
val state by viewModel.tunerState.collectAsStateWithLifecycle()
…
val notice = tunerNoticeOf(status = permission.status, listening = state.listening, hasRequested = hasRequested)
…
modifier = modifier.fillMaxSize().then(if (state.listening is TunerListening.Hearing) Modifier.keepScreenOn() else Modifier),
…
TunerDisplay(state = state, …, referencePitch = settings.toConfig().referencePitch, …)
…
TunerOptions(state = state, settings = settings, …)
```

`dialogs/TunerSheet.kt:54-60`, `:85-106` — the same. So every reading recomposes the whole page: the root, the
`SettingsPage` section lambda (it captures `state`), `TunerOptions` (which re-runs `settings.toConfig()` — an
`InstrumentTuning.fromId` lookup — at `TunerOptions.kt:54`), `InstrumentChoice`, `TunerStrings` and
`ReferencePitchSetting`, none of which change with the cents. In addition `TunerMeter.kt:89-94` allocates a new `Path`
on every draw while the in-tune check shows, and the marker's spring redraws every frame:

```kotlin
if (check.value > 0f) {
    val unit = notchHeight / 4
    val path = Path().apply {
        moveTo(…); lineTo(…); lineTo(…)
    }
```

## Fix

Behavior-preserving; nothing on screen changes.

1. **Both screens keep the state as a `State` and derive what changes rarely.** In `TunerScreen` (and the same in
   `TunerSheet`):

   ```kotlin
   val state = viewModel.tunerState.collectAsStateWithLifecycle()
   …
   val status = permission.status
   val notice by remember(status, hasRequested) {
       derivedStateOf { tunerNoticeOf(status = status, listening = state.value.listening, hasRequested = hasRequested) }
   }
   val isHearing by remember { derivedStateOf { state.value.listening is TunerListening.Hearing } }
   val tone by remember { derivedStateOf { state.value.tone } }
   val issue by remember { derivedStateOf { (state.value.listening as? TunerListening.Hearing)?.issue } }
   val heardNote by remember { derivedStateOf { (state.value.listening as? TunerListening.Hearing)?.reading?.note } }
   val config = remember(settings) { settings.toConfig() }
   ```

   `keepScreenOn` reads `isHearing`; `TunerDisplay` gets `referencePitch = config.referencePitch`.

2. **`TunerDisplay` reads the state itself.** Its parameter becomes `state: State<TunerState>` (the repo passes
   `State<String>` the same way in `songEditor/EditorToolbar.kt` and `SongPreview.kt`), and its body starts with
   `val tunerState = state.value`, the rest reading `tunerState` where it read `state`. `TunerDisplay` is a restartable
   Composable, so each reading recomposes it and nothing above it. Update its KDoc only if it mentions the parameter.

3. **`TunerOptions` takes only what it draws from the state**: replace `state: TunerState` with `tone: Int?`,
   `issue: TunerInputIssue?` and `heardNote: Int?`, and `settings: TunerSettings` + its internal `toConfig()` with
   `config: TunerConfig` (it reads only `config.tuning` and `config.referencePitch`; `onSettingsChanged` stays). Its
   body maps one to one: `hearing?.issue` → `issue`, `state.tone` → `tone`, `hearing?.reading?.note` → `heardNote`.
   The callers pass the derived values — read inside the `SettingsPage` section lambda on the tab, so a change of
   `heardNote` recomposes that lambda only. `InstrumentChoice` and `ReferencePitchSetting` are lane A's files: only
   their call sites in `TunerOptions` change (they already take plain values).

4. **`TunerMeter` remembers one `Path`**: `val checkPath = remember { Path() }` beside the other remembered values,
   and in the draw block `checkPath.reset()` followed by the same `moveTo` / `lineTo` calls, then
   `drawPath(path = checkPath, …)`.

Land it after the other `TunerDisplay` plans of this lane (52–55, 62), whose code it rebases over; it changes how the
display gets its state, not what it computes.

Docs: `ui/tuner/CLAUDE.md`, the `TunerDisplay` / `TunerMeter` bullet: add "It reads the tuner's state itself (a
`State`), so the readings, thirty a second, recompose the display and nothing around it; the screens pass the rest of
the page only what changes with a note or a setting."

## Tests

None: recomposition scope is not logic. `tunerNoticeOf` and `canListenWithoutTap` keep their tests unchanged.

## Manual check

Android Studio's Layout Inspector with recomposition counts on a phone, Tuner tab, a held string: `TunerDisplay` and
`TunerMeter` count up continuously; `TunerOptions`, `InstrumentChoice`, `TunerStrings` and `ReferencePitchSetting`
count only when the heard note or a setting changes. Then check nothing regressed: the notice still replaces the
display on a refusal, the screen stays on while hearing, a string chip is selected for the heard note, a tone still
names its note in the display, and the sheet over a song behaves the same.
