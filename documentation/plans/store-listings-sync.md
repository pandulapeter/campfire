<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: store listings written by the release, in every language

**Status:** not started. Written against the code on 2026-10-28 (a236470d2). The goal is that publishing a release
(`publish-all.yml`, which calls each store's workflow) also writes every store's listing text, in every language, from
`documentation/store-listings/<store>/<language>.md`, so nothing is pasted by hand any more.

**Scope:**

- **Updates only.** The owner creates every language's listing by hand once, in each console; from then on the release
  keeps them current. A language the store has no listing for is never created by CI (see Decisions).
- **Text only.** Screenshots, the app icon, the feature graphic, and every other image stay manual. No script reads,
  writes, or deletes an image, so an image set by hand is never touched.
- **Release notes in every language**, so the stores stop showing English "What's new" in a German listing.

## What is there today (the hooks)

- `release_description.py` reads the release body's hidden comments: one `whats-new en-US` block (a block in any other
  language is ignored), Play's `update-priority`, and a `submit` switch per store. It holds the notes against every
  store's limit before any build. `publish-all.yml` hands its outputs to the six workflows.
- **Microsoft** (`microsoft_store_submission.py`): copies the last published submission, which already holds a
  `listings` dictionary keyed by language, writes `releaseNotes` into every listing's `baseListing`, replaces the
  package, PUTs the whole submission, and commits. A PUT replaces the submission, so every field it sends back as it
  came stays as it was, images included.
- **Apple** (`app_store_submission.py`, both Apple workflows): finds or creates the version for `campfire.versionName`,
  attaches the build, PATCHes `whatsNew` on every `appStoreVersionLocalization`, and submits. Shared HTTP in
  `store_http.py`; tested in `test_app_store_submission.py` with a fake `request`.
- **Play** (`publish-android.yml`): `r0adkll/upload-google-play@v1.1.5` uploads the APK and mapping file and writes
  `whatsnew/whatsnew-en-US` (cut to 500 characters by an inline Python step), in one edit it commits itself.
- **The files**: each `<store>/<language>.md` has `## Field (limit)` headings over fenced blocks, read by
  `check_store_listings.py`'s `sections()` / `BLOCK`. The other languages' files hold only the fields that store keeps
  per language; their "Paste into …" line is prose, not something to parse.

## L1. One reader for the listing files (`.github/scripts/store_listings.py`)

