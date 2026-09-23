<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Release pipeline and stores — manual test script

Written 2026-09-23 against `master` at `2740892c`. Publishing a GitHub release is the release: `release.yml` checks the
tag and the sync key and then calls `android-publish.yml`, `web-publish.yml`, `desktop-publish.yml` and
`ios-publish.yml` side by side. Five of this round's fixes changed those workflows (15, 18, 19, 20, 21) and two the
iOS project (22, 24); **none of them has run on GitHub yet** — they were read and reasoned about, and only the parts
that can run on a laptop were run. So the first real release is also the first test of them, and it is much cheaper to
find a mistake in a rehearsal.

`🆕` marks tests of something changed in this round. **P0** blocks the release or ships a broken/unsafe build, **P1**
is visible breakage, **P2** polish.

## Before you start

- **Rehearse on a fork first.** Fork the repository to a throwaway GitHub account or organisation. A fork has no
  secrets, which is exactly what the "refuses to publish without a key" tests need; add a dummy `DROPBOX_APP_KEY` to
  the fork afterwards for the tests that need one. Do **not** give the fork the Play service account, the signing
  keystore or the website deploy key: the Android upload step and the web deploy are to be tested in the real
  repository only, by hand-dispatch, when you are ready for them to go out.
- **Pre-releases are ignored** by `release.yml` (`types: [released]`), so a pre-release in the real repository is a
  safe place to attach artifacts from a hand-dispatched workflow without triggering the whole release.
- **Local checks** need no GitHub at all and should be done first (section 1).
- The release notes format (the hidden comments `<!-- whats-new en-US … -->` and
  `<!-- play-store update-priority: N -->`) is documented at the top of `release.yml`; the `release-notes` skill writes
  a paste-ready draft.

---

## 1. Local checks (no GitHub)

