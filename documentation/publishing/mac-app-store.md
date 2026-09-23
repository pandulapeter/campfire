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

What is left between the desktop build as it is today and a listing on the Mac App Store that every GitHub release
updates by itself. `desktop-publish.yml` already attaches an unsigned `.dmg` for both kinds of Mac to every release.
The boxes that are ticked are done, and the ones marked *(verify)* rest on rules that change — read the current
version before relying on them.

This is the hardest of the three stores, for one reason: **every Mac App Store app runs in the App Sandbox**, and a
JVM application has to be told about that in detail. Expect a review round or two. JetBrains' guide is the reference
for everything in section 2:
https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Signing_and_notarization_on_macOS/README.md

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. Accounts, identifiers and certificates

- [ ] The Apple Developer Program membership is the same one the iOS build needs (team `N45A6ZHDGY`).
- [ ] The App ID `com.pandulapeter.campfire` already exists for iOS; an App ID is not per platform, so it is the
      one the Mac build uses too. Register a second one for the bundled Java runtime,
      `com.oracle.java.com.pandulapeter.campfire` — that is the bundle ID jpackage gives the runtime, and it is signed
      and provisioned as a bundle of its own.
- [ ] Add macOS to the **existing app record** (app id `6815160850`) with *Add Platform*, so the name, the privacy
      answers and the rating are shared with iOS. See [ios-app-store.md](ios-app-store.md).
- [ ] Create two certificates: *Mac App Distribution* (its name in the keychain starts with
      `3rd Party Mac Developer Application:`, and signs the app) and *Mac Installer Distribution*
      (`3rd Party Mac Developer Installer:`, signs the `.pkg`). The Compose plugin adds those two prefixes itself, so
      the Apple Distribution certificate the iOS build uses cannot stand in for the first one.
- [ ] Create two *Mac App Store* provisioning profiles with the Mac App Distribution certificate: one for each of
      the two App IDs above.
- [ ] While there: a *Developer ID Application* certificate is what signs and notarizes the `.dmg` on GitHub, so that
      it stops being "unsigned". It is not needed for the store, but it comes with the same membership (section 6).

## 2. Changes to the project

- [x] `TargetFormat.Pkg`, `bundleID`, `appCategory` (*Music*), `minimumSystemVersion` 12.0 (App Store Connect
      refuses an arm64-only build that asks for less) and `packageBuildVersion` from `campfire.mac.buildNumber`, in
      `app/desktop/build.gradle.kts`.
- [x] Signing from `campfire.mac.signingIdentity` / `.signingKeychain`, and — only with
      `campfire.desktop.distribution=mac-app-store` — `appStore`, the two profiles
      (`campfire.mac.provisioningProfile` / `.runtimeProvisioningProfile`) and the two entitlements files,
      `app/desktop/app-store.entitlements` and `app-store-runtime.entitlements`, which say why each entitlement is
      there. Empty properties keep the ad hoc signature a checkout has always had.
- [x] `ITSAppUsesNonExemptEncryption` = `false`.
- [x] `appIcon.icns` holds every size up to 1024×1024, drawn from the iOS icon on the macOS icon grid.
- [x] The runtime image has no `bin` folder any more (the class data sharing step left an empty one, which jpackage
      refuses in a store build), and the other processor's skiko library is taken out of the joined jar.
- [ ] The processor question. jpackage packages for the machine it runs on, so a build is either Apple silicon or
      Intel. The plan is Apple silicon only: App Store Connect accepts an arm64-only Mac app, Intel Macs simply do not
      see the listing, and the `.dmg` on GitHub still covers them. A universal build would mean `lipo`-ing two runtime
      images and two skiko libraries together, which Compose Desktop does not do.

## 3. What the sandbox changes

Checked with the store build's own bundle signed ad hoc (without the two App ID entitlements, which an ad hoc
signature cannot carry) on 2026-09-23:

