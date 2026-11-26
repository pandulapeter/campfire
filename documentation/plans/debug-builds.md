<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: debug builds apart from release builds, with an icon of their own

**Status:** not started. Part 1 makes a debug build its own app on every platform: its own identity, its own data, its
own Dropbox sign-in, and deep links that reach it, on physical devices too. Part 2 marks it with a "D" in the middle of
the logo, in every theme color. Part 2 builds on Part 1 only on iOS (the debug icons need the Debug configuration's own
asset catalog), so the two can overlap elsewhere.

## Where each platform stands

| Platform | Debug build | Release build | Apart today? |
|---|---|---|---|
| Android | the `debug` build type: `.debug` app ID, "Campfire (debug)" (`app/android/src/debug/res`) | `release` | Installs and data apart; the Dropbox redirect `campfire://oauth` is claimed by both, so with both installed the browser's return shows a chooser, and the wrong pick loses the sign-in |
| iOS | Xcode's Debug configuration | Release (`publish-ios.yml`) | **No**: one `BUNDLE_ID` (`Config.xcconfig`), so a debug install replaces the App Store one and runs on its library, preferences, Keychain credentials, App Group, and widgets |
| Desktop | `:app:desktop:run` (no ProGuard; the stand-in runtime allowed) | `createReleaseDistributable` and the packages made from it | Mostly: the Mac App Store build is sandboxed and the MSIX keeps its package folder, but a `run` on Linux shares `~/.local/share/campfire` with the `.deb` |
| Web | `wasmJsBrowserDevelopmentRun` on `localhost` | the production distribution on GitHub Pages | Yes: another origin, so OPFS, the Web Lock, and the credentials are apart |

## Decisions

- **Debug is told apart by its build, never by sniffing.** Each app shell knows which build it is (Android's build type,
  the iOS configuration's xcconfig, a system property the desktop `run` task sets, the web's development webpack) and
  hands what differs to shared code. No `packageName.endsWith(".debug")`.
- **Only the Dropbox redirect gets a scheme of its own, and only on Android**: `campfire-debug://oauth` in debug.
  - The rest of `campfire://` stays shared. The launcher shortcuts and widgets name their own component
    (`AppLinkIntents.kt`, `WidgetActions.kt`), so they never reach the other install. A website "Open in Campfire"
    (`campfire://s/`) showing a chooser is fine: either app imports it.
  - iOS keeps `campfire://oauth`: `ASWebAuthenticationSession` hands the redirect to the app that opened it, whatever
    else claims the scheme. Widget links and quick actions open their own app too.
  - The desktop signs in through the loopback port, and a `run` registers no scheme. The web returns to its own page.
- **Android's debug build is not verified for `https://campfire-songbook.com/s/`.** Verifying it would mean listing the
  committed debug keystore's fingerprint in `assetlinks.json`, which any app signed with that public key under
  `.debug` could then claim. A link reaches the debug build through the landing page's Open in Campfire and its
  chooser, or by pasting it.
- **iOS's debug App ID is listed in the site's `apple-app-site-association`.** An App ID is bound to the team, so
  listing it is safe, and Universal Links then reach a debug build on a device without the store app installed. With
  both installed, which one iOS picks is checked once on a device and written down. A long press on the link offers the
  other.
- **The desktop debug data folder is a sibling**: `Campfire Debug` (macOS, Windows) and `campfire-debug` (Linux). It
  lets a `run` and an installed build run side by side, each with its own single-instance guard. The library a `run`
  has used so far on macOS and Windows is moved there by hand once; nothing migrates it (a debug-only concern).
- **Debug icons are generated once, not maintained by a tool in the repo.** The generator lives in the scratchpad while
  Part 2 is done, as the "no temporary tooling" rule says. The D is redrawn by hand if the logo ever changes.

## Part 1: separate builds

### Step 1. Android: the debug Dropbox redirect

- Declare the sign-in scheme per build type in `app/android/build.gradle.kts`: a `manifestPlaceholders` entry for the
  manifest's sign-in filter (`AndroidManifest.xml`, the `oauth` filter only), and the same value handed to Koin by
  `:app:android` the way it hands the `Context` (`@Provided`).
- `CAMPFIRE_OAUTH_REDIRECT` (`data/source/remote/implementation/.../OAuthRedirect.kt`) stays the default for the other
  platforms. On Android, `AndroidSyncAuthenticator` and `isSyncRedirect` (`SyncRedirect.android.kt`) use the scheme
  they're handed. Unit-test that a `campfire-debug://oauth` redirect is recognized as the debug build's and a
  `campfire://oauth` one isn't, and the other way round.
- **Owner step:** add `campfire-debug://oauth` to the redirect URIs of "Campfire Sync" in the Dropbox App Console.
- Check: with the store app and a debug build both installed on a device, Connect Dropbox in the debug build returns to
  it with no chooser, and the store app's own sign-in still works.

### Step 2. iOS: a debug bundle ID

