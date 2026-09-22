# 09 · The iOS build has no privacy manifest, so App Store Connect refuses the first upload

**Severity:** store/policy (iOS. Certain on the first App Store or TestFlight upload: App Store Connect has refused a
build whose binary references a required-reason API with no declaration (ITMS-91053) since 1 May 2024. It is not a
warning. Nothing breaks for the sideloaded `.ipa` that `ios-publish.yml` attaches today) · **Area:** `:app:ios` (the
Xcode project in `app/ios/iosApp`)

## Symptom
Once `ios-publish.yml` becomes the TestFlight upload (its header says it will), App Store Connect answers the
upload with "ITMS-91053: Missing API declaration - Your app's code in the "Campfire" file references one or more
APIs that require reasons, including the following API categories: NSPrivacyAccessedAPICategoryFileTimestamp". The
build never reaches TestFlight.

## Cause
There is no `PrivacyInfo.xcprivacy` anywhere in the repository, and the app target's Resources phase
(`app/ios/iosApp/iosApp.xcodeproj/project.pbxproj:153-163`) copies only the storyboard and the two asset catalogs.

What the shipped binary actually references was checked on the last Release build for devices
(`DerivedData/.../Release-iphoneos/Campfire.app/Campfire`, 21 Sept, Kotlin 2.4.20, CMP 1.12.0, skiko 0.150.1,
ktor 3.5.2) with `nm -u`, and on the static `ComposeApp` framework it was linked from with `nm -A -u`:

| Symbol in the linked app | Category | Who references it |
|---|---|---|
| `_NSFileModificationDate` | File timestamp | the app's own Kotlin: `IosFileStorage.info`, `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.ios.kt:94`, for every file in the library (inside the app container) |
| `_stat` | File timestamp | skia (`SkOSFile_posix.o`, `SkOSFile_stdio.o`) and ICU (`umapfile.o`, which maps its data file out of the bundle) |
| `_fstat` | File timestamp | skia (`SkOSFile_posix.o`) |

Nothing else on Apple's list survives linking:
- `mach_absolute_time` is referenced only by skia's `libdng_sdk.dng_utils.o` inside the framework, and the linker
  drops that member (it is absent from the linked app). The reviewer's claim that `TimeSource.Monotonic` is backed
  by `mach_absolute_time` does not hold for Kotlin 2.4.20: the Kotlin code references `_clock_gettime`, which is not
  a required-reason API. So **no system boot time category**.
- No `systemUptime`, no `NSUserDefaults` (`_OBJC_CLASS_$_NSUserDefaults` is absent: the language and theme live in
  `preferences.json`), no `activeInputModes`, no disk space keys or `statfs`/`statvfs`, no `getattrlist` family.

The app also reads the size of files the user picked or opened in place (`IosFilePicker.kt:137`,
`attributesOfItemAtPath(...)[NSFileSize]` in `readImportedFile`), which is file metadata outside the container, so
`3B52.1` belongs next to `C617.1`.

No library brings a manifest of its own that could cover any of this: there is no `*.xcprivacy` in any Compose,
skiko, ktor, Koin or kotlinx artifact in `~/.gradle/caches`, and the `ComposeApp.framework` Gradle produces has none.
It would not help if it did: the framework is static (`app/ios/build.gradle.kts`, `isStatic = true`), so its code is
linked into the `Campfire` executable and its only bundle is the app's. The app's own manifest is the one that has to
declare everything the binary references.

