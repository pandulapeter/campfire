<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Record the startup profile from the cold start alone

**Challenged:** amended — JUnit 4 does not run `@Test`s in declaration order and the collections share the app's data, so whichever runs first gets the one-time first run (demo planting, onboarding); pin the order so `journeys()` takes it and `startup()` records an ordinary cold start, and have `startup()` wait for the list's first card, not only the first frame.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android
**Files:** `app/baselineprofile/src/main/kotlin/com/pandulapeter/campfire/baselineprofile/BaselineProfileGenerator.kt`,
`app/baselineprofile/CLAUDE.md`, `app/android/src/main/generated/baselineProfiles/*` (re-recorded)

## Problem

`BaselineProfileGenerator` runs the whole journey in one `rule.collect(packageName = PACKAGE_NAME,
includeInStartupProfile = true) { … }`, so `startup-prof.txt` is byte-identical to `baseline-prof.txt` (47,822 lines each).
With `dexLayoutOptimization = true` the startup DEX section then holds the onboarding, Settings, Setlists and song details too,
spreading the classes a cold start needs over more pages.

## Fix

Two `@Test`s in one class, run in a fixed order (`@FixMethodOrder(MethodSorters.NAME_ASCENDING)` with names that sort
`journeys` first, e.g. `a_journeys` / `b_startup`): JUnit 4's default order is a hash order, and `collect` never clears the
app's data between its iterations or between tests (the generator already relies on that), so only the first collection
ever sees the first run.

- `journeys()`, `includeInStartupProfile = false`: today's whole block, the onboarding walk with **Next** and the demo
  library's planting included; that code runs once per installation and has no place in the startup DEX section.
- `startup()`, `includeInStartupProfile = true`: `pressHome()`, `startActivityAndWait()`, then wait for the first demo
  card (`By.textContains("Rising Sun")`), so the library read, the indexing and the cards' first composition are in the
  profile, not just the first frame `startActivityAndWait` returns at. If an onboarding still appears (the first test
  skipped), dismiss it with **Skip** rather than walking it.

Update the module `CLAUDE.md` ("One `BaselineProfileRule.collect` with `includeInStartupProfile`") with the two
collections and why they are ordered. Re-record on an emulator (`./gradlew :app:android:generateBaselineProfile`) and
commit the output; the plugin merges both into `baseline-prof.txt` and only `startup()` into `startup-prof.txt`. This also
picks up the review's fix that walks the tools behind a phone's Tools item.

## Tests

None; the generator asserts nothing. The re-recorded `startup-prof.txt` must be much smaller than `baseline-prof.txt`, hold no
rules for the Settings screen's composables (`screens/settings/SettingsScreen*` and its tabs; `LibrarySummary` and
`DemoLibraryOffer` live in that package but are read at startup, so a bare `screens/settings` match is no failure), and `baseline-prof.txt` must hold rules for `screens/metronome`, `screens/tuner` and `screens/chords`.

## Manual check

Needs an emulator. Compare cold start (`adb shell am start -W`) on a low-end emulator profile before and after; drop the split
if it is not faster.
