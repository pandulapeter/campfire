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

- One `BaselineProfileRule.collect` with `includeInStartupProfile`: cold start (a first run plants the demo library and
  walks the onboarding with **Next** to **Start playing**), song list flings, a demo song opened and flung, Setlists and
  the demo setlist, the tools (**Tools** on a phone's bar opens the Metronome, then its Tuner and Chords tabs; a tall
  rail gives each its own item; the tuner asks for nothing without a tap), and Settings.
- Screens are found by their visible **English** text (no test tags). Each step waits up to 5 s and is skipped if the
  text never appears, so a renamed string costs a step, not the run. The journey repeats without clearing data, so the
  onboarding is only looked for, not expected.

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
   `androidx/navigation3`, and `org/koin` rules.

## Measuring it

Install the release APK; `adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_BASELINE_PROFILE -n
com.pandulapeter.campfire/androidx.profileinstaller.ProfileInstallReceiver`; wait ~10 s (it reports `result=0` either
way, and a `force-stop` before the write loses it); `adb shell cmd package compile -f -m speed-profile
com.pandulapeter.campfire` (`dumpsys package dexopt` should say `speed-profile`); then average `TotalTime` of
`adb shell am start -W -n com.pandulapeter.campfire/.CampfireMainActivity` over several force-stopped starts, ignoring the
first (it plants the demo library).