- [x] The JDK takes `user.home` from the container, so the library lands in
      `~/Library/Containers/com.pandulapeter.campfire/Data/Library/Application Support/Campfire` with no change to the
      code. A library from the `.dmg` build is not carried over. The "Location" row in Settings only shows the path.
- [x] The demo library is planted, the single-instance socket listens, and the window draws, with nothing in the log.
- [x] Import through the system's panel and opening a `.cho` from Finder.
- [x] Export through the system's panel, to a folder outside the container.
- [x] Connecting Dropbox end to end: the browser opens, the redirect reaches `127.0.0.1:53682`, the tokens are saved.
- [ ] The same again in the TestFlight build, which is the first one that runs with the real signature, profiles and
      App ID entitlements. A build signed for the store does not start from the build folder.

## 4. The store listing (by hand, once)

- [ ] Screenshots at one of the accepted sizes (1280×800, 1440×900, 2560×1600 or 2880×1800) *(verify)*, in
      English.
- [ ] Description, keywords, support and marketing URL, privacy policy URL, App Privacy (**Data Not Collected**), age
      rating, category *Music*, price *Free* — the same answers as for iOS.
- [ ] Notes for the reviewer: no account; sync is optional and needs the reviewer's own Dropbox; why the app listens
      on a local port for a few seconds during authorization.
- [ ] Build the first `.pkg` by hand — `./gradlew :app:desktop:packageReleasePkg -Pcampfire.desktop.distribution=mac-app-store`
      with the four `campfire.mac.*` signing values in `local.properties` — upload it with Apple's *Transporter* app,
      try it through TestFlight and submit it for review by hand.

Already taken care of in the code: Settings lists no builds at all, only a link to the README, and a build running
on macOS names only the Mac App Store, in its rating row (`storeForRating`). A build made with
`-Pcampfire.desktop.distribution=mac-app-store` has no donation link (guideline 3.1.1, `canAskForDonations`).

## 5. Automating it

In `desktop-publish.yml`, on the macOS leg (or legs), next to the `.dmg` that is built today.

- [ ] Add repository secrets: `MAC_CERTIFICATES_P12_BASE64`, `MAC_CERTIFICATES_PASSWORD` and the two provisioning
      profiles (base64). The App Store Connect API key is already there for iOS (`APP_STORE_CONNECT_KEY_ID`,
      `_ISSUER_ID`, `_PRIVATE_KEY`).
- [ ] Import the `.p12` into a temporary keychain on the runner, write the profiles out, run
      `./gradlew :app:desktop:packageReleasePkg -Pcampfire.desktop.distribution=mac-app-store` with the signing properties —
      the distribution property is what takes the donation link out (guideline 3.1.1), and a leg that forgets it
      uploads a build App Review turns down — and upload with
      `xcrun altool --upload-package … --type macos --apiKey … --apiIssuer …` *(verify the current upload command;
      Apple has been moving this between tools)*.
- [ ] Add `campfire.mac.buildNumber` to the build number check in `release.yml` once a published release carries it;
      before that, the previous release has no value to compare with and the check would fail.
- [ ] Submitting for review and "What's New" text are the same decision, and the same `<!-- app-store … -->` blocks,
      as in [ios-app-store.md](ios-app-store.md).
- [ ] Remember what green means: the build was **delivered**. Review happens afterwards.

## 6. The `.dmg` on GitHub, signed

Independent of the store, and much less work: with a *Developer ID Application* certificate, Compose Desktop's
`notarizeDmg` task signs and notarizes the `.dmg`, after which it opens without the Gatekeeper override.

- [ ] Add the `signing { }` and `notarization { }` blocks for the non-store build, run `notarizeDmg` instead of
      `packageDmg` in `desktop-publish.yml`, and drop `-unsigned` from the asset name and the paragraph about it from
      the README.

## 7. When the listing is live

- [ ] Fill in `Distribution.MAC_APP_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`.
- [ ] Point the macOS badge in `README.md` at the listing and move it up among the published ones.
- [ ] Update the release-flow section of the root `CLAUDE.md` and the packaging paragraph of `app/desktop/CLAUDE.md`.
