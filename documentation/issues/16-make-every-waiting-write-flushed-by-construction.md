# Make every value waiting for its debounce flushed by construction, flush it on pause too, and let domain writes finish once started

**Kind:** structure (data safety) · **Severity:** medium · **Priority:** P2 · **Platforms:** all · **Lane:** P (after 13 and 15) · **Effort:** M (3 steps)
**Files:** `ui/state/DebouncedPreference.kt`, new `ui/state/PendingWrites.kt`, `ui/state/AppExitController.kt`,
`ui/playing/PendingOverrides.kt`, `ui/playing/PlayingOverrides.kt`, `ui/fontScale/FontScaleController.kt`,
`ui/metronome/MetronomeController.kt`, `ui/tuner/TunerController.kt`, `ui/screens/export/ExportController.kt`,
`ui/CampfireViewModel.kt`, `ui/state/songs/SelectionEditing.kt`, `ui/state/setlists/SetlistsController.kt` (a comment),
`ui/firstRun/FirstRunController.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/` (`preferences/UpdateUserPreferencesUseCaseImpl.kt`,
`songs/EditSongsUseCaseImpl.kt`, `songs/SaveSongContentUseCaseImpl.kt`, `setlists/SaveSetlistUseCaseImpl.kt`), the
matching `domain/api` KDoc, tests (`DebouncedPreferenceTest.kt`, `PendingOverrides` tests, plan 12's `AppExitTest`),
`ui/CLAUDE.md`, `ui/playing/CLAUDE.md`

**Challenged:** amended — `SetlistsController`'s two `NonCancellable` blocks stay: each spans `createSetlist` (which
already writes an empty file) and `saveSetlist`, so dropping it could leave an empty setlist; step 2 launches the flush
before `startScheduledSynchronization()` and says a setlist override flushed on pause reaches the other devices on the
next run, not necessarily the one started then.

