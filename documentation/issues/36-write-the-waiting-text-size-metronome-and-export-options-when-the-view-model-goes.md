# Write everything still waiting for its debounce — text size, metronome settings, export options, tempo and capo overrides — before the view model or the desktop process goes

**Kind:** bug (data loss, small)  ·  **Severity:** low  ·  **Platforms:** Android (activity finished); desktop on quit
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/CLAUDE.md
**Challenged:** amended — on the desktop `onCleared` runs as the window's composition is disposed by `exitApplication`, right before `exitProcess(0)`, so a write launched there on `Dispatchers.Default` races the process ending; the three values are now also written, awaited, in `settleSynchronizationBeforeExit` (the desktop's awaited pre-exit hook). The `onCleared` write is wrapped in `try`/`catch` unconditionally, since an exception escaping a bare `CoroutineScope` would crash the process.
**Challenged:** amended (second pass) — the waiting tempo and capo overrides have the same desktop loss and are now in scope: `onCleared`'s existing detached write of them races `exitProcess(0)` after a window close, and on macOS Cmd+Q / the app menu's Quit (`response::performQuit`, which ends the JVM without disposing the composition) `onCleared` never runs at all, so they are always lost there. `settleSynchronizationBeforeExit` now also takes them out of their debounce and writes them, awaited, before the sync wait; the snapshot-and-cancel is one helper shared with `onCleared`.

## Problem

Three preferences are written 500 ms after they stop changing, by collectors in `viewModelScope`
(`CampfireViewModel.kt:1343-1359`, 8ee010b36):
```kotlin
unsavedFontScale.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { fontScale -> updateUserPreferences { it.copy(fontScale = fontScale) } ... }
_pendingPrintSettings.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { savePrintSettings(it) }
_pendingMetronomeSettings.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { settings -> updateUserPreferences { it.copy(metronomeSettings = settings) } ... }
```
and the tempo and capo overrides are written by a job per song launched in `viewModelScope` that waits
`FONT_SCALE_SAVE_DELAY_MILLIS` first (`changeTempo` `:2560-2572`, `changeCapo` `:2633-2645`; the not-yet-started ones
are in `tempoWriteJobs` / `capoWriteJobs`, and a job takes itself out of its map, on the main thread, the moment its
delay ends and before its first suspension, so every job still in a map is still in its delay).

