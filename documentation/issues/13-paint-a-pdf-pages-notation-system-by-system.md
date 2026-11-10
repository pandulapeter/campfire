<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Paint only the notation systems a PDF page draws, kept with the page's other layouts

**Challenged:** sound

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`,
`…/ui/notation/NotationPainter.kt`, `…/ui/print/CLAUDE.md` (the Renderer bullet on caching),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`

Lane D, after plan 07.

## Problem

`PrintRenderer.draw` paints a tune's whole drawing to draw one of its systems, on the preview's main thread:

```kotlin
drawNotationSystem(system = notationPainter.paintOf(notation.drawing, unitSize = notation.scale)[notation.system], …)
```

`paintOf` lays out every word of every system of the drawing, keeps at most 8 drawings, and clears all of them when
full (`if (paints.size >= MAX_PAINTED_DRAWINGS) paints.clear()`), so paging a preview through a songbook of many tunes
lays out whole tunes again for one system each, and a page showing systems of more than 8 drawings repaints them every
frame of a zoom or a fade.

## Fix

Paint per system, with the page's other layouts:

1. `NotationPainter` gets an internal `paintSystem(system: NotationSystem, unitSize: Float)` (today's private
   per-system `paint`, with plan 06's local layout map scoped to the call); `paintOf`, `paints`, and
   `MAX_PAINTED_DRAWINGS` go.
2. `PrintRenderer`'s `PrintPageCache` builds, per page, the painted systems of its `page.notations` beside its text
   layouts (e.g. a small holder of both); `draw` reads them from there. The cache is by page identity and already sized
   for what a frame draws (the 8 pages drawn last), so a page's systems are painted once while it is on screen.

Considered and not recommended: painting every system in the producer's `Dispatchers.Default` block — it would hold
every tune's laid-out words for the document's life (a songbook of 100 tunes is tens of thousands of layouts), while the
page's own texts are already laid out on first draw by the same page cache.

Must not break: the PDF's look (same paints, same scale); the export's own renderer (`PrintRendererFactory`, drawing
pages in bands) and the preview's; `PrintPageCache` stays unsynchronized and per renderer.

## Tests

- `PrintRendererTest`: a page with a notation system draws the same pixels as before (an existing render test, or one
  comparing a page drawn twice); a page cache entry holds only that page's systems.
- `PrintPageCacheTest`, `PdfRoundTripTest` pass unchanged.