`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

Six values are saved only once they hold still (500 ms): the text size, the metronome, tuner, and export settings
(`DebouncedPreference`), and the tempo and capo overrides (`PendingOverrides`, which write the preferences for a library
song and the setlist file for a setlist song). `DebouncedPreference`'s KDoc states the rule nothing enforces:
"Whatever is still waiting when the owner goes is written by [flushAll], which every such preference has to be passed
to, or its last value is lost on quit." The passing is a hand-kept list in `CampfireViewModel`:

```kotlin
waitingPreferences = listOf(
    fontScaleController.fontScalePreference,
    metronomeController.metronomeSettingsPreference,
    tunerController.tunerSettingsPreference,
    exportController.printSettingsPreference,
),
```

plus `overrides.takeWaitingOverrideWrites()` called by hand in `settleSynchronizationBeforeExit` and `onCleared`. A
seventh debounced value compiles, works, and is lost on every quit. And `AppExitController.onAppPaused` — "the last
moment it is certainly running: iOS ends a process in the background without a word" — flushes none of them: a value
set just before the app is backgrounded waits on a timer in a process iOS may suspend and kill.

Separately, "a write once started finishes" is decided call site by call site: the domain does it for the multi-step
writes (`RenameSongFileUseCaseImpl`, `DeleteSongUseCaseImpl`, `DeleteLibraryUseCaseImpl`, `EditSetlistUseCaseImpl`,
`DeleteSetlistUseCaseImpl`), while `:presentation` wraps single domain calls in `withContext(NonCancellable)`
(`SelectionEditing`: `editSongs`; `SetlistsController` twice: `saveSetlist`; `FirstRunController` twice:
`updateUserPreferences`), and every other library change runs on `viewModelScope` through
`MessageSink.launchLibraryChange`, cancellable mid-write when the Android activity finishes.

**Reshaped after verification.** The review proposed moving write-behind into the preferences repository and running
library writes on an app scope below `:presentation`. Not done, because: the debounce exists so the preferences are
not *published* to every screen once a frame (commits `01dcd6b86`, `5c547be0e`), which a write-behind repository would
still do in memory; the setlist half of the overrides is a library write no preferences repository covers; and the
flush-on-exit problem would move down a layer rather than go away. Making the domain's writes non-cancellable gives
the same guarantee as an app scope with no new scope to own.

## Fix

### Step 1 — `PendingWrites`: nothing debounced exists unregistered

New `ui/state/PendingWrites.kt`, one per view model, built before the holders:

```kotlin
internal class PendingWrites(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val writePreferences: PreferencesWrite,
) {
    /** A debounced preference, saving itself once it holds still, and flushed by [flushAll]. */
    fun <T : Any> preference(apply: UserPreferences.(T) -> UserPreferences): DebouncedPreference<T>
    /** Registers the tempo or capo overrides, whose writes [flushAll] takes too. */
    fun register(overrides: PendingOverrides<*>)
    /** Today's exit order: the preferences in one read-modify-write (`DebouncedPreference.flushAll`), then the overrides. */
    suspend fun flushAll()
}
```

`preference` builds the `DebouncedPreference`, starts its collector on `scope` (replacing each holder's
`start…Writer()`), and keeps it. `PendingOverrides` takes `pendingWrites` in its constructor and registers itself.
The four holders take `pendingWrites` instead of `updateUserPreferences` + `writeDelayMillis` for their debounced value
(they keep `updateUserPreferences` where they also write directly). `AppExitController` takes `pendingWrites` instead of
`waitingPreferences` and `overrides`; `settleSynchronizationBeforeExit` calls `pendingWrites.flushAll()` where it called
`writeWaitingPreferences()` and `overrides.takeWaitingOverrideWrites()()`. `onCleared` keeps its detached
`CoroutineScope(Dispatchers.Default + NonCancellable)` but must take the overrides' waiting writes *before* launching
(as `takeWaitingOverrideWrites()` does today, cancelling their jobs synchronously): give `PendingWrites` a
`fun takeAll(): suspend () -> Unit` for that and implement `flushAll()` as `takeAll()()`. Behaviour unchanged.

**Size note (`check_sizes.py`):** `CampfireViewModel.kt` (936 lines) may not grow, and `MetronomeController` sits at the 12-parameter limit and `ExportController` (15) past it: `pendingWrites` replaces parameters, never adds one.

### Step 2 — flush on pause (behaviour change, its own commit)

`onAppPaused` also launches `pendingWrites.flushAll()` on `scope`, first, before `startScheduledSynchronization()`: on
`Main.immediate` it takes the waiting overrides synchronously, so a stepper set again after the pause starts a fresh
debounce. The write itself lands after the callback returns, so a setlist override may miss the run started on that
pause and go with the next one (a run starts on every return to the front); locally nothing is lost. A value saved half
a second early is harmless; one lost to a killed background process is not. On the desktop ON_PAUSE also follows a
window losing focus, which only saves sooner. `ui/CLAUDE.md` and the KDoc of `onAppPaused` say so.

### Step 3 — domain writes finish once started

`UpdateUserPreferencesUseCaseImpl`, `EditSongsUseCaseImpl`, `SaveSongContentUseCaseImpl`, and
`SaveSetlistUseCaseImpl` run their write under `withContext(NonCancellable)`, as the multi-step use cases already do;
their `domain/api` KDoc says "once started, it finishes even if the caller is cancelled". The presentation sites whose
`withContext(NonCancellable)` wraps only such a call drop it (`SelectionEditing`, `FirstRunController`'s two).
`SetlistsController`'s two stay, since each spans two writes (`createSetlist`, then `saveSetlist`), and say so. `SongTextStore`'s and `EditorSession`'s stay: they keep the in-memory song texts and
editor state in step with the file under `songWriteMutex`, which is presentation state, and their comments say why.

Delete the `waitingPreferences` KDoc and the "has to be passed to [flushAll]" sentence from `DebouncedPreference`.

## Tests

- `PendingWritesTest` (`commonTest`, `ui/state/`, virtual time):
  `` `a preference made by it saves once it holds still` ``,
  `` `flushAll writes every waiting preference in one write and the overrides after it` ``,
  `` `a value set after the flush was read is kept waiting` `` (moved from `DebouncedPreferenceTest` where it lives).
- Domain: `` `a preferences update finishes when its caller is cancelled` `` and the same for `EditSongs`,
  `SaveSongContent`, `SaveSetlist`, each cancelling the caller inside the fake repository's write.
- Plan 12's `AppExitTest` passes unchanged; add `` `going to the background writes the values that were still waiting` ``.
