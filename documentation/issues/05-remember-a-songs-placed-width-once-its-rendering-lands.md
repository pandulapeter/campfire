<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Remember where a song's tunes were placed once their rendering lands, not on every composition

**Challenged:** amended — the test as first written stepped the text size up and down between two sizes, which puts
only two entries in the cache and would have passed without the fix (dropping the plan wrongly): it now steps through
at least 16 distinct sizes; plan 20 follows this one and edits the same `widthFor` call.

**Kind:** bug, performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`,
`…/ui/notation/NotationCache.kt` (KDoc of the placed widths only), `…/ui/notation/CLAUDE.md` (if a line needs it),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SheetMusicReopenTest.kt`

Lane N, after plan 03 and before plan 20, which adds a `widestWidth` argument to the same `placement.widthFor(…)` call
this plan leaves without the write (keep `base` hoisted where plan 20 can read it).

## Problem

`rememberSongNotations` writes the app-wide cache while composing:

```kotlin
val base = NotationRequest(tunes, narrowestWidth, availableWidth, unitSize, chords, songTitle, painting, parts)
val width = placement.widthFor(tunes, narrowestWidth, availableWidth, unitSize, rememberedWidth = cache?.placedWidthFor(base))
if (width != narrowestWidth) cache?.rememberPlacedWidth(base, width)
```

`base` holds `unitSize` and `availableWidth`, which change on every frame of a pinch or a window resize, and
`widthFor` then answers the tunes' last placed width (`placedWidths` survives the context change), so every such frame
puts a new entry into `placedWidths`, a `LinkedHashMap` bounded to 16 (`maxTunes`). A pinch of a few dozen frames evicts
every other song's placed width; a song opened again then guesses the narrowest column, its cached rendering for its
real column is not found, and it shows its text first — breaking "a tune seen before is notation on its first frame".
It is also a side effect of composition on app-wide state: a composition that is discarded still writes.

## Fix

Remove the write from composition. In the `LaunchedEffect`, right after `rendered = RenderedNotations(…)` (both the
engraving path and the all-cached path end there), remember the width the rendering was made for:
`if (request.width != narrowest) cache?.rememberPlacedWidth(<the base of this request>, request.width)`, where the base
is the request with the narrowest width — hoist `base` out of the `if` so the effect's lambda captures the one of the
composition that launched it (the effect is keyed by `request`, which is derived from it), or carry the narrowest
width in the request. Only a rendering that landed is remembered, so a pinch or a resize writes once when it settles
(after `RERENDER_DELAY_MILLIS`), not per frame. Reading (`placedWidthFor` in composition) stays as it is.

Must not break: a song opened again at the same size and page is engraved for its column on its first frame
(`SheetMusicReopenTest`); `NotationPlacement`'s settling; the cache stays
main-thread only (the effect resumes on Main).

## Tests

**Write this test first; drop this plan if it passes without the fix.** In `SheetMusicReopenTest`: open song A (its
tune placed in a column wider than the narrowest — a desktop window if the phone's is the narrowest), wait for its
staff, go back, open another song (B) with a tune, and with the clock stopped step the text size through **at least
16 distinct sizes** and back, a frame composed after each step and each step less than `RERENDER_DELAY_MILLIS` after
the one before (every step restarts the rendering effect, so nothing renders until the last; and the size ends where it began, which keeps A's base the same), go back, and open A: its
staff must be there on the first frame. The cache holds 16 placed widths and a base is one entry per distinct page width
and unit: stepping between two sizes, however often, writes two entries and evicts nothing. From 100% up to the
maximum, 250%, is 15 steps, which with B's own 100% entry and A's makes 17, one past the bound; for a margin go on down
to 80% (`UserPreferences.MIN_FONT_SCALE`) before returning to 100%, A's size (the text size is app-wide). Song B's tune must be placed wider than its narrowest column at
every one of those sizes (a wide desktop window), or nothing is written for it. Use the text size control the
existing tests use (`AppTestNavigation.kt`), or the keyboard shortcut in `TestWindow.DESKTOP` with
`ImpersonatedPlatform.WINDOWS`. Add a second song file under `tools/screenshots/src/test/resources/songs` if needed.