`onCleared` (`:2736-2748`) rescues the tempo and capo overrides still inside their debounce, on a scope of its own,
but not the three preferences:
```kotlin
override fun onCleared() {
    metronome.stop()
    val waitingTempos = ...; val waitingCapos = ...
    ...
    CoroutineScope(Dispatchers.Default + NonCancellable).launch { ... writeTempo / writeCapo ... }
}
```
Scenario (Android): on the Metronome tab drag the volume slider (or change the subdivision, or pinch a song's text),
then press Back twice within half a second (Metronome → Songs → finish). The activity finishes, `viewModelScope` is
cancelled while the debounce is waiting, and the next launch has the old volume / text size. Export options are flushed
when the export screen closes (`setVisibleDialog`, `:3820`), but only by a `viewModelScope.launch`, which is cancelled
the same way if the activity finishes with the screen up.

The desktop loses all five, the overrides included:
- **Window close / Ctrl+Q / the key handler's exit** (`CampfireDesktopApplication.kt:107-117`): `leave` awaits
  `settleSynchronizationBeforeExit()` and then calls `exitApplication`; `application(exitProcessOnExit = true)` ends in
  `exitProcess(0)` right after the composition (and with it the window's `ViewModelStore`) is disposed. `onCleared`'s
  write is launched on `Dispatchers.Default` and nothing waits for it, so it races the process ending. With no sync
  run owed `settleSynchronizationBeforeExit` returns at once, so a stepper tapped just before closing the window is
  inside its 500 ms debounce when `onCleared` runs.
- **macOS Cmd+Q and the application menu's Quit** (`:126-139`): `leave(response::performQuit)` ends the JVM through
  AppKit's terminate without disposing the composition, so `onCleared` never runs and nothing rescues any of them.

The library's tempo and capo overrides are synced (root `CLAUDE.md`, Sync: "The library's per-song overrides travel
too"), and a setlist's are written into its `*.setlist.json`, so a lost one is also a change the other devices never see.

## Fix

1. Extract the write of the three waiting preferences into one suspend function:
   ```kotlin
   /**
    * Writes the text size, the metronome settings and the export options that are still waiting for their debounce, in
    * one read-modify-write of the preferences, for a view model or a process that is about to go. Each is let go of
    * only if nothing newer arrived meanwhile, as the collectors do.
    */
   private suspend fun writeWaitingPreferences() {
       val fontScale = unsavedFontScale.value
       val metronomeSettings = _pendingMetronomeSettings.value
       val printSettings = _pendingPrintSettings.value
       if (fontScale == null && metronomeSettings == null && printSettings == null) return
       try {
           updateUserPreferences { preferences ->
               preferences.copy(
                   fontScale = fontScale ?: preferences.fontScale,
                   metronomeSettings = metronomeSettings ?: preferences.metronomeSettings,
                   printSettings = printSettings ?: preferences.printSettings,
               )
           }
           fontScale?.let { unsavedFontScale.compareAndSet(it, null) }
           metronomeSettings?.let { _pendingMetronomeSettings.compareAndSet(it, null) }
           printSettings?.let { _pendingPrintSettings.compareAndSet(it, null) }
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Exception) {
           // Only what was already being lost.
           println("Could not write the waiting preferences: ${exception.message}")
       }
   }
   ```
   (`printSettings` is the `UserPreferences` field `savePrintSettings` (`:3276`) writes with the same `copy`.)

2. Extract the snapshot of the waiting overrides from `onCleared` into a helper that also cancels their jobs, and
   returns the writes to make — so `onCleared` (whose scope is already cancelled) and `settleSynchronizationBeforeExit`
   (whose scope is alive, so the jobs would otherwise still fire and write the same value a second time) share it:
   ```kotlin
   /**
    * Takes the tempo and capo overrides still waiting for their debounce out of it - their jobs cancelled, which only
    * ever catches one in its delay, since a job leaves its map the moment the delay ends - and returns their writes,
    * for a view model or a process that is about to go.
    */
   private fun takeWaitingOverrideWrites(): suspend () -> Unit {
       val waitingTempos = tempoWriteJobs.mapNotNull { (key, job) -> job.cancel(); pendingTempos.value[key]?.let { key to it.bpm } }
       val waitingCapos = capoWriteJobs.mapNotNull { (key, job) -> job.cancel(); pendingCapos.value[key]?.let { key to it.fret } }
       tempoWriteJobs.clear()
       capoWriteJobs.clear()
       return {
           waitingTempos.forEach { (key, bpm) -> writeTempo(key, bpm) }
           waitingCapos.forEach { (key, fret) -> writeCapo(key, fret) }
       }
   }
   ```
   Both callers run on the main thread, as every other access to the two maps does (`viewModelScope` is
   `Dispatchers.Main.immediate`; the desktop's `leave` launches on the window's `rememberCoroutineScope`, the same EDT).

3. `onCleared` (Android: the activity finished; desktop: the window disposed): take the overrides, then write
   everything on the same detached scope, the three preferences first:
   ```kotlin
   override fun onCleared() {
       metronome.stop()
       val writeWaitingOverrides = takeWaitingOverrideWrites()
       // Written on a scope of their own, since this one is being cancelled: each was waiting for its debounce, which
       // this scope's cancellation would otherwise drop.
       CoroutineScope(Dispatchers.Default + NonCancellable).launch {
           try {
               writeWaitingPreferences()
               writeWaitingOverrides()
           } catch (exception: Exception) {
               // A bare scope has no handler, and an exception escaping it would end the process.
               println("Could not write the waiting values: ${exception.message}")
           }
       }
   }
   ```
   (`writeTempo` / `writeCapo` / `writeWaitingPreferences` already catch their own failures; the outer `try` is for
   anything else, e.g. a `sendMessage` on a cleared view model.) The launch is unconditional: each part returns at once
   when nothing waits. Update `onCleared`'s KDoc first paragraph to name the three preferences next to the tempo and
   the capo.

4. `settleSynchronizationBeforeExit`: right after `metronome.stop()` and **before** the sync wait, write everything
   that is waiting, awaited:
   ```kotlin
   // The process ends right after this, and onCleared's detached write would race it - or, on a macOS Quit, never run.
   // Before the sync wait, so that an override written now is in the library a run that is still to start carries.
   writeWaitingPreferences()
   takeWaitingOverrideWrites()()
   ```
   and add one sentence to its KDoc: what is still waiting for its debounce (the text size, the metronome settings, the
   export options and the tempo and capo overrides) is written first, since the process ends right after. On the
   desktop the `onCleared` that follows a window close then finds nothing waiting. This is the only path where the
   process is known to end right after; Android's `onCleared` is followed by a process that lives on.

   Reaching the cloud folder is best effort, not part of the fix: the write schedules a sync run through a collector on
   the sync repository's own scope (`SyncRepositoryImpl.kt:163-164`), a hop later, so `startScheduledSynchronization()`
   right after may not see it yet, exactly as for any other write made just before quitting. The value is in the
   preferences or the setlist file either way, and the next launch's run carries it (it settles the synced preferences
   on every completed run).

A value already being written by its collector or job when the scope is cancelled is written once more with the same
value, which is harmless; `updateUserPreferences` serializes the writes, so an older value cannot land after a newer
one.

presentation/CLAUDE.md: where `fontScale` is described as persisted "with a debounce" (the `SongDisplayControls.kt`
entry), add that a value still waiting when the view model is cleared, or when the desktop process is about to end, is
written then; and wherever the tempo / capo overrides' debounced write is described (`presentation/CLAUDE.md:286`, "the writes still
waiting are made on a scope of their own in `onCleared`"; the metronome settings are named in the next sentence), say the same of them — written when the view model is cleared, and awaited before the
desktop process ends, a macOS Quit included.

**Ordering with plan 37**, which also adds a first step to `settleSynchronizationBeforeExit`: the order inside it is
plan 37's draft settle, `metronome.stop()`, then this step's two writes, then the sync wait. Whichever lands second
keeps the other's lines.

## Tests

None: lifecycle; no pure logic.

## Manual check

Android: on the Metronome tab drag the volume to a clearly different value and immediately press Back twice to leave
the app; reopen: the volume is the new one. Same with a pinch on a song followed by Back, Back, and with a tap on a
song's tempo stepper followed by Back, Back (reopen the song: the tempo is the stepped one).
Desktop, with no sync account connected (so the settle returns at once): change the volume, or step a library song's
tempo and capo, and close the window within half a second; relaunch: the new values are there. Repeat on macOS with
Cmd+Q, and once with the tempo stepped on a song opened from a setlist (the setlist file holds the new tempo).