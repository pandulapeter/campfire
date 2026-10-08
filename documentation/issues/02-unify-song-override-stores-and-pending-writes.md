# Replace the twin tempo / capo / transposition override machinery with one generic `SongOverrides<T>` and `PendingOverrides<T>`

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** medium  ·  **Platforms:** all
**Challenged:** amended — new files go into `ui.playing` (where the package-move pass puts `Tempos`/`Capos`/`Transpositions`)
instead of a new `ui/overrides/`; `Transpositions` and the view model's `transpositions` become `internal` (a public
wrapper around the internal `SongOverrides` does not compile, and nothing outside the module reads either);
`withEntry`'s null is spelled out per caller (`changeTransposition` writes the setlist unchanged and reports nothing, as
today); `PendingOverrides` gets an explicit failure callback and keeps the per-field `println`.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(`transpositions`, `storedTempos`, `pendingTempos`, `tempoWriteJobs`, `tempos`, `storedCapos`, `pendingCapos`,
`capoWriteJobs`, `capos`, `stepTempo`, `setTempo`, `resetTempo`, `changeTempo`, `writeTempo`, `stepCapo`, `resetCapo`,
`changeCapo`, `writeCapo`, `changeTransposition`, `takeWaitingOverrideWrites`, the two settle collectors in `init`,
`PendingTempo`, `PendingCapo`, nested `Transpositions`); `ui/metronome/SongTempo.kt` (`Tempos`, `TempoKey`);
`ui/screens/songDetails/SongCapo.kt` (`Capos`, `CapoKey`) — after the package-move pass these, and the nested
`Transpositions`, live in `ui.playing`: find them by name; new `ui/playing/SongOverrides.kt`,
`ui/playing/PendingOverrides.kt`; every reader of `Tempos`/`Capos`/`Transpositions`/`TempoKey`/`CapoKey`
(`ui/metronome/MetronomePatterns.kt`, `MetronomeContext.kt`, `screens/songs/SongsScreen.kt`,
`screens/setlists/SetlistsScreen.kt`, `screens/songDetails/SongDetailsScreen.kt`, `SongMetadataActions.kt`,
`dialogs/SongPlayingDialog.kt`, `CampfireViewModel.preparePrintSource`); tests `SongTempoTest`, `SongCapoTest`, `MetronomePatternsTest` (all three construct `TempoKey`/`CapoKey`; wherever the
package-move pass left them), new `commonTest/.../playing/SongOverridesTest.kt` and `PendingOverridesTest.kt`; `presentation/CLAUDE.md` (Metronome section, "Where a capo lives" / "Where a tempo lives")
**Depends on:** none

## Problem

A tempo override and a capo override are stored, pended, debounced, written, settled and flushed by two copies of the
same ~120 lines that differ only in a field name. At 2940b0e0a:

- `Tempos` (`ui/metronome/SongTempo.kt:24`) and `Capos` (`ui/screens/songDetails/SongCapo.kt:21`) are character for
  character the same class (`library: Map<String, Int>`, `bySetlist: Map<String, Map<String, Int>>`, `get(song, setlist)`,
  `with(key, value)`), as are `TempoKey` and `CapoKey`; `CampfireViewModel.Transpositions` is the same layout with
  a default of 0 and `wrapTransposition` on read.
- The view model folds each store from the same two sources three times:

  ```kotlin
  private val storedTempos = combine(userPreferences, setlists) { userPreferences, setlists ->
      Tempos(library = userPreferences?.tempos.orEmpty(),
          bySetlist = setlists.associate { setlist -> setlist.fileName to setlist.entries.mapNotNull { entry -> entry.tempo?.let { entry.songFileName to it } }.toMap() })
  }.asState(Tempos())
  // …storedCapos is the same with `capo`, transpositions the same with `transposition`
  ```
- `changeTempo`/`changeCapo` (`:2696`, `:2768`), `resetTempo`/`resetCapo` (`:2683`, `:2760`) and
  `writeTempo`/`writeCapo` (`:2710-2747`, `:2782-2824`) are line-for-line twins, including the "a song a sync run took
  out of the setlist has no entry" rule and the `updateEditableSetlist { setlist.copy(entries = setlist.entries.map { … entry.copy(tempo = bpm) … }) }`
  rewrite; `changeTransposition` repeats that entry rewrite a third time (`:2577`) and `reorderSetlist` a fourth.
