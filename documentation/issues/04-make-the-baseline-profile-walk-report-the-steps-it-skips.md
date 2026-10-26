# Check that the recorded Baseline Profile covers every walked screen, and walk the screens it never reached

**Kind:** performance · **Severity:** low · **Platforms:** Android
**Files:** `app/baselineprofile/src/main/kotlin/com/pandulapeter/campfire/baselineprofile/BaselineProfileGenerator.kt`,
`app/baselineprofile/CLAUDE.md`, `app/android/build.gradle.kts`

**Challenged:** amended — the check moved from failing the instrumentation run to checking the written profile. A
failed test fails AGP's connected test task, and Gradle then never runs the tasks after it that copy the profile into
`src/main/generated`, so "fail after `rule.collect`" would most likely lose the recording it reports on; the plan could
only find out on an emulator, leaving D3 unanswerable before execution. A check of the written `baseline-prof.txt` for
one rule per walked screen is deterministic, needs no emulator to land, keeps the files, and names a skipped step by its
missing screen, which makes a per-step recorder in the generator unnecessary.

**Decision D3** (awaiting the user): fail `generateBaselineProfile` when the written profile lacks a walked screen
(recommended), or only print the missing screens.

## Problem

The committed profile (`app/android/src/main/generated/baselineProfiles/baseline-prof.txt`, recorded for 5.0.1 on
2026-10-22) has no rule for any composable of the Chords tab, the Scales tab, or Settings: under
`presentation/ui/screens/chords/` it holds only `ChordLibraryState`, under `scales/` only `ScaleLibraryState`, under
`settings/` only `LibrarySummary` and `SettingsCategoryPaneKt` — classes the view model and the song list touch.
`ChordsScreenKt`, `ScalesScreenKt`, `SettingsScreenKt`, the editor (`LoadedSongEditorKt`), and `CampfireBottomSheetKt` have
0 rules; `TunerScreenKt` (61) and `MetronomeScreenKt` (51) were reached. So these steps of the walk did nothing:

```kotlin
listOf("Tuner", "Chords", "Scales").forEach { tool ->
    toolButton(tool)?.click()
    device.waitForIdle()
}
waitFor(By.text("Settings"))?.click()
device.waitForIdle()
```

and nobody could tell, by design (`app/baselineprofile/CLAUDE.md`: "Each step waits up to 5 s and is skipped if the
text never appears, so a renamed string costs a step, not the run"). The 40th review already fixed one such silent miss
(the tools found `By.desc`); it happened again. The walk also never opens the editor, a bottom sheet, or a list search,
so their first use on Android runs interpreted / JIT-compiled from cold.

## Fix

1. `app/android/build.gradle.kts`: a `doLast` on the `generateBaselineProfile` task
   (`tasks.matching { it.name == "generateBaselineProfile" }.configureEach { … }`; confirm the name with
   `./gradlew :app:android:tasks --all | grep -i baselineprofile`) that reads
   `src/main/generated/baselineProfiles/baseline-prof.txt` and fails with the list of walked screens that have no rule.
   One entry per step of the walk, as the class the screen's composable compiles to, e.g.
   `presentation/ui/screens/songs/SongsScreenKt`, `…/songDetails/SongDetailsScreenKt`, `…/setlists/SetlistsScreenKt`,
   `…/metronome/MetronomeScreenKt`, `…/tuner/TunerScreenKt`, `…/chords/ChordsScreenKt`, `…/scales/ScalesScreenKt`,
   `…/settings/SettingsScreenKt`, `…/songEditor/LoadedSongEditorKt`, `presentation/ui/dialogs/sheet/CampfireBottomSheetKt`
   (check each against the source file names). Configuration cache: capture the file as a `layout.projectDirectory.file`
   provider and the list in locals inside `configureEach`, never a script `val` the action reaches. The files are written
   before the check runs, so a failed check keeps the recording for comparison. A comment ties the list to
   `BaselineProfileGenerator`'s steps (a new step adds its screen here).
   Under D3's other answer the same `doLast` prints the missing screens with `logger.warn` instead of failing.
2. Add steps to `a_journeys`, found by visible English text or content description from
   `presentation/src/commonMain/composeResources/values/strings.xml`:
   - in the opened demo song, at its top: tap the title (opens About the song, `song_details_song_info`, a
     `CampfireBottomSheet`), then Back; open the song's overflow menu and its **Edit** (`song_editor_edit`; the editor,
     `LoadedSongEditor`), type nothing, Back;
   - on Songs: the search button, type `rising`, then Back.
3. Find why Chords, Scales, and Settings were missed — the code alone does not say (the tool buttons are looked up by
   content description, `toolButton`, which reached the Tuner). Candidates to check on the emulator with
   `uiautomator dump` after the Tuner step: the button group's descriptions on the Tuner screen, a `waitForIdle` that
   returns before the Tuner screen settles, the Settings item's label on the `Resizable_Experimental` emulator's bar or
   rail. Fix the lookup accordingly. This needs one run of the walk; if the lane cannot run an emulator, it lands 1–2 and
   leaves the cause to the next prepare-release run, where the check names the missed screens.
4. `app/baselineprofile/CLAUDE.md`: replace "Each step waits up to 5 s and is skipped if the text never appears, so a
   renamed string costs a step, not the run" with: a step still waits up to 5 s and is skipped, and
   `generateBaselineProfile` then fails naming the screen the profile lacks (the files are written regardless); a new step
   adds its screen to the list in `app/android/build.gradle.kts`. Add the new steps to the journey list, and to "Recording
   it" step 3 that the check runs by itself. Update the generator's class KDoc ("skipped where the text never appears")
   the same way.

Regenerating the profile is not part of this plan: prepare-release does it before every release. Until then the check
would fail on today's committed profile, which is the point; it only runs with `generateBaselineProfile`, never in a
build or in CI.

## Tests

None can run in CI (the generator needs an emulator and is never run there). Compile the generator
(`./gradlew :app:baselineprofile:tasks --all | grep -i compile` names the task, e.g.
`:app:baselineprofile:compileNonMinifiedReleaseKotlin`), configure `:app:android` with the configuration cache on
(`./gradlew :app:android:help`), and run `./gradlew spotlessCheck`. To try the check without an emulator, run its logic
once against the committed profile (a throwaway copy of the `doLast` body in the scratch folder, or temporarily) and
confirm it names Chords, Scales, Settings, the editor, and the bottom sheet. If an emulator is available, run the walk
once as `app/baselineprofile/CLAUDE.md`'s "Recording it" says, check the check passes, and do **not** commit the
regenerated profile files (`git checkout app/android/src/main/generated`).
