<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: screenshots rendered and published by the release

**Status:** not started. Written against the code on 2026-11-14 (48243c7bb).

Today the store images, the website's screenshots, its link preview, and the README banners are rendered on the
owner's Mac (the `store-screenshots` and `website-screenshots` skills), checked by eye, uploaded by hand to each store
console, and committed by hand to `campfire-website`. Now that the library the tool renders is committed
(`tools/screenshots/library/`), a clean checkout renders the same pictures, so the release can make and publish them:
every store's images, the website's screenshots and link preview, and the README banners, when the release's
description asks for it. And a language the app gained since the last release gets its listing created in every
store, with its text and its images, by the same run: no console is opened by hand.

## Decisions

- **A hidden switch turns it on**: `<!-- screenshots: true -->` in the release description, read by
  `release_description.py` with the other switches. Absent, it is false and no image is touched anywhere, as today.
  This is the one switch whose default is the weaker action (the others default to true, `.github/CLAUDE.md`): it
  costs an hour of macOS runners and replaces what every store shows, so it is asked for. A value other than
  `true`/`false`, or a comment that looks like it but does not read, stops the release before any build, as the
  others do. No approval: the switch is the decision, and the run's contact sheets are there to look at afterwards.
- **Rendered in the release run, from the tag's checkout**, so the images always show the code the release ships and
  never need matching to it. `publish-all.yml` calls a new reusable `screenshots.yml` right after `prepare`, beside the
  tests, so the two run at the same time.
- **The store jobs wait for it.** App Store Connect takes screenshots only on a version still in Prepare for Submission,
  so they must land before the iOS and macOS jobs submit; the Microsoft Store's images are part of the submission the
  Windows job commits; Play's go into the same edit as the release, which is committed at the end. A job inside a
  called workflow cannot wait for a job of its caller, so each store's whole workflow (`android`, `ios`, `macos`,
  `windows`) `needs` `screenshots` as well as `prepare` and `test`. With the switch off, `screenshots` is skipped and
  costs them nothing. `linux` and `web` never wait. Splitting each store workflow into a build and a submit job, so
  that only submitting waits, is left until a measured run shows the wait matters: the renders run beside the tests,
  so the stores wait only for whatever of the render outlasts them.
- **The images never hold the app back.** Every waiting job runs `if: !cancelled()` with `prepare` and `test`
  succeeded, whatever `screenshots` did. When `screenshots` failed (a render, the framing, or the validation below), each
  store gets `screenshots: false` and ships the build with the images it already has, the website and the README
  stay as they are, and the run ends red with the reason in its summary. A bug fix reaching users matters more than
  fresh pictures, and the old ones still show the app. The one exception is a store that must create a listing for a new language
  and requires its images (below): that store stops before changing anything, the others ship.
- **Validated before any store is touched.** The last step of `screenshots.yml` checks every image against the rules of
  the store it is for (below): count per set, exact pixel sizes, no alpha channel, file size, aspect ratio, every
  language the store has. A failure is a failed `screenshots` job, so it is caught before any store changes, not
  after half of them did.
- **Images replace, never merge.** Each set the run has is emptied and refilled, so a shot taken out of the listing
  disappears from the store too. A set the run has no images for is left alone (a device the tool cannot draw yet).
- **Images follow `listings`.** A store's `listings: false` leaves its images alone too; `submit: false` does not: the
  draft it leaves gets the new images, which is what a hand-finished submission wants.
- **Artifacts are the only storage.** The run's images are one artifact, kept 14 days (`retention-days`), roughly
  0.5–1 GB. The repository is public, so it costs nothing and anyone signed in can download it, harmless for images
  the stores show anyway. Never a branch, the repository's history, or release assets.
- **macOS runners render.** The iOS and Mac chrome is set in `FontFamily.Default`, SF Pro only on macOS (`Fonts.kt`).

## `screenshots.yml`

