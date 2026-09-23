<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Publishing to the Mac App Store

What is left of putting the desktop build on the Mac App Store. Version 4.3.0 (build 35) was built and uploaded by
hand and submitted for review on 2026-09-23, as a macOS platform of the iOS app's record (app id `6815160850`), and
`macos-publish.yml` uploads every release from then on. The boxes that are ticked are done.

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. Accounts, identifiers and certificates

- [x] The App ID `com.pandulapeter.campfire` is the iOS app's; a second one, `com.oracle.java.com.pandulapeter.campfire`,
      is for the bundled Java runtime, which jpackage gives that bundle ID and which is signed and provisioned as a
      bundle of its own.
- [x] macOS is a platform of the existing app record, so the name, the privacy answers and the rating are shared.
- [x] *Mac App Distribution* (`3rd Party Mac Developer Application:`) signs the app and *Mac Installer Distribution*
      (`3rd Party Mac Developer Installer:`) the `.pkg`. The Compose plugin adds those prefixes itself, so the Apple
      Distribution certificate the iOS build uses cannot stand in for the first one — and current Xcode only offers
      the second: the first is requested on the developer website with a certificate signing request.
- [x] Two *Mac App Store Connect* provisioning profiles, one for each App ID. They expire yearly (next: 2027-09-23).

## 2. Changes to the project

- [x] `TargetFormat.Pkg`, `bundleID`, `appCategory` (*Music*), `minimumSystemVersion` 12.0 (App Store Connect
      refuses an arm64-only build that asks for less) and `packageBuildVersion` from `campfire.buildNumber`, in
      `app/desktop/build.gradle.kts`.
- [x] Signing from `campfire.mac.signingIdentity` / `.signingKeychain`, and — only when a `.pkg` is asked for —
      `appStore`, the two profiles
      (`campfire.mac.provisioningProfile` / `.runtimeProvisioningProfile`) and the two entitlements files,
      `app/desktop/app-store.entitlements` and `app-store-runtime.entitlements`, which say why each entitlement is
      there. Empty properties keep the ad hoc signature a checkout has always had.
- [x] `ITSAppUsesNonExemptEncryption` = `false`.
- [x] `appIcon.icns` holds every size up to 1024×1024, drawn from the iOS icon on the macOS icon grid.
- [x] The runtime image has no `bin` folder any more (the class data sharing step left an empty one, which jpackage
      refuses in a store build), and the other processor's skiko library is taken out of the joined jar.
- [x] Apple silicon only. jpackage packages for the machine it runs on, and a universal build would mean `lipo`-ing
      two runtime images and two skiko libraries together, which Compose Desktop does not do; App Store Connect
      accepts an arm64-only Mac app from macOS 12 on (ITMS-90869). Intel Macs are not served at all.
- [x] The provisioning profiles' extended attributes are cleared before they are copied into the bundle: one
      downloaded through a browser is quarantined, and App Store Connect refuses the build by mail (ITMS-91109).

## 3. What the sandbox changes

Checked with the store build's own bundle signed ad hoc (without the two App ID entitlements, which an ad hoc
signature cannot carry) on 2026-09-23:

- [x] The JDK takes `user.home` from the container, so the library lands in
      `~/Library/Containers/com.pandulapeter.campfire/Data/Library/Application Support/Campfire` with no change to the
      code. A library of a build made without the sandbox is not carried over. The "Location" row in Settings only shows the path.
- [x] The demo library is planted, the single-instance socket listens, and the window draws, with nothing in the log.
- [x] Import through the system's panel and opening a `.cho` from Finder.
- [x] Export through the system's panel, to a folder outside the container.
- [x] Connecting Dropbox end to end: the browser opens, the redirect reaches `127.0.0.1:53682`, the tokens are saved.
- [ ] The same again in the TestFlight build, which is the first one that runs with the real signature, profiles and
      App ID entitlements. A build signed for the store does not start from the build folder.

## 4. The store listing (by hand, once)

- [x] Screenshots, description, keywords, URLs and the notes for the reviewer; App Sandbox Information asks for a
      reason for `network.client`, `network.server` and `files.user-selected.read-write` only.
- [x] 4.3.0 (35) uploaded with *Transporter* and submitted for review.

Already taken care of in the code: Settings lists no builds at all, only a link to the README, and a build running
on macOS names only the Mac App Store, in its rating row (`platformStore`), and no build that runs on macOS has a
donation link (guideline 3.1.1, `canAskForDonations`).

## 5. Automating it

`macos-publish.yml` builds, signs, starts a copy in the sandbox and uploads; `release.yml` calls it and checks
`campfire.buildNumber`. `desktop-publish.yml` no longer builds anything for the Mac.

- [x] No certificate or profile is stored: the run creates both certificates and both profiles through the API and
      revokes them at the end (`.github/scripts/app_store_signing.py`), with the Admin API key iOS uses. The ones
      made by hand in section 1 are only for building by hand, and expire in 2027 without the pipeline noticing.
- [ ] Run the workflow once by hand with `master` as its `release_tag` (the `4.3.0` tag predates the Mac build) and a
      `build_number` of 36, to see it get as far as TestFlight before a release depends on it.
- [x] A release submits the build for review with the release's notes, as for iOS; the first release that does it is
      the test of the writing half of `.github/scripts/app_store_submission.py`.

## 6. When the listing is live

- [ ] Fill in `Distribution.MAC_APP_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`.
- [ ] Point the macOS badge in `README.md` at the listing and move it up among the published ones.
