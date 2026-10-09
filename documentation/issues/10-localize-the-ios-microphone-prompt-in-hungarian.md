# Localize the iOS microphone prompt in Hungarian

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** iOS (the macOS desktop build is left as it is, see Fix)
**Files:** `app/ios/iosApp/iosApp/hu.lproj/InfoPlist.strings` (new),
`app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`, `app/ios/CLAUDE.md`

## Problem

The iOS app declares that it speaks Hungarian (`app/ios/iosApp/iosApp/Info.plist`):

```xml
<key>CFBundleLocalizations</key>
<array>
    <string>en</string>
    <string>hu</string>
</array>
```

and asks for the microphone with an English-only sentence:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Campfire listens through the microphone to hear your instrument while the tuner is open. The sound is analyzed on this device, and is never recorded or sent anywhere.</string>
```

There is no `hu.lproj/InfoPlist.strings` anywhere in `app/` (nor any `.lproj`), so on a Hungarian iPhone the whole app
is Hungarian except the system prompt that decides whether the tuner can work at all, which App Review also reads as
the purpose string.

The macOS desktop build has the same English sentence (`app/desktop/macos/microphone.plist`, merged into the bundle's
`Info.plist` through `extraKeysRawXml` in `app/desktop/build.gradle.kts`). Contrary to what the review note assumed,
the Mac bundle declares no `CFBundleLocalizations` at all, so macOS treats the whole bundle as English.

## Fix

### iOS

1. Create `app/ios/iosApp/iosApp/hu.lproj/InfoPlist.strings` (UTF-8), starting with the MPL header as a `/* … */`
   comment (copied from a sibling's wording; `.strings` takes C comments), then:

   ```
   /* The question the system asks before the tuner first listens. Keep it in step with NSMicrophoneUsageDescription in Info.plist. */
   "NSMicrophoneUsageDescription" = "A Campfire a mikrofonon keresztül hallja a hangszeredet, amíg a hangoló nyitva van. A hangot ez az eszköz elemzi, és soha nem rögzíti vagy küldi el sehová.";
   ```

   The sentence follows the app's own Hungarian notice (`tuner_notice_not_asked` in `values-hu/strings.xml`: "A Campfire
   a mikrofonon keresztül hallja a hangszert. A hangot ez az eszköz elemzi, és soha nem rögzíti vagy küldi el sehová.")
   and the app's informal voice ("Koppints"), adding the "while the tuner is open" of the English plist string.
   No `en.lproj` is needed: English is the development region and falls back to `Info.plist`.
2. Reference it from the Xcode project (objectVersion 56, classic groups, so the file is not picked up by itself).
   In `project.pbxproj`, using IDs that follow the project's existing hand-written ones (`C4F1000A2E8000000000000N`;
   01 and 02 are taken):
   - `PBXBuildFile` section:
     `C4F1000A2E80000000000003 /* InfoPlist.strings in Resources */ = {isa = PBXBuildFile; fileRef = C4F1000A2E80000000000004 /* InfoPlist.strings */; };`
   - `PBXFileReference` section:
     `C4F1000A2E80000000000005 /* hu */ = {isa = PBXFileReference; lastKnownFileType = text.plist.strings; name = hu; path = hu.lproj/InfoPlist.strings; sourceTree = "<group>"; };`
   - the `7555FF7D242A565900829871 /* iosApp */` group's `children`: add `C4F1000A2E80000000000004 /* InfoPlist.strings */,`
     after the `PrivacyInfo.xcprivacy` line;
   - the `7555FF79242A565900829871 /* Resources */` build phase's `files`: add
     `C4F1000A2E80000000000003 /* InfoPlist.strings in Resources */,`;
   - a new section, between `/* End PBXSourcesBuildPhase section */` and `/* Begin XCBuildConfiguration section */`:

     ```
     /* Begin PBXVariantGroup section */
     		C4F1000A2E80000000000004 /* InfoPlist.strings */ = {
     			isa = PBXVariantGroup;
     			children = (
     				C4F1000A2E80000000000005 /* hu */,
     			);
     			name = InfoPlist.strings;
     			sourceTree = "<group>";
     		};
     /* End PBXVariantGroup section */
     ```

   `hu` is already in the project's `knownRegions`. Tabs, as the rest of the file.
3. Check the build: `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64
   SYMROOT=<dir> OBJROOT=<dir> build`, then confirm `<dir>/<configuration>-iphonesimulator/Campfire.app/hu.lproj/InfoPlist.strings`
   exists, and that Xcode opens the project and lists the file under `iosApp` with a Hungarian localization.

### macOS desktop — not feasible here

Shipping `hu.lproj/InfoPlist.strings` in the Mac bundle would need files in `Campfire.app/Contents/Resources/` and a
`CFBundleLocalizations` key. The Compose Desktop plugin's `macOS { }` block offers `infoPlist { extraKeysRawXml }` (keys
only) and `appResourcesRootDir` (which lands under `Contents/app/resources`, not `Contents/Resources`), and exposes
no jpackage `--app-content` or resource-directory option for that folder. Copying the folder into the `.app` after
`createDistributable` would have to run before the plugin signs the store build, which it does inside the same
packaging task, so the result would fail the App Store's signature check. That needs a spike of its own (for example
whether `jpackage --resource-dir` with a Resources override is reachable through the plugin), so this plan leaves the
desktop build English and says so; the executor should not attempt it here.

## Tests

None: a resource of the app shell.

## Manual check

On an iPhone (or simulator) set to Hungarian, with Campfire's microphone permission reset (delete and reinstall, or
Settings → General → Transfer or Reset → Reset Location & Privacy): open the Tuner tab and tap the button that asks for
the microphone. The system alert carries the Hungarian sentence. Set to English, it carries the English one.

## Docs

`app/ios/CLAUDE.md`: in the paragraph that says the tuner adds only `NSMicrophoneUsageDescription`, add that its
Hungarian text is `iosApp/hu.lproj/InfoPlist.strings`, referenced from the project as a variant group, and that a new
language in `CFBundleLocalizations` needs its own `<lang>.lproj/InfoPlist.strings` too. `presentation/CLAUDE.md`'s
`strings.xml` section (which says a new `values-xx` folder needs the language in `CFBundleLocalizations`) gains "and an
`InfoPlist.strings` for the microphone prompt".
