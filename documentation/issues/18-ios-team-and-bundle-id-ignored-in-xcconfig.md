# 18 · The iOS team id and bundle id are documented as living in Config.xcconfig, but editing them there does nothing

**Severity:** docs / build configuration (iOS. Bites a fork, or the App Store setup the guide walks through: a changed
`TEAM_ID` / `BUNDLE_ID` is silently ignored) · **Area:** `app/ios/iosApp` (Xcode project), `documentation/publishing/ios-app-store.md`

## Symptom
`app/ios/CLAUDE.md:17` says "Team id, bundle id and app name live in `iosApp/Configuration/Config.xcconfig`", and
`ios-app-store.md:21-27` points at that file for the team and the app name. Changing `TEAM_ID` or `BUNDLE_ID` there
changes nothing: the build still signs for `N45A6ZHDGY` as `com.pandulapeter.campfire`. Separately, `ios-app-store.md`
§6 (`:94-95`) asks to remove "Create iOS store listing" and TestFlight entries from the README's to-do list, which has
had neither since `29820b93`.

## Cause
Of the xcconfig's three values only `APP_NAME` is referenced (`PRODUCT_NAME = "${APP_NAME}"`,
`project.pbxproj:363`, `:396`). The target's Debug and Release configurations hold literals:
`DEVELOPMENT_TEAM = N45A6ZHDGY;` (`:348`, `:381`) and `PRODUCT_BUNDLE_IDENTIFIER = com.pandulapeter.campfire;`
(`:362`, `:395`). Both configurations do use the xcconfig as their base (`baseConfigurationReference`, `:221`, `:285`),
so a reference resolves.

## Fix
Make the project read the xcconfig, which is what the docs already say and keeps a fork's changes to one file. Checked
in a throwaway worktree with `xcodebuild -project iosApp.xcodeproj -target iosApp -configuration {Debug,Release}
-showBuildSettings`: the resolved values are unchanged (`N45A6ZHDGY`, `com.pandulapeter.campfire`, `Campfire`), and
setting `BUNDLE_ID=org.example.fork` in the xcconfig then resolves `PRODUCT_BUNDLE_IDENTIFIER = org.example.fork`.

1. `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`, in both target configurations:
   - `:348` and `:381`: `DEVELOPMENT_TEAM = N45A6ZHDGY;` → `DEVELOPMENT_TEAM = "$(TEAM_ID)";`
   - `:362` and `:395`: `PRODUCT_BUNDLE_IDENTIFIER = com.pandulapeter.campfire;` →
     `PRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID)";`

   (`sed -i '' 's/DEVELOPMENT_TEAM = N45A6ZHDGY;/DEVELOPMENT_TEAM = "$(TEAM_ID)";/; s/PRODUCT_BUNDLE_IDENTIFIER = com.pandulapeter.campfire;/PRODUCT_BUNDLE_IDENTIFIER = "$(BUNDLE_ID)";/' app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
   does exactly these four lines.) `Info.plist` already reads `$(PRODUCT_BUNDLE_IDENTIFIER)`.
2. `documentation/publishing/ios-app-store.md:94-95`, replace "Update the release-flow section of the root `CLAUDE.md`,
   and remove the "Create iOS store listing" and TestFlight entries from the README's to-do list." with "Update the
   release-flow section of the root `CLAUDE.md`."

Caveat worth one sentence in `app/ios/CLAUDE.md` (below): picking a team in Xcode's *Signing & Capabilities* tab writes
a literal back over `$(TEAM_ID)`, so the team is changed in the xcconfig, not there.

## Tests
None (build configuration).

## Verify
1. `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -configuration Release -showBuildSettings | grep -E " (DEVELOPMENT_TEAM|PRODUCT_BUNDLE_IDENTIFIER|PRODUCT_NAME) ="`
   prints `N45A6ZHDGY`, `com.pandulapeter.campfire`, `Campfire`, for Debug too.
2. Build and run in the simulator as in the root `CLAUDE.md` (`xcodebuild … -sdk iphonesimulator … build`, then
   `xcrun simctl install/launch`); the app installs over the existing one (same bundle id), keeping its library.
3. Open the project in Xcode: *Signing & Capabilities* shows the team resolved.

## Docs
`app/ios/CLAUDE.md:17`: after "Team id, bundle id and app name live in `iosApp/Configuration/Config.xcconfig`" add
"(the target's settings read all three from it, so a team picked in Xcode's Signing tab would write a literal over
`$(TEAM_ID)` — change it in the file)". `ios-app-store.md:21-27` becomes true as written.

## Touches
- `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
- `app/ios/CLAUDE.md`
- `documentation/publishing/ios-app-store.md`

## Depends on
None.
