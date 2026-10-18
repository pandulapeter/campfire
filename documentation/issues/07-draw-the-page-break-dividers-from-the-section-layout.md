<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Draw the song's page-break dividers from its layout instead of composing one per line

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongLyrics.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongSectionsLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`

## Problem

`SongLyrics.kt`:

```kotlin
repeat((sections.size - 1 + units.isCuttableBefore.count { it }).coerceAtLeast(0)) { HorizontalDivider() }
```

composes one Material `HorizontalDivider` (a `Canvas` layout node) for every place a page could start, about one per line of
a cuttable song, on every composed pager page, though nearly none is ever measured.

Do plan 06 first; this plan edits the code it moves.

## Fix

Draw the dividers from `SongSectionsLayout`'s own `drawWithContent`, at the tops its measure already computes
(`arrangement.dividerTops`), written into a holder during placement and read in draw (layout → draw is the allowed
direction), with `DividerDefaults.Thickness` and the color `HorizontalDivider` uses.

## Tests

The golden renders must not change (`./gradlew :tools:screenshots:test`); a pixel change there means the color or the
thickness differs.