- `init` has two identical settle collectors:

  ```kotlin
  combine(storedTempos, pendingTempos) { stored, pending ->
      pending.filter { (key, value) -> value.isWritten && stored[key.songFileName, key.setlistFileName] == value.bpm }.keys
  }.collect { settled -> if (settled.isNotEmpty()) pendingTempos.update { it - settled } }
  // …and the same for capos
  ```
- `takeWaitingOverrideWrites` (`:2927`) walks both job maps by hand.

A third per-song override (the next playing value, or a per-setlist font size) would be a third copy, and a fix to one
copy (the "no entry" rule was such a fix) has to be remembered for the other.

## Fix

1. **`SongPlace` and `SongOverrides<T>`** (pure, `ui/playing/SongOverrides.kt`):
   ```kotlin
   internal data class SongPlace(val songFileName: String, val setlistFileName: String?)   // replaces TempoKey and CapoKey
   internal data class SongOverrides<T : Any>(
       private val library: Map<String, T> = emptyMap(),
       private val bySetlist: Map<String, Map<String, T>> = emptyMap(),
   ) {
       operator fun get(songFileName: String, setlistFileName: String?): T?
       operator fun get(place: SongPlace): T? = get(place.songFileName, place.setlistFileName)
       fun with(place: SongPlace, value: T?): SongOverrides<T>
       companion object {
           fun <T : Any> of(library: Map<String, T>, setlists: List<Setlist>, read: (Setlist.Entry) -> T?): SongOverrides<T>
       }
   }
   internal typealias Tempos = SongOverrides<Int>
   internal typealias Capos = SongOverrides<Int>
   ```
   (The two aliases are then one type, so a `Capos` could be passed where a `Tempos` is wanted; the callers are few
   and named `tempos =` / `capos =`, which is judged enough. If not, give `SongOverrides` a phantom kind parameter,
   `SongOverrides<K : OverrideKind, T>`, instead.) Keep `Tempos`/`Capos` as typealiases so `effectiveTempo(…, tempos: Tempos, …)`, `effectiveCapo`, `MetronomePatterns`
   and the screens keep compiling; replace `TempoKey`/`CapoKey` with `SongPlace` everywhere (mechanical).
   `Transpositions` becomes a thin wrapper `internal data class Transpositions(private val overrides: SongOverrides<Int> = SongOverrides())`
   whose `get` keeps `wrapTransposition(overrides[song, setlist] ?: 0)` — the read wrap and the 0 default must not
   change. It is public today (it was nested in the public view model), and a public class whose public constructor
   takes the internal `SongOverrides` does not compile; nothing outside `:presentation` reads it (`app/*` and
   `tools/screenshots` do not), so make it `internal` and the view model's `val transpositions` `internal val` with it
   (its readers — `SongList`, `SetlistList`, `SongDetailsScreen`, `ChordShapesSheet`, `SongPlayingDialog` — are all in the module).
   `storedTempos`, `storedCapos` and `transpositions` become `SongOverrides.of(prefs.tempos, setlists) { it.tempo }`,
   `… { it.capo }`, `… { it.transposition.takeIf { t -> t != 0 } }` (the transposition store filters zeros today:
   `setlist.entries.filter { it.transposition != 0 }` — keep that).
   One commit; `SongTempoTest` and `SongCapoTest` keep passing with only the key type renamed.

2. **`Setlist.withEntry` helper** (presentation-internal extension, not in `:data:model`, which would be a module
   change for one helper): `internal fun Setlist.withEntry(songFileName: String, change: (Setlist.Entry) -> Setlist.Entry): Setlist?`
   returning null when the setlist has no entry for the song (which is exactly the `hasEntry` flag `writeTempo` and
   `writeCapo` compute today), and use it in `writeTempo`, `writeCapo` and `changeTransposition`. `updateEditableSetlist`'s
   transform returns a non-null `Setlist`, so each caller says what null means, keeping today's behaviour:
   `writeTempo`/`writeCapo` — `setlist.withEntry(song) { it.copy(tempo = bpm) }?.also { hasEntry = true } ?: setlist`
   (the `hasEntry || bpm == null` rule after it is unchanged); `changeTransposition` —
   `setlist.withEntry(song) { it.copy(transposition = wrapTransposition(change(it.transposition))) } ?: setlist`, which
   writes the setlist unchanged and sends no message, exactly as today's `map` over entries does (its
   `?: sendMessage(OperationFailed)` only fires when `updateEditableSetlist` itself returns null). `reorderSetlist`
   rewrites every entry and is left alone. One commit.

