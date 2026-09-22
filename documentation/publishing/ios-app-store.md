<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Publishing to the iOS App Store

What is left between the iOS build as it is today and a listing on the App Store that every GitHub release updates by
itself. The app builds and runs; `ios-publish.yml` already produces an unsigned `.ipa` for every release. None of the
steps below has been done yet, and the ones marked *(verify)* rest on rules that change — read the current version
before relying on them.

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. Accounts and identifiers

- [ ] Confirm the Apple Developer Program membership of team `N45A6ZHDGY` (the one in
      `app/ios/iosApp/Configuration/Config.xcconfig`) is active.
- [ ] Register the App ID `com.pandulapeter.campfire` under *Certificates, Identifiers & Profiles*, with no
      capabilities: the app uses the Keychain, a background task and a custom URL scheme, none of which needs one.
- [ ] Create the app record in App Store Connect. **The name "Campfire" is almost certainly taken** — App Store names
      are unique — so the listing uses "Campfire - Songbook & Chords", the name in
      [store-listing.md](../store-listing.md). If that one is taken too, decide on another before going on. The name
      under the icon (`APP_NAME` in the same xcconfig) can stay "Campfire".
- [ ] If the Mac App Store build is coming too, it can live in the **same app record** under the same bundle ID, as a
      second platform. See [mac-app-store.md](mac-app-store.md).

## 2. Changes to the project

- [ ] Add `ITSAppUsesNonExemptEncryption` = `NO` to `app/ios/iosApp/iosApp/Info.plist`. The app only uses the
      system's HTTPS, which is exempt, and without the key every upload stops to ask.
- [ ] Add a privacy manifest (`PrivacyInfo.xcprivacy`) to the app target. There is none today, and Kotlin/Native and
      Compose Multiplatform touch "required reason" APIs (file timestamps among them), which App Store Connect rejects
      without a declaration. JetBrains documents the entries:
      https://kotlinlang.org/docs/apple-privacy-manifest.html *(verify)*
- [ ] Share a scheme in the Xcode project (*Product → Scheme → Manage Schemes → Shared*). There is none checked in, and
      `xcodebuild archive` wants a scheme where today's `-target` build does not.
- [ ] Check that `campfire.ios.buildNumber` in `gradle.properties` is raised with every release. It already moves with
      the Android version code; App Store Connect refuses a build number it has seen.

## 3. The store listing (by hand, once)

- [ ] Screenshots: the 6.9" iPhone and the 13" iPad sizes are the required ones *(verify)*, in English.
- [ ] Description, keywords, support URL (the GitHub issues page) and marketing URL (the repository), in
      English. The name, subtitle, promotional text and description are in
      [store-listing.md](../store-listing.md).
- [ ] Privacy policy URL: `https://pandulapeter.com/legal/privacy_policy-campfire.html`.
- [ ] App Privacy questionnaire: **Data Not Collected**. Sync goes from the device to the user's own Dropbox, which is
      not collection by the developer.
- [ ] Age rating questionnaire, category *Music*, price *Free*, availability.
- [ ] Notes for the reviewer: there is no account and nothing to log in to; sync is optional and needs the reviewer's
      own Dropbox; the two bundled demo songs are public domain.

## 4. Before the first submission

- [ ] Check the Dropbox app's status in the Dropbox App Console: an app in *development* status can only be connected
      by a limited number of users, and *production* status has to be applied for.
- [ ] Upload a first build by hand from Xcode (*Product → Archive → Distribute App*), install it through TestFlight on
      a real device and go through sync once: the `campfire://` redirect back from Safari, a run that continues in the
      background, and opening a `.cho` file from the Files app are the three things the simulator cannot prove.
- [ ] Submit that build for review by hand. An API only accepts builds for an app whose first version exists.

Already taken care of in the code: no donation link on iOS (guideline 3.1.1, `canAskForDonations`), no other platform
or store named in Settings (2.3.10) and no "Coming soon" rows (2.1): the About section lists no builds at all, only a
link to the README, and its rating row names the App Store alone (`storeForRating`).

## 5. Automating it

`ios-publish.yml` turns from "build unsigned and attach" into "build, sign, upload" — and can keep attaching the
unsigned `.ipa` as well, or stop.

- [ ] Create an App Store Connect API key (*Users and Access → Integrations*, role *App Manager*) and add three
      repository secrets: `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID` and `APP_STORE_CONNECT_KEY_BASE64`
      (the `.p8` file).
- [ ] Sign in CI with that key rather than with an exported certificate:
      `xcodebuild archive -scheme iosApp -allowProvisioningUpdates -authenticationKeyPath … -authenticationKeyID …
      -authenticationKeyIssuerID …`, then `xcodebuild -exportArchive` with an `ExportOptions.plist` whose `method` is
      `app-store-connect` and whose `destination` is `upload`. Cloud-managed signing means no `.p12` and no
      provisioning profile among the secrets. *(verify: the key needs the rights to create a distribution certificate.)*
- [ ] Decide how far the automation goes. An upload lands in TestFlight and stops there. Submitting it for review and
      filling in "What's New" is one more step, through the App Store Connect API or fastlane's `deliver`.
- [ ] If it goes that far: add `<!-- app-store en-US … -->` and `hu` blocks to the release description, read them in
      `release.yml`'s `prepare` job the way the Play blocks are read, and teach the `release-notes` skill to write
      them (4000 characters at most, plain text).
- [ ] Remember what green means: the build was **delivered**. Review happens afterwards, and a rejection arrives by
      email rather than as a failed job.

## 6. When the listing is live

- [ ] Fill in `Distribution.APP_STORE`'s `url` in `presentation/…/ui/platform/Platform.kt`
      (`https://apps.apple.com/app/id<the app's id>`). That is all it takes for every build to link to it.
- [ ] Point the iOS badge in `README.md` at the listing and move it up among the published ones.
- [ ] Update the release-flow section of the root `CLAUDE.md`.
