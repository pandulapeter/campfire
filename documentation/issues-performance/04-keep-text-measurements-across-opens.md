<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# Keep the lyric fragment and chord name measurements across opens, in one app-wide holder per set of styles

**Challenged:** amended — the key carries `LocalLocaleList.current`, which CMP 1.13's `rememberTextMeasurer` reads and passes to the measurer (the plan's code built it from `LocaleList.current` and left it out of the key); `SongLyrics` is past 120 lines and may not grow; what the app-wide holder keeps alive is said (bounded native paragraphs, stale resolver entries after an Android recreation); measured with `push:fresh` too.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** (`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`)
`ui/songView/SongTextMeasurements.kt`,
`ui/songView/SongTextMeasurementsHolder.kt` (new; or a companion in `SongTextMeasurements.kt` if it stays small),
`ui/songView/SongLyrics.kt`,
`ui/songView/ChordCellLayouts.kt` (optional step 3),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongTextMeasurementsTest.kt`,
`ui/songView/CLAUDE.md` (one line under Layout)

## Problem

Every page measures every lyric fragment, chord name, and annotation afresh, because the measurer and the maps that keep
the answers live and die with the page. `ui/songView/SongLyrics.kt`:

```kotlin
// One measurer for the whole page. Everything measured through it is kept by whoever asked for it (see
// [SongTextMeasurements] and [TabRows]), so a cache of its own would only hold every layout a second time.
val textMeasurer = rememberTextMeasurer(cacheSize = 0)
...
val textMeasurements = remember(textMeasurer, lyricsStyle, chordStyle, annotationStyle) {
    SongTextMeasurements(
        textMeasurer = textMeasurer,
        lyricsStyle = lyricsStyle,
        chordStyle = chordStyle,
        annotationStyle = annotationStyle,
    )
}
```

`SongTextMeasurements`' own KDoc says why it is keyed this way: "It lives for as long as the measurer and the three styles
do - which is everything a width depends on: the styles carry the text size, the measurer is rebuilt with the density,
the layout direction and the font resolver". Those are the same on every open of every song at one text size, yet a new
page starts with empty `fragmentWidths`, `chordLayouts`, `annotationLayouts`, `tabRows`, `gridLayouts`, and `gridTexts`.
The push-frame measurement put text layout (`Paragraph._nLayout`, `SongTextMeasurements.measureFragment`) at about 16%
of a long song's layout samples: chorded lines ask for fragment widths and chord names, which a song (and a library)
repeats endlessly.

The same holds for the Chords section's names: `ChordCellLayouts` measures every cell's name and second name per page
(`remember(chordsSection?.cells, chordStyle, keyHeading, textMeasurer, density, fontScale)`), and `textMeasurer` is part
of that key, so even an equal set of cells is measured again on a new page.

Thread-safety: `SongTextMeasurements` is used only from composition, measurement, and drawing on the main thread (its
users are `SongLyrics`, `SongSectionContent`, `SongSectionItem`, `SongSectionChildren`, `SongLineWithChords`,
`SongTabBlock`, `SongGridLine`, `SongChordsSection`, `ChordShapeCrossfade`); the engraved notation paints off the main
thread with a `TextMeasurer` of its own (`ui/notation/SongNotations.kt`, `TextMeasurer(fontResolver, localeList, density, LayoutDirection.Ltr, cacheSize = 0)`),
never this one.

## Fix

1. **An app-wide holder of measurements by what a width depends on.** New `SongTextMeasurementsHolder` (internal
   object or a class held in a `staticCompositionLocalOf` provided once at the app root; recommended: a plain
   `internal object`, since its contents are pure functions of the key and the root's `CompositionLocal`s are already
   many):

   ```kotlin
   internal data class SongTextMeasurementsKey(
       val fontFamilyResolver: FontFamily.Resolver, // compared by identity; one per window (Android: per activity)
       val localeList: LocaleList,
       val density: Density,
       val layoutDirection: LayoutDirection,
       val lyricsStyle: TextStyle,
       val chordStyle: TextStyle,
       val annotationStyle: TextStyle,
   )
   fun of(key: SongTextMeasurementsKey): SongTextMeasurements  // LRU, MAX_KEPT_STYLE_SETS = 3
   ```

   It builds the `TextMeasurer` itself (`TextMeasurer(key.fontFamilyResolver, key.localeList, key.density, key.layoutDirection, cacheSize = 0)`) and hands back the same `SongTextMeasurements` for an equal key. Main thread only, said in
   its KDoc. Bounded twice: at most three style sets (a pinch makes a new one every frame; only the settled size is worth
   keeping, and the LRU lets the frames of a pinch fall out), and each `SongTextMeasurements` keeps its own per-map bounds
   (`bounded()` at 4,096, `MAX_TAB_RUNS` at 64), which already guard the editor's preview, which shares the holder.

2. **`SongLyrics` asks the holder** instead of `rememberTextMeasurer(cacheSize = 0)` + `remember { SongTextMeasurements(...) }`:
   read exactly what `rememberTextMeasurer` reads in CMP 1.13 (`TextMeasurerHelper.kt`): `LocalFontFamilyResolver.current`,
   `LocalLocaleList.current`, `LocalDensity.current`, and the `LocalLayoutDirection.current` at that same point (the
   screen's, outside the `CompositionLocalProvider` of the song's direction), and
   `remember(key) { SongTextMeasurementsHolder.of(key) }`. Pass `textMeasurements.textMeasurer`
   wherever `textMeasurer` was passed (`ChordCellLayouts`). Update the comment above it: the measurer is the holder's,
   and the reason for `cacheSize = 0` stays. The locale list is in the key because a language switch changes line breaking
   and shaping. `SongLyrics` (269 lines) is past the 120-line limit and may not grow: the swap replaces
   `rememberTextMeasurer` and the eight-line `remember { SongTextMeasurements(...) }`, so it should shrink; if the key's
   reads would make it longer, put them in a `rememberSongTextMeasurements(lyricsStyle, chordStyle, annotationStyle)`
   beside the holder.

   **What it keeps alive.** Up to three `SongTextMeasurements` outlive every page, each with up to 4,096 chord, 4,096
   annotation, and 4,096 grid `TextLayoutResult`s (each a native Skia paragraph off Android) and 64 tab runs; in use a
   library's chord names are a few hundred, so this is a few MB at most, and the bounds are what they were for the
   editor's preview. On Android an activity recreated (a configuration change the app does not handle) brings a new
   `FontFamily.Resolver`; the old entries can then never be hit and simply age out of the LRU (the resolver keeps the
   application context, not the activity). Say both in the holder's KDoc.

3. **Optional, same change: chord name layouts through the holder.** In `ChordCellLayouts`, measure `names`,
   `secondNames`, and `headings` through a bounded map kept on `SongTextMeasurements` (keyed by text, style, and the
   `maxLines = 1, softWrap = false` call), so a Chords section on a new page reuses the names any page measured.
   Drop `textMeasurer` from that `remember`'s keys once it is stable. Skip this step if it pushes `SongTextMeasurements.kt`
   past its size or makes the class two things.

4. **Not this plan: keeping the column search's result.** `SectionMeasurements` already keeps the decided grid per
   `SectionGridKey` and sizes per `UnitContent` while a page lives (`SectionSizesPool`). Carrying them across opens would
   be keyed by every unit's content plus the pool's key, and the first section's height depends on things outside
   `UnitContent` (playing controls or the read only line, the tuner and metronome buttons), so a reused grid could be
   wrong without anything failing. With this plan and plan 03 the reopen's intrinsic measurements become cheap (text
   measured before, model kept); revisit only if plan 01's numbers still show `SongGridSearch.searchGrid` dominating a
   reopen.

Update `ui/songView/CLAUDE.md` (Layout): text measurements are app-wide per style set, a few kept, main thread only.

## Tests

`SongTextMeasurementsTest` (desktopTest, since it needs the Compose UI runtime, as the existing tests there do):

- the holder hands back the same `SongTextMeasurements` for two equal keys built from separately made but equal styles,
  and a different one for a different font size, density, or layout direction;
- after more distinct keys than it keeps, the first key gets a fresh instance;
- a fragment measured through one handed-back instance is not measured again through the next one handed back for an
  equal key (assert `fragmentWidth` returns from the map: e.g. `chordLayout` returns the same `TextLayoutResult`
  instance, `assertSame`).

**Measure** (after plan 01): `./gradlew :tools:screenshots:benchmark`, push to song details for Proud Mary and for the
tab song, opened after another song was opened at the same text size: the push frame's layout time should fall by most
of the text-layout share (≈16% of layout on the long song); the very first open after launch is unchanged, and
`push:fresh` gains only what other songs measured before (chord names, common fragments).
