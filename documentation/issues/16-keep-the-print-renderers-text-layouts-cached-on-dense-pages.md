<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Keep the print renderer's text layouts cached on dense pages

**Challenged:** amended — an LRU does not fix the stated case: a frame drawing more distinct texts than the bound is a
cyclic access, where LRU misses every entry just as `clear()` does, so only a bound above one frame's working set helps,
and `LinkedHashMap(accessOrder = true)` is JVM-only (shared code is JVM-free). The fix is now a cache keyed by the
pages drawn, whose working set (at most ~6 pages a frame) is known.

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

Cache the layouts per drawn page instead of per `(text, style)`:

- `draw(scope, page, scale)` looks `page` up in a short list of `(PrintPage, List<TextLayoutResult>)` entries matched by
  identity (`===`; `PrintPage` is a data class whose `equals`/`hashCode` walk all its texts on every call), the most
  recently drawn first, and on a miss lays out `page.texts` once and puts the entry in front.
- Keep 8 pages: a frame draws at most the pager's current and neighbouring pages of two documents during an options
  crossfade (`PrintPages`' `AnimatedContent`), so the working set always fits, and memory is bounded by pages rather
  than by fragment count. A re-layout makes new `PrintPage` objects, so the old entries simply age out.
- The preview renderer is only drawn on the main thread; the layout and the PDF export each use their own
  `newRenderer()` (`LaidOutDocumentProducer`, `ExportScreen`), so the list needs no locking. `selectableText` (export
  only) can keep a plain uncached `text()` or use the same page lookup.

## Tests

Pull the page cache into a small class: a page drawn again returns the same layouts (no second measure, counted through
the measure function it is handed), a ninth page evicts the least recently drawn, and an equal-but-new `PrintPage`
misses.
