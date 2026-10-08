# Replace the three hand-rolled debounced preference writers with one `DebouncedPreference<T>`

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(`unsavedFontScale`, `_pendingPrintSettings`, `pendingPrintSettings`, `_pendingMetronomeSettings`, `metronomeSettings`,
`setFontScale`, `setPrintSettings`, `savePrintSettings`, `updateMetronomeSettings`, `writeWaitingPreferences`,
`setVisibleDialog`'s export branch, the three debounce collectors in `init`, the font scale echo collector,
`FONT_SCALE_SAVE_DELAY_MILLIS`); new `ui/state/DebouncedPreference.kt`; new
`commonTest/.../state/DebouncedPreferenceTest.kt`; `presentation/CLAUDE.md` (the `SongDisplayControls.kt` and Export
paragraphs mention "saved once they have held still" — wording stays true)
**Depends on:** none

## Problem

Three preferences that change every frame (a pinch's text size, the export screen's options, a dragged metronome
slider) are each saved by a copy of the same pattern — a nullable `MutableStateFlow` of the unsaved value, a
`debounce(500)` collector in `init` that writes and then `compareAndSet(value, null)`, a flush in
`writeWaitingPreferences` for `onCleared` and the desktop quit, and in one case an extra flush when a dialog goes:

```kotlin
viewModelScope.launch {
    unsavedFontScale.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { fontScale ->
        updateUserPreferences { it.copy(fontScale = fontScale) }
        unsavedFontScale.compareAndSet(fontScale, null)
    }
}
viewModelScope.launch {
    _pendingPrintSettings.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { savePrintSettings(it) }
}
viewModelScope.launch {
    _pendingMetronomeSettings.filterNotNull().debounce(FONT_SCALE_SAVE_DELAY_MILLIS).collect { settings ->
        updateUserPreferences { it.copy(metronomeSettings = settings) }
        _pendingMetronomeSettings.compareAndSet(settings, null)
    }
}
```

`writeWaitingPreferences` (`:2898`) then reads the three fields again by hand, writes them in one read-modify-write and
`compareAndSet`s each; `setVisibleDialog` flushes `_pendingPrintSettings` separately. All three reuse a constant named
`FONT_SCALE_SAVE_DELAY_MILLIS`. A fourth such preference means a fourth copy in four places, and forgetting the flush
in `writeWaitingPreferences` loses the value on quit — silently.

## Fix

1. Add `ui/state/DebouncedPreference.kt`:
   ```kotlin
   internal class DebouncedPreference<T : Any>(
       private val apply: UserPreferences.(T) -> UserPreferences,
   ) {
       private val _pending = MutableStateFlow<T?>(null)
       val pending: StateFlow<T?> = _pending.asStateFlow()
       fun set(value: T) { _pending.value = value }
       fun update(change: (T?) -> T) = _pending.update(change)
       /** Launches the debounced writer; call from `init` where the old collector was launched. */
       fun start(scope: CoroutineScope, delayMillis: Long, write: suspend ((UserPreferences) -> UserPreferences) -> Unit)
       /** Writes the pending value now (the export screen going). */
       suspend fun flush(write: …)
       /** For one batched write: the pending value folded into [preferences], and a commit that clears it if unchanged. */
       fun foldInto(preferences: UserPreferences): UserPreferences
       fun clearIfStill(value: T?)
       companion object {
           /** One read-modify-write for every pending value, each cleared only if nothing newer arrived meanwhile. */
           suspend fun flushAll(vararg preferences: DebouncedPreference<*>, write: suspend ((UserPreferences) -> UserPreferences) -> Unit)
       }
   }
   ```
   `write` is the view model's `updateUserPreferences` use case (`UpdateUserPreferencesUseCase`, a transform applied
   to what the repository holds). The writer is exactly today's: `pending.filterNotNull().debounce(delay).collect { v -> write { apply(it, v) }; _pending.compareAndSet(v, null) }`.
   `flushAll` must keep today's single `updateUserPreferences` call for all three (one publish, not three), and the
   snapshot-then-`compareAndSet` order of `writeWaitingPreferences`, including its `catch (Exception)` that only logs.
2. In the view model: `unsavedFontScale` → `fontScalePreference = DebouncedPreference<Float> { copy(fontScale = it) }`,
   `_pendingPrintSettings` → `printSettingsPreference`, `_pendingMetronomeSettings` → `metronomeSettingsPreference`.
   Keep the public `pendingPrintSettings` (= `printSettingsPreference.pending`) and `metronomeSettings`
   (`combine(userPreferences, metronomeSettingsPreference.pending) { … }`) as they are. Replace the three collectors
   with `start(...)` calls **at the same positions in `init`** (the font scale echo collector, which reads
   `unsavedFontScale.value == null`, must stay after the font scale writer and now reads
   `fontScalePreference.pending.value == null`). `writeWaitingPreferences` becomes
   `DebouncedPreference.flushAll(fontScalePreference, metronomeSettingsPreference, printSettingsPreference, write = updateUserPreferences::invoke)`;
   the export branch of `setVisibleDialog` becomes `viewModelScope.launch { printSettingsPreference.flush(…) }`.
   Rename `FONT_SCALE_SAVE_DELAY_MILLIS` to `PREFERENCE_SAVE_DELAY_MILLIS` (it is also the tempo/capo write delay;
   rename those uses too). One commit.

Tempo and capo overrides are debounced per song and written to setlists as well as preferences; they are plan 02's
`PendingOverrides`, not this.

## Tests

`DebouncedPreferenceTest` (`runTest`, virtual time): a burst of sets writes once with the last value after the delay;
a set arriving while a write is in flight is not cleared by that write's `compareAndSet` and is written next;
`flushAll` writes every pending value in one call and clears only those unchanged since the snapshot; a failing write in
`flushAll` is caught and leaves the values pending.

## Manual check

Pinch the text size on a song and quit the desktop app with Cmd+Q within half a second, relaunch (size kept); change
two export options and press Back immediately, reopen the export screen (options kept); drag the metronome volume and
background the Android app within half a second, swipe it away, relaunch (volume kept).
