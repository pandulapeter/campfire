# 47 · The root CLAUDE.md says the iOS version lives in the Xcode project; it comes from gradle.properties

**Severity:** docs (iOS. Misleads whoever bumps the version for a release) · **Area:** root `CLAUDE.md`

## Symptom
A maintainer bumping the version follows the root `CLAUDE.md` ("The iOS version lives in the Xcode project.") and looks
in Xcode for a version to raise. There is none: `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` are deliberately
absent from `project.pbxproj`.

## Cause
`CLAUDE.md:216-217`: "Dependency versions in `gradle/libs.versions.toml` (…). The iOS version lives in the Xcode
project." The "Set version from gradle.properties" build phase (`app/ios/iosApp/iosApp.xcodeproj/project.pbxproj:185-202`)
reads `campfire.versionName` and `campfire.ios.buildNumber` (`gradle.properties:18-20`) and writes them into the built
`Info.plist` with PlistBuddy. `app/ios/CLAUDE.md:17` describes this correctly. The list of properties in the next
bullet (`CLAUDE.md:218-220`) also leaves out the iOS build number and `campfire.web.precompress`.

## Fix
Root `CLAUDE.md`:

1. `:216-217`, replace "The iOS version lives in the Xcode project." with "The iOS version and build number are
   `campfire.versionName` and `campfire.ios.buildNumber`, written into the built `Info.plist` by a build phase of the
   Xcode project, which sets no version of its own."
2. `:219-220`, replace "the app version and version code, the Android release signing values, and the Dropbox app
   key." with "the app version, the Android version code and the iOS build number, the Android release signing values,
   the Dropbox app key, and whether the web distribution is precompressed."

## Tests
None (docs).

## Verify
Read against `gradle.properties` and the build phase's script. Nothing to compile.

## Docs
This is the doc change.

## Touches
- `CLAUDE.md`

## Depends on
None. 48 edits another sentence of the same file.
