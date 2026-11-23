<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Measure what laying every screen out inside a `SharedTransitionLayout` costs, and if it makes transition frames measurably longer, draw the swapping tab parts from one overlay node instead

**Challenged:** amended — step 2's overlay draws each part at its position in the overlay's own coordinates, not `positionInRoot`: the overlay sits in `ScreenDeck`, whose layer slides with the export screen, so root positions would move a part twice; and it reuses plan 09's record-and-draw-elsewhere helper (same lane, landed first).

**Kind:** performance  ·  **Severity:** medium (needs measurement first)  ·  **Platforms:** all  ·  **Run on:** any machine
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt` (`ScreenDeck`; must not grow)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/tabChrome/TabChrome.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/tabChrome/SharedTabChromeDrawing.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/tabChrome/TabChromeForeground.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/tabChrome/TabChromeOverlay.kt` (new, only if replaced)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/CLAUDE.md` (the tab swap bullet)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongLyrics.kt` (only to check its `LookaheadScope`)

## Problem

Since `23ec5e35d` (today, after 5.5.0, so not a cause of the owner's release drops, but in the debug builds where they
also see skipped frames) every screen is laid out inside a `SharedTransitionLayout` (`CampfireScreens.kt:434-439`):

```kotlin
SharedTransitionLayout(
    modifier = Modifier.fillMaxSize().underExportScreen(exportTransition, layoutDirection, scrimColor, isExportCovering),
) {
    tabChrome.sharedTransitionScope = this
    content()
}
```

A `SharedTransitionLayout` is a `LookaheadScope`: every layout node under it gets a lookahead measure and placement
before the real one, the lazy grids and the song details' lyrics included. The app uses it for one thing,
`renderInSharedTransitionScopeOverlay` (`SharedTabChromeDrawing.kt:54-57` and `:101`, `TabChromeForeground.kt:44`), so
the New button, the search pill, and the tools' parts draw over both screens while two tabs swap; nothing uses
`sharedElement` / `sharedBounds`, whose layout is what lookahead is for. The probe (at `6ddedf2d4`, which includes the
change) saw lookahead bookkeeping among the compose leaves of the push (`LayoutNode.setLookaheadRoot` 3 %) and in
layout (`LookaheadCapablePlaceable.updatePlacedUnderMotionFrameOfReference` 4 % of `sheet:open`), but never compared
against a tree without it.

It also changes `SongLyrics.kt:350`'s own `LookaheadScope { SongSectionsLayout(…) }`: nested in an outer lookahead
scope it is no longer a lookahead root of its own, and `SongSectionsLayout`'s `isLookingAhead` branches
(`SongSectionsLayout.kt:246-301`: centering hold, section glides, divider tops) now run in the outer scope's passes.
The song details screen's glides were written before the outer scope existed.

## Fix

**Step 1 — measure (do this first, then decide).** In a scratch branch, not committed: replace `SharedTransitionLayout`
in `ScreenDeck` with a `Box` of the same modifier, and make `renderInSharedTransitionScopeOverlay` a no-op (guard the
three call sites on a temporary flag; the swap's overlay drawing is lost, which a measurement does not mind). Run plan
01's transitions suite on both trees, same machine, `--runs 10`, twice each.

**Drop this plan if** the warm trigger frame of `push:long`, `pop:plain`, and `tab:songs>setlists` differs by less than
1.5 ms (about 5 %) and the layout part by less than 10 %; write the numbers into this file and close it.

**Step 2 — if it is measurably more,** replace the layout with an overlay of the app's own:

- `ScreenDeck` lays the screens in a plain `Box`; after `NavDisplay`, one `TabChromeOverlay` node (a `Spacer` with
  `fillMaxSize()` and a draw modifier) draws the parts that are swapping.
- Each part keeps its `sharedTabChrome` modifier, which today already draws by itself from root coordinates
  (`onPlaced { chrome.coordinates = it }`, `travellingBounds`, `currentContainerColor`): while `chrome.isSwapping`, its
  draw modifier records its drawing into a `GraphicsLayer` (`rememberGraphicsLayer`) instead of drawing it in place, and
  registers the layer with `TabChrome` with its z-index; the overlay draws the registered layers in z order, each at
  `overlayCoordinates.localPositionOf(partCoordinates)` — not `positionInRoot`, which includes `ScreenDeck`'s own
  export slide (`underExportScreen`) that the overlay, inside the deck, already gets. Use the recording helper plan 09
  introduces (`chrome/ChromeLayers.kt`, landed before this in lane N) rather than a second one. Outside a swap nothing is registered and the part draws in place as now.
- Order: the overlay node is drawn after `NavDisplay` in the same frame the parts record, which holds as long as it is
  placed after it in the same parent (draw order follows placement order).
- `TabChrome.sharedTransitionScope` and the `ExperimentalSharedTransitionApi` opt-ins go away.
- Check `SongLyrics`' glides once its `LookaheadScope` is a root again: open a song, fold and unfold a section, resize
  the window, page with the arrows (`songView/CLAUDE.md`'s glides) — they were written against that, so this restores
  rather than changes them; `SongDetailsFitTest`, `ColumnTurnTest`, `PedalCoverageTest` cover the layouts.

Update `chrome/CLAUDE.md`'s tab swap bullet: the parts are drawn by one overlay after `NavDisplay`, not through a
`SharedTransitionLayout`.

## Tests

Step 1 needs none (nothing is committed). Step 2: `components/tabChrome/TabSwapNewButtonTest.kt` (the New button carried
over a swap) and `screens/tuner/TunerInstrumentPillTest.kt`, `screens/chords/ToolCapo*Test.kt` (the tools' parts) must pass
unchanged; add to `TabSwapNewButtonTest.kt`: `` `mid swap the New button is drawn once, over both screens` `` (pause
the clock halfway through Songs → Setlists; the button's pixels are at the travelling rectangle and not also at either
screen's own place).

**Measure:** step 1 is the measure (plan 01, `push:*`, `pop:*`, `tab:*`, `sheet:open`). After step 2, the same scenarios
should match step 1's Box tree within noise.

## Manual check

A tab swap on Android with a predictive back from Setlists to Songs, released half-way and completed: the parts follow
the finger linearly (`chrome/CLAUDE.md`), drawn once.
