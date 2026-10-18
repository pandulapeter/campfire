<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Hand the bar and rail handover's room to the screens as layout reads

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android and iOS (rotation), desktop and web (resize)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationChromeScaffold.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/KeyboardAwarePadding.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/CLAUDE.md`

## Problem

During a handover `NavigationChromeScaffold` builds a new `NavigationChromeSize` on every measure from
`transition.progress` and subcomposes the content with it. `CampfireScreens.kt` reads `railWidth` / `navigationBarHeight`
into `TopLevelScreenSurface`'s `.padding(start = railWidth, bottom = navigationBarHeight)` and into
`KeyboardAwarePadding(..., coveredHeight = navigationBarHeight, ...)`, whose `equals` includes `coveredHeight`. So the visible
list screen recomposes on every frame of the ~15–25 frame spring after each rotation across 600dp.

## Fix

Pass a `() -> NavigationChromeSize` (or `State`) instead of the value; inset `TopLevelScreenSurface` through a `layout {}`
modifier (or a `PaddingValues` reading the provider); make `coveredHeight` a provider excluded from `equals`, as `ime` already
is. The decisions taken from the settled width (`ListLayout`, `PageWidthLayout`) stay. Then drop the parenthesis the review
added to `chrome/CLAUDE.md` ("the handover itself still recomposes them each frame").

## Tests

`NavigationChromeKindTest` and the chrome tests must pass unchanged; the golden renders cover the settled layouts.