- [ ] **REL-001** 🆕 (P0) Secrets with awkward characters survive `local.properties`
  1. In a scratch copy of the repository (never the real `local.properties` with the real keys), write
     `local.properties` the way `android-publish.yml` does — each value with every `\` doubled — with
     `campfire.android.keyPassword` set to a password containing `$`, a backtick, `"`, `'` and `\` (e.g.
     ``a$b`c"d'e\f``).
  2. `./gradlew -q :app:android:properties | grep -i keyPassword` (or add a temporary `println` of
     `project.property("campfire.android.keyPassword")`).
  **Expected:** the value printed is exactly the original password, not a shell-expanded or backslash-eaten version.
  <sub>[r5-20]</sub>

- [ ] **REL-002** 🆕 (P1) The desktop distribution property is enforced
  1. `./gradlew :app:desktop:createReleaseDistributable` (default), then with
     `-Pcampfire.desktop.distribution=mac-app-store`, then with `-Pcampfire.desktop.distribution=appstore`.
  **Expected:** the default builds as `download`; `mac-app-store` builds and the running app's About section has **no**
  "Buy me a coffee" row; `appstore` fails configuration naming the four valid values (`download`, `mac-app-store`,
  `microsoft-store`, `linux`).
  <sub>[r5-15]</sub>

- [ ] **REL-003** 🆕 (P1) The packaged desktop runtime has locale data and the accessibility bridge
  1. `./gradlew :app:desktop:createReleaseDistributable`; read the `release` file of the bundled runtime
     (`…/Campfire.app/Contents/runtime/Contents/Home/release` on macOS).
  2. Start that app image, switch the app to Hungarian, open a song's language picker.
  **Expected:** `MODULES` lists `jdk.localedata` and `jdk.accessibility`. Language names in the picker are Hungarian
  ("német", "angol"), not English — the release image, not `:app:desktop:run`, is the test.
  <sub>[r5-17]</sub>

- [ ] **REL-004** 🆕 (P1) The iOS version phase reads `local.properties`
  1. Build the iOS app for the simulator with the recipe in the root `CLAUDE.md` (Release configuration).
  2. Put `campfire.ios.buildNumber=99` into `local.properties`, build again; remove it, build again.
  **Expected:** the build log shows "note: version set from gradle.properties: <version> (<build>)"; the built
  `Info.plist` carries `CFBundleShortVersionString` = `campfire.versionName` and `CFBundleVersion` = the build number,
  99 while the override is there. It builds with no `local.properties` at all.
  <sub>[r5-22]</sub>

- [ ] **REL-005** 🆕 (P2) The App Store category is in `Info.plist`
  1. In the built app of REL-004: `plutil -p …/Campfire.app/Info.plist`.
  2. Install on the simulator and look at the home screen.
  **Expected:** `LSApplicationCategoryType` = `public.app-category.music`; `CFBundleName` = `Campfire`; the home-screen
  label reads "Campfire".
  <sub>[r5-24][r4-18]</sub>

- [ ] **REL-006** (P1) iOS project identity and privacy manifest
  1. `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -showBuildSettings | grep -E 'DEVELOPMENT_TEAM|PRODUCT_BUNDLE_IDENTIFIER|PRODUCT_NAME'`
     for Debug and Release.
  2. `plutil -lint app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy`; check it is inside the built app.
  3. Built `Info.plist`: `ITSAppUsesNonExemptEncryption` is false; `CFBundleLocalizations` has `en` and `hu`.
  **Expected:** team `N45A6ZHDGY`, `com.pandulapeter.campfire`, `Campfire`; the manifest lints and is bundled; both
  keys as stated.
  <sub>[r4-09][r4-10][r4-15][r4-18]</sub>

## 2. Workflow rehearsal on a fork

- [ ] **REL-010** 🆕 (P0) Every publish workflow refuses to build without the Dropbox key
  1. In the fork, with **no** `DROPBOX_APP_KEY` secret, dispatch Publish Android, Publish Web, Publish Desktop and
     Publish iOS by hand.
  2. Publish a release in the fork (tag = the current `campfire.versionName`, e.g. `v4.3.0`).
  **Expected:** 1: each fails within seconds at "Check the sync credentials" (iOS: "Write local.properties") with an
  `::error::DROPBOX_APP_KEY is empty or not visible to this workflow…` line, and **no Gradle run**. 2: `release.yml`
  fails in its first job at "Check the sync credentials", before any of the four builds starts.
  <sub>[r5-19]</sub>

- [ ] **REL-011** (P0) A tag that is not the version is rejected
  1. In the fork (dummy key added now), publish a release tagged with a version different from `campfire.versionName`
     at that commit. Then publish one as a pre-release with the right tag.
  **Expected:** the first fails at "Check the tag against the version" with "The release is tagged …, but
  gradle.properties at that tag says campfire.versionName=…"; the pre-release triggers nothing.
  <sub>[CLAUDE]</sub>

- [ ] **REL-012** 🆕 (P0) The Linux packages build on Ubuntu 22.04 and install on both sides of `t64`
  1. In the fork, dispatch Publish Desktop against a pre-release.
  2. Download `…linux-amd64.deb`; install it in an Ubuntu 22.04 and an Ubuntu 24.04 (Docker or VMs):
     `apt install ./campfire…deb`.
  3. `dpkg-deb -I` the package; `dpkg-deb -c` and look for the `.desktop` entry; `desktop-file-validate` it.
  **Expected:** the two Linux legs run on `ubuntu-22.04` / `ubuntu-22.04-arm`; the package installs on both releases
  with no unmet `…t64` dependency; the `.desktop` entry carries `StartupWMClass` matching the window (see
  `05-linux.md` for the running check); `desktop-file-validate` is clean.
  <sub>[r5-18][r5-23]</sub>

- [ ] **REL-013** (P0) Every desktop leg starts the app it packaged
  1. The same run: look at each of the five legs (Linux amd64/arm64, macOS Apple silicon/Intel, Windows).
  **Expected:** each builds the release image, starts it (Xvfb on Linux) with an empty data directory, sees the demo
  library appear, and only then attaches its installer; the log shows `-Pcampfire.desktop.distribution=linux` on the
  Linux legs and `download` on the others; no `--add-opens` warning on macOS/Windows. (This smoke test cannot notice a
  missing `jdk.localedata` — REL-003 does.)
  <sub>[CLAUDE][r5-15][r5-17][r5-23]</sub>

- [ ] **REL-014** 🆕 (P1) Release-notes backslashes are kept on a release, expanded only by hand
  1. In the fork, dispatch Publish Android with `release_notes` = `- One\n- Two` (stop it after "Generate release
     notes"; the fork has no signing secrets anyway) and read the generated `whatsnew-en-US`.
  2. Publish a throwaway release in the fork whose description holds `<!-- whats-new en-US` … `Fixed importing from
     C:\songs` … `-->`; read the generated notes in the Android job.
  **Expected:** 1: two lines. 2: `C:\songs` arrives whole — no line break, no cut at `\c`.
  <sub>[r5-21]</sub>

- [ ] **REL-015** (P1) The Play "what's new" and update priority come from the release description
  1. Release description with a `<!-- whats-new en-US … -->` block of ~480 characters and
     `<!-- play-store update-priority: 3 -->`.
  2. A release with neither.
  **Expected:** 1: the Android job receives the English text whole (no truncation warning) and `update_priority=3`.
  2: it falls back to the visible description with its markdown stripped, and priority 0.
  <sub>[r4-07][CLAUDE]</sub>

- [ ] **REL-016** (P1) The iOS job produces an installable-by-sideloading `.ipa`
  1. From the fork's Publish iOS (dummy key) or the real one against a pre-release: download the `-unsigned.ipa`.
  2. Sign and install it with a sideloading tool and a personal Apple ID; open it.
  **Expected:** the app starts, has the demo library and offers Connect Dropbox (the key was written into
  `local.properties` by the job).
  <sub>[CLAUDE]</sub>

## 3. The real repository

- [ ] **REL-020** 🆕 (P0) Secrets stay off the command line and the APK is signed with the Play key
  1. Dispatch Publish Android by hand with no `release_tag` (it builds and uploads to Play — do this only for a
     version you are happy to ship, or stop it before the upload step).
  2. Read the "Build release APK" log.
  3. `apksigner verify --print-certs` on the built APK.
  **Expected:** no `-Pcampfire.android…` / `-Pcampfire.dropbox…` arguments anywhere in the log and nothing masked
  (`***`) where a value used to be; the certificate SHA-256 equals Play Console's upload / app signing key; the APK's
  Settings offers Connect Dropbox.
  <sub>[r5-20]</sub>

- [ ] **REL-021** (P0) The web deployment
  1. After `web-publish.yml`: open `https://pandulapeter.com/campfire/` and the github.io address; a deep address
     (`…/campfire/settings/about`); connect Dropbox.
  **Expected:** loads with the progress bar; the github.io address redirects; the deep address survives the 404
  hand-off; the Dropbox redirect returns to `…/campfire/` connected. See `06-web.md` WEB-010/022/050.
  <sub>[CLAUDE][r4-49]</sub>

