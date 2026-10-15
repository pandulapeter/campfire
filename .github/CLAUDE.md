<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# .github

The workflows that test and publish Campfire, and the scripts and actions they share. The `campfire.*` properties and
`local.properties` are the root `CLAUDE.md`'s `## Build`.

## Secrets

- Every workflow writes `local.properties` with `.github/scripts/write_local_properties.py`, each value passed through
  `env:` — never `-P` (visible in the process list) or interpolated into `run:` (the shell re-reads `$`, backticks, and
  quotes). Backslashes are doubled (`java.util.Properties` escapes); UTF-8 with LF, on Windows too.
- `.github/scripts/require_secrets.py` stops a workflow whose secrets are empty.
- Every build passes `campfire.dropbox.appKey` from `DROPBOX_APP_KEY`; each workflow, and `publish-all.yml` first, refuses
  to start without it (a build without it silently has no sync). The check lives in the workflows, not Gradle, because
  the empty checked-in default must keep building a fresh clone.

## Releases

### Publishing a GitHub release is the release

- `publish-all.yml` answers a published release (not a pre-release). Before any build it checks that the tag equals the
  commit's `campfire.versionName` and that `campfire.buildNumber` is higher than the last published release's (stores
  would refuse a reused one only after the other builds went out), runs every test, then calls the six workflows below
  in parallel.
- Each workflow can be dispatched by hand (to publish without a release, or redo one store). It builds the tag's code,
  but `.github/scripts` and `.github/actions` come from the commit the workflow runs from — dispatching from a branch
  with `release_tag` set is how a script fix reaches an existing tag.
- JDK and Gradle come from `.github/actions/setup-jdk`, shared with `tests.yml`.
- **A release carries only what no official channel offers**: just the Linux `.deb`. The Mac App Store build is the Mac
  build (Apple silicon only), the Microsoft Store build the Windows one; `packageReleaseMsi` and `packageDmg` still build
  but nothing publishes them.
- Every desktop leg starts the built app image before publishing (ProGuard breaks things only a start shows, see
  `app/desktop`) via `.github/scripts/start_release_build.sh`, in an empty data directory.

### Release notes and per-store switches

`publish-all.yml` reads hidden comments in the release description with `.github/scripts/release_description.py`
(format in `publish-all.yml`'s header): the `whats-new` notes for every store, Play's `update-priority`, and a `submit`
switch for `play-store`, `app-store`, `mac-app-store`, and `microsoft-store` (`false` leaves that store a draft).

- Anything unreadable is not taken as absent, since every default is the stronger action: an unknown store, a `submit`
  other than `true`/`false`, a priority outside 0–5, or notes over App Store Connect's 4 000 or Partner Center's 1 500
  characters (whatever `submit` says) stop the release before any build. Over Play's 500 is a warning (Play gets the
  lines that fit).
- Text is carried as is, backslashes included; only the hand-dispatched form's `\n` is expanded.
- Fallbacks: the visible description without markdown, then "Bug fixes and improvements.", or (hand dispatch with
  nothing given) the commit log since the previous tag. Store listings are English only.
- Play's `update_priority` decides whether the old version announces the new one: `:presentation`'s `ui/update/CLAUDE.md`.

## The workflows

### `publish-web.yml`

