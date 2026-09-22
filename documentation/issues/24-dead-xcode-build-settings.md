<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 24 — Two Xcode build settings that do nothing

## What goes wrong at release time

The project sets an app category and a display name that never reach the built app. The display name is harmless —
`CFBundleName` already resolves to `Campfire` — but the category is not: **`LSApplicationCategoryType` is what App
Store Connect reads to pre-fill the app's primary category**, and it is what the Mac App Store uses to file the app
under Music. Missing, the submission arrives with no category, and the value that was set in the project in good
faith is not the one anybody has to go and correct by hand.

The deeper cost is the one a reader pays: two settings that look like the app's identity is declared in the project
file, when in fact it is declared in `Info.plist` and these two lines are inert. The next person to change the
category changes the wrong one.

## Cause

`app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`, verified at HEAD `984861e4`. Lines 360-361 (the Debug
configuration) and 393-394 (Release), identical in both:

```
				INFOPLIST_FILE = iosApp/Info.plist;
				INFOPLIST_KEY_CFBundleDisplayName = "Campfire";
				INFOPLIST_KEY_LSApplicationCategoryType = "public.app-category.music";
```

Every `INFOPLIST_KEY_*` build setting is an input to Xcode's **generated** `Info.plist`, which is only produced when
`GENERATE_INFOPLIST_FILE = YES`. This project never sets it:

```
$ grep -n "GENERATE_INFOPLIST_FILE" app/ios/iosApp/iosApp.xcodeproj/project.pbxproj
(no matches)
```

It uses a checked-in `Info.plist` instead (`INFOPLIST_FILE = iosApp/Info.plist`), and that file declares neither key:

```
$ grep -c "CFBundleDisplayName\|LSApplicationCategoryType" app/ios/iosApp/iosApp/Info.plist
0
```

So the built `Release` `Info.plist` has neither — which is also why nobody has noticed the display name is missing:
`CFBundleName` is `$(PRODUCT_NAME)`, `PRODUCT_NAME` is `${APP_NAME}` and `Configuration/Config.xcconfig` sets
`APP_NAME=Campfire`, so the home screen already reads *Campfire*.

## The change

Move the one that matters into `Info.plist` as a literal, and delete both build settings.

### 1. `app/ios/iosApp/iosApp/Info.plist`

Add, in the alphabetical neighbourhood of the other `LS*` keys (next to `LSRequiresIPhoneOS`), with the reason as a
comment in the voice of the file's existing ones:

```xml
	<!-- The primary category App Store Connect pre-fills the listing from, and what the Mac App Store files the app
	     under. It has to be in this file rather than as an INFOPLIST_KEY_* build setting, because those are inputs to
	     the Info.plist Xcode generates and this target ships a checked-in one. -->
	<key>LSApplicationCategoryType</key>
	<string>public.app-category.music</string>
```

`CFBundleDisplayName` is **not** moved. It would only repeat what `CFBundleName` already resolves to, and a second
name to keep in step with `APP_NAME` is a thing that goes stale — it is deleted rather than relocated. If a display
name that differs from the bundle name is ever wanted, it belongs here, spelled out, next to the other keys.

### 2. `project.pbxproj`

Delete the four lines — 360-361 and 393-394 — leaving the surrounding settings untouched. Debug becomes:

```
				INFOPLIST_FILE = iosApp/Info.plist;
				IPHONEOS_DEPLOYMENT_TARGET = 15.3;
```

and Release the same. Nothing else in either configuration moves; in particular `INFOPLIST_FILE` stays, since it is
what makes the checked-in file the one that is used.

Edit the file as text. Opening the project in Xcode to delete a build setting rewrites unrelated parts of
`project.pbxproj` and the diff stops being reviewable.

## Tests

None; there is no logic here. Run the standard suite to confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

**Needs a Mac with Xcode.**

```bash
cd /path/to/Campfire

# The project and the plist still parse.
plutil -lint app/ios/iosApp/iosApp.xcodeproj/project.pbxproj app/ios/iosApp/iosApp/Info.plist

# Build the Release configuration the way ios-publish.yml does.
xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -configuration Release \
  -sdk iphonesimulator -arch arm64 SYMROOT=/tmp/campfire-ios OBJROOT=/tmp/campfire-ios-obj \
  CODE_SIGNING_ALLOWED=NO build

# The category is now in the built plist — before the change this prints nothing.
plutil -p /tmp/campfire-ios/Release-iphonesimulator/Campfire.app/Info.plist | grep LSApplicationCategoryType

# And the app is still called Campfire: CFBundleName resolves through PRODUCT_NAME / APP_NAME.
plutil -p /tmp/campfire-ios/Release-iphonesimulator/Campfire.app/Info.plist | grep CFBundleName
```

Then install it in the simulator and confirm the home screen label still reads *Campfire*:

```bash
xcrun simctl install booted /tmp/campfire-ios/Release-iphonesimulator/Campfire.app
```

The App Store Connect half — the category arriving pre-filled on the listing — needs an Apple Developer account and a
real upload, and cannot be checked before the app is submitted. The `plutil` line above is what proves the change.

Also confirm `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` still passes; it does not read the project
file, but it is the cheap check that the iOS target as a whole is untouched.

## Docs

None. No `CLAUDE.md` mentions either setting: the root one describes the iOS version and build number
("`campfire.versionName` and `campfire.ios.buildNumber`, written into the built `Info.plist` by a build phase of the
Xcode project, which sets no version of its own"), which is a different mechanism and stays true. The `Info.plist`
carries its own explanation in the comment added above, which is where this file's documentation lives.

## Files touched

- `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
- `app/ios/iosApp/iosApp/Info.plist`

## Depends on

Nothing. Touches the same file as plan 22, in a different section (the two `XCBuildConfiguration` objects vs. the
shell script build phase); land them in either order.

## Rules

- Load the `code-style` skill before the first edit; the XML comment follows the voice of the ones already in
  `Info.plist` — prose, the reason rather than the restatement.
- Never leave anything unused behind: the build settings go, they are not commented out.
- No Kotlin, no strings, no `commonMain` changes here.
