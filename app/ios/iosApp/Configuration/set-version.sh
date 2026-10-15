#!/bin/sh
#
# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.
#
# Writes campfire.versionName and campfire.buildNumber into the Info.plist of the target being built. The last build
# phase of both the app and the share extension: App Store Connect refuses an extension whose version differs from its
# app's, and the app's phase cannot write into the embedded extension's Info.plist, which is signed by then.
set -eu

PROPERTIES_FILE="$SRCROOT/../../../gradle.properties"
LOCAL_PROPERTIES_FILE="$SRCROOT/../../../local.properties"
BUILT_PLIST="$TARGET_BUILD_DIR/$INFOPLIST_PATH"

if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "error: gradle.properties not found at $PROPERTIES_FILE"
  exit 1
fi

# local.properties overrides any campfire.* property here exactly as it does for Gradle: it is read second and the
# last of the two values wins. Without it a rebuild for TestFlight keeps the committed CFBundleVersion and App Store
# Connect turns the upload down as a duplicate.
read_property() {
  if [ -f "$LOCAL_PROPERTIES_FILE" ]; then
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$PROPERTIES_FILE" "$LOCAL_PROPERTIES_FILE" | tail -n 1 | tr -d "[:space:]"
  else
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$PROPERTIES_FILE" | tail -n 1 | tr -d "[:space:]"
  fi
}

VERSION_NAME=$(read_property "campfire\.versionName")
BUILD_NUMBER=$(read_property "campfire\.buildNumber")

if [ -z "$VERSION_NAME" ]; then
  echo "error: campfire.versionName is missing from gradle.properties"
  exit 1
fi

if [ -z "$BUILD_NUMBER" ]; then
  echo "error: campfire.buildNumber is missing from gradle.properties"
  exit 1
fi

/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION_NAME" "$BUILT_PLIST"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $BUILD_NUMBER" "$BUILT_PLIST"

echo "note: version set from gradle.properties: $VERSION_NAME ($BUILD_NUMBER)"
