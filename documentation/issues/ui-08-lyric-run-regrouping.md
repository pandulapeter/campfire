<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Precompute lyric section runs instead of regrouping on recomposition

Priority: medium for long songs. Affects zoom, theme changes, folding, and the editor preview.

## Evidence

`SongLyrics` remembers `toRenderSections` for a parsed song (around line 172), but `RenderSection.Lines.lines` flattens its parts through a getter on every read (around line 1680). `SongSectionContent` reads it to determine the section's foldable kind, then calls `part.lines.groupIntoRuns()` for every line part during composition (around lines 495 and 565). Group boundaries depend on the song text, not on font scale or color. A pinch changes font scale once per frame and recomposes/re-measures the song, recreating those lists each time.

## Implementation plan

1. In `SongLyrics.kt`, compute each `SectionPart.Lines` run grouping once when `toRenderSections` creates the part, or cache it as a non-state property on that immutable part. Likewise cache the flattened lines and `wholeFoldableKind` result on `RenderSection.Lines`. Do not key these values on `fontScale`, colors, width, or folded state; those do not change the grouping.
2. Replace the `groupIntoRuns()` and `section.lines` recomposition calls with the cached values. Keep the current order of comments among the line parts and the existing run-name numbering used by fold keys.
3. Add pure tests for mixed lyrics/tab/grid runs, comments cutting and rejoining sections, chorus recalls, and lyrics-only mode. Compare the precomputed groups and fold keys with the current logic. Configure `:presentation` common tests if needed.
4. Profile one long song on web while pinching and while changing theme. Count `groupIntoRuns` calls before and after; after the change they should occur only when a new rendered song or lyrics-only mode creates new sections.

## Done when

- Font-scale and color changes do not flatten and regroup unchanged song lines.
- Tabs, grids, chorus recalls, fold controls, and reading order render exactly as before.
- The existing `SectionMeasurements` cache remains keyed on the values that actually affect text size and layout.
