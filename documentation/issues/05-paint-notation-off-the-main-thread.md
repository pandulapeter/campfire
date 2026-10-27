<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Paint each tune's staff systems off the main thread, once per rendering, and keep them with the rendering

**Kind:** performance (and a flicker) · **Severity:** medium · **Platforms:** all (the web gains only the move out of composition)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`,
`.../ui/notation/NotationPainter.kt`, `.../ui/songView/SongLyrics.kt`, `.../ui/songView/SongNotationBlock.kt` (a comment),
`.../ui/print/PrintRenderer.kt` (only if the cache key changes, see Fix 5), `.../ui/notation/CLAUDE.md`, new
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPaintingTest.kt`

(`...` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.)

## Problem

`ui/notation/CLAUDE.md` says "`rememberSongNotations` renders a song's `abc` runs off the main thread", but only the
engraving and the XML parse are: the painting, which is the costly half of what the screen needs, runs during
composition. `SongNotations.of`, called by `SectionRun` (`ui/songView/SongSectionContent.kt`,
`val notationRun = if (kind == FoldableKind.VERBATIM) notations.of(run) else null`) for every notation run on every
composition of its section, paints on a cache miss:

```kotlin
// ui/notation/SongNotations.kt
fun of(run: List<ChordProLine>): NotationRun? {
    val tune = run.abcTune() ?: return null
    val rendering = rendered.renderings[tune] ?: return null
    val painter = painter ?: return null
    return NotationRun(
        systems = painter.paintOf(rendering.drawing, rendering.unitSize),
        ...
```

and `NotationPainter.paint` parses every SVG path (`pathParser.parsePathString(...)`) and runs `textMeasurer.measure`
for every chord symbol, syllable, and annotation of every system. Every new rendering misses (arrival, each
transposition step, each settled resize or pinch), so a tune of a dozen systems with lyrics lays out hundreds of texts
on the main thread inside one composition, a dropped frame or several on Android at exactly the moment the song is
moving (a section glide starts in the same frame).

The cache itself makes it worse for a song with many tunes (a fiddle-tune set, a hymnal page):

```kotlin
// ui/notation/NotationPainter.kt
fun paintOf(drawing: NotationDrawing, unitSize: Float) = paints.getOrPut(drawing) {
    // Every rendering is a new drawing, and a window being resized asks for one after another, so only a few are kept.
    if (paints.size >= MAX_PAINTED_DRAWINGS) paints.clear()
    drawing.systems.map { paint(it, unitSize) }
}
...
const val MAX_PAINTED_DRAWINGS = 8
```

With nine or more tunes the map is cleared while the song's own runs are being asked for, so every recomposition (a
fold, a scroll-driven recomposition of the page, the metronome panel) repaints most tunes. Each repaint is a new list of
new `NotationSystemPaint`s, and `rememberNotationCrossfade` (`ui/songView/SongNotationBlock.kt`) tells renderings apart
by that list:

```kotlin
if (run.systems != crossfade.systems) {
    crossfade.systems = run.systems
    if (!run.isRerendered || crossfade.leaving == null || crossfade.progress >= HALFWAY) {
        crossfade.leaving = crossfade.lastRun?.takeIf { run.isRerendered }
        crossfade.progress = if (crossfade.leaving == null) 1f else 0f
```

`NotationSystemPaint` has identity equality, so after a transposition (`isRerendered` true) every thrashing
recomposition restarts the cross-fade from 0: the tune blinks to faint and back, again and again, until something
stops recomposing it. Its doc comment even relies on the cache: "Each rendering is told apart by its systems, which the
painter keeps for as long as the rendering stands".

The same cache key also ignores `unitSize` (`getOrPut(drawing)`): the PDF (`PrintRenderer`, the only other caller)
paints a drawing at the `notation.scale` it first meets it at, so one tune printed both inside a chorus (narrower,
smaller scale) and outside it gets one of the two with words laid out at the other's size.

## Fix

Paint in the effect that renders, off the main thread, with a measurer of the job's own, exactly as the export does:
`ui/screens/export/ExportScreen.kt` builds
`PrintRenderer(measurer = TextMeasurer(fontResolver, Density(1f), LayoutDirection.Ltr, cacheSize = 256), ...)` with
`fontResolver = LocalFontFamilyResolver.current` and `LaidOutDocumentProducer.kt` measures with a fresh one inside
`withContext(Dispatchers.Default) { val measurements = newRenderer(); layoutPrintDocument(...) }`. That has shipped on
all four platforms, which answers whether `TextMeasurer` and font resolution work off the main thread there: a
`TextMeasurer` built from the composition's `FontFamily.Resolver`, `Density`, and `LayoutDirection` is plain data plus
the resolver, whose typeface cache is synchronized; the bundled Inter is already loaded into the `FontFamily` the
theme hands out. A `TextLayoutResult` and an `androidx.compose.ui.graphics.Path` made on another thread are drawn on
the main thread unchanged (Android's `StaticLayout` and `android.graphics.Path`, Skia's paragraph and path, are not
thread-bound). On the web `Dispatchers.Default` is the page's one thread, so painting still costs a frame there, but in
an effect between frames rather than inside a composition.

