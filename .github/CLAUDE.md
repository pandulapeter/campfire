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

The workflows that test and publish Campfire. The `campfire.*` properties and `local.properties` are the root
`CLAUDE.md`'s `## Build`.

## Secrets

- Every workflow writes `local.properties` with `.github/scripts/write_local_properties.py`, each value passed through
  `env:` — never `-P` (visible in the process list) or interpolated into `run:` (the shell re-reads `$`, backticks, and
  quotes).
- Every build passes `campfire.dropbox.appKey` from `DROPBOX_APP_KEY`; each workflow, and `publish-all.yml` first, refuses
  to start without it (a build without it silently has no sync). The check lives in the workflows, not Gradle, because
  the empty checked-in default must keep building a fresh clone.

## Releases

- **Publishing a GitHub release is the release**: `publish-all.yml` answers a published release (not a pre-release).
  Before any build it checks that the tag equals the commit's `campfire.versionName` and that `campfire.buildNumber` is
  higher than the last published release's (stores would refuse a reused one only after the other builds went out),
  runs every test, then calls the six publishing workflows in parallel.
- Each workflow can be dispatched by hand. It builds the tag's code, but `.github/scripts` and `.github/actions` come
  from the commit the workflow runs from — dispatching from a branch with `release_tag` set is how a script fix reaches
  an existing tag.
- **A release carries only what no official channel offers**: just the Linux `.deb` and AppImage. The Mac App Store build is the Mac
  build (Apple silicon only), the Microsoft Store build the Windows one; `packageReleaseMsi` and `packageDmg` still build
  but nothing publishes them.
- Every desktop leg starts the built app image before publishing (ProGuard breaks things only a start shows, see
  `app/desktop`) via `.github/scripts/start_release_build.sh`, in an empty data directory.

### Release notes and per-store switches

`publish-all.yml` reads hidden comments in the release description (format in its header): the `whats-new` notes (one
block per app language, named by the language alone: a country in the tag stops the release, since each store's
locales are filled in from the language), Play's `update-priority`, a `submit` and a `listings` switch per store
(`submit: false` leaves a draft, `listings: false` writes only the notes), and `screenshots` (below).

- Anything unreadable is not taken as absent, since every default is the stronger action: an unknown store, a switch
  other than `true`/`false`, a priority outside 0–5, a `whats-new` block in a language the app lacks, or notes over
  App Store Connect's 4 000 or Partner Center's 1 500 characters (whatever `submit` says) stop the release before any
  build. Over Play's 500 is a warning (Play gets the lines that fit).
- Text is carried as is, backslashes included; only the hand-dispatched form's `\n` is expanded.
- Fallbacks: the visible description without markdown and HTML (the store badges), then "Bug fixes and
  improvements.", or (hand dispatch with nothing given) the commit log since the previous tag. A language without a
  block, and every language of a hand dispatch, gets the English notes.
- Play's `update_priority` decides whether the old version announces the new one: `:presentation`'s `ui/update/CLAUDE.md`.

### Store listings are written by the release

- Every store's listing text comes from `documentation/store-listings/<store>/<language>.md`
  (`.github/scripts/store_listings.py`: `FIELDS` maps each heading to the API's field, `MANUAL` lists what stays by
  hand). The files are the only source of truth: text edited in a console is overwritten by the next release.
- **Keyed by language, locales from the store**: a language is written into every store locale of it (`fr-FR` and
  `fr-CA` alike), so a regional copy added in a console is filled with no code change; a store locale of a language the
  app lacks is left alone with a warning. A store code that is not the ISO 639-1 code needs an entry in `ALIASES`.
