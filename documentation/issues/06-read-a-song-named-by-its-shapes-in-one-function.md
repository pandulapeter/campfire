# Read a song whose chords are named by the shapes above a capo in one function, which the page, the PDF, and the chord shape sheet all call

**Challenged:** amended — snippets re-checked at `0e28af38f` (all still match). `SongReading` now carries its
`ShapeNames`, so `shownSongOf` cannot be handed a different one; the page takes the `SongRenderer` (no other caller of
`SongDetailsPage` exists, so nothing in `tools/screenshots` or the tests passes the lambdas); the PDF must compute
`inferred` and `shapeNames` before the reading (today `transposed` is read first); the sheet passes the page's
`tabCapo` so its two reads equal the page's, and the "pure extraction" claim now says what does differ (tab frets,
never a name); the tests compare chords by pitch and derive the key line relationally; ordering with plan 01 noted.

**Kind:** architecture / duplication  ·  **Severity:** high  ·  **Platforms:** all
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/ShapeNames.kt` (new functions beside `shapeNamesOf`; or a new `playing/SongReading.kt` if `ShapeNames.kt` would pass the size check)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPage.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPages.kt` (passes the page the `SongRenderer` in place of its two renderer lambdas)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/instrument/ChordShapeSheet.kt`
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongReadingTest.kt` (new; uses `rendering/TestSongRenderer.kt`'s `testSongRenderer()`)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/CLAUDE.md` (the "reads the song twice" sentence names the function)

## Problem

Under Preferences' Chord names = shapes and a capo, a song is read twice: once as it sounds (scales, degrees,
diagrams) and once moved down by the name capo (the names on the page). That sequence, and the "named inferred key",
and the "key that sounds fills the key line" step, are written out three times, and the three must agree exactly.

