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
None of the steps below has been done yet, and the ones marked *(verify)* rest on rules that change — read the current
version before relying on them.

This is the hardest of the three stores, for one reason: **every Mac App Store app runs in the App Sandbox**, and a
JVM application has to be told about that in detail. Expect a review round or two. JetBrains' guide is the reference
for everything in section 2:
https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Signing_and_notarization_on_macOS/README.md

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. Accounts, identifiers and certificates

- [ ] The Apple Developer Program membership is the same one the iOS build needs (team `N45A6ZHDGY`).
- [ ] Use the bundle ID `com.pandulapeter.campfire`, and add macOS as a platform to the **same app record** as the iOS
      app if there is one — the listing name question ("Campfire" is almost certainly taken) is answered once for
      both. See [ios-app-store.md](ios-app-store.md).
- [ ] Create two certificates: *Mac App Distribution* (signs the app) and *Mac Installer Distribution* (signs the
      `.pkg`). Export both as one `.p12`.
- [ ] Create two *Mac App Store* provisioning profiles, as the guide above describes: one for the app's bundle ID and
      one for the bundled Java runtime (`com.pandulapeter.campfire.runtime` or similar, its own App ID).
- [ ] While there: a *Developer ID Application* certificate is what signs and notarizes the `.dmg` on GitHub, so that
      it stops being "unsigned". It is not needed for the store, but it comes with the same membership (section 6).

## 2. Changes to the project

All in `app/desktop/build.gradle.kts`, inside `nativeDistributions { macOS { … } }`, unless it says otherwise.

- [ ] Add `TargetFormat.Pkg` to `targetFormats`: the Mac App Store takes a `.pkg`, not a `.dmg`.
- [ ] Set `bundleID = "com.pandulapeter.campfire"`, `appCategory = "public.app-category.music"`,
      `minimumSystemVersion`, and a `packageBuildVersion` fed from a new `campfire.mac.buildNumber` property — App
      Store Connect refuses a build number it has seen, exactly as on iOS.
- [ ] Set `appStore = true`, the `signing { }` block (identity and keychain from `campfire.*` properties, so that a
      plain checkout still builds unsigned), `provisioningProfile` and `runtimeProvisioningProfile`.
- [ ] Write the two entitlements files and point `entitlementsFile` / `runtimeEntitlementsFile` at them. The app
      needs:
  - `com.apple.security.app-sandbox`
  - `com.apple.security.network.client` — sync
  - `com.apple.security.network.server` — the two loopback sockets: the one on `127.0.0.1:53682` that receives the
    OAuth redirect (`DesktopSyncAuthenticator`) and the single-instance listener on a system-chosen port. Say why
    both exist in the review notes, since a server entitlement gets asked about.
  - `com.apple.security.files.user-selected.read-write` — the import and export dialogs
  - the JVM's own: `com.apple.security.cs.allow-jit`, `com.apple.security.cs.allow-unsigned-executable-memory`,
    `com.apple.security.cs.disable-library-validation` *(verify against the guide)*
- [ ] Add `ITSAppUsesNonExemptEncryption` = `false` through `infoPlist { extraKeysRawXml }`.
- [ ] Check that `appIcon.icns` holds every size up to 1024×1024; the store validates the icon.
- [ ] **Turn the donation link off in the store build.** `canAskForDonations` is `true` for the whole desktop target,
      and guideline 3.1.1 applies on the Mac as it does on iOS. One jar serves the `.dmg` and the store alike, so the
      decision has to be made at runtime: a sandboxed process has `APP_SANDBOX_CONTAINER_ID` in its environment.
- [ ] Decide the processor question. jpackage packages for the machine it runs on, and one version of an app takes
      one build, so the two `.pkg` files of the two runners cannot both be uploaded. Either ship Apple silicon only
      *(verify that App Store Connect accepts an arm64-only build for a new app)*, or merge the two app images into a
      universal one with `lipo` before packaging, which Compose Desktop does not do by itself.

## 3. What the sandbox changes — check each one in a signed build

A sandboxed build can only be tested signed: run `packagePkg`, install the result, and start it from Applications.

- [ ] The library moves to `~/Library/Containers/com.pandulapeter.campfire/Data/Library/Application Support/Campfire`.
      The code needs no change (`user.home` points into the container), but a library from the `.dmg` build is not
      carried over, and the "Location" row in Settings shows the container path — check that it still opens in Finder.
- [ ] Opening a `.cho` file from Finder and dropping files onto the window: both are supposed to work, since the
      system grants access to what the user handed over.
- [ ] The import and export dialogs (`java.awt.FileDialog`) have to be the system's own panel for the
      user-selected-files entitlement to apply. They are on macOS; confirm.
- [ ] Connecting Dropbox end to end: the browser opens, the redirect reaches the local socket, the tokens are saved.
- [ ] `preferences/sync-credentials.json` sits in the container, which is an improvement on a world-readable folder.

## 4. The store listing (by hand, once)

- [ ] Screenshots at one of the accepted sizes (1280×800, 1440×900, 2560×1600 or 2880×1800) *(verify)*, in
      English.
- [ ] Description, keywords, support and marketing URL, privacy policy URL, App Privacy (**Data Not Collected**), age
      rating, category *Music*, price *Free* — the same answers as for iOS.
- [ ] Notes for the reviewer: no account; sync is optional and needs the reviewer's own Dropbox; why the app listens
      on a local port for a few seconds during authorization.
- [ ] Upload the first `.pkg` by hand with Apple's *Transporter* app and submit it for review by hand.

Already taken care of in the code: Settings lists no builds at all, only a link to the README, and a build running
on macOS names only the Mac App Store, in its rating row (`storeForRating`). A build made with
`-Pcampfire.desktop.distribution=mac-app-store` has no donation link (guideline 3.1.1, `canAskForDonations`).

## 5. Automating it

In `desktop-publish.yml`, on the macOS leg (or legs), next to the `.dmg` that is built today.

- [ ] Add repository secrets: `MAC_CERTIFICATES_P12_BASE64`, `MAC_CERTIFICATES_PASSWORD`, the two provisioning profiles
      (base64), and the App Store Connect API key if the iOS workflow has not added it already
      (`APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_BASE64`).
- [ ] Import the `.p12` into a temporary keychain on the runner, write the profiles out, run
      `./gradlew :app:desktop:packagePkg` with the signing properties, and upload with
      `xcrun altool --upload-package … --type macos --apiKey … --apiIssuer …` *(verify the current upload command;
      Apple has been moving this between tools)*.
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

- [ ] Fill in `Distribution.MAC_APP_STORE`'s `url` in `presentation/…/ui/platform/Platform.kt`.
- [ ] Point the macOS badge in `README.md` at the listing and move it up among the published ones.
- [ ] Update the release-flow section of the root `CLAUDE.md` and the packaging paragraph of `app/desktop/CLAUDE.md`.
