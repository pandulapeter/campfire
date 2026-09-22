# 15 · The iOS bundle declares English only, so a Hungarian iPhone gets the system sheets in English

**Severity:** minor (iOS. Every Hungarian user sees the system UI inside the app in English: the Dropbox sign-in
alert, the document picker, the share sheet, the notification permission prompt. The App Store also lists the app as
English only, and iOS Settings offers no per-app language for it) · **Area:** `:app:ios` (`Info.plist`, the Xcode
project)

## Symptom
On an iPhone whose language is Hungarian:
- the "“Campfire” Wants to Use “dropbox.com” to Sign In" alert of `ASWebAuthenticationSession`,
- the document picker's buttons, the share and save sheets,
- the "Campfire Would Like to Send You Notifications" prompt

are in English, although the app itself (following the system by default) is in Hungarian. Settings → Campfire has no
Language row, and the App Store product page lists English only.

## Cause
UIKit picks the language of the system UI it draws for an app from the app's own list of localizations, matched
against the user's preferred languages. That list comes from the bundle's `.lproj` directories and its
`CFBundleLocalizations` key. The app's strings live in Compose resources (`composeResources/values[-hu]`), which iOS
cannot see, so:
- the built `Campfire.app` has no `.lproj` at all (checked on the last Release build), and
- `app/ios/iosApp/iosApp/Info.plist` has no `CFBundleLocalizations`; only `CFBundleDevelopmentRegion`
  (`$(DEVELOPMENT_LANGUAGE)`, `en`).

The bundle therefore supports English only, and a Hungarian user falls back to it. The project agrees:
`project.pbxproj:137-140`, `knownRegions = (en, Base,);`.

`CFBundleLocalizations` is the key Apple documents for an app that "does not use the existing bundle localization
mechanism", which is this case. Adding it does not change how the app picks its own language: Compose's
`Locale.current` on iOS reads `NSLocale.preferredLanguages` (CMP 1.12.0 `PlatformLocale.darwin.kt:31`), which is the
user's list and not narrowed to the bundle's. A per-app language chosen in iOS Settings (which the key makes
available) is put at the front of that list for the app, so "System default" in Campfire follows it too.

## Fix
1. `app/ios/iosApp/iosApp/Info.plist`, after the `CFBundleDevelopmentRegion` entry, add:

   ```xml
   	<!-- The languages the app speaks. Its strings are Compose resources, which iOS cannot see, so without this the
   	     bundle is English only: the system UI drawn for the app (the sign-in alert, the document picker, the share
   	     sheet, the notification prompt) stays English on a Hungarian iPhone, and Settings offers no language for
   	     it. Keep in step with the values-* folders of :presentation's composeResources. -->
   	<key>CFBundleLocalizations</key>
   	<array>
   		<string>en</string>
   		<string>hu</string>
   	</array>
   ```

2. `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`, in the `PBXProject` section, `knownRegions`:

   ```
   			knownRegions = (
   				en,
   				Base,
   				hu,
   			);
   ```

   (This is what Xcode's own localization settings show; it changes nothing at runtime but keeps the project
   telling the same story as the plist.)

No `.lproj` should be needed: `CFBundleCopyBundleLocalizations`, which UIKit and Settings read, includes the key's
languages. If Verify step 2 or 3 shows otherwise on the simulator (the alert stays English, no Language row), the
fallback is an `en.lproj` and a `hu.lproj` holding an `InfoPlist.strings` with only the license header comment in it,
added to the target as one variant group; the directories alone make the localizations real. Whether App Store
Connect's language list follows the key is only visible after an upload; the same fallback applies if it does not.
Do not copy any system strings into the app; the system has its own translations once the bundle says it speaks the
language.

## Tests
None (build configuration).

## Verify
1. `plutil -lint app/ios/iosApp/iosApp/Info.plist` prints `OK`.
2. Build and install on the simulator (root `CLAUDE.md` command). Set the simulator's language to Magyar
   (Settings → General → Language & Region), relaunch Campfire:
   - the app is in Hungarian (as before);
   - Settings → Library → Connect Dropbox: the system alert is in Hungarian ("… szeretné használni a(z) „dropbox.com”
     tartományt a bejelentkezéshez");
   - Import: the document picker's Cancel/Open are Hungarian.
3. Simulator back to English, then Settings → Apps → Campfire shows a Language row; choose Magyar: Campfire, set to
   "System default" in its own settings, comes up in Hungarian.
4. With the simulator in Hungarian and Campfire set to English in its own settings, the system sheets stay Hungarian
   (they follow the system and the bundle, not the in-app choice); that is expected and matches every other
   platform's system dialogs.

## Docs
`app/ios/CLAUDE.md`, a sentence at the end of the paragraph about `Info.plist`'s document integration (or a short
paragraph after it):

"`CFBundleLocalizations` lists `en` and `hu`. The app's strings are Compose resources, which iOS cannot see, so the
key is what tells UIKit which languages the app speaks: without it the system UI drawn for the app (the sign-in alert,
the document picker, the share sheet, the notification prompt) stays English on a Hungarian iPhone, and Settings
offers no per-app language. Keep it in step with the `values-*` folders of `:presentation`'s composeResources."

`presentation/CLAUDE.md`, the `composeResources/values[-hu]/strings.xml` bullet, after "Adding a new `values-xx`
folder needs a clean build so the plugin regenerates `AppLocale`.": add "It also needs the language added to
`CFBundleLocalizations` in the iOS `Info.plist`, which is how iOS learns that the app speaks it (see `app/ios`)."

## Touches
- `app/ios/iosApp/iosApp/Info.plist`
- `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
- `app/ios/CLAUDE.md`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 09 edits the same `project.pbxproj` and 10 the same `Info.plist`, so run 09, 10 and 15 one after another.
