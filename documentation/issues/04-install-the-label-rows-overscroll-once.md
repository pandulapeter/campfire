<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Compose a song card's label row once rather than twice as it scrolls in

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ContentOverscroll.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/labels/SongLabels.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md`

## Problem

`SongLabels` uses `.bounceHorizontalScroll(scrollState)`, and `ContentOverscroll.kt` decides in composition:

```kotlin
if (effect == null || state.canScrollForward || state.canScrollBackward) return this
```

`canScrollForward` derives from `ScrollState.maxValue`, which is written during measure. On a card's first composition the
answer is the pre-measure one; after the first measure a row that fits flips it, and `SongLabels` recomposes the next frame
to add the clip, overscroll and nested scroll nodes. Every tagged card entering the viewport is composed twice.

Drop this plan if `ScrollState`'s initial `maxValue` turns out to be 0 (then the first composition already takes the fitting
branch, and only overflowing rows flip).

## Fix

Always install the nested scroll connection and the overscroll, and let `ShortContentOverscroll.onPreScroll` decide at the
gesture whether the content fits (it already re-checks). Move the `onDispose` pull reset into the connection. This is
shared by the grid and the sheets (`bounceScrollableContent`, `bounceVerticalScroll`), so check each caller still bounces
only when it fits.

## Tests

None in `commonTest`. Check by hand that a fitting sheet still drags down, a fitting label row still bounces, and an
overflowing one still scrolls with the platform overscroll.