Builds the distribution and `rsync --delete`s it over `app/` of the `campfire-website` repository
(https://campfire-songbook.com/app/), a folder that holds only the distribution.

### `publish-linux.yml`

- `packageReleaseDeb` on amd64 and arm64 (jpackage only packages for its own machine), attached to the release — the
  only way Linux is distributed (website and README link to the latest release). The legs don't cancel each other.
- Runs on the **oldest** supported Ubuntu: a `.deb` depends on the system libraries it was built against.
- Also builds `createReleaseDistributable` for the start check (the plugin leaves no image behind otherwise), both under
  Xvfb.

### `publish-windows.yml`

- `packageReleaseMsix` on a Windows runner, checks the manifest's identity and version against `gradle.properties`, runs
  the start check, keeps the `.msix` as a run artifact, and submits with
  `.github/scripts/microsoft_store_submission.py`. Unsigned: the Store signs it. Nothing is attached to the release.
- The script finds the app by package identity name (no Store ID kept) and builds on the last published submission.
  Green means submitted, not certified.
- **A draft the script made whose commit failed** is reused: only package and "What's new" replaced, the rest (publish
  mode included) kept.
- **A draft started in Partner Center stops the run**, untouched: the API cannot change a submission it did not create.
  Finish it there with the run's `.msix` artifact, or delete it.
- **New screenshots: dispatch by hand with `submit` off.** The run writes the notes into its own draft without touching
  the package (Partner Center only takes an API-uploaded package when the API commits). Then, in Partner Center, replace
  the package with the run's artifact, add screenshots, and submit; the run ends green with a warning saying so.
- A committed submission with this version is left alone (reruns succeed); one with another version stops the run (one
  in progress per product).
- Auth: Microsoft Entra application with the Manager role (`MICROSOFT_STORE_TENANT_ID`, `_CLIENT_ID`) and **no secret** —
  a federated credential trusts GitHub's OIDC token for `repo:pandulapeter/campfire:environment:microsoft-store`. Hence
  the job runs in the `microsoft-store` environment and `publish-all.yml` grants `id-token: write`. Nothing expires.

### `publish-macos.yml`

- `packageReleasePkg` on Apple silicon (asking for the `.pkg` is what signs and sandboxes it), with Mac App Distribution
  and Mac Installer Distribution certificates and the two Mac App Store profiles (app and bundled JRE) found for the
  signing key, written into `local.properties` as on a developer's machine.
- A store-signed build only starts under TestFlight, so the start check runs on an ad-hoc-signed copy with the same
  entitlements minus the two naming the App ID; the demo library must appear in the fresh sandbox container.
- Uploads with `altool`, then submits like iOS. The `build_number` input re-uploads a release under a new number.

### `publish-ios.yml`

Archives with the signing key's Apple Distribution certificate; xcodebuild makes the App Store profile with the API key;
uploads to TestFlight. The Xcode project starts Gradle without properties, so the sync key goes into
`local.properties`, which the version build phase also reads (after `gradle.properties`, last value wins) — that is how
the `build_number` input works without a commit. The share extension's App ID and the App Group on both App IDs were
registered in the developer portal by hand, once; automatic signing then puts the group into every profile.

### Apple submission (both Apple workflows)

- `.github/scripts/app_store_submission.py` puts the build and "What's New" (not on a platform's first version) on the
  version for `campfire.versionName` and submits; with `submit` off it stops before submitting, for screenshots to be
  added in App Store Connect.
- An existing **draft** (prepared with new screenshots, in a review submission or not, or rejected) is used as is: only
  build and "What's New" replaced, release option kept, submitted in its existing review submission.
- Already in review with this build: left alone (reruns succeed). Another version still waiting for Apple (in review,
  or approved and unreleased): the build is not submitted, stays in TestFlight, and the run ends green with a warning;
  submit by hand later or let the next release replace it.
- Green means submitted, not approved; rejections are answered in App Store Connect.

### Apple signing

- **Nothing Apple signs with expires.** Secrets hold only keys: `APPLE_SIGNING_KEY` (unencrypted PEM) and the App Store
  Connect API key (`APP_STORE_CONNECT_KEY_ID`, `_ISSUER_ID`, `_PRIVATE_KEY` — the `.p8` text, not base64, an Admin key).
  `.github/scripts/app_store_signing.py` finds each certificate type made for the signing key (matched by public key),
  imports it into a run-local keychain, and creates a new one for the same key when none exists or the newest has under
  two months left; Mac App Store profiles likewise.
- **Kubriko shares the same key and secrets**: Apple allows two Apple Distribution certificates per team, one shared and
  one free for renewal. A certificate made by hand in Xcode takes the free slot (a 409 on creation means that).
- **Never revoke the signing key's certificates**: a revoked certificate makes any build not yet approved an invalid
  binary (ITMS-90238), even after submission. Old ones expire on their own; renewal comes early enough.
- The `app-store-signing-ios` / `-macos` artifacts list certificates runs made for keys of their own; a run revokes
  those once the build they signed is not attached to a version waiting, being prepared, or rejected (one naming no
  build is kept while any version is waiting), then deletes the artifact. **Never use this for a Developer ID
  certificate** — revoking it breaks every downloaded copy.

### `publish-android.yml`

- Builds `assembleRelease` with the `ANDROID_*` secrets and uploads it and its mapping file to the production track (a
  draft with `submit` off).
- **An APK, not a bundle**: the Play listing predates the bundle requirement and was never migrated; `bundleRelease`
  would be rejected.
- Notes come from the `release_notes` input, which `publish-all.yml` fills from the release description.