- Split the configuration: `Config.xcconfig` keeps what both share. A `Debug.xcconfig` and a `Release.xcconfig` include
  it and set `BUNDLE_ID` (`com.pandulapeter.campfire.debug` / `com.pandulapeter.campfire`) and `APP_NAME` ("Campfire
  (debug)", shortened if the Home Screen truncates it badly). Each configuration of the project, the app, the widgets,
  and the share extension takes its own file as `baseConfigurationReference`. `APP_GROUP_ID`, the extensions' IDs, and
  the Kotlin side's App Group lookup follow from `BUNDLE_ID` already.
- Build the share inbox's Darwin notification name from the bundle ID instead of the literal
  `com.pandulapeter.campfire.share-inbox` (`ShareInboxWriter.swift`, `iOSApp.swift`), so a share into one install doesn't
  wake the other. The exported UTI `com.pandulapeter.campfire.songbookpro-backup` stays as it is: both apps declaring
  it is harmless.
- Device signing: register the App IDs `…campfire.debug`, `…debug.widgets`, and `…debug.ShareExtension` with the App
  Groups and Associated Domains capabilities, and the group `group.com.pandulapeter.campfire.debug`, through the App
  Store Connect API key the release already uses (or Xcode's automatic signing on the owner's Mac). Debug stays on
  Automatic signing. Release and `publish-ios.yml`'s manual profiles, keyed by `PRODUCT_BUNDLE_IDENTIFIER`, don't change.
- **Website step** (the sibling `CampfireWebsite` repository): add `N45A6ZHDGY.com.pandulapeter.campfire.debug` to the
  `applinks` details of `apple-app-site-association` for `/s/*`.
- Update what names the bundle ID: `app/ios/CLAUDE.md`'s `xcodebuild` and `simctl` lines, and the verification recipes
  that launch the app by its ID.
- Check, on a physical iPhone with the App Store build installed: the debug build installs beside it with an empty
  library, Dropbox sign-in works in both, a share from Photos or Files lands only in the app picked, both apps' widgets
  show their own library, and a `campfire-songbook.com/s/` link opens a debug build that's installed alone.

### Step 3. Desktop: a debug data folder

- The `run` task (and `runDistributable`, also debug) sets a `campfire.debug` system property in
  `app/desktop/build.gradle.kts`. The packaged builds never set it.
- `desktopDataDirectory()` (`presentation/.../ui/platform`) and `DesktopFileStorage.kt` (which must agree) pick the
  sibling folder when it's set. Unit-test both against one table so they can't drift apart.
- Deep links: a `run` can't register `campfire://`. A link is passed as an argument (`--args`) or pasted, which both
  already import. Note this in `app/desktop/CLAUDE.md`.
- Check: a `run` beside the installed build on Linux (the Lima VM) and on macOS, each with its own library.

### Step 4. Web: confirm, change nothing

- The dev server is already its own origin. Check that the Dropbox app lists the dev server's address as a redirect URI
  (the web build redirects to its own page); if it doesn't, adding it is an owner step.
- `/s/` addresses on the dev server already open the import. Nothing to change.

### Step 5. Documentation

- Root `CLAUDE.md`: one line under Build on what tells a debug build apart on each platform (ID, data, and Android's
  redirect scheme), and the owner steps (Dropbox redirect URIs, Apple App IDs, AASA).
- `app/android/CLAUDE.md`, `app/ios/CLAUDE.md`, and `app/desktop/CLAUDE.md`: their part of it.

## Part 2: the debug icon

A "D" in the middle of the logo, in every theme color and every icon style the platform has. Settle the D's shape,
weight, and color on Android first (step 6), where one vector covers every color, and have the owner approve it there
before generating the bitmaps for the other platforms.

### Step 6. Android (one file)

- Every launcher alias's adaptive icon (`mipmap-anydpi/ic_launcher*.xml`) shares `drawable/ic_launcher_foreground.xml`
  as its foreground and its monochrome layer. A debug copy in `app/android/src/debug/res/drawable` with the D drawn into
  the logo gives the D to all ten colors and the themed (monochrome) icon at once. The D is a path (Inter Bold's glyph,
  converted), not text.
- The launcher shortcuts and the notification icons stay as they are.

### Step 7. iOS

- Ten debug icon sets (the default plus the nine alternates), each with `icon.png`, `icon-dark.png`, and
  `icon-tinted.png`: the release ones with the D composited in, in the color each style needs (tinted: the grayscale
  the system tints).
- They go in a second asset catalog (`DebugAssets.xcassets`) under the **same names** as the release sets. Each
  configuration excludes the other's catalog (`EXCLUDED_SOURCE_FILE_NAMES`), so `ASSETCATALOG_COMPILER_APPICON_NAME`, the
  alternate icon names, and the `setAlternateIconName` code (`CampfireViewController.kt`) don't change.

### Step 8. Desktop

- Ten `app_icon_debug*.png` beside `app/desktop/src/main/composeResources/drawable/app_icon*.png`. `AppIcon.kt` takes
  them when `campfire.debug` is set.
- In debug only, the D icon is also set on macOS's Dock and on Windows, where the window icon is the taskbar's for an
  unpackaged process. That the system can't draw a runtime Dock image in its clear, tinted, or dark styles doesn't
  matter for a debug build, and seeing the D there is the point.
- `appIcon.icns`, `appIcon.ico`, and the Linux entry's PNG are used only for packages, which are always release, so they
  stay as they are.

### Step 9. Web

- Ten `favicon-debug-*.png` variants. The development build serves them under the release names (a copy step of the
  development webpack's resources), so `index.html`'s favicon script, `FaviconEffect.kt`, and the service worker don't
  change, and a production build can never pick one up.

### Step 10. Credits and docs

- No dependency is added, so `OpenSourceComponents.kt` doesn't change. Inter's license already covers drawing the D
  from its glyph.
- Each platform's `CLAUDE.md` names where its debug icons live and that they're made from the release ones.
