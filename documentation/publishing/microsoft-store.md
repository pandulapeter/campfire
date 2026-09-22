<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Publishing to the Microsoft Store

What is left between the desktop build as it is today and a listing on the Microsoft Store that every GitHub release
updates by itself. `desktop-publish.yml` already attaches an unsigned `.msi` to every release. None of the steps below
has been done yet. The installer builds in CI, but **it has never been installed and used on Windows**, so step 1
comes before everything else. The steps marked *(verify)* rest on rules that change.

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. See it run on Windows

- [x] Dispatch *Publish Desktop* by hand for the latest release tag and check that the Windows leg goes through.
      Done: `gradlew` runs under Git Bash, WiX is downloaded, and the accented vendor name goes through the MSI
      tooling; the 4.2.2 release carries the `.msi` it built.
- [ ] Install that `.msi` on a Windows machine or VM and go through the app: the Start menu entry, the library under
      `%APPDATA%\Campfire`, the file dialogs, opening a `.cho` file from Explorer, and connecting Dropbox (the redirect
      to `127.0.0.1:53682` may raise a firewall question the first time).

## 2. Account and name

- [ ] Open a Partner Center developer account as an individual. *(verify the fee: it has been a small one-time payment,
      and free for individuals in many countries since 2025.)*
- [ ] Reserve the product name: "Campfire - Songbook & Chords", as in [store-listing.md](../store-listing.md). A
      reserved name can differ from the name under the icon, as on the App Store.
- [ ] Note the three values on the *Product identity* page: `Package/Identity/Name`, `Package/Identity/Publisher` and
      the publisher display name. The package manifest has to repeat them to the letter.

## 3. Choose the package format

- **MSIX (recommended).** The Store signs it, so **no code signing certificate has to be bought**, and it installs,
  updates and uninstalls cleanly. Compose Desktop does not produce one, so it is assembled by hand (section 4).
- **The existing `.msi`.** The Store also lists classic installers by URL, but then the installer has to be signed
  with a certificate from a public authority (a yearly cost, and SmartScreen reputation still has to build up), be
  hosted at a versioned HTTPS address and install silently. The release assets would do as the address. Not
  recommended, but it needs no new packaging.

The rest of this file assumes MSIX.

## 4. Changes to the project

- [ ] Add an `AppxManifest.xml` under `app/desktop`, with the identity values from step 2, a four-part version
      (`campfire.versionName` plus `.0` — the Store keeps the last part for itself), the
      `Windows.FullTrustApplication` entry point pointing at `Campfire.exe`, the `runFullTrust` capability, and the
      six ChordPro file type associations that `chordProFileAssociations()` declares for the `.msi`.
- [ ] Add the tile and logo images the manifest refers to (44×44, 150×150 and the 50×50 store logo at least), made
      from `app_icon.png`.
- [ ] Build the package from the unpacked application: `./gradlew :app:desktop:createDistributable`, copy the
      manifest and the images next to `Campfire.exe`, then `makeappx pack` from the Windows SDK, which the
      `windows-latest` runner has. No signing: an unsigned `.msix` is what Partner Center takes.
- [ ] Check what packaging does to the library. A packaged app's writes to `%APPDATA%` are redirected into the
      package's own folder (`%LOCALAPPDATA%\Packages\<package family>\LocalCache\Roaming`), so the "Location" row in
      Settings would name a folder that is not where the files are. Either show the real path when the app finds
      itself packaged, or accept it and say so. A library from the `.msi` build is not carried over either way.
- [ ] To try a package before submitting it, sign it with a self-signed certificate and install it on a machine that
      trusts that certificate — a Store package cannot be sideloaded otherwise.

Nothing to change for the store's rules: the Microsoft Store has no rule against naming other platforms, so Settings
lists them all, and the donation link is allowed (policy 10.8) as long as the submission says the app links to an
external payment *(verify the current wording of 10.8)*.

## 5. The store listing (by hand, once)

- [ ] Screenshots (1366×768 or larger), description, search terms and the 1:1 store logo, in English and Hungarian.
      The English short and full description are in [store-listing.md](../store-listing.md).
- [ ] Privacy policy URL (`https://pandulapeter.com/legal/privacy_policy-campfire.html`), support contact (the GitHub
      issues page), category *Music*, price *Free*, markets.
- [ ] The age rating questionnaire (IARC, the same one Play uses).
- [ ] Justify `runFullTrust` in the submission notes: "a desktop application packaged as MSIX" is the expected answer.
- [ ] Notes for certification: no account; sync is optional and needs the tester's own Dropbox.
- [ ] Check the Dropbox app's status in the Dropbox App Console: an app in *development* status can only be connected
      by a limited number of users.
- [ ] Upload the first `.msix` and submit it by hand. The submission API only works for a product that has been
      through certification once.

## 6. Automating it

A Windows-only job next to the `.msi` leg of `desktop-publish.yml`, or a `windows-store-publish.yml` of its own that
`release.yml` calls like the others.

- [ ] In Partner Center, link a Microsoft Entra ID tenant and create an application with the *Manager* role. Add four
      repository secrets: `MICROSOFT_STORE_TENANT_ID`, `MICROSOFT_STORE_CLIENT_ID`, `MICROSOFT_STORE_CLIENT_SECRET` and
      `MICROSOFT_STORE_SELLER_ID`. The product ID is not a secret and can sit in the workflow.
- [ ] Use the Microsoft Store Developer CLI: the `microsoft/setup-msstore-cli` action, `msstore reconfigure` with the
      four values, then `msstore publish` with the `.msix`. *(verify the current command line.)*
- [ ] "What's new in this version" is part of the listing. If it should come from the release: a
      `<!-- microsoft-store en-US … -->` block in the release description, read in `release.yml`'s `prepare` job the
      way the Play blocks are read, and written by the `release-notes` skill.
- [ ] Remember what green means: the package was **submitted**. Certification happens afterwards, usually within a
      few days, and a failure arrives by email rather than as a failed job.

## 7. When the listing is live

- [ ] Fill in `Distribution.MICROSOFT_STORE`'s `url` in `presentation/…/ui/platform/Platform.kt`
      (`https://apps.microsoft.com/detail/<product id>`).
- [ ] Point the Windows badge in `README.md` at the listing and move it up among the published ones. Decide whether
      the unsigned `.msi` stays on the releases; it is of little use once the Store has the app.
- [ ] Update the release-flow section of the root `CLAUDE.md` and the packaging paragraph of `app/desktop/CLAUDE.md`.