- `workflow_call` (from `publish-all.yml`, input `release_tag`) and `workflow_dispatch` (input `ref`), the latter to
  look at what a release would publish without releasing; it publishes nothing either way. No secrets: rendering
  reaches nothing outside (`Fakes.kt`), and no `DROPBOX_APP_KEY` check, since nothing it builds ships.
- **Render**: a matrix of shards on `macos-latest`, each `./gradlew :tools:screenshots:run --args="--shard i/n
  --parallel 2 --out renders"` (two processes of about 1.6 GB fit a hosted runner), uploading its renders as an
  artifact of its own. The promo set stays out (`byNameOnly`). `n` is at most 3: an account runs 5 macOS jobs at once,
  and the tests' macOS job runs beside them. Each shard has a `timeout-minutes`, so a hung render fails the job
  rather than holding every store for the 6-hour limit.
- **Frame**, one job after the matrix: every shard downloaded into one `renders/`; `--frame store --out store`;
  `--frame readme` (the README banners into `documentation/screenshots`, the link preview into `renders/`);
  `website_screenshots.py --website website` into an empty folder, which writes the website's own tree
  (`assets/screenshots/**`, `assets/og-image.jpg`) without a website checkout.
- **Validate**, then upload one artifact, `screenshots`, laid out by destination: `play-store/<language>/<type>/`,
  `app-store/<language>/<display type>/`, `mac-app-store/<language>/`, `microsoft-store/<language>/`, `website/`
  (the website repository's paths), `readme/` (`documentation/screenshots`), and `contact-sheets/`, one per store row
  and one for the website, built by a script in `.github/scripts` with its test. The job summary lists the counts per
  destination.

## The stores

Each publishing workflow gains a `screenshots` boolean input. Set, it downloads the run's `screenshots` artifact
(same run: `actions/download-artifact` by name) and its store script replaces the images before it submits. Every
script makes its changes idempotent (empty the set, then fill it), so a store job re-run from the run's page, which
still has the run's artifact, ends in the same state however far the failed attempt got.

### Play (`play_store_release.py`)

- In the edit that already uploads the APK and writes the listing: per language, `edits.images.deleteall` and
  `edits.images.upload` for `phoneScreenshots`, `sevenInchScreenshots`, `tenInchScreenshots`, and `featureGraphic`;
  committed with the rest.
- Validation: 2–8 phone screenshots, up to 8 of each tablet size, each side 320–3840 px with the long side at most
  twice the short one (a phone's own 1080 × 2424 would fail; the framed listing canvas must not), ≤ 8 MB, PNG or JPEG;
  the feature graphic exactly 1024 × 500.
- **To check first:** the Chromebook row. The API's image types name no Chromebook; if Play takes them only in the
  console, that row stays manual and `.github/CLAUDE.md` says so.

### App Store and Mac App Store (`app_store_submission.py`)

- On the version the script already finds or creates (a new version starts with the previous one's screenshots; a
  draft made by hand is replaced like any other, since the switch asked for it): per localization and display type,
  delete the set's screenshots, then for each image reserve an `appScreenshot` (name, size), PUT its upload
  operations, PATCH it `uploaded` with its MD5, and finally order the set as the listing is. Submit only once every
  image is `COMPLETE`; one `FAILED`, or still processing after a timeout, stops that store before submitting.
- A version already waiting for or in review cannot change; the script stops there as it does today.
- Display types: the iPhone row to the 6.9" type, the iPad to the 13", the Mac to the desktop type. Validation: at
  most 10 per set, each exactly a size its display type accepts, no alpha channel.
- **To check first:** the iPhone Duo row's display type (none in the API yet: its images wait, said in the summary);
  whether the product page header and search results images (`Compositions.kt`) have an API at all (likely not: they
  stay manual, said in `.github/CLAUDE.md`).

### Microsoft Store (`microsoft_store_submission.py`)

- In the submission the script already builds and commits: each language's listing marks its current images
  `PendingDelete` and adds the run's (`imageType: Screenshot`, `PendingUpload`, in the listing's order); the files go
  into the zip uploaded to the submission's `fileUploadUrl`. A draft started in Partner Center still stops the run,
  untouched.
- Validation: at most 10 screenshots per language, PNG, the sizes Partner Center accepts for desktop.
- `.github/CLAUDE.md`'s "new screenshots: dispatch by hand with `submit` off and add them in Partner Center" goes.

## The website and the README

- **Website**: a `website-screenshots` job of `publish-all.yml`, after `screenshots` succeeded, checks out
  `pandulapeter/campfire-website` with `CAMPFIRE_WEBSITE_DEPLOY_KEY`, `rsync --delete`s the artifact's
  `assets/screenshots/` over the checkout (a shot taken out of the site, or a language's image that became identical to
  the English one and is no longer written, leaves no file behind) and replaces `assets/og-image.jpg`, then commits
  "Update the screenshots to v<version>." It shares a `concurrency` group with `publish-web.yml`'s push, so the two
  commits to the website never race; the web app does not wait for the images.
- An image no page of the website references fails the job before it commits, so a new shot never goes live unseen: a
  new screenshot needs its page written by hand in the website repository first.
- **README**: a `readme-screenshots` job, after `screenshots` succeeded, commits `documentation/screenshots/01–06.webp`
  to `main` ("Update the README screenshots to v<version>."), fetching and rebasing on a rejected push (`main` may
  have moved since the tag; only `documentation/` changes, so it never conflicts and starts no test run).
- Neither waits for the stores: the website and the README describe the app as released, and a store's review only
  delays when users get it.

## New languages

`.github/CLAUDE.md`'s rule "a language's first listing is added by hand, once, in every console, with its
screenshots" goes: a store missing one of the app's languages gets it created by the run, and the run fails only where
a store's API cannot.

- **Which languages are new is asked of each store**, never worked out from git: `match_locales` already returns the
  app's languages a store has no locale for, so a language a failed earlier release never got into one store is
  created there next time, and one already added by hand is simply written over.
- **The release detects it before any build.** `prepare` compares the app's languages (`app_languages`, the `values-*`
  folders) at the tag with those at the previous release's tag. A language added since turns the screenshots switch
  on where it is absent (said in the run's summary), since a store may require a new listing's images (the Microsoft
  Store does); an explicit `screenshots: false` or any store's `listings: false` alongside a new language stops the
  release before any build, with a message saying why, since either would leave a listing that cannot be created
  whole. The comparison is only the early warning; each store script still asks its store.
- **Everything the new language needs is in the repository before the release**, which the tests already enforce:
  `store_listings.py --check` (in `tests.yml`) fails unless every store folder of `documentation/store-listings` has
  exactly the app's languages, and the framing needs `screenshot-texts/<language>.md`, which the check gains. The
  `add-language` skill writes all of them.
- **A locale for each store**: a language with no locale in a store gets one, from a table in `store_listings.py`
  beside `ALIASES` (Play `de-DE`, `hu-HU`, `iw-IL`; App Store Connect `de-DE`, `hu`, `he`; Partner Center `de-de`,
  `hu-hu`, `he-il`; one entry per app language and store, checked by `--check` to cover every language). Regional
  copies (`fr-CA`) stay something a console adds and the scripts then fill in, as today.
- **Created in order, before anything is uploaded into it**: the listing first, then its images, then the submission.
  A store job that fails midway leaves a listing the next attempt finds and fills in, since every step replaces.

### Per store

- **Play**: `edits.listings.update` on a language the app has no listing in creates it (title, short and full
  description from the store's file), then the images go in as for every language; the edit is committed with the
  release. A language without its own images falls back to the default language's on Play, so even a store job
  without the run's images could create it, but the switch is on anyway.
- **App Store and Mac App Store**: on the version being prepared, `POST appStoreVersionLocalizations` (description,
  keywords, promotional text, support and marketing URLs, and "What's New", which a version that is not the first
  requires for every localization: the language's notes, else the English), and `POST appInfoLocalizations` for the
  app info's name, subtitle, and privacy policy URL (the website's page in that language,
  `campfire-songbook.com/<language>/privacy/`, else the English one). Both are possible only while the version and
  the app info are editable, which is when the script runs. The Mac shares the app record, so its app info
  localization is created once, by whichever of the two jobs gets there first (each creates it only if it is missing).
- **Microsoft Store**: the submission's `listings` gains the language (`baseListing` from the store's file, its images
  `PendingUpload`), alongside the package that declares it; Partner Center lists a language only for a package that
  supports it, and the app's `.msix` declares every language of the app. `.github/CLAUDE.md`'s "newly declared
  language: dispatch by hand with `submit` off and `listings` off" goes with the screenshots one.
- **To check first**, against each API: that Play and Partner Center create a listing from an update in an edit or a
  submission (Play's API does; Partner Center's is the one to try, with `submit: false`, on the first language added);
  that Partner Center requires at least one screenshot per listing language; that App Store Connect asks nothing more
  of a new localization than the fields above.

### The website and the README

- A language the website has no pages for yet (its pages come from `localize.py` in that repository) gets no images
  copied, with a warning in the summary, rather than failing the job: the images land with the first release after
  its pages exist. The "image no page references" check judges only the languages the site has.
- The README banners are English only and do not change.

## By hand

- `publish-all.yml`'s dispatch form gains a `screenshots` checkbox, false by default, the same switch.
- A single store workflow dispatched by hand (a store retried after its run's artifact expired, or a script fix on an
  existing tag) gets `screenshots: false` unless given `screenshots_run`, the ID of a run whose artifact to use: it
  is downloaded across runs (`run-id`, `github-token`), and refused if that run's commit is not the tag's or the
  artifact has expired (then: dispatch `screenshots.yml` on the tag and pass its run).
- A store script without images that finds a language its store lacks creates it where the store allows a listing
  without images (Play, App Store Connect) and stops before changing anything where it does not (Partner Center),
  naming the run to pass as `screenshots_run`.
- The skills stay for looking at a change locally; publishing is the release's.

## Steps

1. **Check the committed library against the old one**, once: render everything before and after the library
   commit (586777322) in a worktree and compare pixel by pixel. Expected differences: the store shots changed since,
   and possibly the website's Amazing Grace iPad shot (its new intro grid may push it past page one). Anything else
   means a cut song shows filler and needs more of its words back.
2. Answer the "to check first" questions against each store's API documentation, and settle the validation rules
   and the locale table.
3. `screenshots.yml` with its framing, validation, and contact sheets, dispatched by hand; measure a full run on
   hosted runners and settle the shard count.
4. The switch in `release_description.py` and the dispatch form, with tests; `publish-all.yml`'s `screenshots`,
   `website-screenshots`, and `readme-screenshots` jobs and the store jobs' `needs` and `if`.
5. Each store script's image replacement and listing creation, with tests: Play, Apple, Microsoft; `prepare`'s new
   language detection and its stops; `--check`'s locale table and screenshot texts coverage.
6. A release with `screenshots: true` and `submit: false` everywhere, then each console looked at, before the first one
   that submits; the same again for the first release that adds a language.
7. Update `.github/CLAUDE.md` (the switch, the jobs, new languages, what stays manual), the `store-screenshots`,
   `website-screenshots`, and `add-language` skills, `tools/screenshots/CLAUDE.md`, `store_listings.py`'s and
   `publish-all.yml`'s header comments.

## Out of scope

- The promo video (`tools/promo-video`): it needs the owner's recording and a look at every cut.
- A regional copy of a language (`fr-CA` beside `fr-FR`): still added in a console, then filled in by every release.
