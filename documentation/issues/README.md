<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# 39th review: v5.0.0 pre-release

Reviewed at `b4325e323` on `master` (2026-10-19), against the last release tag `4.8.0` (705 commits). Angle: release
blockers only — data loss, crashes, and regressions. Lean: four Opus area reviewers (storage/sync/imports,
`:presentation`, `:chordpro`/metronome/tuner, platforms/CI/stores), the scoped docs check on Sonnet, no live run.

## Headlines

- No data loss and no store blocker found. Upgrading from 4.8.0 data and syncing with a 4.8.0 peer were checked solid.
- One crash on a crafted JSON import, fixed during the review.
- One silent change of meaning on save (a German-notation edit turns a tab's `B|` into `Bb|` beside a `♯`/Latin/`H`
  name): plan 01.
- CI was red on `b4325e323` (goldens and the reference-pitch UI test); the user's own `841dfba22` and `293f39e26` fix
  it. A green Tests run is needed on the commit that gets tagged.

## Fixed during the review

- `1ea24739e` Skip an imported JSON file nested deeper than its parser can follow instead of crashing the app.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Keep a tab's B string a B natural when a song is edited in German notation and saved | medium | C |

## Lanes

| Lane | Area | Plans | Files owned |
|------|------|-------|-------------|
| C | `:chordpro` tabs | 01 | `chordpro/.../tabs/ChordProStaffNames.kt`, `ChordProNotationTest.kt`, `chordpro/.../tabs/CLAUDE.md` |

Merge order: C (the only lane). No shared files.

## Challenge

Plan 01 was challenged by a fresh Opus agent and **amended**: the byte-exact round-trip guard is dropped for German
(it would still have skipped a system with a file's `H|`), Latin keeps today's code, and `respelled` stays unchanged.

## Decisions

- **Taken 2026-10-19:** under plan 01 a half-step-down tab's B♭ string shows as `Bb|` in the German editor, not `B|`
  (accepted, as recommended).

Not plans, for the user's release checklist:
- Tuner's Chromatic switch removed (945519904): a 4.8.0 user who picked Chromatic now opens the tuner on their
  instrument; chromatic tuning is Other, which also hides chord diagrams. Intended per the docs; say it in the notes.
- Settings → Songs no longer has the Instrument row (intended).
- The Windows display name is now "Campfire: Songbook & Chords" (`gradle.properties`); Partner Center refuses an MSIX
  whose `DisplayName` is not a reserved name of the product, so reserve it first if it is not.

## Checked and found solid

- Every 4.8.0 document (preferences, setlists, sync index, credentials, editor draft, covers) decodes unchanged;
  synced preferences byte-compatible with a 4.8.0 peer; imports never overwrite unconfirmed; 60,000-case import fuzz
  and 20,000 mutated zips threw nothing uncaught.
- 945519904's removals leave no dangling references; strings complete in both languages; 4.8.0 back stacks and web
  addresses still resolve.
- All 340 `.cho` files in the repo round-trip through German and Latin byte-identically; hostile chordpro input fast,
  no throws; metronome unchanged, tuner releases its audio.
- Version 5.0.0 / build 50 reaches every platform (the iOS share extension built locally); no new dangerous
  permission, privacy manifests right, nothing new ships uncredited, no network call outside the remote module.
- Docs check: no false claims in the scoped `CLAUDE.md` files; the 20 stale-name hits are platform names.

## Dropped after verification

None.
