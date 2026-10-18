<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Record the startup profile from the cold start alone

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** Android
**Files:** `app/baselineprofile/src/main/kotlin/com/pandulapeter/campfire/baselineprofile/BaselineProfileGenerator.kt`,
`app/baselineprofile/CLAUDE.md`, `app/android/src/main/generated/baselineProfiles/*` (re-recorded)

## Problem

`BaselineProfileGenerator` runs the whole journey in one `rule.collect(packageName = PACKAGE_NAME,
includeInStartupProfile = true) { … }`, so `startup-prof.txt` is byte-identical to `baseline-prof.txt` (47,822 lines each).
With `dexLayoutOptimization = true` the startup DEX section then holds the onboarding, Settings, Setlists and song details too,
spreading the classes a cold start needs over more pages.

## Fix

Two `@Test`s: `startup()` cold-starts to the first frame of the song list (walking the onboarding if it appears) with
`includeInStartupProfile = true`; `journeys()` walks everything else with `includeInStartupProfile = false`. Update the
module `CLAUDE.md` ("One `BaselineProfileRule.collect` with `includeInStartupProfile`"). Re-record on an emulator
(`./gradlew :app:android:generateBaselineProfile`) and commit the output; this also picks up the review's fix that walks the
tools behind a phone's Tools item.

## Tests

None; the generator asserts nothing. The re-recorded `startup-prof.txt` must be much smaller than `baseline-prof.txt`, hold no
`screens/settings` rules, and `baseline-prof.txt` must hold rules for `screens/metronome`, `screens/tuner` and `screens/chords`.

## Manual check

Needs an emulator. Compare cold start (`adb shell am start -W`) on a low-end emulator profile before and after; drop the split
if it is not faster.
