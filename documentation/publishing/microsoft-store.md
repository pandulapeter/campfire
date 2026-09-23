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
updates by itself. The first submission is in certification, and `windows-publish.yml` submits every release after it.
The steps marked *(verify)* rest on rules that change.

Delete this file once the last box is ticked: by then `CLAUDE.md` describes how it works.

## 1. See it run on Windows

- [x] Dispatch *Publish Desktop* by hand for the latest release tag and check that the Windows leg goes through.
      Done: `gradlew` runs under Git Bash, WiX is downloaded, and the accented vendor name goes through the MSI
      tooling; the 4.2.2 release carries the `.msi` it built.
- [ ] Install that `.msi` on a Windows machine or VM and go through the app: the Start menu entry, the library under
      `%APPDATA%\Campfire`, the file dialogs, opening a `.cho` file from Explorer, and connecting Dropbox (the redirect
      to `127.0.0.1:53682` may raise a firewall question the first time).

## 2. Account and name

- [ ] Open a Partner Center developer account as an individual: free, with an ID and a selfie for verification.
- [ ] Reserve the product name: "Campfire - Songbook & Chords", as in [store-listing.md](../store-listing.md). A
      reserved name can differ from the name under the icon, as on the App Store.
- [ ] Copy the three values on the *Product identity* page into `gradle.properties`: `Package/Identity/Name` into
      `campfire.windows.identityName`, `Package/Identity/Publisher` into `campfire.windows.publisher` and the publisher
      display name into `campfire.windows.publisherDisplayName` (a character outside ASCII escaped as `\uXXXX`), and the
      reserved name into `campfire.windows.displayName`. None of them is a secret, so they are committed.

## 3. The package format

MSIX. The Store signs it, so **no code signing certificate has to be bought**, and it installs, updates and uninstalls
cleanly. The existing `.msi` could only be listed signed with a certificate from a public authority, which is a yearly
cost and the one thing this project has avoided everywhere else.

## 4. Changes to the project

- [x] `app/desktop/AppxManifest.xml` and `packageReleaseMsix`, which packs the release app image with makeappx, the
      logos scaled from `app_icon.png` and indexed with makepri (see `app/desktop/CLAUDE.md`). It needs the Windows
      SDK, or `campfire.windows.sdkBinDirectory` pointing at the `bin/<version>/x64` folder of the
      `Microsoft.Windows.SDK.BuildTools` NuGet package.
- [x] The library stays in `%APPDATA%\Campfire`: the manifest excludes it from the AppData virtualization of packaged
      apps, so the "Location" row is right, an uninstall leaves the library behind as the `.msi` does, and somebody
      moving from the `.msi` build finds their songs where they were.
- [ ] Install the package on Windows 11 (Developer Mode, then
      `Add-AppxPackage -Register app/desktop/build/tmp/packageReleaseMsix/package/AppxManifest.xml`) and check: the
      Start menu entry and the taskbar icon, the demo library appearing in `%APPDATA%\Campfire` rather than under
      `%LOCALAPPDATA%\Packages`, opening a `.cho` from Explorer (with the app closed and with it open), and connecting
      Dropbox. `Get-AppxPackage Campfire* | Remove-AppxPackage` removes it, and the library has to survive that.
- [ ] Once more on Windows 10 if one is at hand, where the exclusion is the older switch for the whole of AppData.

Nothing to change for the store's rules: Settings names no other build (only a link to the README), and the donation
link is allowed (policy 10.8) as long as the submission says the app links to an external payment *(verify the current
wording of 10.8)*.

## 5. The store listing (by hand, once)

- [ ] Screenshots (1366×768 or larger), description, search terms and the 1:1 store logo, in English.
      The short and full description are in [store-listing.md](../store-listing.md).
- [ ] Privacy policy URL (`https://pandulapeter.com/legal/privacy_policy-campfire.html`), support contact (the GitHub
      issues page), category *Music*, price *Free*, markets.
- [ ] The age rating questionnaire (IARC, the same one Play uses).
- [ ] Justify the two restricted capabilities in the submission notes: `runFullTrust`, "a desktop application
      packaged as MSIX"; `unvirtualizedResources`, "the song library is the user's own documents, kept in
      %APPDATA%\Campfire so that it outlives an uninstall and is the folder the app shows the user".
- [ ] Notes for certification: no account; sync is optional and needs the tester's own Dropbox.
- [ ] Check the Dropbox app's status in the Dropbox App Console: an app in *development* status can only be connected
      by a limited number of users.
- [x] Upload the first `.msix` and submit it by hand. The submission API only works for a product that has been
      through certification once.

## 6. Automating it

`windows-publish.yml` builds and checks the package and, for a release, submits it through the Microsoft Store
submission API (`.github/scripts/microsoft_store_submission.py`) with the release's `whats-new` block as its "What's
new in this version". `linux-publish.yml` is what is left of `desktop-publish.yml`, and nothing attaches an `.msi` to a
release any more.

- [ ] In Partner Center, under Account settings → Tenants, associate a Microsoft Entra ID tenant (or create a new one
      there, which is free). Then, under User management → Microsoft Entra applications, create an application,
      give it the **Manager** role, and on its page copy the tenant ID and the client ID.
- [x] Add two repository secrets: `MICROSOFT_STORE_TENANT_ID` and `MICROSOFT_STORE_CLIENT_ID`.
- [x] Give the application a federated credential instead of a key, since a key expires two years at most after it was
      made: in the Microsoft Entra admin center, App registrations → the application → Certificates & secrets →
      Federated credentials, with the issuer `https://token.actions.githubusercontent.com`, the subject
      `repo:pandulapeter/campfire:environment:microsoft-store` and the audience `api://AzureADTokenExchange`.
- [ ] Once a run has signed in that way, delete the application's client secret in Entra (and in Partner Center, if
      it is listed there) and the `MICROSOFT_STORE_CLIENT_SECRET` repository secret, which nothing reads any more.
- [ ] Once the first submission is published, dispatch *Publish Windows* by hand without ticking *submit*, to see the
      Windows build go through on a runner. The first real submission is the next release.
- [ ] Remember what green means: the package was **submitted**. Certification happens afterwards, usually within a
      few days, and a failure arrives by email rather than as a failed job.

## 7. When the listing is live

- [ ] Fill in `Distribution.MICROSOFT_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`
      (`https://apps.microsoft.com/detail/<product id>`).
- [ ] Point the Windows badge in `README.md` at the listing and move it up among the published ones.
