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

The Baseline Profile generator for `:app:android`: a plain `com.android.test` module (AGP compiles its Kotlin, as it
does `:app:android`'s) with the `androidx.baselineprofile` plugin, targeting the app's `nonMinifiedRelease` build.
Without it the release APK carried only the profiles its libraries ship, none of which cover the app's own code.

`BaselineProfileGenerator` is one `BaselineProfileRule.collect` with `includeInStartupProfile`, walking what every
launch goes through: a cold start (on a fresh installation that is a first run, so the demo library is planted and the
welcome sheet is dismissed with **Get started**), a fling down and up the song list, *House of the Rising Sun* opened,
flung and left, the **Setlists** tab and the demo setlist, and **Settings**. Screens are found by their visible English
text, which Compose exposes to UI Automator, so the app carries no test tags; every step waits up to five seconds and
is skipped where its text never appears, so that a renamed string costs a step of the profile rather than the run. The
collection runs the journey several times without clearing the app's data, which is why the welcome sheet is only
looked for rather than expected.

It is not a test: it asserts nothing and CI never runs it. The profile is recorded by hand and committed, and
`:app:android` is configured never to generate it during a build (`automaticGenerationDuringBuild = false`), so
`assembleRelease` needs no device.

**Recording it:**

1. Boot the emulator: `~/Library/Android/sdk/emulator/emulator -avd Resizable_Experimental -no-snapshot-load
   -no-boot-anim`, and wait for `adb shell getprop sys.boot_completed` to be `1`. If another emulator is running, set
   `ANDROID_SERIAL` to this one. The emulator has to be in English.
2. **Uninstall `com.pandulapeter.campfire`** (`adb uninstall com.pandulapeter.campfire`, never the `.debug` one) if it
   is there: the generator installs its build over that id, and a build signed with another key, or an old one with a
   library of its own, fails the install or starts the journey somewhere other than a first run.
3. `./gradlew :app:android:generateBaselineProfile`. It writes `baseline-prof.txt` and `startup-prof.txt` into
   `app/android/src/main/generated/baselineProfiles`; commit both. They should hold `Lcom/pandulapeter/campfire/`,
   `androidx/navigation3` and `org/koin` rules.

Regenerate it when the startup path or the main screens change noticeably. A stale profile is only less useful, never
wrong: rules naming code that no longer exists are ignored.

To see what it is worth on a device: install the release APK, `adb shell am broadcast -a
androidx.profileinstaller.action.INSTALL_BASELINE_PROFILE -n
com.pandulapeter.campfire/androidx.profileinstaller.ProfileInstallReceiver`, wait about ten seconds for the receiver to
write it (the broadcast reports `result=0` either way, and a `force-stop` before the write loses it), then
`adb shell cmd package compile -f -m speed-profile com.pandulapeter.campfire` — `adb shell dumpsys package dexopt`
should now say `speed-profile` rather than `verify` — and average `TotalTime` of `adb shell am start -W -n
com.pandulapeter.campfire/.CampfireMainActivity` over a few force-stopped starts, the first one aside, since it plants
the demo library.
