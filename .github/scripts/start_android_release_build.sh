# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

# Starts the Android release build on the device adb is connected to (ANDROID_SERIAL picks one of several), for
# tests.yml. R8's full mode removes what a library's keep rules forget (a Room database's constructor, which a startup
# initializer creates it with), and what it breaks fails only at run time, most often as the process starts. So the APK
# is installed fresh and started as a first run (the demo library imported, which reaches serialization, Koin, and the
# ChordPro parser), every app link the launcher shortcuts and the widgets open is followed, and the app is started
# again, once as a second launch and once by a link; after each step the process has to be alive and the crash buffer
# empty. Nothing is tapped: the onboarding, which refuses links, is left with Back from its first page.
#
#     bash .github/scripts/start_android_release_build.sh APK

APK="$1"
PACKAGE=com.pandulapeter.campfire
ACTIVITY="$PACKAGE/.CampfireMainActivity"
SONG=traditional_american-house_of_the_rising_sun
SETLIST_SONG=getting_started/traditional_american-home_on_the_range_my_western_home
if [ ! -f "$APK" ]; then
  echo "::error::No APK at '$APK'." >&2
  exit 2
fi

# Waits for the step to settle, then fails it when the app died or anything crashed meanwhile.
check() {
  sleep "${2:-5}"
  CRASHES=$(adb logcat -b crash -d)
  if [ -n "$CRASHES" ] || ! adb shell pidof "$PACKAGE" > /dev/null; then
    echo "::error::The Android release build did not survive: $1."
    echo "$CRASHES"
    exit 1
  fi
  echo "Survived: $1."
}

open_link() {
  adb shell am start -a android.intent.action.VIEW -d "campfire://$1" "$PACKAGE" > /dev/null 2>&1 || exit 1
  check "campfire://$1"
}

adb uninstall "$PACKAGE" > /dev/null 2>&1 || true
adb install "$APK" || exit 1
adb logcat -b crash -c

adb shell am start -n "$ACTIVITY" > /dev/null || exit 1
check "the first launch" 15
adb shell input keyevent KEYCODE_BACK
check "leaving the onboarding"

for LINK in setlists metronome tuner chords scales settings "song/$SONG" "setlist/$SETLIST_SONG" "song/$SONG/edit"; do
  open_link "$LINK"
done

adb shell am force-stop "$PACKAGE"
adb shell am start -n "$ACTIVITY" > /dev/null || exit 1
check "a second launch" 10

# Last, since the editor they open keeps its text over a restart and refuses every link after it.
adb shell am force-stop "$PACKAGE"
open_link "song/$SONG"
open_link paste
open_link new
echo "The Android release build started and followed every link."
