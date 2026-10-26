<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:baselineprofile

The Baseline Profile generator for `:app:android`: a `com.android.test` module with the `androidx.baselineprofile`
plugin, targeting `nonMinifiedRelease`. Not a test: it asserts nothing and CI never runs it.

## The journey (`BaselineProfileGenerator`)

- Two collections, run in name order (`@FixMethodOrder`; JUnit's default is a hash order). `a_journeys`, for the Baseline
  Profile only: cold start (a first run plants the demo library and walks the onboarding with **Next** to **Start
  playing**), song list flings, a demo song opened, its **About the song** sheet and its editor (**Edit song file**,
  nothing typed) opened from the song's menu, the song flung, the songs search with `rising` typed, Setlists and
  the demo setlist, the tools (**Tools** on a phone's bar opens the Metronome, then its Tuner, Chords, and Scales buttons; a tall
  rail gives each its own item; the tuner asks for nothing without a tap), and Settings. `b_startup`, with
  `includeInStartupProfile`: a cold start until the first demo card, nothing else, so the startup profile (which lays out
  the DEX with `dexLayoutOptimization`) holds only what a launch needs. Data is never cleared between them, so the walk
  must run first to be the one that sees the first run; an onboarding left for `b_startup` is skipped, not walked.
- Screens are found by their visible **English** text (no test tags). A step still waits up to 5 s and is skipped if
  the text never appears, and `generateBaselineProfile` then fails naming each walked screen the written profile has no
  rule for (the files are written regardless, to compare). A new step adds its screen's class to the list in
  `app/android/build.gradle.kts`.

## Why it is recorded by hand, never in CI

- An emulator in the release run is slow and unreliable, and a profile is only an optimization; it must not block a
  Play submission.
- **A release builds its tag**: a profile recorded during the run could only be committed after the tag, so the APK
  would lack it or be built from a commit the tag doesn't name.
- The files are tens of thousands of churning lines, and CI has no write access to the repository.

The prepare-release skill regenerates it before every release. A stale profile is only less useful, never wrong.

## Recording it

1. Boot the emulator: `~/Library/Android/sdk/emulator/emulator -avd Resizable_Experimental -no-snapshot-load
   -no-boot-anim`; wait for `adb shell getprop sys.boot_completed` to be `1`. Set `ANDROID_SERIAL` if another emulator
   runs. The emulator must be in English.
2. **Uninstall `com.pandulapeter.campfire`** (`adb uninstall com.pandulapeter.campfire`, never the `.debug` one): the
   generator installs over that id, and another signature or an existing library fails the install or skips the first
   run.
3. `./gradlew :app:android:generateBaselineProfile` writes `baseline-prof.txt` and `startup-prof.txt` into
   `app/android/src/main/generated/baselineProfiles`; commit both. They should hold `Lcom/pandulapeter/campfire/`,
   `androidx/navigation3`, and `org/koin` rules; that every walked screen has one is checked by the task itself.

## Measuring it

Install the release APK; `adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_BASELINE_PROFILE -n
com.pandulapeter.campfire/androidx.profileinstaller.ProfileInstallReceiver`; wait ~10 s (it reports `result=0` either
way, and a `force-stop` before the write loses it); `adb shell cmd package compile -f -m speed-profile
com.pandulapeter.campfire` (`dumpsys package dexopt` should say `speed-profile`); then average `TotalTime` of
`adb shell am start -W -n com.pandulapeter.campfire/.CampfireMainActivity` over several force-stopped starts, ignoring the
first (it plants the demo library).