The song details page, `SongDetailsPage.kt` (inside `rememberSongLyricsModel`'s builder):

```kotlin
val transposed = transposeSong(inputs.text, inputs.transposition, inputs.tabCapo, inputs.spelling, inputs.tabs)
// Where the page names the shapes, it is read a second time, moved down by the capo with its tabs' frets left
// where they are: moving the song that sounds instead would apply its own key changes twice.
val shapes = inputs.shapeNames
val named = shapes?.let { transposeSong(inputs.text, inputs.transposition - it.capo, inputs.tabCapo - it.capo, inputs.spelling, inputs.tabs) } ?: transposed
val namedInferredKey = if (shapes == null) inputs.inferredChordsKey else shapes.inferredChordsKey
prepareSongLyrics(
    song = notateSong(named.withTempo(inputs.tempoOverride).withCapo(inputs.capoOverride), inputs.spelling, namedInferredKey).let { song ->
        // The song is played in the key that sounds, whatever its chords are named by.
        val key = shapes?.soundingKey ?: inputs.inferredKey?.takeIf { song.metadata.key.isNullOrBlank() }
        key?.let { song.copy(metadata = song.metadata.copy(key = it)) } ?: song
    },
    standardSong = transposed,
    namedSong = named,
    nameCapo = shapes?.capo ?: 0,
    namedInferredKey = namedInferredKey,
    ...
```

The PDF, `ExportController.kt` (per entry of `printSongs`), the same steps and the same comment:

```kotlin
val transposed = content?.let { withContext(Dispatchers.Default) { songRenderer.transposedSong(it.text, transposition, spelling, tabs = tabs, capo = tabCapo) } }
...
val named = shapeNames?.let { names ->
    content?.let { withContext(Dispatchers.Default) { songRenderer.transposedSong(it.text, transposition - names.capo, spelling, tabs = tabs, capo = tabCapo - names.capo) } }
} ?: transposed
val printed = named?.withTempo(tempo)?.withCapo(capo, isShown = playback.isCapoShownFor(preferences))
...
val namedInferredKey = if (shapeNames == null) inferredChordsKey else shapeNames.inferredChordsKey
val rendered = printed?.let { withContext(Dispatchers.Default) { songRenderer.notatedSong(it, spelling, namedInferredKey) } }?.let { rendered ->
    // The song is played in the key that sounds, whatever its chords are named by.
    val key = shapeNames?.soundingKey ?: inferredKey?.takeIf { rendered.metadata.key.isNullOrBlank() }
    key?.let { rendered.copy(metadata = rendered.metadata.copy(key = it)) } ?: rendered
}
```

The chord shape sheet, `ChordShapeSheet.kt`, which looks the tapped chord up by the page's name and dismisses itself
when none carries it (`if (chords != null && chord == null) return LaunchedEffect(Unit) { dialogHost.dismissSheet(dialog) }`),
so a drift between it and the page closes the sheet on a tap:

```kotlin
val sounding = songChordsOf(songRenderer.transposedSong(it, transposition, spelling), notation, tuning, capo = capo, inferredKey = inferredKey)
shapeNames?.let { names ->
    val named = songRenderer.transposedSong(it, transposition - names.capo, spelling)
    sounding.namedAsShapes(named, notation, names.capo, names.inferredChordsKey)
} ?: sounding
```

The 30th review ruled that the PDF follows the screen's rules. Today that holds only because three copies were edited
together; a fix to one (the "moving the song that sounds would apply its key changes twice" bug that the second read
exists for is the example) has to be found and repeated in the others.

**The capo-shown difference is two equivalent forms, not a behavioural difference.** The PDF clears the capo from the
song's metadata (`withCapo(capo, isShown = false)` → `metadata.capo = null`, `playing/SongCapo.kt`); the screen keeps
it in the song and passes `isCapoShown` to `prepareSongLyrics`, whose line of values drops it
(`songView/model/SongMetadata.kt`: `!shouldShowChords || !isCapoShown -> null`). Both leave the capo out of the
printed or shown values line where `SongPlayback.isCapoShownFor` says so. Keep both as they are: the shared function
applies `withCapo(capoOverride)` only, and the PDF hides the capo itself afterwards (below).

The other inputs that differ are deliberate and stay parameters: the screen fills a blank key with the inferred key only
in read only mode (`SongPages.kt`: `inferredKeys[song.fileName]?.takeIf { isReadOnly && song.key.isNullOrBlank() }`),
the PDF always; the sheet reads no tabs (its names come from the lyrics' chords; a tab of another instrument left in
can only add names, never remove the one tapped).

## Fix

Add one pure reading, as `SongRenderer` extensions beside `shapeNamesOf` in `playing/ShapeNames.kt` (the module already
keeps shape-naming there, `SongRenderer` stays the parse-and-transpose primitive):

```kotlin
/** A song read for a reader naming its chords as [shapeNames] says: [transposed] sounds, [named] is what the page names. */
@Immutable
internal data class SongReading(
    val transposed: ChordProSong,   // what sounds, standard notation: scales, degrees, diagrams
    val named: ChordProSong,        // the same instance as transposed where shapeNames is null
    val shapeNames: ShapeNames?,    // the one passed in, so shownSongOf reads the sounding key from the same value
    val namedInferredKey: String?,  // shapeNames?.inferredChordsKey where shapeNames is set, else inferredChordsKey
) {
    val nameCapo: Int get() = shapeNames?.capo ?: 0
}

internal fun SongRenderer.songReadingOf(
    text: String, transposition: Int, tabCapo: Int, spelling: UserPreferences.ChordSpelling, tabs: TabReading?,
    shapeNames: ShapeNames?, inferredChordsKey: String?,
): SongReading

/** [SongReading.named] as the page and the PDF show it: tempo and capo put on, notated, its key the one that sounds. */
internal fun SongRenderer.shownSongOf(
    reading: SongReading, spelling: UserPreferences.ChordSpelling, tempo: Int?, capo: Int?, inferredKey: String?,
): ChordProSong
```

`songReadingOf` is today's two reads verbatim: `transposedSong(text, transposition, spelling, tabs = tabs, capo = tabCapo)`
and, where `shapeNames != null`, `transposedSong(text, transposition - shapeNames.capo, spelling, tabs = tabs,
capo = tabCapo - shapeNames.capo)`. `shownSongOf` is `notatedSong(named.withTempo(tempo).withCapo(capo), spelling,
namedInferredKey)` followed by the key fill (`shapeNames?.soundingKey ?: inferredKey?.takeIf { key is blank }`).
`ChordProNotation.toNotation` never reads `metadata.capo`, so the PDF hiding the capo after notation rather than
before (below) changes nothing. The editor's preview (`songEditor/SongPreview.kt`, `renderSong`, the file as written,
no shape names) is not one of the callers and stays as it is.

Move the two code comments quoted above into these functions' KDoc. No defaulted parameters (every caller passes
every value, so a forgotten one is a compile error rather than a silent drift).

Callers:

- **Page**: replace the `transposeSong` / `notateSong` lambda parameters of `SongDetailsPage` with
  `songRenderer: SongRenderer` (`SongPages.kt` already holds it, the lambdas exist only to forward to it, and
  `SongPages.kt:188` is the page's only caller: no test or `tools/screenshots` code passes the lambdas; a dozen other
  composables already take the renderer, compared by instance under strong skipping). The builder calls
  `songReadingOf(inputs.text, inputs.transposition, inputs.tabCapo, inputs.spelling, inputs.tabs, inputs.shapeNames,
  inputs.inferredChordsKey)` + `shownSongOf(reading, inputs.spelling, inputs.tempoOverride, inputs.capoOverride,
  inputs.inferredKey)` and passes `reading.transposed`, `reading.named`, `reading.nameCapo`, `reading.namedInferredKey`,
  `reading.shapeNames?.shapesKey` to `prepareSongLyrics` and `notationChordsOf(inputs, transpose =
  reading.named.metadata.transpose, nameCapo = reading.nameCapo)`. Update the KDoc that names `[transposeSong]`
  (`@param inferredChordsKey`) to name `SongRenderer.transposedSong`.
- **PDF**: reorder `printSongs`' per-entry block so `inferred` (`inferredKeyOfText`, which reads the text, not
  `transposed`), `shapeNames`, `inferredKey`, and `inferredChordsKey` are computed first; then, inside one
  `withContext(Dispatchers.Default)`, `content?.let { songReadingOf(it.text, transposition, tabCapo, spelling, tabs,
  shapeNames, inferredChordsKey) }` and `reading?.let { shownSongOf(it, spelling, tempo, capo, inferredKey)
  .withCapo(null, isShown = playback.isCapoShownFor(preferences)) }` (`withCapo(null, isShown = true)` is the identity,
  so this is exactly today's `rendered`). `transposed` / `named ?: transposed` become `reading.transposed` /
  `reading.named` (both null with no content, as today); `printChordsOf`, `printScaleGroupsOf`, `shapesKey`,
  `areChordsSounding`, and `NotationChordSpelling` read `reading.nameCapo`, `reading.namedInferredKey`, and
  `reading.shapeNames` where they read `shapeNames` today.
- **Chord shape sheet**: `songReadingOf(it, transposition, tabCapo = playback.tabCapoFor(userPreferences), spelling,
  tabs = null, shapeNames, inferredChordsKey = inferredKey)` (the sheet's `inferredKey` is already the moved one), then
  `songChordsOf(reading.transposed, notation, tuning, capo = capo, inferredKey = inferredKey)` and, where
  `reading.shapeNames != null`, `.namedAsShapes(reading.named, notation, reading.nameCapo, reading.namedInferredKey)`.
  It never calls `shownSongOf`. Add `shapeNames`' dependency to the `produceState` keys as today.

Behaviour must not change anywhere. The one input that differs from today is the sheet's tab capo (today both its reads
pass none): it only moves tab frets (`ChordProTransposer.rewriteBy`'s `fretShift`), never a chord name, which the
sheet alone reads, so what it offers and when it dismisses are unchanged. Passing the page's value rather than 0 keeps
the sheet's reads equal to the page's apart from `tabs = null`.

## Tests

New `playing/SongReadingTest.kt` (commonTest, `testSongRenderer()`):

- A capo-2 song (`{capo: 2}`, chords `Em C G D`, a mid-song `{key: Am}` and a mid-song `{transpose: 2}`) read with
  `transposition = 2` (the file's capo counted in, as `soundingTransposition` gives) and a `ShapeNames(capo = 2, ...)`:
  `transposed`'s first chords are `F#m D A E` (sounding), `named`'s the written shapes `Em C G D`, and the mid-song key
  change and `{transpose}` are applied once in each: every chord of `named` is the matching chord of `transposed`
  moved down by exactly 2 semitones, compared by pitch class (the two reads may spell a chord differently, since each
  picks flats by its own key) — the bug the second read exists for moved them twice. The key of every mid-song
  `{key}` block of `named` is the one of `transposed` moved down by 2; derive it rather than hard-coding a name, since
  `SongKeys.withKeyOfChords` reads a capo chart's key.
- No `ShapeNames`: `named` is the same instance as `transposed`, `nameCapo == 0`, `namedInferredKey ==` the passed
  `inferredChordsKey`.
- `shownSongOf` with a reading whose `ShapeNames(soundingKey = "F#m")` sets the key to `F#m`; without shape names and
  a song naming no key, the passed `inferredKey` fills it; a song naming its key keeps it.
- The sheet's names equal the page's: for the capo-2 song, `songChordsOf(reading.transposed, ...).namedAsShapes(...)`
  names exactly the chord names `prepareSongLyrics(...)`'s Chords section carries for the same reading (call both with
  the same `SongReading`; assert the lists of names are equal).

Run `./gradlew :presentation:desktopTest` and the existing `ExportControllerTest` / `PdfRoundTripTest`, which must
pass unchanged.

**Ordering with other work**: plan 01 edits `ExportController.kt` too (its three `isSharedCopy = false` arguments,
outside `printSongs`) and `ExportControllerTest`'s fakes; the two touch different lines and merge in either order.