- `read(store) -> {language: {field: text}}`, built on `check_store_listings.py`'s parsing (move `sections()` and
  `BLOCK` into the new module and import them back, so both read a heading the same way). The field key is the heading
  without its limit (`Full description`); a field with several blocks (Microsoft's product features, search terms) is a
  list.
- **Locales are a table in the code**, not the prose line: per store, the app language → the store's locale codes the
  file is written to. Play `es` → `es-ES`, `es-419`, `es-US`; `fr` → `fr-FR`, `fr-CA`; `en` → `en-US`. Apple `es` →
  `es-ES`, `es-MX`; `fr` → `fr-FR`, `fr-CA`; `hu` → `hu`; `it` → `it`; `ro` → `ro`; `en` → `en-US`. Microsoft `en` →
  `en-us`, `de` → `de-de`, and so on, as `AppxManifest.xml` declares them. Check every code against each console's
  list once while writing this table.
- **Fields are a table too**, per store: heading → API field. Every heading with a limit must be mapped or listed as
  manual, and a test fails otherwise, so a renamed or new heading fails `tests.yml`, never a release:

  | Store | Written | Manual (stays pasted by hand) |
  |---|---|---|
  | Play | App title → `title`, Short description → `shortDescription`, Full description → `fullDescription` | – |
  | App Store | Name, Subtitle (app info); Promotional text, Description, Keywords, Support URL, Marketing URL (version); App Review notes (review detail, English) | Copyright (the version's `copyright`, rarely changes; write it too if the API step proves easy) |
  | Mac App Store | Promotional text, Description, Keywords (version); App Review notes (English) | App Sandbox Information (not in the public API; stays manual) |
  | Microsoft | Short description, Description, Product features → `features`, Search terms → `keywords`, Copyright and trademark info → `copyrightAndTrademarkInfo` | Title (the reserved name), Developed by |

  English-only fields (URLs, review notes) are read from `en.md`; the URLs go to every locale's version localization.
- `python3 .github/scripts/store_listings.py --check` (run by `tests.yml` beside `check_store_listings.py`): every store
  folder has the same languages, every file parses, every mapped field is present in `en.md`, every language has its
  locales. Tests in `test_store_listings.py`.

## L2. Release notes in every language

- The notes come from the release description, exactly as the English do: prepare-release already writes a
  `whats-new <locale>` block per language of the app after `whats-new en-US` (`de-DE`, `es-ES`, `fr-FR`, `hu-HU`,
  `it-IT`, `ro-RO`; its "Style — the store \"what's new\"" section), each at most 500 characters, translated from the
  in-app message. Today `release_description.py` ignores every block but `en-US`.
- `release_description.py` reads them all: each held against every store's limit (Play's 500 still only a warning), an
  unknown locale or one written almost like a known one stopping the release as a malformed `en-US` does now. It hands
  the workflows, through `publish-all.yml`, one more output, `release_notes_by_language` (JSON, `{"de": "…", …}`, keyed
  by the app's language codes), beside today's `release_notes`. A language with no block, and the hand dispatch, which
  stays English, get the English notes. Update `publish-all.yml`'s header, where the block format is described.
- Each store script maps languages to locales through L1's table and writes that language's notes to each: Play as the
  release's notes per locale, Apple as each version localization's `whatsNew`, Microsoft as each listing's
  `releaseNotes`.

## L3. A switch per store

- `<!-- <store> listings: false -->` in the release body, read like `submit` (unknown values stop the release, the
  default is the stronger action: write). `false` leaves that store's listing text alone and writes only the notes, for
  a release made while a listing is being reworked in the console.
- Every script prints, per locale, which fields it changed (old and new lengths, not the text), so the run log says
  what went out.

## M. Microsoft Store (first: its hand steps caused the trouble)

In `microsoft_store_submission.py`, beside `write_release_notes`:

- `write_listings(submission, listings)`: for each language and locale, if `submission["listings"]` has that locale,
  set the mapped `baseListing` fields; leave every other field (images, title, URLs) as it came. A locale the
  submission has no listing for is **not created**: the run fails before anything is uploaded, naming the locale and
  saying to add its listing in Partner Center once (by hand, with its screenshots), unless `listings: false`.
- Order in `main`: release notes, listings, then the package, so a refused listing fails before the upload. The
  existing draft handling is unchanged: a draft the script made is reused, a draft started in Partner Center still
  stops the run.
- **Step 0, before writing code**: on the next release with `microsoft-store submit: false`, print the copied
  submission's `listings` keys and one `baseListing`'s field names to the log, and confirm `shortDescription`,
  `features`, `keywords`, and `copyrightAndTrademarkInfo` are the names the API uses for this app (an MSIX app on the
  classic submission API). Adjust the table to what it prints.
- Limits Partner Center enforces beyond the headings: 7 search terms of at most 30 characters and 21 words in all.
  Add those to `check_store_listings.py` (as a heading form, `(7 max, 30 characters each, 21 words in all)`) so they
  fail before a release.
- Tests: extend the script's tests (create `test_microsoft_store_submission.py` on `test_app_store_submission.py`'s
  fake `request` pattern): fields written per locale, images and title untouched, a missing locale fails before the
  upload, `listings: false` writes only the notes.

## A. Apple (iOS and Mac)

In `app_store_submission.py`, after the build is attached and before `submit`:

- **Version localizations** (`GET /appStoreVersions/{id}/appStoreVersionLocalizations`): PATCH `description`,
  `keywords`, `promotionalText`, `supportUrl`, `marketingUrl`, and `whatsNew` per locale, replacing today's
  `write_whats_new`. A locale with no localization is not created (it fails before submitting, as Microsoft's does),
  since the owner adds each language once by hand.
- **Name and subtitle** (iOS run only; the Mac shares the app record): the app info in an editable state (`GET
  /apps/{id}/appInfos`, the one whose `appStoreState` is `PREPARE_FOR_SUBMISSION` or similar), then PATCH each
  `appInfoLocalization`'s `name` and `subtitle`. Apple refuses a name another app holds: that fails the run before
  submitting, with Apple's message. When no app info is editable (the version is already in review), skip with a
  warning; the next version picks it up.
- **Review notes**: PATCH the version's `appStoreReviewDetail` `notes` from `en.md` (create it when missing, as
  Apple's API allows).
- **Promotional text between releases**: Apple lets it change without a review. A `listings_only` input on both Apple
  workflows' dispatch form runs only the version localization step for `promotionalText` on the live version. Optional;
  do it last.
- Tests in `test_app_store_submission.py`: the PATCH bodies per locale, the Mac run never touching name and subtitle, a
  missing locale and a taken name failing before `submit`, a version in review skipping the name with a warning.

## P. Google Play

Today's upload is a third-party action that commits its own edit, so listing text written in a second edit would be a
second commit and a second review. **Recommended: replace the action with `play_store_release.py`**, one edit for
everything, on the same `PLAY_SERVICE_ACCOUNT_JSON`:

- Auth: a service account JWT exchanged for a token (`google-auth`, installed pinned in the step, or the JWT signed
  with `openssl` to stay on the standard library as the other scripts do; prefer the latter if it stays short).
- `edits.insert` → `edits.apks.upload` → `edits.deobfuscationfiles.upload` (the mapping file) → `edits.tracks.update`
  (production, the release name, `inAppUpdatePriority`, `status` completed or draft as `submit` says, and
  `releaseNotes` per locale from L2, each cut to 500 at a line boundary: move that inline Python into the script) →
  `edits.listings.update` per locale (title, short and full description; Play's `listings.update` keeps the images) →
  `edits.commit` with `changesNotSentForReview=false`.
- A locale with no listing is not created (fails before the commit, as above).
- The alternative, keeping the action and writing listings in a separate edit first, is smaller but sends two changes
  for review; not recommended.
- Tests: `test_play_store_release.py` with a fake request, as the others.

## Docs

- `.github/CLAUDE.md`: the listings are written by the release (the per-store sections and "Release notes and per-store
  switches"), what stays manual (images, Microsoft title, Mac sandbox information, Apple copyright), and that a
  language's first listing is added by hand.
- prepare-release step 9: the files are now what ships, so its "pasted by hand" sentences change; step 11's report says
  the listings go out with the release rather than what to paste where.
- add-language: after the listings are written, tell the user to add the new language's listing in every console once
  (with its screenshots) before the next release, or that release fails on the missing locale.
- The store-listings memory note.

## Order

1. L1 (reader, tables, `--check` in `tests.yml`) and the Microsoft search term limits in `check_store_listings.py`.
2. M step 0 (a release with `microsoft-store submit: false` that logs the listing field names), then M.
3. L2 and L3 (multilingual notes and the switch), used by M at once.
4. A.
5. P.
6. A's `listings_only` dispatch, if still wanted.

## Decisions

- **A language the store has no listing for: fail, never create.** *Recommended*: the first listing needs screenshots
  by hand anyway, and creating a text-only listing would publish it without them. Alternative: create it and warn.
- **The listing files are the only source of truth.** Any text edited in a console is overwritten by the next release.
  Images are never touched.
- **Play: replace the upload action with an own script** (one edit, one review). Alternative: two edits.
- **Release notes per language from the in-app message.** Alternative: English notes in every language, as now.

## Risks

- A store's API rejects a field it accepts in the console (length counted differently, a character it refuses): the
  run fails after the builds. Mitigation: every limit is checked in `tests.yml` before any release; the first release
  after each step runs with that store's `submit: false` and is checked in the console before submitting.
- Apple's name uniqueness: a localized name can be refused at any time if another app takes it first. The run fails
  before submitting, with Apple's message.
- Microsoft's classic submission API is the one the script already uses; if Partner Center moves this app to the newer
  API, every field name changes. Step 0 confirms the names before code is written.
