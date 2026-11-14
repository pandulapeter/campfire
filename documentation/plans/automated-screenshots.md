<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: screenshots rendered on CI and published by the release

**Status:** not started. Written against the code on 2026-11-14 (7767d774d).

Today the store images, the website's screenshots, its link preview, and the README banners are rendered on the
owner's Mac (the `store-screenshots` and `website-screenshots` skills), checked by eye, and uploaded by hand to each
store console and committed by hand to `campfire-website`. Now that the library the tool renders is committed
(`tools/screenshots/library/`), a clean checkout renders the same pictures, so CI can make them and the release can
publish them: every store's images, the website's screenshots and link preview, and the README banners.

## Decisions

- **Two steps, joined by a run ID.** A `screenshots.yml` workflow, dispatched by hand on the commit about to be
  released, renders and frames everything and leaves it as one artifact with contact sheets. The owner looks at them;
  choosing to pass that run's ID to the release is the approval. `publish-all.yml` gains a `screenshots_run` input:
  empty, no image is touched anywhere (today's behaviour); set, every store, the website, and the README get that
  run's images. No environment gate is needed: nothing leaves GitHub until a release names the run.
- **The images belong to a release, not to `main`.** Store screenshots, the website, and the README banners all show
  the released app, so they change when it ships ("Release-time tasks are not blockers"), in the release run.
- **The run must be of the tag's commit.** The release refuses a `screenshots_run` whose head SHA is not the tag's
  commit, so an image never shows code the release does not ship (the framing also stamps the version into its
  folder name and the What's new dialog is marked seen by version).
- **Artifacts are the only storage.** One artifact per run, kept 14 days (`retention-days`), roughly 0.5–1 GB of PNG
  and WebP; the repository is public, so it costs nothing and anyone signed in can download it, which is harmless for
  images the stores show anyway. Never a branch, the repository's history, or release assets.
- **macOS runners render.** The iOS and Mac chrome is set in `FontFamily.Default`, which is SF Pro only on macOS
  (`Fonts.kt`); Roboto and Open Sans are downloaded by the build and would render anywhere, but one platform keeps
  the jobs identical.
- **Images replace, never merge.** Each store's sets are emptied and refilled for every language and device the run
  has, so a shot taken out of the listing disappears from the store too.

## `screenshots.yml`

- `workflow_dispatch` only, input `ref` (default the dispatching commit). Writes `local.properties` like every other
  workflow (no secrets are needed: rendering reaches nothing outside, `Fakes.kt`).
- **Render**, a matrix of shards on `macos-latest`: `./gradlew :tools:screenshots:run --args="--shard i/n --parallel
  2 --out renders"` (two processes of about 1.6 GB each fit a hosted runner's memory; measure, then pick `n` for a
  run under an hour). The promo set stays out (`byNameOnly`): the video is made by hand from the owner's recording.
  Each shard uploads its renders as an artifact of its own.
- **Frame**, one job after the matrix: downloads every shard into one `renders/`, runs `--frame store` and `--frame
  readme` (the README banners land in `documentation/screenshots`, the link preview in `renders/`), then
  `website_screenshots.py` with `--website` pointed at an empty folder, so it writes the website's tree
  (`assets/screenshots/**`, `assets/og-image.jpg`) without a website checkout. The store framing writes into the Desktop
  unless given `--out`, which the job passes.
- **Contact sheets**: one per store row and one for the website, at the top of the artifact, named so the owner opens
  them first. Built by a small script in `.github/scripts` (Pillow), with a test, since it now runs on CI rather than
  in a skill's scratchpad.
- **One artifact**, `screenshots-<versionName>`, laid out by destination: `stores/<store>/<language>/…`,
  `website/…` (the website repository's own paths), `readme/…` (`documentation/screenshots`), `contact-sheets/…`.
  The job summary lists counts per destination and any render that failed (the tool exits non-zero on one; the job
  then fails and no artifact is offered for release).

## The release

`publish-all.yml` passes `screenshots_run` to every publishing workflow. Each one, when it is set, downloads the
artifact with `actions/download-artifact` (`run-id` and `github-token`, which reads another run's artifact) and checks
its head SHA against the tag before anything else.

### Play (`play_store_release.py`)

- In the same edit that writes the listing text: per language, `edits.images.deleteall` then `edits.images.upload`
  for `phoneScreenshots`, `sevenInchScreenshots`, `tenInchScreenshots`, and the `featureGraphic`; committed with the
  rest of the edit.
- **To check first:** where the Chromebook row goes. The Play API's image types name no Chromebook; Play may take
  Chromebook images only in the console. If so, that row stays manual and the plan says so in `.github/CLAUDE.md`.

### App Store and Mac App Store (`app_store_submission.py`)

- Screenshots belong to a version in Prepare for Submission, which the script already finds or creates before it
  writes "What's New": for each localization, per display type, delete the set's screenshots, then for each image
  reserve an `appScreenshot` (file name, size), PUT its upload operations, and PATCH it `uploaded` with its MD5. Wait
  for every image to leave `AWAITING_UPLOAD`/`UPLOAD_COMPLETE` for `COMPLETE` before submitting; a `FAILED` one stops
  the run.
- Display types: the iPhone and iPad rows map to the 6.9" and 13" types, the Mac row to the desktop type.
- **To check first:** the iPhone Duo row's display type (the foldable may have none in the API yet, then its images
  wait), and whether the product page header and search results images (`Compositions.kt`) have an API at all. They
  likely do not; then they stay a manual step, said in `.github/CLAUDE.md`.

### Microsoft Store (`microsoft_store_submission.py`)

- The submission the script builds already carries every language's `baseListing`; its `images` list is replaced with
  the run's screenshots (`imageType: Screenshot`, in the listing's order), and the files go into the zip uploaded to
  the submission's `fileUploadUrl` with the package. The script commits only submissions it created, so this works
  for every release it already publishes.
- The rule "new screenshots: dispatch by hand with `submit` off and add them in Partner Center" in `.github/CLAUDE.md`
  goes away.

### The website (`publish-web.yml`)

- The job that already checks out `pandulapeter/campfire-website` with `CAMPFIRE_WEBSITE_DEPLOY_KEY` and rsyncs the web
  build into `app/` also copies the artifact's `website/` tree over the checkout: `assets/screenshots/` with `rsync
  --delete` (a shot taken out of the site leaves no file behind; the site's placeholder covers a missing one),
  `assets/og-image.jpg` replaced. One commit for both, in that job, so two pushes to the website never race.
- A screenshot new to the site still needs its page in the website repository first, written by hand; the release
  only refreshes images the pages already name. The job fails if the artifact has a file no page references, so a new
  shot never ships unseen.

### The README banners

- A last job of `publish-all.yml`, after every store succeeded, commits `documentation/screenshots/01–06.webp` from the
  artifact to `main` ("Update the README screenshots to v<version>."), retrying a rejected push after a rebase; it
  touches only `documentation/`, so it starts no test run.

## Steps

1. **Check the committed library against the old one**, once, by hand: render everything before and after the
   library commit (586777322) in a worktree and compare pixel by pixel. Expected differences: the store shots changed
   since, and possibly the website's Amazing Grace iPad shot (its intro grid is new; its text size was chosen so the
   whole song fills page one). Anything else means a cut song shows filler and needs more of its words back.
2. The contact sheet script, with its test.
3. `screenshots.yml`; measure a full run on hosted runners and settle the shard count.
4. The release side: `screenshots_run` in `publish-all.yml` and each publishing workflow, the SHA check, then Play,
   Apple, Microsoft, the website, and the README, each behind its own failure (one store refusing images does not hold
   back the others' builds, as today).
5. Update `.github/CLAUDE.md` (the workflow, the input, what stays manual), the `store-screenshots` and
   `website-screenshots` skills (a release now uploads; the skills remain for looking at a change locally), and
   `tools/screenshots/CLAUDE.md`.

## Out of scope

- The promo video (`tools/promo-video`): it needs the owner's recording and a look at every cut.
- A language's first listing in each store, still added by hand once (`.github/CLAUDE.md`), now without screenshots:
  the next release fills them in.