## Fix
1. Create `app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy` (next to `Info.plist`), exactly:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!--
    This file is part of Campfire.
    Copyright (c) Pandula Péter 2017-2026.
    https://github.com/pandulapeter/campfire

    This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
    If a copy of the MPL was not distributed with this file, You can obtain one at
    https://mozilla.org/MPL/2.0/.
   -->
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
   	<!-- Nothing is tracked and nothing is collected: the only thing that leaves the device is sync, to a folder
   	     of the user's own, and no server of Campfire's is involved in it. -->
   	<key>NSPrivacyTracking</key>
   	<false/>
   	<key>NSPrivacyTrackingDomains</key>
   	<array/>
   	<key>NSPrivacyCollectedDataTypes</key>
   	<array/>
   	<!--
   	     What the linked binary references from Apple's list of required-reason APIs, and nothing else: the Kotlin
   	     code reads NSFileModificationDate for the files of the library, and skia and ICU call stat and fstat on
   	     their own files in the bundle (C617.1); the import reads the size of files the user picked or opened in place
   	     (3B52.1). The framework is static, so what Compose, skiko and the Kotlin runtime call is the app's to declare.
   	     Check it again after a Kotlin, Compose or skiko update, see app/ios/CLAUDE.md.
   	-->
   	<key>NSPrivacyAccessedAPITypes</key>
   	<array>
   		<dict>
   			<key>NSPrivacyAccessedAPIType</key>
   			<string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
   			<key>NSPrivacyAccessedAPITypeReasons</key>
   			<array>
   				<string>C617.1</string>
   				<string>3B52.1</string>
   			</array>
   		</dict>
   	</array>
   </dict>
   </plist>
   ```

   (Tabs for indentation, as in `Info.plist`.)

2. Add it to the Xcode project. `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`, four edits (the two ids are new
   and appear nowhere else in the file):

   a. In `/* Begin PBXBuildFile section */`, after the `LaunchScreen.storyboard in Resources` line:
      ```
      		C4F1000A2E80000000000001 /* PrivacyInfo.xcprivacy in Resources */ = {isa = PBXBuildFile; fileRef = C4F1000A2E80000000000002 /* PrivacyInfo.xcprivacy */; };
      ```
   b. In `/* Begin PBXFileReference section */`, after the `LaunchScreen.storyboard` line:
      ```
      		C4F1000A2E80000000000002 /* PrivacyInfo.xcprivacy */ = {isa = PBXFileReference; lastKnownFileType = text.xml; path = PrivacyInfo.xcprivacy; sourceTree = "<group>"; };
      ```
   c. In the `7555FF7D242A565900829871 /* iosApp */` group's `children`, after
      `6B5BC2812D29658700759926 /* LaunchScreen.storyboard */,`:
      ```
      				C4F1000A2E80000000000002 /* PrivacyInfo.xcprivacy */,
      ```
   d. In `7555FF79242A565900829871 /* Resources */` (the `PBXResourcesBuildPhase`), `files`, after
      `058557BB273AAA24004C7B11 /* Assets.xcassets in Resources */,`:
      ```
      				C4F1000A2E80000000000001 /* PrivacyInfo.xcprivacy in Resources */,
      ```

   The file has to be in the app target's Resources phase so that it is copied to the root of `Campfire.app`, which
   is where App Store Connect looks. It must not go into the Kotlin module: a static framework's resources are not
   bundled.

3. Do **not** declare `NSPrivacyAccessedAPICategorySystemBootTime` (the reviewer's `35F9.1`) or any other category
   the binary does not reference. The manifest is also what Xcode's privacy report is built from, and a declaration
   of an API the app does not call says something untrue about it.

## Tests
None (build configuration).

## Verify
1. `plutil -lint app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy` prints `OK`.
2. Build for the simulator with the `xcodebuild ... -sdk iphonesimulator ...` command from the root `CLAUDE.md` and
   check that `<SYMROOT>/Debug-iphonesimulator/Campfire.app/PrivacyInfo.xcprivacy` exists. Open the project in Xcode
   once and confirm it loads, and that the file shows in the navigator under `iosApp` with the target membership box
   ticked.
3. Build Release for devices (`-configuration Release -sdk iphoneos CODE_SIGNING_ALLOWED=NO`) and check the binary
   still references nothing more than the table above:
   ```
   nm -u Campfire.app/Campfire | grep -E '_(stat|fstat|lstat|fstatat|getattrlist|getattrlistbulk|fgetattrlist|getattrlistat|statfs|fstatfs|statvfs|fstatvfs|mach_absolute_time|NSFileModificationDate|NSFileCreationDate|NSFileSystemFreeSize|NSFileSystemSize|NSURL.*(Date|Capacity).*Key)$|NSUserDefaults'
   strings -a Campfire.app/Campfire | grep -E -x 'systemUptime|activeInputModes|standardUserDefaults|fileModificationDate|creationDate|modificationDate'
   ```
   The first prints `_NSFileModificationDate`, `_fstat` and `_stat` and nothing else; the second prints nothing.
4. Before the first real upload, archive in Xcode and run Organizer's "Generate Privacy Report" on it: it lists File
   Timestamp with C617.1 and 3B52.1.

## Docs
`app/ios/CLAUDE.md`, a new paragraph after the one about `Info.plist`'s document integration:

"`PrivacyInfo.xcprivacy`, in the app target's Resources phase, is the privacy manifest App Store Connect refuses a
build without. It declares no tracking and no collected data, and the one required-reason category the linked
binary references: file timestamps, for the library's own files (`NSFileModificationDate` in `IosFileStorage`, and
skia's and ICU's `stat`/`fstat` on the bundle, C617.1) and for the size of a file the user picked or opened in place
(3B52.1). The Kotlin framework is static, so what Compose, skiko and the Kotlin runtime call is the app's to declare;
none of them ships a manifest. After a Kotlin, Compose or skiko update, `nm -u` on a Release build's `Campfire`
executable is how to see whether a new category has come in (`mach_absolute_time`, `systemUptime` and
`NSUserDefaults` are the likely ones) before App Store Connect says so."

## Touches
- `app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy` (new)
- `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
- `app/ios/CLAUDE.md`

## Depends on
Nothing. 15 edits the same `project.pbxproj` sections (`PBXBuildFile`, `PBXFileReference`, the `iosApp` group and
the Resources phase) and 10 and 15 edit `Info.plist`, so run 09, 10 and 15 one after another.
