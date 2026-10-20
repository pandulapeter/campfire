<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Measure an address gone forward to on the home screen the Features switches leave, not on the songs

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Challenged:** amended — the depth check moves into a pure `BrowserRoutes.resolveForward` in commonMain that `goForward` calls, so the test exercises the decision and fails on the old measure (as written it only pinned `withoutDisabledFeatures`, which passes today); corrected the claim that a switched-off screen's address "ends the same".
**Files:** `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserHistory.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutesTest.kt`,
`presentation/src/wasmJsMain/CLAUDE.md`

## Problem

A browser Forward to an entry with no recorded `NavigationState` is opened from its address, but only if the address
stands for a stack exactly as deep as the entry. `BrowserHistory.goForward` (`BrowserHistory.kt:204-218` at 396acf923):

```kotlin
?: (path ?: entry?.path)
    ?.let {
        BrowserRoutes.resolve(
            path = it,
            ...
            current = viewModel.navigator.navigationState,
            ...
        )
    }
    ...
    ?.takeIf { BrowserRoutes.entryCount(it) == to + 1 }
```

An entry has no recorded state when it was pushed in passing, or when the entry under it was rewritten in place
(`synchronize` clears the states above: `entries[depth] = entries[depth].copy(state = null)`), or when the list was
padded with `Entry(path = null)`.

`BrowserRoutes.resolve` always builds on the songs (`BrowserRoutes.kt`: `val home = current.copy(backStack =
listOf(CampfireDestination.Songs), …)`). The cut to the real home (`withoutDisabledFeatures`, `rootedAt`) only happens
later, in `Navigator.restoreNavigationState`. The depth check therefore counts a Songs screen that will not be there.
Usually that cancels out: with Songs off, `tuner` resolves to `[Songs, Tuner]`, two entries, and becomes `[Metronome,
Tuner]`, also two. But with every main screen switched off, the home is Settings. The app on Settings with a tab other
than General makes two entries (`settings/general`, `settings/preferences`), while `resolve("settings/preferences")`
gives `[Songs, Settings]` on that tab, three entries. A Forward from General to that entry is refused, and the
browser is sent back, though the app could open it exactly.

## Fix

Compare the depth of the state as it will be restored, not as resolved, and make that decision a pure function in
commonMain, where it can be tested (`goForward` is wasm-only and has no harness).

1. `BrowserRoutes.kt`: add, beside `resolve`, with KDoc carrying the comment that now sits above the `takeIf` in
   `goForward`, extended by a sentence: the depth is that of the stack the app will actually be on, rooted on the home
   screen the switches leave ([withoutDisabledFeatures], which `Navigator.restoreNavigationState` applies), since
   `resolve` builds every address on the songs.

   ```kotlin
   fun resolveForward(
       path: String,
       depth: Int,
       songs: List<Song>,
       setlists: List<Setlist>,
       current: NavigationState,
       isPerformanceModeEnabled: Boolean,
       features: FeatureSwitches,
   ) = resolve(path = path, songs = songs, setlists = setlists, current = current, isPerformanceModeEnabled = isPerformanceModeEnabled)
       ?.takeIf { entryCount(it.withoutDisabledFeatures(features)) == depth + 1 }
   ```

   It returns the state as resolved (the restore cuts it), so nothing else changes.
2. `BrowserHistory.goForward`: replace the `resolve(...)` call and its `takeIf` with `BrowserRoutes.resolveForward(...,
   depth = to, features = FeatureSwitches.of(viewModel.preferencesController.userPreferencesState.value.data))`, the
   same source `Navigator.restoreNavigationState` reads the switches from (the repository's state, not
   `userPreferences`), read once with the other inputs at the top of the function.
3. `presentation/src/wasmJsMain/CLAUDE.md`, the Forward bullet: "an address is only used if it is exactly as deep as
   the entry" → "…as deep as the entry once rooted on the home screen the switches leave (`BrowserRoutes.resolveForward`)".

Why not have `resolve` take the switches and build on `TopLevel.home(features)` instead: `resolve` would then have to
avoid stacking a tab on itself (`settings/…` with Settings as home, `metronome` with Metronome as home) and keep the
old-bottom rule of `rootedAt`. That duplicates `rootedAt` inside `resolve`, and `navigateToBrowserAddress` and the
launch would have to change with it. The cut already exists and is what the restore applies, so measuring through it
makes the check agree with the restore by construction. An address naming a screen that is switched off now measures
shallower than its entry and is refused, as it should be: the app stays where it was and `synchronize` takes the
browser back. Today it passes the check and its cut state is restored, which can move the app off the screen it was on
to a shallower one before the browser is taken back.

## Tests

`BrowserRoutesTest.kt` (commonTest), against `resolveForward`. Build the switches with the `FeatureSwitches`
constructor (as `FeatureDestinationsTest`'s private `features` helper does; that one is not reachable from here):

- `a settings tab's address is opened forward with every main screen switched off`: with every main screen off and
  `current` a Settings-only state on General, `resolveForward("settings/preferences", depth = 1, …)` is not null and
  restores to `[Settings]` on Preferences. It is null with today's measure (`entryCount` of the resolved state is 3).
- `a tool's address is opened forward with the songs switched off`: `tuner`, depth 1 → not null (the case that
  already works).
- `a switched-off screen's address is refused going forward`: Songs off, `song/a`, depth 1 → null.
- `an address deeper or shallower than its entry is refused`: everything on, `settings/preferences` at depth 2 → not
  null, at depth 1 → null (the refusal the existing comment describes).