- [ ] **REL-022** (P0) The release's assets are named honestly
  **Expected:** the macOS `.dmg`s and the Windows `.msi` and the `.ipa` carry `-unsigned` in their names; the Linux
  `.deb`s and the signed APK do not. No build of the app links to any of them (About has only GitHub, "Every version of
  Campfire", the rating row where a listing exists, the privacy policy and — not on Apple's stores — the coffee).
  <sub>[CLAUDE][r5-16]</sub>

## 4. Google Play

- [ ] **REL-030** (P0) The in-app update gate on an internal testing track
  1. Install version N from an internal testing track on a real phone (Play-installed; a sideloaded APK never sees
     updates).
  2. Upload N+1 with update priority 2 (or 3). Open the app.
  3. Upload N+2 with priority 4 (or 5). Open the app; try Back; rotate; open the editor with unsaved text before the
     update appears.
  **Expected:** 2: a dismissible offer; the flexible update downloads in the background; Restart is offered, and waits
  while the editor holds unsaved text or a sync runs. 3: a screen over the app that cannot be dismissed; Back closes
  the app; a rotation keeps the screen; it is not put over an editor with unsaved text until that is saved or
  discarded. The strings follow the language chosen in the app. Priority 0–1 shows nothing. (Details in
  `01-android.md`.)
  <sub>[r2-47][r2-50][r4-14][CLAUDE]</sub>

- [ ] **REL-031** (P1) The Play build and the GitHub APK are the same app
  1. Install the GitHub APK over a Play install (and the other way round).
  **Expected:** installs as an update (same signature); library and settings kept.
  <sub>[CLAUDE]</sub>

- [ ] **REL-032** 🆕 (P1) The rating row opens the Play listing
  1. On a Play install and on a sideloaded APK: Settings → About → "Rate Campfire".
  **Expected:** the Play listing opens (a Custom Tab or the Play app) on both.
  <sub>[r5-16]</sub>

## 5. Apple

- [ ] **REL-040** (P0) First TestFlight upload
  1. Xcode → Archive → Distribute (App Store Connect), then install from TestFlight on a real iPhone.
  2. Connect Dropbox (the `campfire://` redirect from Safari), sync once in the background, open a `.cho` from Files.
  **Expected:** the upload skips the export-compliance question (`ITSAppUsesNonExemptEncryption`); the Organizer's
  privacy report lists File Timestamp C617.1 and 3B52.1; everything in step 2 works.
  <sub>[r4-09][r4-10][pub-ios]</sub>

- [ ] **REL-041** 🆕 (P0) Nothing in the iOS build breaks App Review's rules
  1. Settings → About on the TestFlight build.
  **Expected:** no donation / "Buy me a coffee" row, no other store or platform named, no "Coming soon" rows; the
  rating row is absent until the App Store listing URL is filled in.
  <sub>[r5-15][r5-16][pub-ios]</sub>

- [ ] **REL-042** (P1) The Dropbox app is ready for strangers
  1. In the Dropbox App Console: the app's status (development apps are limited to a few users) and its redirect URIs.
  **Expected:** production status (or applied for) before the first store review; all five redirect URIs listed.
  <sub>[pub-ios][pub-ms]</sub>

## 6. Deferred (tracked so they are not forgotten)

- Mac App Store: a sandboxed `.pkg` with entitlements, `ITSAppUsesNonExemptEncryption` in the desktop `Info.plist`,
  icons up to 1024 px, the arm64-only vs universal decision, review notes explaining the two loopback sockets (the
  single-instance endpoint and the Dropbox redirect). Out of scope this round by decision; build with
  `-Pcampfire.desktop.distribution=mac-app-store` when it is done. <sub>[pub-mas][r5-15]</sub>
- Microsoft Store: an MSIX with the manifest identity, tiles and `runFullTrust`; check whether the packaged app's
  `%APPDATA%` is redirected and whether the Location row in Settings then shows the real path; a self-signed sideload
  test; the rating row appears once the `MICROSOFT_STORE` listing URL is filled in. <sub>[pub-ms][r5-16]</sub>
