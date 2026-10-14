<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Golden renders — plan

Written 2026-10-14 against `312cbd101`. One of four test plans; reuses the setup that `ui-test-harness.md` extracts.

## Goal

Catch unintended visual changes on the screens most likely to break quietly: the smallest supported phone
(360×640 dp), Hungarian (longer words), large font scales, and the keyboard up. These are the checks the reviews keep
handing back as "verify on 360×640". Goldens catch *changes*; whether a change looks good stays the user's call.

Not the goal: pixel-testing every screen, or replacing the store screenshots.

## Approach

- A test in `tools/screenshots`' test source set renders a list of cases with the existing offscreen renderer and
  compares each to a committed PNG under `tools/screenshots/src/test/resources/goldens/`, with a small tolerance
  (anti-aliasing differences, not layout changes).
- On a mismatch the test writes the new image and a diff image into `build/` and fails. Updating the goldens is a
  Gradle property (`-Pcampfire.updateGoldens=true`) that rewrites them; the PNG diff is then reviewed in the commit.
- Fonts: the pinned fonts the tool already downloads, so a render is the same on every machine. Goldens are recorded
  and compared on one OS (Linux in CI); a Mac run skips the comparison with a message rather than failing.
- Keyboard: add an `ime` inset to the fake `PlatformWindowInsets` in `Device.kt`. First check that the shared UI reads
  it offscreen; if it doesn't, leave the keyboard cases out rather than faking around it.

## Cases

Start with about 15, chosen where a regression has actually happened before:

- the song list, song details (with chords), and the editor at 360×640;
- every sheet with a text field (New song, Edit song details, setlist, cover search) at 360×640 with the keyboard up;
- the same song details and New song in Hungarian at font scale 1.3 and 2.0;
- the short-window rail on a phone on its side;
- light theme only, except one dark case per screen type.

Each new case needs a reason (a bug it would have caught). The list is in one place in the test file.

## Risks

- **Churn**: every intended UI change rewrites goldens. Keep the set small so a diff is quick to review; if updating
  them becomes a chore nobody looks at, the set is too big.
- **Repository size**: PNGs at 360×640 are small, but cap the set rather than letting it grow per feature.

## Effort

About 2 days, including the keyboard inset.

## Docs

How to update the goldens: one paragraph in `tools/screenshots/CLAUDE.md`.
