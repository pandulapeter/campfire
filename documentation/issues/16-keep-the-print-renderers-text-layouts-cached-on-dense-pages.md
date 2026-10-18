<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Keep the print renderer's text layouts cached on dense pages

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`

## Problem

`PrintRenderer.kt`:

```kotlin
if (cache.size >= 512) cache.clear()
return cache.getOrPut(value to style) { measurer.measure(...) }
```

Nearly every lyric fragment is a key of its own. When a frame draws more than 512 distinct texts (a small font in several
columns; a page swipe drawing two pages; an option crossfade drawing two documents), every frame clears the cache and
measures everything again (the `TextMeasurer`'s own cache is 256), so the preview's frame rate drops during zoom and fades.

## Fix

Evict the least recently used entries instead of clearing (a `LinkedHashMap` in access order) at a bound of 2,048 —
recommended, after measuring the preview's memory with a dense setlist, since a `TextLayoutResult` holds a paragraph — or
only raise the bound.

## Tests

Pull the cache into a small class and test that it never exceeds its bound and keeps the most recently used entries.