1. **What painting needs is part of the request.** In `SongNotations.kt` add

   ```kotlin
   /** What a tune's words are laid out with: the page's density, direction, fonts, and the lyrics' unscaled style. */
   internal data class NotationPainting(
       val density: Density,
       val layoutDirection: LayoutDirection,
       val fontResolver: FontFamily.Resolver,
       val textStyle: TextStyle,
   ) {
       /** A painter of its own, for one job on any thread: a painter keeps caches that are not thread safe. */
       fun newPainter() = NotationPainter(
           textMeasurer = TextMeasurer(fontResolver, density, layoutDirection, cacheSize = 0),
           density = density,
           textStyle = textStyle,
       )
   }
   ```

   and a `val painting: NotationPainting` to `NotationRequest`, so a density or style change renders (and paints)
   again, as the painter's `remember(textMeasurer, density, notationTextStyle)` key does today. Engraving again is a
   few milliseconds warm, and those changes are rare (a window moved to another screen), so no separate repaint path is
   worth its code. `cacheSize = 0` as the page's own measurer has: every layout is kept by the paint that holds it.

2. **The rendering holds its systems.** `NotationRendering` becomes
   `internal class NotationRendering(val drawing: NotationDrawing, val unitSize: Float, val systems: List<NotationSystemPaint>)`
   (keep `drawing`: plans 06 and 10 read it). In `rememberSongNotations`'s `LaunchedEffect`, create one painter per run
   of the effect and paint each tune right after it is engraved, on `Dispatchers.Default`:

   ```kotlin
   val painter = request.painting.newPainter()
   val renderings = request.tunes.mapNotNull { (tune, offset) ->
       ensureActive() // a cancelled run (a newer request) stops between tunes rather than painting the rest
       render(renderer, painter, tune, offset, request)?.let { tune to it }
   }.toMap()
   ```

   with `render` painting inside `withContext(Dispatchers.Default) { painter.paint(drawing, request.unitSize) }`. One
   painter per run, never one shared across runs: a cancelled run's `withContext` block keeps going until its next
   suspension, so two runs can overlap and would race on the painter's `HashMap`s and `PathParser`.

3. **`SongNotations` stops painting.** Drop its `painter` parameter (and `rememberSongNotations`'s), and make `of`
   return `systems = rendering.systems`. `remember(rendered, painter, unitSize)` becomes `remember(rendered, unitSize)`.
   The systems are now one list per rendering for as long as it stands, so `rememberNotationCrossfade`'s identity check
   fires exactly once per new rendering, and the 9-tune thrash cannot happen. Update `NotationCrossfade`'s KDoc ("which
   the painter keeps" → "which the rendering holds").

4. **`SongLyrics.kt`**: replace `notationPainter` with

   ```kotlin
   val fontResolver = LocalFontFamilyResolver.current
   val layoutDirection = LocalLayoutDirection.current
   val notationPainting = remember(density, layoutDirection, fontResolver, notationTextStyle) {
       NotationPainting(density, layoutDirection, fontResolver, notationTextStyle)
   }
   ```

   and pass `painting = notationPainting` instead of `painter = notationPainter`.

5. **`NotationPainter`**: split the public surface into `fun paint(drawing: NotationDrawing, unitSize: Float): List<NotationSystemPaint>`
   (uncached, what the screen's job calls) and the cached `paintOf` the PDF keeps, keyed by `drawing to unitSize`
   rather than `drawing` alone, which fixes the PDF's chorus case above at no cost. The PDF draws one page at a time
   from one `PrintRenderer`, so its 8-entry cache stays.

6. **`ui/notation/CLAUDE.md`**: the `NotationPainter` bullet says the systems are painted with the rendering, off the
   main thread, by a painter of each rendering's own (the web's only thread aside), and kept with it; the screen never
   paints during composition.

## Tests

New `presentation/src/desktopTest/.../ui/notation/NotationPaintingTest.kt` (desktop, since a `TextMeasurer` needs
`createFontFamilyResolver()`, as `PrintRendererTest` and `SongTextMeasurementsTest` build theirs):

- `` `a tune is painted off the main thread with a measurer of its own` ``: parse one of `NotationDrawingParserTest`'s
  fixtures that has a chord symbol and a lyric (move the fixture to a shared test helper if it is private), paint it
  with `NotationPainting(Density(1f), LayoutDirection.Ltr, createFontFamilyResolver(), TextStyle.Default).newPainter()`
  inside `withContext(Dispatchers.Default)`, and assert every `NotationTextPaint.layout` has a positive width and the
  chord is `isChord`.
- `` `a song of nine tunes hands each run the same systems on every composition` ``: build a `RenderedNotations` of
  nine renderings (any painted systems), make one `SongNotations`, call `of` twice for each tune's run, and assert
  `assertSame` on the two `systems`. This is the regression the 8-entry cache caused.

The cross-fade restart itself is only visible running the app; the identity test above is what decides it, so no UI
test is needed.
