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

The workflows that test and publish Campfire, the scripts and the actions they share. How the build itself is
configured — the `campfire.*` properties and `local.properties` — is the root `CLAUDE.md`'s `## Build`.

## Secrets

CI has no `local.properties`, so every workflow writes one from its own secret store with
`.github/scripts/write_local_properties.py` (tested), each value reaching the
script through `env:` rather than being interpolated into it: a `-P` puts the value in the runner's process list,
and a secret substituted into a `run:` block is re-read by the shell, so a password holding a `$`, a backtick or a
quote would sign with something other than what is stored. Backslashes are doubled on the way in, since
`java.util.Properties` reads one as an escape. The file is written and read as UTF-8, with LF line endings on the Windows runner too, so a value outside
ASCII survives as well. Before any of it, `.github/scripts/require_secrets.py` stops a workflow whose secrets are
empty.

## Releases

- **Publishing a GitHub release is the release.** `publish-all.yml` answers it (a pre-release is left alone) by checking
  that the tag is the `campfire.versionName` of the commit it is on — a tag on a commit that still carries the last
  version would submit that version again under a new name — and that `campfire.buildNumber` is higher than the
  last published release's (the highest of the three per-store counters, for a release from before there was one),
  since a store would refuse a used one only after the other builds had gone out, running every test, and then calling the six workflows below side by side. Each of them is the local build command plus the secrets a checkout does not have, and each can still be
  dispatched by hand, to publish without a release or to repeat one half of a release that went wrong. What they build is the tag's, but the
  scripts in `.github/scripts` and the actions in `.github/actions` come from the commit the workflow itself runs from — the tag's on a release, and the
  branch's when the workflow is dispatched by hand with `release_tag` set, which is how a fix to one reaches a release
  already tagged without a new tag. The JDK and Gradle are set up by one action of the repository's own
  (`.github/actions/setup-jdk`), in `tests.yml` and every publish workflow alike; the step that fetches it from the
  workflow's commit is the one written out in each. Every build
  passes `campfire.dropbox.appKey` from the `DROPBOX_APP_KEY` secret, because a published app built without it would
  quietly have no sync provider at all — so each workflow, and `publish-all.yml` before it calls any of them, refuses to
  start when that secret is empty. The check is in the workflows rather than in Gradle: an empty key is the
  checked-in default and has to keep building a fresh clone. **A release carries only what no official channel
  offers**: nothing that a store or the website already hands out is attached to it, which leaves the Linux `.deb`.
  Nothing for the Mac or for Windows is attached: the Mac App Store build is the Mac build, and it is Apple silicon
  only, and the Microsoft Store build is the Windows build. `packageReleaseMsi` and `packageDmg` still build, and
  nothing publishes either.
  - `publish-web.yml` builds the distribution and copies it over `app/` in the `campfire-website` repository
    (https://campfire-songbook.com/app/), which it reaches with the deploy key in `CAMPFIRE_WEBSITE_DEPLOY_KEY`. The
    copy is an `rsync --delete`, so the folder holds nothing but the distribution — the privacy policy and the rest of
    the site live elsewhere there.
  - `publish-linux.yml` builds `packageReleaseDeb` on amd64 and arm64 — jpackage only packages for the machine it runs
    on — and attaches both to the release, which is the whole of how the Linux build is handed out (the website's download
    section and the README's "Get Campfire" section link to the latest release's page, and a `.deb` is not something anybody signs on its own).
    It builds on the oldest supported Ubuntu rather than the newest, since a `.deb` asks for the system libraries it
    was built against and the runner therefore decides the lowest distribution it installs on. The two legs do not
    cancel each other. ProGuard breaks an app in ways only starting it shows (see `app/desktop`), so each leg also
    builds the app image (`createReleaseDistributable`; the plugin packages the jars directly and leaves no image
    behind on its own) and starts it under Xvfb with an empty data directory, and attaches nothing unless the demo
    library appears, the process is still there after that, its log names no exception and at least 80% of the
    classes it loaded came from the class data sharing archives — the start check
    `.github/scripts/start_release_build.sh` makes for the Windows and macOS legs as well, each workflow preparing
    only its own environment around it. The packaging itself runs under Xvfb too, since it
    starts the image once to record the archive of the app's own classes that the package ships (see `app/desktop`).
  - `publish-windows.yml` builds `packageReleaseMsix` on a Windows runner (whose image has the SDK's makeappx),
    checks the identity and the version in the package's manifest against `gradle.properties`, starts the app image it
    was made of the way the Linux legs do (and reads the `campfire.log` the app writes into its data directory for an
    exception, and requires the class data sharing archive the packaging's training start recorded to be used), keeps
    the `.msix` as an artifact of the run and submits it with `.github/scripts/microsoft_store_submission.py`. That finds the app by its package identity name,
    so no Store ID is kept anywhere, creates a submission — a copy of the last published one — swaps its package for
    the new one, writes the release's `whats-new` notes as its "What's new in this version", sets it to be published
    as soon as it passes certification, uploads and commits it, and waits for Partner Center to accept the commit. A
    green run means submitted, not certified. A **draft** the script made whose commit failed is used instead of the copy:
    only its package and its "What's new" are replaced, everything else in it (the publish mode included) is kept as it
    is, and it is committed. **A draft started in Partner Center stops the run**, untouched: the API refuses to change a
    submission it did not create, so one prepared there is finished there with the run's `.msix` artifact, or deleted
    so that the run can make its own. **A release with new screenshots is dispatched by hand with `submit` off**
    instead: the run writes the notes into a draft of its own making and leaves its package alone, since Partner Center
    only takes in a package the API uploaded when the API commits the submission — one sent from Partner Center goes
    out with whatever package it shows, which for a copy is the last release's. So the package is replaced there with
    the run's `.msix` artifact, the screenshots are added and it is submitted there; the run ends green with a warning
    that says so.
    A submission past its commit with this version is left alone, so a repeated run succeeds; one past its commit with
    anything else stops the run, since a product has only one in progress at a time. It signs in as a Microsoft Entra application with the
    Manager role in Partner Center (`MICROSOFT_STORE_TENANT_ID` and `_CLIENT_ID`) and **with no secret**: the
    application has a federated credential that trusts the OIDC token GitHub hands the job, for the subject
    `repo:pandulapeter/campfire:environment:microsoft-store` — which is why the job runs in the `microsoft-store`
    environment and why `publish-all.yml` grants it `id-token: write` — so, like everything Apple's workflows use, nothing
    it signs in with expires (a client secret would, after two years at most). The package is unsigned, since the
    Store signs what it certifies with a certificate of its own, so nothing a later run does can invalidate a build
    still in certification; nothing is attached to the release.
  - `publish-macos.yml` builds `packageReleasePkg` on an Apple silicon runner — asking for the `.pkg` is what signs
    and sandboxes it — signed with a Mac App
    Distribution and a Mac Installer Distribution certificate and the two Mac App Store provisioning profiles (the
    app's and the bundled Java runtime's) that the run finds for the signing key (see below), all of them written into
    `local.properties` as a developer's machine keeps them. A build signed for
    the store does not start outside TestFlight, so the start check of the other desktop legs is made on a copy
    signed ad hoc with the same entitlements less the two that name the App ID — the demo library has to appear in
    the fresh sandbox container. It uploads the `.pkg` with `altool` and the same App Store Connect API key as iOS,
    and submits it for review the way iOS does (below); its `build_number` input uploads a release again under a
    number App Store Connect has not seen.
  - `publish-ios.yml` archives the app signed with the Apple Distribution certificate of the signing key (see below),
    lets xcodebuild make the App Store profile for it with the App Store Connect API key, and uploads the exported
    `.ipa` to App Store Connect, where it lands in TestFlight. Nothing is attached to the release.
  - **Both Apple workflows submit what they upload for review** (dispatched by hand with `submit` off, they stop short
    of the submission and leave the version prepared, for new screenshots to be added and submitted in App Store
    Connect): `.github/scripts/app_store_submission.py` waits for
    App Store Connect to process the build, takes the platform's version for `campfire.versionName` — the existing one, the
    editable one renamed, or a new one set to be released as soon as it is approved — attaches the build, writes
    the release's `whats-new` notes as its "What's New" (all but a platform's first version) and submits it. A
    **draft** that is already there — a version prepared in App Store Connect with the release's new screenshots,
    added to a review submission or not, or one that was rejected — is used as it is: only its build and its "What's
    New" are replaced, everything else in it (the release option included) is kept, and it is submitted in the review
    submission it is already in. A green
    run means submitted, not approved; App Review answers by email, and a rejection is answered in App Store Connect.
    A version that is already in review with this build is left alone, so a repeated run succeeds. Where another
    version of the platform is still waiting for Apple — in review, or approved and not on the store yet — the build
    is not submitted at all, since a platform takes one version at a time: it stays in TestFlight and the run ends
    green with a warning, before waiting for processing; it is submitted by hand once the other has been decided, or
    replaced by the next release's. The Xcode project
    starts Gradle itself and passes it no properties, so the sync key is written into `local.properties` there —
    which the version build phase reads too, after `gradle.properties` and with the last value winning, which is how
    the hand-dispatched form's `build_number` uploads a release again under a number App Store Connect has not seen
    without a commit. The archived `Info.plist` is checked against the expected version before anything is uploaded.
  - **Nothing Apple signs with expires.** A distribution certificate lasts a year; the private
    key it is made for and the App Store Connect API key (`APP_STORE_CONNECT_KEY_ID`, `_ISSUER_ID` and `_PRIVATE_KEY`,
    the last one the `.p8` file's text rather than base64, an Admin key) do not. So the secrets hold the two keys alone
    — the signing key in `APPLE_SIGNING_KEY`, as unencrypted PEM text — and `.github/scripts/app_store_signing.py`
    asks the API for the certificate of each type made for that key, matching it by its public key, and imports it into
    a keychain of the run's own. Where there is none, or the newest has less than two months left, it creates one for
    the same key; Mac App Store profiles are found or made for it the same way. **Kubriko signs with the same key and
    the same secrets**, since Apple allows the team only two Apple Distribution certificates: the two pipelines share
    one, and the other place is the renewal's (a certificate made by hand in Xcode takes one too, which is what a
    creation refused with 409 means). **The signing key's certificates are never revoked**: a build whose certificate is revoked before
    App Review approves it is refused as an invalid binary (ITMS-90238), even after it was processed, attached and
    submitted; the old one is left to expire, by which time everything it signed has long been decided, and the
    renewal comes early enough that no build waiting for review is ever signed with one about to expire. The
    `app-store-signing-ios` / `-macos` artifacts hold what runs made for keys of their own: each run revokes what one
    names once App Store Connect says the build it signed is not attached to a version still waiting for Apple, being
    prepared or rejected (one that names no build is kept while any version of the platform is waiting), and deletes
    the artifact; an artifact that expires leaves its certificates to expire on their own. It must never be used for a
    Developer ID certificate, whose revocation breaks every copy of an app already downloaded.
  - `publish-android.yml` writes the keystore out of `ANDROID_KEYSTORE_BASE64`, builds `assembleRelease` signed with
    the other three `ANDROID_*` secrets and uploads it and its mapping file to the production track with
    `PLAY_SERVICE_ACCOUNT_JSON` (as a draft there when dispatched by hand with `submit` off, rolled out from the Play
    Console); nothing is attached to the release. It is an **APK** and not an app bundle because the Play listing predates the bundle
    requirement and was never migrated; a `bundleRelease` would be rejected on upload. The "what's new" text comes
    from the workflow's `release_notes` input, which `publish-all.yml` fills from comments in the release's description
    that the rendered page hides (`<!-- whats-new en-US … -->`, written for every store and passed to the Apple and Windows workflows as well, and `<!-- play-store update-priority: 0 -->`, and one
    `<!-- <store> submit: true -->` for each of `play-store`, `app-store`, `mac-app-store` and `microsoft-store`, whose
    `false` passes that store's workflow `submit` off so the release is left there as a draft; the
    format is in that file's header). `.github/scripts/release_description.py` reads them, and nothing it cannot read
    is taken as absent, since every default is the stronger action: a store name it does not know, a `submit` other
    than `true` or `false`, a priority outside 0–5, or notes longer than App Store Connect's 4 000 characters or
    Partner Center's 1 500 (whatever those stores' `submit` says) stop the release before a build starts, and notes
    over Play's 500 are a warning, since Play is given the lines that fit. The text is carried through as it is,
    backslashes included; only the hand-dispatched
    form's `\n` is expanded, since a single-line text box has no other way to ask for a line break. It falls back to the visible description with its markdown taken out, and to "Bug fixes and improvements." where the description has no visible text either — or,
    dispatched by hand with nothing given, to the commit log since the previous tag. Every store listing is in
    English only, however many languages the app itself speaks. Its `update_priority` input is
    what decides whether the new version says anything about itself inside the old one — see Updates below.
