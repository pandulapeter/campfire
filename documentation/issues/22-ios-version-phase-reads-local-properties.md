<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 22 — The iOS version build phase ignores `local.properties`

## What goes wrong at release time

The root `CLAUDE.md` makes an unconditional promise: "**`local.properties` overrides any of them, and is never
committed.**" It is not true on iOS. The Xcode build phase that stamps `CFBundleShortVersionString` and
`CFBundleVersion` into the built `Info.plist` reads `gradle.properties` and nothing else, so
`campfire.ios.buildNumber` cannot be overridden the way every other `campfire.*` property can.

The concrete failure is a TestFlight rebuild. An upload that App Store Connect rejects — an export compliance answer,
a missing icon, a binary that fails its static analysis — has to go up again with a **higher build number**, and the
obvious way to do that without a commit is `campfire.ios.buildNumber=35` in `local.properties`. The build takes it
for the Kotlin framework (Gradle reads it) and ignores it for the `Info.plist`, so the `.ipa` carries `34` again and
App Store Connect answers "The provided entity includes an attribute with a value that has already been used" — with
no hint that two different numbers were in play. The only way forward is a commit that changes `gradle.properties`,
which is a version bump in the repository for a build that was never released.

The same gap hides `campfire.versionName`: a release candidate built with an overridden version name ships with the
committed one in its plist.

## Cause

`app/ios/iosApp/iosApp.xcodeproj/project.pbxproj:207`, the `shellScript` of the "Set version from gradle.properties"
build phase, verified at HEAD `984861e4`. Unescaped, the part that matters:

```sh
PROPERTIES_FILE="$SRCROOT/../../../gradle.properties"
…
read_property() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$PROPERTIES_FILE" | tail -n 1 | tr -d "[:space:]"
}

VERSION_NAME=$(read_property "campfire\.versionName")
BUILD_NUMBER=$(read_property "campfire\.ios\.buildNumber")
```

One file, named once. The `tail -n 1` is already there for the right reason — the last value of a property wins — so
the fix is to give `sed` the second file rather than to restructure anything.

`local.properties` sits next to `gradle.properties`: `SRCROOT` is `app/ios/iosApp`, so `$SRCROOT/../../..` is the
repository root, which is `settingsDir` for `settings.gradle.kts:73-80`, the loader that gives Gradle the same
override. The iOS release workflow already writes that file — `ios-publish.yml:51-54` puts the Dropbox key there —
so the file exists in CI as well as on a developer's machine.

## The change

One line of `project.pbxproj`. Replace line 207 in full with:

```
			shellScript = "set -eu\n\nPROPERTIES_FILE=\"$SRCROOT/../../../gradle.properties\"\nLOCAL_PROPERTIES_FILE=\"$SRCROOT/../../../local.properties\"\nBUILT_PLIST=\"$TARGET_BUILD_DIR/$INFOPLIST_PATH\"\n\nif [ ! -f \"$PROPERTIES_FILE\" ]; then\n  echo \"error: gradle.properties not found at $PROPERTIES_FILE\"\n  exit 1\nfi\n\n# local.properties overrides any campfire.* property here exactly as it does for Gradle: it is read second and the\n# last of the two values wins. Without it a rebuild for TestFlight keeps the committed CFBundleVersion and App Store\n# Connect turns the upload down as a duplicate.\nread_property() {\n  if [ -f \"$LOCAL_PROPERTIES_FILE\" ]; then\n    sed -n \"s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p\" \"$PROPERTIES_FILE\" \"$LOCAL_PROPERTIES_FILE\" | tail -n 1 | tr -d \"[:space:]\"\n  else\n    sed -n \"s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p\" \"$PROPERTIES_FILE\" | tail -n 1 | tr -d \"[:space:]\"\n  fi\n}\n\nVERSION_NAME=$(read_property \"campfire\\.versionName\")\nBUILD_NUMBER=$(read_property \"campfire\\.ios\\.buildNumber\")\n\nif [ -z \"$VERSION_NAME\" ]; then\n  echo \"error: campfire.versionName is missing from gradle.properties\"\n  exit 1\nfi\n\nif [ -z \"$BUILD_NUMBER\" ]; then\n  echo \"error: campfire.ios.buildNumber is missing from gradle.properties\"\n  exit 1\nfi\n\n/usr/libexec/PlistBuddy -c \"Set :CFBundleShortVersionString $VERSION_NAME\" \"$BUILT_PLIST\"\n/usr/libexec/PlistBuddy -c \"Set :CFBundleVersion $BUILD_NUMBER\" \"$BUILT_PLIST\"\n\necho \"note: version set from gradle.properties: $VERSION_NAME ($BUILD_NUMBER)\"\n";
```

