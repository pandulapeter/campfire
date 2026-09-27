<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 08 — Unchanged sections keep their measurements and their nodes after an edit or a transposition

| | |
|---|---|
| Lane | A |
| Impact | medium in the editor's preview on long songs; low–medium on the details screen |
| Confidence | high that the waste exists; medium on the size of the win (the intrinsic part only exists on multi-column widths) |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt |
| Depends on / conflicts with | Edits `SectionMeasurements` and the section loop in `SongLyrics`, which 04 and 05 also edit. Land after them and before 07. Order: … 04 → 05 → **08** → 07 … |
| Commit message | `Keep the editor's preview quick on long songs by measuring only the sections that changed.` |

## Problem
I checked the editor reviewer's claim against the code. It holds, with one qualification about where it costs the most.

1. **Every measured height is thrown away when anything changes.** `SongLyrics.kt:177` and `:195`:
   ```kotlin
   val sections = remember(song, shouldShowChords, defaultLabels) { song.toRenderSections(shouldShowChords, defaultLabels) }
   val sectionMeasurements = remember(sections, fontScale, density, foldedSections) { SectionMeasurements(sectionCount = sections.size) }
   ```
   Any one-character edit in the editor produces a new `ChordProSong`, so a new `sections` list, so a new `SectionMeasurements`. A transposition step on the details screen does the same. Every section's intrinsic height at every candidate width, and every `minIntrinsicWidth`, is then measured again (`:1226-1273`), even though usually only one section changed. The heights are only asked for where more than one column fits (`gridFor(1)` needs none, and the `while` at `:1261` short-circuits on `maxColumnCount == 1`). So this cost appears in a Preview-only pane or a wide Split pane (two or more 384dp columns), and on the details screen on tablets and desktops.
2. **An inserted section shifts every node below it.** `SongLyrics.kt:257` emits the sections by position (`sections.forEachIndexed { index, section -> … }`), with no `key`. When the edit inserts or removes a section (a new `{soc}`, a blank line splitting a paragraph), every slot below the insertion receives the section that used to be its neighbour:
   - Each `SongLineWithChords` there sees a different `line`, so it re-pads (`:1665-1673`).
   - Each `Text` gets different text, so it lays out again.
   - Each `fadingIn` `Animatable` and each `TabRows` belongs to a different section than before.

   So the whole rest of the song is laid out again at every width, including on a phone.

## Fix
1. **Heights by section content.** Replace the index-keyed arrays with one entry per distinct section, reused across a change of `sections` as long as the other keys (scale, density, folds, styles) hold:
   ```kotlin
   /** What one section measures, kept for as long as a section equal to it is on the page. */
   private class SectionSizes {
       val heightsByWidth = HashMap<Int, Int>()
       var minWidth = UNMEASURED
   }

   /** Reuses the sizes of the sections that are still there, by content, and starts the others empty. */
   private class SectionSizesPool {
       private var bySection = HashMap<RenderSection, ArrayDeque<SectionSizes>>()
       fun measurementsFor(sections: List<RenderSection>): SectionMeasurements {
           val next = HashMap<RenderSection, ArrayDeque<SectionSizes>>()
           val sizes = sections.map { section ->
               (bySection[section]?.removeFirstOrNull() ?: SectionSizes()).also { next.getOrPut(section) { ArrayDeque() }.addLast(it) }
           }
           bySection = next
           return SectionMeasurements(sizes)
       }
   }
   ```
   `SectionMeasurements` keeps its API (`height(index, width, measure)`, `minWidth`, `grid`) but looks up `sizes[index]`. Each `SongLyrics` composition then does:
   ```kotlin
   val pool = remember(fontScale, density, foldedSections, lyricsStyle) { SectionSizesPool() }
   val sectionMeasurements = remember(sections, pool) { pool.measurementsFor(sections) }
   ```
   - The grid cache (`lastGridKey`) lives in the new `SectionMeasurements`, so it is still searched again for new sections. The search itself is cheap integer work over cached heights (`SectionGrid.kt`).
   - Keep the `MAX_SECTION_WIDTHS` bound: clear a `SectionSizes`'s map when it holds more than that many widths, between searches, as `grid()` does now.
   - `RenderSection` and everything in it are data classes all the way down (`ChordProLine`, `GridToken`, `CommentStyle`), so equality is structural. A section equal to one before measures the same height at the same width with the same styles and folds, because nothing outside the section decides its height. Hashing happens once per change of `sections`, not per lookup.
   - An edit to one line leaves every other section's heights in place. A transposition keeps the sections that carry no chords (comments, lyric-only sections).
2. **Nodes by section content.** Precompute a stable key per section once, and wrap each section in `key(...)`:
   ```kotlin
   val sectionKeys = remember(sections) {
       val seen = HashMap<RenderSection, Int>()
       sections.map { section -> val occurrence = seen.merge(section, 1, Int::plus)!!; SectionKey(section.hashCode(), occurrence) }
   }
   …
   sections.forEachIndexed { index, section ->
       key(sectionKeys[index]) { … as today … }
   }
   ```
   `SectionKey` is a private data class of two `Int`s, so the key comparison per composition is cheap. A hash collision only costs reuse, never correctness: Compose matches equal keys in order. The emission order, and with it the measurable order `SongSectionsLayout` relies on (`allMeasurables.take(sectionCount)`), is unchanged. `sectionAnimations` stays index-based as today: it only records the too-tall flag, which the next lookahead measurement writes again.
3. **What must not change:**
   - The layout decisions: same heights, same grid.
   - The dividers, which remain the trailing unkeyed children after the sections.
   - A toggled fold still fades in only its own section.

## Verification
- Run `./gradlew :presentation:desktopTest`.
- Add a `commonTest` case for `SectionSizesPool`: equal sections get the same `SectionSizes` back, duplicates are handed out in order, and new sections start empty. It is pure logic, like `SectionGridTest`.
- Editor, Preview-only pane on a desktop window wide enough for two columns, a long song. Type in the last verse while tracing (Perfetto on Android, or a JFR/async-profiler run of `:app:desktop:run`). `maxIntrinsicHeight` calls per refresh drop from all sections × widths to the changed section × widths.
- Split pane: insert `{soc}` near the top. The sections below keep their node identity (Layout Inspector shows no recomposition of their `SongLineWithChords`).