3. **`PendingOverrides<T>`** (`ui/playing/PendingOverrides.kt`), the pending/debounce/settle half, generic in the
   value, taking `scope`, `delayMillis` (today `FONT_SCALE_SAVE_DELAY_MILLIS`, 500; `PREFERENCE_SAVE_DELAY_MILLIS` if
   plan 06 landed first), `stored: StateFlow<SongOverrides<T>>`, `write: suspend (SongPlace, T?) -> Boolean` (true =
   written and expected to round-trip; false = drop the pending value and call `onFailed`), `onFailed: () -> Unit`
   (the view model passes `{ sendMessage(Message.OperationFailed) }`) and `name: String` (`"tempo"`, `"capo"`, for
   today's `println("The $name could not be written: …")`). The `try`/`catch` around the write (rethrowing
   `CancellationException`, logging, dropping and calling `onFailed` on any other exception) moves into
   `PendingOverrides`, so the `write` lambda is only the store update. It owns what `pendingTempos` + `tempoWriteJobs` + the settle
   collector + `PendingTempo(bpm, isWritten)` own today and exposes:
   - `val effective: StateFlow<SongOverrides<T>>` (stored overlaid with pending, today's `tempos` / `capos`);
   - `fun set(place, value: T?)` (today's body of `changeTempo` after it computed `override`: pend, cancel the old job,
     start a delayed write);
   - `fun reset(place)` (today's `resetTempo`: cancel, pend null, write at once);
   - `fun start()` launching the settle collector;
   - `fun takeWaiting(): suspend () -> Unit` (today's `takeWaitingOverrideWrites` for one store).
   The view model's `changeTempo` keeps computing the clamped value and the "equal to the song's own is no override"
   rule (that part differs: `MetronomePattern.coerceBpm` vs `coerceIn(Song.CAPO_RANGE)`), then calls `pendingTempos.set`.
   `writeTempo`/`writeCapo` collapse into one `writeOverride(place, value, field: (Setlist.Entry, T?) -> Setlist.Entry, library: (UserPreferences, Map<String, T>) -> UserPreferences)`
   or two small lambdas passed at construction; the exception handling and the `println` stay (with the field name in
   the message). `takeWaitingOverrideWrites` becomes `val a = tempoOverrides.takeWaiting(); val b = capoOverrides.takeWaiting(); return { a(); b() }`
   — keep the order (tempos first), since `settleSynchronizationBeforeExit` and `onCleared` run it.
   Start the two settle collectors from `init` at the same position they are launched today (tempo first, then capo).
   `takeWaiting`'s returned lambda calls `write` directly (no `scope.launch`), since `onCleared` runs it on a detached
   scope after `viewModelScope` is cancelled. One commit.

Transposition stays relative and un-debounced (`changeTransposition` applies `change` to the stored value inside the
write, on purpose — see its KDoc), so it uses `SongOverrides` and `withEntry` but not `PendingOverrides`.

## Tests

- New `SongOverridesTest`: `of` reads library vs setlist and never mixes them; `with(place, null)` removes; `with` on a
  setlist place leaves other setlists alone; `get` on an unknown setlist is null.
- New `PendingOverridesTest` (`runTest` + `TestScope`, virtual time): a set is visible in `effective` at once and
  written once after the delay; two sets within the delay write once with the second value; `reset` cancels a waiting
  write and writes null at once; a pending value is dropped only after the store reports it (`isWritten` and stored
  equals pending); a failing write drops the pending value and reports; `takeWaiting` returns exactly the values whose
  delay had not ended and cancels their jobs.
- Existing: `SongTempoTest`, `SongCapoTest`, `MetronomePatternsTest`, `MetronomeContextTest`, `SetlistSongToggleTest`.
- Consistency with plans 29/30: nothing here changes which `UserPreferences` maps are per song (`transpositions`,
  `tempos`, `capos` are read and written by exact file name, as today); plan 30's `withSongRenamed`/`withoutSongOverrides`
  keep listing them in the data layer. A future per-song override added through `SongOverrides` must be added there too.

## Manual check

On a phone, in a setlist: hold the tempo stepper's + for two seconds (one setlist write after release, value never
steps back), tap the tempo's value to reset, step the capo, leave the screen within half a second and reopen (value
kept), do the same from the library (preferences); with a second device synced, remove the song from the setlist on
the other device while the stepper is held on this one (snackbar "operation failed", stepper returns to the file's
value). On the desktop, step a capo and quit with Cmd+Q within half a second, relaunch (kept).