- **A language's first listing is created by the release**, in each store that lacks it (asked of the store, never
  worked out from git), at the locale `LOCALES` names: Play's in the edit, App Store Connect's version and app info
  localizations (the privacy policy the website's page in that language, else the English one), and a Partner Center
  listing copied from the English one with its own text. Partner Center takes no listing without screenshots, so there
  a run without the rendered images stops before changing anything; Play and App Store Connect create it anyway.
  `prepare` compares the app's languages with the previous release's tag: a new one turns `screenshots` on where it is
  absent and stops the release where it says `screenshots: false` or a store's `listings: false`.
- **Never touched**: the Microsoft title and "Developed by", and the Mac's App Sandbox Information (not in the API).
  Apple's copyright, URLs, and review notes come from `en.md`.

### Screenshots are rendered and published by the release

- **`<!-- screenshots: true -->`** in the description: the one switch whose default is the weaker action (false),
  since the render costs an hour of macOS runners and replaces what every store shows. Read as strictly as the others.
- **`screenshots.yml`** renders from the tag, beside the tests: the iOS and Mac devices on three macOS runners (their
  chrome is SF Pro, which only macOS has), the Android and Windows ones on Linux runners (`--platforms`), each job with a
  timeout and the build's daemons stopped before it renders (a macOS runner has 7 GB); then one macOS job frames the store images, composes the banners and the link
  preview, lays out the website's tree (`website_screenshots.py --website` into an empty folder), checks every store
  image against its store's rules (`store_images.py --check`: count, sizes, alpha, file size, every language, and every
  framed file named by `SETS` or `MANUAL`), draws contact sheets (`contact_sheets.py`, Pillow), and uploads one
  artifact, `screenshots`, kept 14 days (`store/`, `website/`, `readme/`, `contact-sheets/`). Dispatchable by hand on
  any ref; it publishes nothing and needs no secret.
- **The store jobs wait for it, never on it**: `android`, `ios`, `macos`, and `windows` need `screenshots` but run on
  `!cancelled()` with `prepare` and `test` succeeded, given `screenshots: true` only if it succeeded. A failed render
  ships every build with the images the stores have, the run red. Each store script replaces a set by emptying and
  refilling it (idempotent on a rerun); a set the run has no images for is left alone; a store whose `listings` are off
  keeps its images.
- **By store**: Play replaces phone, 7" and 10" screenshots and the feature graphic in the release's edit. App Store
  Connect replaces the 6.9" iPhone, 13" iPad, and Mac sets of each localization and submits only once every image is
  `COMPLETE`. Partner Center replaces the desktop screenshots in the submission it commits; with `submit` off they wait,
  since it takes an uploaded file only on the API's commit.