Unescaped, that is the same script with a second properties file and one comment:

```sh
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
…
```

Two details that decide whether this works:

- **The `if`, rather than building a file list in a variable.** `set -eu` is on, so a bare
  `[ -f "$LOCAL_PROPERTIES_FILE" ] && FILES="…"` would abort the script when the file is absent, which is the normal
  case; and an unquoted `$FILES` passed to `sed` would break on a `SRCROOT` containing a space, which a checkout under
  "My Projects" has.
- **`sed` over two files with one `tail -n 1`.** `sed -n` concatenates its inputs, `local.properties` comes second,
  so the last match is its value whenever it has one and `gradle.properties`' otherwise. That is exactly the rule
  Gradle follows, and it needs no second invocation.

The error messages keep naming `gradle.properties`, since that is where the property must be declared; an override is
optional by definition.

Rename the build phase while here, since it now reads two files: line 200 becomes

```
			name = "Set version from the Gradle properties";
```

and the two comment occurrences of the old name — line 107
(`A5786ACC05B3A85784A4B7DD /* Set version from gradle.properties */,` in the target's `buildPhases`) and line 190
(the object's own header comment) — are renamed to match. Xcode regenerates those comments from `name`, so leaving
them stale would only mean the next person who opens the project in Xcode gets an unrelated diff.

## Tests

None; this is a build phase and the root `CLAUDE.md` restricts tests to pure logic. Run the standard suite to confirm
nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

**Needs a Mac with Xcode.** There is no way to run an Xcode build phase anywhere else.

```bash
cd /path/to/Campfire

# 1. The project still parses, and the phase is named as intended.
plutil -lint app/ios/iosApp/iosApp.xcodeproj/project.pbxproj
xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -list

# 2. Without an override, nothing changes: the plist carries what gradle.properties says (4.3.0 / 34 at HEAD).
xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -configuration Release \
  -sdk iphonesimulator -arch arm64 SYMROOT=/tmp/campfire-ios OBJROOT=/tmp/campfire-ios-obj CODE_SIGNING_ALLOWED=NO build
plutil -p /tmp/campfire-ios/Release-iphonesimulator/Campfire.app/Info.plist | grep -E 'CFBundleVersion|CFBundleShortVersionString'

# 3. With an override, the plist follows it — this is the whole change.
echo 'campfire.ios.buildNumber=99' >> local.properties
xcodebuild … build     # the same command as above
plutil -p /tmp/campfire-ios/Release-iphonesimulator/Campfire.app/Info.plist | grep CFBundleVersion   # must print 99

# 4. A local.properties that does not exist must still build.
git stash -- local.properties || rm local.properties
xcodebuild … build
```

Step 3 printing `34` is the bug; printing `99` is the fix. The build phase also echoes `note: version set from
gradle.properties: …`, which Xcode shows in the log — a second place to read the two numbers.

The TestFlight half (App Store Connect accepting the re-upload) needs an Apple Developer account and cannot be
proven until the app is submitted; steps 2-4 are the whole of what the change does.

## Docs

Root `CLAUDE.md`, the Build section. The promise is already written as unconditional and becomes true rather than
changing:

> **`local.properties` overrides any of them, and is never committed.** `settings.gradle.kts` loads it and writes
> each entry onto every project before it is configured, so no build file knows the mechanism exists — they all just
> read a property.

Add one clause to the sentence that follows the Xcode project's arrangement, in the `ios-publish.yml` bullet:

> The Xcode project starts Gradle itself and passes it no properties, so the sync key is written into
> `local.properties` there.

becomes

> The Xcode project starts Gradle itself and passes it no properties, so the sync key is written into
> `local.properties` there — which the version build phase reads too, after `gradle.properties` and with the last
> value winning, so a build number can be raised for a re-upload without a commit.

And in the Build section's first bullet, the sentence about the iOS version:

> The iOS version and build number are `campfire.versionName` and `campfire.ios.buildNumber`, written into the built
> `Info.plist` by a build phase of the Xcode project, which sets no version of its own.

gains ", reading `gradle.properties` and then `local.properties` the way Gradle does".

## Files touched

- `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
- `CLAUDE.md`

## Depends on

Nothing. Touches the same file as plan 24, in different sections (the shell script phase vs. the two build
configurations); land them in either order.

## Rules

- Load the `code-style` skill before the first edit.
- Edit `project.pbxproj` as text, not by opening Xcode: Xcode rewrites unrelated parts of the file and the diff stops
  being reviewable.
- No Kotlin, no strings, no `commonMain` changes here.
- Everything configurable stays a `campfire.*` property, and `local.properties` overriding it is the rule this change
  restores rather than invents.
