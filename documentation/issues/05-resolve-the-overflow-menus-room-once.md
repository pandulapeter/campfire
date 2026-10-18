<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Resolve the overflow menu's room once, not in every card

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all (Android mostly)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/OverflowMenu.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt` (or wherever `LocalOverlayState` is provided),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/CLAUDE.md`

## Problem

`OverflowMenu.kt` reads, in every card's composition and with the menu closed:

```kotlin
val windowHeight = LocalWindowInfo.current.containerSize.height
val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
```

On Android each `WindowInsets.*` getter is `WindowInsetsHolder.current()` (a `LocalView` read, a synchronized map lookup and a
`DisposableEffect`), twice per card, plus a `UnionInsets` per composition, for a value only an open menu uses.

## Fix

Resolve the limit once near the root (on `LocalOverlayState`, already provided by `CampfireApp`) and read it inside the
dropdown's existing `layout {}` (recommended: it removes the work from every card and keeps the exit animation's value
without a holder); or read the insets only while the menu is expanded, keeping the last value for the exit animation.

## Tests

None in `commonTest`. Check by hand that a menu opened near the bottom with the keyboard up still fits above it.
