<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Resolve the overflow menu's room once, not in every card

**Challenged:** amended — not on `OverlayState`: the view model owns it, so it outlives the Android `View` the insets belong to and would hold stale, leaked insets after a recreation; use a composition local of its own instead, and read the window's height in the layout, since reading `containerSize` once at the root would recompose the root on every frame of a window resize.

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

Resolve the inputs once near the root and read their values only inside the dropdown's existing `layout {}`:

- A `staticCompositionLocalOf<MenuRoom?> { null }` (its own local, not a field of `OverlayState`), provided in `CampfireApp`
  beside `LocalOverlayState`, holding `LocalWindowInfo.current` (the object, not its `containerSize`) and
  `WindowInsets.safeDrawing.union(WindowInsets.ime)`: the two getters are still called there every composition (on Android
  they register the accessor), the union `remember`ed on them.
- `OverlayState` is the view model's (`dialogHost.overlayState`) and outlives the Activity: on Android the insets are bound
  to the `View` that resolved them (`WindowInsetsHolder`), so storing them there would keep a recreated activity's menus
  reading the old view's insets.
- Read `windowInfo.containerSize.height` and the insets' top and bottom inside `layout {}` only. Reading `containerSize` at
  the root while composing would subscribe the root to it and recompose the app on every frame of a desktop or web
  window resize (today each card subscribes instead, which this removes too). Confirm `containerSize` is snapshot state on every
  platform, which is what makes the layout run again on a resize.
- Where the local is null (a composition without `CampfireApp`), fall back to today's per-menu reads, so the iOS popup layer's
  unbounded constraints after rotation are still bounded.

The alternative (read the insets only while the menu is expanded, keeping the last value for the exit animation) stays
possible but needs a holder; the local above needs none.

## Tests

None in `commonTest`. Check by hand that a menu opened near the bottom with the keyboard up still fits above it.
