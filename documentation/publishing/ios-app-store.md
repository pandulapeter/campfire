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
itself. Version 4.3.0 (build 34) was uploaded by hand and submitted for review on 2026-09-23; the App Store Connect
app id is `6815160850`. The boxes that are ticked are done. The ones marked *(verify)* rest on rules that change — read the current version
before relying on them.

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. Accounts and identifiers

- [x] Confirm the Apple Developer Program membership of team `N45A6ZHDGY` (the one in
      `app/ios/iosApp/Configuration/Config.xcconfig`) is active.
- [x] Register the App ID `com.pandulapeter.campfire` under *Certificates, Identifiers & Profiles*, with no
      capabilities: the app uses the Keychain, a background task and a custom URL scheme, none of which needs one.
- [x] Create the app record in App Store Connect. **The name "Campfire" is almost certainly taken** — App Store names
      are unique — so the listing uses "Campfire - Songbook & Chords", the name in
      [store-listing.md](../store-listing.md). If that one is taken too, decide on another before going on. The name
      under the icon (`APP_NAME` in the same xcconfig) can stay "Campfire".
- [ ] When the Mac App Store build comes, add it to the **same app record** under the same bundle ID with *Add
      Platform*: it was left out on purpose until then. See [mac-app-store.md](mac-app-store.md).
- [x] *Pricing and Availability → iPhone and iPad Apps on Apple Silicon Macs* is off, so that the iOS build is not
      what a Mac gets before the JVM one, which would then replace it under a library in another place.
- [x] The EU Digital Services Act trader status is declared (it is per account, under *Business*).

## 2. Changes to the project

- [x] `ITSAppUsesNonExemptEncryption` = `NO` is in `app/ios/iosApp/iosApp/Info.plist` (the app only uses the
      system's HTTPS, which is exempt; without the key every upload stops to ask).
- [x] The privacy manifest (`app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy`, in the target's resources) declares no
      tracking, no collected data and the file-timestamp reasons Kotlin/Native and Compose need (`C617.1`, `3B52.1`).
      Check it against JetBrains' list again before the first upload, since App Store Connect rejects a missing reason:
      https://kotlinlang.org/docs/apple-privacy-manifest.html *(verify)*
- [x] No shared scheme is needed: `xcodebuild archive -scheme iosApp` finds the one xcodebuild creates for the target
      when none is checked in.
- [x] `campfire.buildNumber`, shared by every platform, has to be raised with every release; `release.yml` refuses a release whose number is
      not higher than the previous release's, since App Store Connect refuses a build number it has seen.

## 3. The store listing (by hand, once)

- [x] Screenshots: the 6.9" iPhone and the 13" iPad sizes are the required ones *(verify)*, in English.
- [x] Description, keywords, support URL (the GitHub issues page) and marketing URL (the repository), in
      English. The name, subtitle, promotional text and description are in
      [store-listing.md](../store-listing.md).
- [x] Privacy policy URL: `https://pandulapeter.com/legal/privacy_policy-campfire.html`.
- [x] App Privacy questionnaire: **Data Not Collected**. Sync goes from the device to the user's own Dropbox, which is
      not collection by the developer.
- [x] Age rating questionnaire, category *Music*, price *Free*, availability.
- [x] Notes for the reviewer: there is no account and nothing to log in to; sync is optional and needs the reviewer's
      own Dropbox; the two bundled demo songs are public domain.

## 4. Before the first submission

- [x] Check the Dropbox app's status in the Dropbox App Console: an app in *development* status can only be connected
      by a limited number of users, and *production* status has to be applied for.
- [x] Upload a first build by hand from Xcode (*Product → Archive → Distribute App*), install it through TestFlight on
      a real device and go through sync once: the `campfire://` redirect back from Safari, a run that continues in the
      background, and opening a `.cho` file from the Files app are the three things the simulator cannot prove.
- [x] Submit that build for review by hand. An API only accepts builds for an app whose first version exists.

Already taken care of in the code: no donation link on iOS (guideline 3.1.1, `canAskForDonations`), no other platform
or store named in Settings (2.3.10) and no "Coming soon" rows (2.1): the About section lists no builds at all, only a
link to the README, and its rating row names the App Store alone (`platformStore`).

## 5. Automating it

`ios-publish.yml` builds, signs and uploads, and no longer attaches an unsigned `.ipa` to the release.

- [x] The team's App Store Connect API key, shared with Kubriko, is in `APP_STORE_CONNECT_KEY_ID`,
      `APP_STORE_CONNECT_ISSUER_ID` and `APP_STORE_CONNECT_PRIVATE_KEY` (the `.p8` file's text, not base64). It has the Admin
      role, which creating certificates takes.
- [x] Sign with an Apple Distribution certificate the run creates through the API and revokes at the end
      (`.github/scripts/app_store_signing.py`), so that no certificate is stored to expire; the key is an Admin key.
- [x] A release submits the build for review with the release's `whats-new` notes as its "What's New", to be released
      as soon as it is approved (`.github/scripts/app_store_submission.py`).
- [ ] Watch the first release that does it: the writing half of the script (a new version, the build, the notes, the
      submission) is only exercised by a real release.
- [ ] Run the workflow once by hand with `master` as its `release_tag` and a `build_number` of 35, to see it get as far
      as TestFlight with a certificate of its own before a release depends on it.

## 6. When the listing is live

- [ ] Fill in `Distribution.APP_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`
      (`https://apps.apple.com/app/id<the app's id>`). That is all it takes for every build to link to it.
- [ ] Point the iOS badge in `README.md` at the listing and move it up among the published ones.
- [ ] Update the release-flow section of the root `CLAUDE.md`.
