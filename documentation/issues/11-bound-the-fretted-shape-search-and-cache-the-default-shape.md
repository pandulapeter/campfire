# Bound the fretted shape search: no search for a chord no hand can hold, a pruned enumeration, and a cached default shape

**Kind:** performance  ·  **Severity:** medium (hostile songs) / low (ordinary ones)  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicings.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordVoicingsTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordVoicings.default` falls back to the full search for every chord the tables do not have:

```kotlin
/**
 * The shape [chord] is shown with on [instrument], or null where there is none. A table lookup wherever the tables
 * have the chord, so that a page never waits for the search.
 */
fun default(chord: Chord, instrument: ChordInstrument): ChordVoicing? = when (instrument) {
    ChordInstrument.KEYBOARD -> keyboard(chord).firstOrNull()
    else -> tableShapes(chord, instrument).firstOrNull() ?: search(chord, instrument).firstOrNull()
}
```

`search` enumerates every combination of up to six options per string at each of 12 positions (`enumerate`, a full
Cartesian product: about 560 000 visits on the guitar) and only filters afterwards. Three costs follow:

1. **A chord with more required notes than strings can never match, yet is searched in full.** In `search`,
   `val required = pitchClasses - omissions(played, tuning.size)` can still be larger than `tuning.size` (each string
   sounds one pitch class). Probe at dac1d9d59: `C(b9,9,#9,11,#11,b13,13,#13,maj7)` (12 pitch classes) took 293 ms on
   the guitar to answer null.
2. **The enumeration visits every non-contiguous combination**, although the first check after the string count
   throws them all away: `if (sounding.last() - sounding.first() + 1 != sounding.size) return@enumerate`.
3. **Nothing is remembered.** `songChordsOf` (presentation `ui/chords/SongChords.kt`, `defaultShape =
   if (definition == null) ChordVoicings.default(sounding, instrument) else null`) runs for every song page, its
   pager neighbours, every notation or instrument change, the PDF (`PrintLayout.kt`'s `printChordsOf`), and
   `ChordShapeInsertion.kt`. The live desktop review measured 491–604 ms for a 48-chord hostile song on the guitar
   (first composition of the page, see the cross-lane note), and 5–43 ms for ordinary songs, repeated for the same
   chords on every page.

The KDoc's "so that a page never waits for the search" is false for every chord outside the tables.

## Fix

All in `ChordVoicings.kt`:

1. In `search`, right after `required`:
   ```kotlin
   // Every string sounds one note, so a chord that needs more notes than there are strings has no shape at all.
   if (required.size > tuning.size) return emptyList()
   ```
2. Prune `enumerate` by the contiguity rule, so a muted string after a sounding one ends the run (keep the check in
   `search`, it is now always true but documents the rule):
   ```kotlin
   private fun enumerate(options: List<List<Int?>>, visit: (List<Int?>) -> Unit) {
       val current = arrayOfNulls<Int>(options.size)
       // 0: no string sounds yet, 1: strings sound, 2: a muted string ended them, so every string after it is muted.
       fun step(string: Int, run: Int) {
           if (string == options.size) {
               visit(current.toList())
               return
           }
           options[string].forEach { fret ->
               val next = when {
                   fret != null && run == 2 -> return@forEach
                   fret != null -> 1
                   run == 1 -> 2
                   else -> run
               }
               current[string] = fret
               step(string + 1, next)
           }
       }
       step(0, 0)
   }
   ```
   (Verified in a scratch worktree: with 1 and 2 applied `ChordVoicingsTest` and `ChordVoicingTablesTest` pass
   unchanged, and the 12-note chord answers in about 11 ms cold, most of it the tables' lazy read.)
3. Memoize `default` in a small bounded cache that is safe when `songChordsOf` runs on `Dispatchers.Default` (the
   Chord shapes sheet, the lyrics' preparation) and on the main thread at once. A plain `HashMap`/`LinkedHashMap` is
   **not** safe there on the JVM, Android or Kotlin/Native (concurrent `put` can corrupt it). Use a copy-on-write
   immutable map behind `kotlin.concurrent.Volatile`, which common code already uses (`SyncRepositoryImpl`,
   `SyncedPreferencesSync`) and which is a no-op on single-threaded wasm; a race only loses an entry, never corrupts:
   ```kotlin
   /** The defaults worked out so far, replaced whole rather than changed, so threads reading it never see it half written. */
   @Volatile
   private var defaults: Map<Pair<Chord, ChordInstrument>, ChordVoicing?> = emptyMap()

   fun default(chord: Chord, instrument: ChordInstrument): ChordVoicing? {
       val key = chord to instrument
       val cached = defaults
       if (key in cached) return cached[key]
       val shape = when (instrument) {
           ChordInstrument.KEYBOARD -> keyboard(chord).firstOrNull()
           else -> tableShapes(chord, instrument).firstOrNull() ?: search(chord, instrument).firstOrNull()
       }
       // A song plays a few dozen chords; starting over when the cache is full keeps it bounded without bookkeeping.
       defaults = (if (cached.size >= MAX_CACHED_DEFAULTS) emptyMap() else cached) + (key to shape)
       return shape
   }
   private const val MAX_CACHED_DEFAULTS = 256
   ```
   `Chord` is a data class of an `Int`, a `List<Int>` and an `Int?`, so it is a sound key. `all` is not cached (the
   sheet runs it off the main thread, once per open cell).
4. Reword the KDoc of `default`: "A table lookup wherever the tables have the chord, and the search otherwise, whose
   answer is remembered for the rest of the session: the first page that names an unusual chord pays for it once."
   Reword the `ChordVoicings` paragraph of `chordpro/CLAUDE.md` ("`default` is a table lookup wherever the tables have
   the chord; `all` runs the search.") the same way, and add that a chord needing more notes than strings has no
   shape without a search.

## Tests

In `ChordVoicingsTest`:
- `a chord with more notes than strings has no shape`: `ChordVoicings.all(parse("C(b9,9,#9,11,#11,b13,13,#13,maj7)"),
  GUITAR)` and `UKULELE` are empty and `default` null (the existing `a chord with no shape answers no shape rather than a
  wrong one` covers the ukulele's crowded case; extend it).
- `the default is the first of all the shapes` already compares `default` with `all().firstOrNull()` for a sweep:
  call `default` twice in it so the cached answer is compared too.
- The existing `every shape the search finds keeps its rules` and the table tests guard the pruning.

## Manual check

On the desktop, open a song holding 48 unusual chords (e.g. `[C7(b9,#9,#11,b13)]`, `[Cmaj9#11]`, … over the guitar)
and swipe to it and back: the page appears without a visible hitch the second time, and switching the notation
back and forth no longer stalls.

Cross-lane: the first composition of a song page still builds its model on the main thread
(`SongLyrics.kt`'s `rememberSongLyricsModel`, `remember { mutableStateOf(inputs to prepare(inputs)) }`, which calls
`songChordsOf`), which belongs to lane D; this plan only makes that call cheap.
