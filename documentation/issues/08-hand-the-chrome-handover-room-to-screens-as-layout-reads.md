<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Hand the bar and rail handover's room to the screens as layout reads

**Challenged:** amended — the provider must be backed by snapshot state (the transition's from/to sizes are plain fields, so a bar or rail re-measured to a new size without a kind change would never reach a layout reading them); `consumeWindowInsets`, `messagesPadding` and `SharedToolsTabRow` read the same sizes in `CampfireScreens`' composition and are now named; `ime` is compared by identity, not excluded.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android and iOS (rotation), desktop and web (resize)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationChromeScaffold.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/KeyboardAwarePadding.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/CLAUDE.md`,
`.../ui/chrome/TopLevelScreenSurface.kt`, `.../ui/chrome/SharedToolsTabRow.kt`, `.../ui/chrome/NavigationChromeKind.kt`

## Problem

During a handover `NavigationChromeScaffold` builds a new `NavigationChromeSize` on every measure from
`transition.progress` and subcomposes the content with it. `CampfireScreens.kt` reads `railWidth` / `navigationBarHeight`
into `TopLevelScreenSurface`'s `.padding(start = railWidth, bottom = navigationBarHeight)` and into
`KeyboardAwarePadding(..., coveredHeight = navigationBarHeight, ...)`, whose `equals` includes `coveredHeight`. So the visible
list screen recomposes on every frame of the ~15–25 frame spring after each rotation across 600dp.

## Fix

1. **A state-backed provider.** Hand the content a stable `() -> NavigationChromeSize` (remembered with the
   transition), plus `settledRailWidth` as a plain value (`ListLayout` / `PageWidthLayout` and the column counts decide
   from it while composing, and it must keep recomposing them when a breakpoint flips). The provider must read only
   snapshot state: today `fromRailWidth` / `toRailWidth` / `fromBarHeight` / `toBarHeight` are plain fields that
   `update()` rewrites on every measure, and only `progress` is state. A bar re-measured taller without a kind change
   (font scale, a system navigation bar inset changing, the expanded rail's labels changing language) changes the
   `NavigationChromeSize` today and so recomposes the screens with it; with a provider over plain fields nothing would
   re-run. Make the four sizes `mutableFloatStateOf` (written only when they differ, so the steady state writes nothing),
   or publish the interpolated size into one `mutableStateOf` from the measure block.
2. **Every composition read in `CampfireScreens` goes**, or `CampfireScreens` itself still recomposes each frame (and
   with it the `NavDisplay` entry lambdas it rebuilds):
   - `TopLevelScreenSurface`: `.padding(start = railWidth, bottom = navigationBarHeight)` *and*
     `.consumeWindowInsets(PaddingValues(start = railWidth, bottom = navigationBarHeight))`: give both one deferred
     `PaddingValues` reading the provider (`consumeWindowInsets(PaddingValues)` reads it lazily, when the insets below
     are measured), with an `equals` by provider identity.
   - `shellContentPadding`: `KeyboardAwarePadding(coveredHeight = …)` becomes a provider (compared by identity, as `ime`
     is compared by identity today rather than excluded); `songEditorContentPadding` and `messagesPadding` pass `{ 0.dp }`
     constants kept as one shared value.
   - `messagesPadding`'s `bottom = maxOf(navigationBarHeight, systemBars.calculateBottomPadding())`: move the
     `navigationBarHeight` part into a provider read in `calculateBottomPadding`.
   - `SharedToolsTabRow(railWidth = …)` and its `.padding(start = railWidth)`: pass the provider, read in a `layout {}`
     or a deferred `PaddingValues`.
3. Then drop the parenthesis the review added to `chrome/CLAUDE.md` ("the handover itself still recomposes them each
   frame").

The subcompose content lambda is still a new instance on every measure of the scaffold; with only stable arguments
(`windowWidth`, `windowSize`, `kind`, the remembered provider, `settledRailWidth`) the slot's root recomposes and then skips
`CampfireScreens`, which is the point.

## Tests

`NavigationChromeKindTest` and the chrome tests must pass unchanged; the golden renders cover the settled layouts. By hand:
a rotation across 600dp and a desktop resize across it still slide the screens with the chrome, and changing the font
scale or the system navigation mode (gestures vs. buttons) on Android still re-insets the screens above the bar.