- **Still by hand** (`store_images.py`'s `MANUAL`): Play's Chromebook screenshots and icon, the iPhone Duo set (no
  display type yet), Apple's product page header and search results image, and Microsoft's poster and box art.
- **The website and the README**: `website-screenshots` rsyncs the artifact over the website's `assets/screenshots/`
  (`--delete`; a language without pages there yet is skipped with a warning), replaces `og-image.jpg`, runs its
  `stamp.py`, fails on an image no page shows, and commits; it shares `publish-web.yml`'s `campfire-website` concurrency
  group. `readme-screenshots` commits `documentation/screenshots/01–06.webp` to main, rebasing on a refused push.
- **A store dispatched by hand** publishes images only with `screenshots_run`: a run's ID whose artifact to take
  (`.github/actions/screenshots`), refused unless that run rendered the commit being built.

## The workflows

- `fix-issues.yml` (by hand): Claude Code lands the plans in `documentation/issues/`, one commit each in the README's
  merge order (or by `EXECUTION.md`), skipping a plan that needs iOS or changes `.github/workflows/`; it pushes main
  (a branch if main moved) and dispatches `tests.yml`.
- `publish-web.yml` `rsync --delete`s the distribution over `app/` of the `campfire-website` repository
  (https://campfire-songbook.com/app/), a folder that holds only the distribution.

### `publish-linux.yml`

- The `.deb` and AppImage on amd64 and arm64 (jpackage only packages for its own machine), attached to the release —
  the only way Linux is distributed.
- appimagetool and the AppImage runtime are pinned releases checked against their SHA-256 in the matrix; a bump takes
  the new digests from the release page. The AppImage is built and start-checked (extracted, as the runner has no
  FUSE) after the `.deb` is attached, so its failure withholds only the AppImage.
- Runs on the **oldest** supported Ubuntu: a `.deb` depends on the system libraries it was built against.

### `publish-windows.yml`

- Submits the unsigned `.msix` (the Store signs it) with `.github/scripts/microsoft_store_submission.py`, which finds
  the app by package identity name and builds on the last published submission, writing "What's new" and the listing
  fields into every language's `baseListing`; the rest goes back as it came. Green means submitted, not certified.
- **A draft the script made whose commit failed** is reused: only package, "What's new", and listing text replaced.
- **A draft started in Partner Center stops the run**, untouched: the API cannot change a submission it did not create.
  Finish it there with the run's `.msix` artifact, or delete it.
- **`submit` off** writes the run's own draft without the package or new screenshots (Partner Center only takes an
  API-uploaded file when the API commits); finish it in Partner Center with the run's artifact, or let a later run take
  it up.
- A committed submission with this version is left alone (reruns succeed); one with another version stops the run.
- Auth: Microsoft Entra application with the Manager role and **no secret** — a federated credential trusts GitHub's
  OIDC token for `repo:pandulapeter/campfire:environment:microsoft-store`, hence the `microsoft-store` environment and
  `publish-all.yml`'s `id-token: write`. Nothing expires.

### `publish-macos.yml` and `publish-ios.yml`

- macOS: `packageReleasePkg` on Apple silicon (asking for the `.pkg` is what signs and sandboxes it). A store-signed
  build only starts under TestFlight, so the start check runs on an ad-hoc-signed copy with the same entitlements minus
  the two naming the App ID. The `build_number` input re-uploads a release under a new number.
- iOS: archived with the App Store profiles of the app and the share extension (`app_store_signing.py profile`, never
  one Xcode made, which manual signing refuses), uploaded to TestFlight. Xcode starts Gradle without properties, so the
  sync key goes into `local.properties`, which the version build phase also reads (last value wins) — how the
  `build_number` input works without a commit. The share extension's App ID and the App Group were registered in the
  developer portal by hand, once; a profile made through the API carries them.
- `.github/scripts/app_store_submission.py` puts the build and "What's New" (not on a platform's first version) on the
  version, writes the listing (on iOS also the app info's name and subtitle; the Mac shares the app record), and
  submits; `submit` off stops before submitting. A name another app holds fails the run before submitting; with no
  editable app info (a version in review) the name and subtitle wait, with a warning.
- An existing **draft** is used as is: only build, "What's New", listing text, and (with the rendered images)
  screenshots replaced, release option kept, submitted in its existing review submission.
- Already in review with this build: left alone. Another version still waiting for Apple: the build stays in
  TestFlight and the run ends green with a warning. Green means submitted, not approved.

### Apple signing

- **Nothing Apple signs with expires.** Secrets hold only keys: `APPLE_SIGNING_KEY` (unencrypted PEM) and the App Store
  Connect API key (`APP_STORE_CONNECT_KEY_ID`, `_ISSUER_ID`, `_PRIVATE_KEY` — the `.p8` text, not base64, an Admin key).
  `.github/scripts/app_store_signing.py` finds each certificate type made for the signing key (matched by public key)
  and creates a new one for the same key when none exists or the newest has under two months left; profiles likewise.
- **xcodebuild never gets the API key** (no `-allowProvisioningUpdates`): automatic signing makes a certificate on
  every runner, its key lost with the runner, until the team's limit refuses the archive. Kubriko's iOS workflow too.
- **Kubriko shares the same key and secrets**: Apple allows two Apple Distribution certificates per team, one shared and
  one free for renewal. A certificate made by hand in Xcode takes the free slot (a 409 on creation means that).
- **Never revoke the signing key's certificates**: a revoked certificate makes any build not yet approved an invalid
  binary (ITMS-90238). **Never revoke a Developer ID certificate** either: that breaks every downloaded copy.

### `publish-android.yml`

- `.github/scripts/play_store_release.py` does everything in one Play edit, one review: the APK and its mapping onto
  production (a draft with `submit` off), the update priority, the notes per locale (cut to 500 at a line boundary),
  the listing text, the images (with the rendered ones), new languages' listings, and the commit. Standard library only, the JWT signed by `openssl`.
- **An APK, not a bundle**: the Play listing predates the bundle requirement and was never migrated; `bundleRelease`
  would be rejected.
