# 57 · Every chorded line measures the same chord names, the same "X" and the same space again, with a text measurer of its own

**Severity:** performance (all platforms; every song with chords, on opening it, on each neighbour page of a setlist, and on every transposition tap) · **Area:** `:presentation` (`SongLyrics.kt`: `SongLyrics`, `SongSectionContent`, `SongLineWithChords`, `SongTabBlock`, `padLyricsToFitChords`)

## Symptom
Open a long song with chords on a slow phone (or in the web build): the page takes a few hundred milliseconds of main
thread time before it shows, the swipe to the next song of a setlist hitches when the neighbour page is composed, and
every tap on the transposition stepper stalls the same way although nothing but the chord names changed. Memory
follows: every chorded line of the three composed pages keeps a text measurer and the last eight layouts it made.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt:1115-1131`:

```kotlin
val textMeasurer = rememberTextMeasurer()                                   // one per line, an 8 entry LRU each
…
val chordLayouts = remember(line, chordStyle, annotationStyle, textMeasurer) {
    line.chords.map { textMeasurer.measure(AnnotatedString(it.name), if (it.isAnnotation) annotationStyle else chordStyle) }
}
val paddedLine = remember(line, lyricsStyle, chordLayouts, textMeasurer, density) {
    line.padLyricsToFitChords(…, measureWidth = { textMeasurer.measure(AnnotatedString(it), lyricsStyle).size.width.toFloat() })
}
val lyricsLineHeight = remember(lyricsStyle, textMeasurer) {
    textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle).size.height
}
```

and `padLyricsToFitChords` (`:1185`) starts with `val paddingWidth = measureWidth(PADDING.toString())`. A line with k
chords therefore runs 2k + 2 text layouts: k chord names, k lyric fragments, the non-breaking space and `"X"`. Nothing
is shared, because the measurer — and with it the only cache — belongs to the line: a song of 300 chorded lines lays
out `"X"` 300 times, the space 300 times and its handful of chord names (`G`, `C`, `D`, `Em`) about 1 200 times, some
1 800 of roughly 3 000 layouts being results it already had. A line that occurs again (a repeated verse, every
`{chorus}` recall) is measured from scratch as well.

A transposition makes every `ChordProLine.Lyrics` a new object (`SongDetailsScreen.kt:502` renders the song again), so
both `remember(line, …)` blocks run again and the k fragments are laid out once more, although the lyrics did not
change by a character. `SongTabBlock` (`:522`) has the same per-block `rememberTextMeasurer()`; its `TabRows` keeps
every layout it makes itself, so the measurer's own cache holds each of them a second time.

What the `TextMeasurer` cache is keyed on matters for the fix (Compose 1.12.0, `CacheTextLayoutInput`): the text, the
layout affecting attributes of the style (so the font size, and through it the app's `fontScale`), the density (so
Android's system font scale), the layout direction, the font family resolver and the constraints. Colour is not part
of it, and since `a7780a35` the styles here carry none.

## Fix
All in `SongLyrics.kt`. One text measurer per `SongLyrics` — that is per pager page, and one for the editor's
preview — and one holder next to it that keeps what the chorded lines have had measured. The holder, not the
measurer's LRU, is the cache: an LRU of any reasonable size is churned by the hundreds of distinct lyric fragments
passing through it, keeps whole `TextLayoutResult`s where only a width is needed, and cannot give a repeated line its
widths back by their text. This mirrors `SectionMeasurements` and `TabRows` in the same file.

1. Add the holder (next to `TabRows` is a good place):

   ```kotlin
   /**
    * What the chorded lines of one page have had measured, shared between them because they keep asking for the
    * same things: a song has a handful of chord names and hundreds of lines carrying them, a repeated verse or a
    * recalled chorus is the same fragments again, and the height of a line and the width of the padding are the
    * same for all of them.
    *
    * It lives for as long as the measurer and the three styles do - which is everything a width depends on: the
    * styles carry the text size, the measurer is rebuilt with the density, the layout direction and the font
    * resolver - and deliberately not for as long as the song does. A transposition renames the chords and leaves
    * the lyrics alone, so the fragment widths it measured before are the ones it needs after.
    *
    * None of this is state: it is filled in by whoever asks first, and the answers never change.
    */
   private class SongTextMeasurements(
       val textMeasurer: TextMeasurer,
       private val lyricsStyle: TextStyle,
       private val chordStyle: TextStyle,
       private val annotationStyle: TextStyle,
   ) {

       /** The height of one line of lyrics, which a chorded line is taller than by the height of its chords. */
       val lyricsLineHeight by lazy(LazyThreadSafetyMode.NONE) {
           textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle).size.height
       }

       /** The width of one [PADDING] character, which the lyrics under a chord wider than they are get filled up with. */
       val paddingWidth by lazy(LazyThreadSafetyMode.NONE) { measureFragment(PADDING.toString()) }

       private val chordLayouts = HashMap<String, TextLayoutResult>()
       private val annotationLayouts = HashMap<String, TextLayoutResult>()
       private val fragmentWidths = HashMap<String, Float>()

       /** The laid out name of [chord], which is drawn as it is: the colour is given where it is drawn. */
       fun chordLayout(chord: ChordProLine.Lyrics.Chord) = if (chord.isAnnotation) {
           annotationLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), annotationStyle) }
       } else {
           chordLayouts.bounded().getOrPut(chord.name) { textMeasurer.measure(AnnotatedString(chord.name), chordStyle) }
       }

       /** The width of a piece of lyrics. Only the number is kept, since nothing is ever drawn from this layout. */
       fun fragmentWidth(fragment: String) = fragmentWidths.bounded().getOrPut(fragment) { measureFragment(fragment) }

       private fun measureFragment(fragment: String) = textMeasurer.measure(AnnotatedString(fragment), lyricsStyle).size.width.toFloat()

       // The editor's preview is one page for as long as the editor is open and sees every fragment that is ever
       // typed, so a map that only grew would grow for the whole session. Starting over costs one measurement of
       // whatever is still on screen, which is what opening the page cost.
       private fun <T> HashMap<String, T>.bounded() = also { if (size >= MAX_MEASURED_TEXTS) clear() }
   }
   ```

   and the constant next to `MAX_TAB_WIDTHS`: `private const val MAX_MEASURED_TEXTS = 4096`.

2. In `SongLyrics`, after the four styles are built (`:152-156`), create both:

   ```kotlin
   // One measurer for the whole page. Everything measured through it is kept by whoever asked for it (see
   // [SongTextMeasurements] and [TabRows]), so a cache of its own would only hold every layout a second time.
   val textMeasurer = rememberTextMeasurer(cacheSize = 0)
   val textMeasurements = remember(textMeasurer, lyricsStyle, chordStyle, annotationStyle) {
       SongTextMeasurements(
           textMeasurer = textMeasurer,
           lyricsStyle = lyricsStyle,
           chordStyle = chordStyle,
           annotationStyle = annotationStyle,
       )
   }
   ```

   `TextStyle` has value equality, so the styles built anew on every recomposition do not rebuild the holder; a
   pinch, which changes `fontScale` on every frame, does, exactly as it invalidates every line's `remember` today.
   `rememberTextMeasurer` is itself keyed on `LocalDensity`, `LocalLayoutDirection` and `LocalFontFamilyResolver`.

3. `SongSectionContent`: replace the `annotationStyle: TextStyle` parameter with
   `textMeasurements: SongTextMeasurements` (both call sites in `SongLyrics`, `:207-215` and `:220-230`, pass
   `textMeasurements = textMeasurements` instead of `annotationStyle = annotationStyle`). `chordStyle` stays, since
   `SongGridLine` uses it. Inside:
   - `SongTabBlock(modifier = Modifier.fillMaxWidth(), lines = lines, style = style, textMeasurer = textMeasurements.textMeasurer)`;
   - `SongLineWithChords(line = line, lyricsStyle = lyricsStyle, textMeasurements = textMeasurements)`.

4. `SongTabBlock` takes `textMeasurer: TextMeasurer` as its last parameter and loses its own
   `val textMeasurer = rememberTextMeasurer()`; `remember(lines, style, textMeasurer) { TabRows(…) }` stays as it is.

5. `SongLineWithChords` becomes:

   ```kotlin
   @Composable
   private fun SongLineWithChords(
       line: ChordProLine.Lyrics,
       lyricsStyle: TextStyle,
       textMeasurements: SongTextMeasurements,
   ) {
       val density = LocalDensity.current
       val chordColor = MaterialTheme.colorScheme.primary
       val annotationColor = MaterialTheme.colorScheme.onSurfaceVariant
       val chordLayouts = remember(line, textMeasurements) { line.chords.map(textMeasurements::chordLayout) }
       val paddedLine = remember(line, textMeasurements, density) {
           line.padLyricsToFitChords(
               chordWidths = chordLayouts.map { it.size.width.toFloat() },
               gap = with(density) { CHORD_GAP.toPx() },
               paddingWidth = textMeasurements.paddingWidth,
               measureWidth = textMeasurements::fragmentWidth,
           )
       }
       val chordLineHeight = chordLayouts.maxOf { it.size.height }
       // Lines without any lyrics (e.g. an intro) only need to be as tall as the chords themselves.
       val lineHeight = with(density) { (if (line.text.isBlank()) chordLineHeight else chordLineHeight + textMeasurements.lyricsLineHeight).toSp() }
       …                                                  // the rest of the function is unchanged
   ```

   `chordLayouts` is a function of `(line, textMeasurements)`, so it needs no place among `paddedLine`'s keys, and
   `lyricsStyle` is covered by the holder's identity. The `drawBehind` block and the `Text` stay exactly as they are:
   one `TextLayoutResult` drawn by several lines is fine, `drawText` is given the colour and the position per call.

6. `padLyricsToFitChords` takes the padding width instead of measuring it:

   ```kotlin
   private fun ChordProLine.Lyrics.padLyricsToFitChords(
       chordWidths: List<Float>,
       gap: Float,
       paddingWidth: Float,
       measureWidth: (String) -> Float,
   ): ChordProLine.Lyrics {
       val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
       …
   ```

   (delete its first line, `val paddingWidth = measureWidth(PADDING.toString())`, and extend the KDoc's first
   sentence: "…by appending non-breaking spaces, each [paddingWidth] wide, to it.").

What a transposition tap costs afterwards: one layout per chord name the page has not drawn yet (a handful, and none
at all when stepping back to a key already visited), and per line k map lookups, the arithmetic and a `StringBuilder`.
The `Text` of a line lays itself out again only where the padded string really changed, i.e. where a chord got wider
or narrower than the lyrics under it (`G` to `G#`); that is the part that is genuinely new.

What must not change:
- Do not key the holder on `song` or `sections`: surviving a transposition is the point.
- Do not give the styles a colour again, and do not move the colours into the holder; the keys must stay free of
  anything a theme cross-fade changes (`a7780a35`).
- Do not hoist the measurer above `SongLyrics` (one for the whole pager): `SongLyrics` is also the editor's preview,
  and the per-page lifetime is what lets a page that leaves the pager give its layouts back.
- Do not make the maps snapshot state; they are written during composition and nothing has to be redrawn for them.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run`. Open the demo songs and a long song with chords, annotations (`[*N.C.]`), a line
   that is only chords, a line whose chord is wider than the syllable under it, a tab block and a `{chorus}` recall.
   Compare against a screenshot taken before the change at the same window size: the pages must be identical to the
   pixel (chord positions, padding, line heights, tab rows).
2. Transpose up and down through twelve steps: chords follow, no chord overlaps its neighbour, lines whose chord got
   wider re-wrap as before. Change the text size with the stepper and with Ctrl + scroll: everything scales.
   On Android change the system font size in the device settings while the song is open: the page follows.
3. Switch the theme while a song is open: chord colours cross-fade, nothing is re-measured (the holder is not rebuilt;
   a breakpoint in `SongTextMeasurements`'s constructor shows it).
4. Editor, Split view, type a chord letter by letter over a long song: the preview follows on every key.
5. Optional, to see the saving: count the calls of `textMeasurer.measure` with a temporary counter before and after on
   one long song (expect roughly `2k + 2` per line before, and `distinct fragments + distinct chord names + 2` per
   page after), and on a transposition tap (expect only the new chord names). Remove the counter.
6. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
`presentation/CLAUDE.md`, `screens/songDetails/SongLyrics.kt` bullet: add one sentence after the one about
`SongTabBlock` drawing its text — "Every text the page measures by hand goes through one `TextMeasurer` per page, and
what the chorded lines ask of it (the chord names, the width of each piece of lyrics, the height of a line) is kept in
`SongTextMeasurements` for the page rather than per line, keyed on the styles and the measurer and not on the song, so
that a transposition only lays out the chord names it has not drawn yet."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Shares `SongLyrics.kt` with plans 54, 56 and 61 (different functions: 54 touches the `sectionModifier` line
in `SongLyrics` and `SongTabBlock`'s neighbourhood, 56 `gridFor`, 61 `flowIntoRows` / `SectionMeasurements`), so the
four have to be scheduled one after another.
