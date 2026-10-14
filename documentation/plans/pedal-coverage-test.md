<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Pedal coverage walk — plan

Written 2026-10-14 against `312cbd101`. One of four test plans; needs `ui-test-harness.md` first.

## Goal

The user's hard requirement: with a page-turner pedal (Up/Down only), **every line of a song can be read**, at any
window size and text size. The stepping is unit-tested on synthetic measurements (`RowSnappingTest`,
`SectionPagingTest`, …), but only a real layout shows whether the composed screen keeps the promise. The tenth review
checked it once by hand (7,747 rests). This test does the same on every CI run.

## Approach

One test class on the UI harness:

1. Open a song in song details on a given `Device` and font scale.
2. Press Down (`performKeyInput { pressKey(Key.DirectionDown) }`) until the step indicator says the end is reached,
   then Up back to the start, letting each step's animation finish.
3. At each rest, record which lyric lines are fully inside the visible content area: their bounds from semantics
   (`onAllNodes(…).fetchSemanticsNodes()`) against the content area's bounds.
4. Assert: every line of the song was fully visible at some rest, going down; same going up; and the walk ended at
   the first and last line.

What it checks is the user-visible outcome only: lines on screen. It doesn't know about rows, cuts, or columns, so a
new layout algorithm can't break the test without breaking the promise.

## Matrix

Keep it small: two songs (a demo song with chords, and one long song with tabs and a grid committed under the test
resources; the screenshot tool's own library is the user's songs and never used), three windows (360×640 phone,
phone on its side, desktop), and two font scales (default and the largest). That's twelve walks. Add a case only when a bug shows a gap.

## Risks

- **Runtime**: twelve walks of up to a few hundred steps each. Turn off animations through the test clock where it
  doesn't change where a step rests; keep the test under a minute.
- **Line identity**: two identical lines (a repeated chorus) must be told apart by position, not text.

## Effort

About a day once the harness exists.

## Docs

One line in `ui/screens/songDetails/stepping/CLAUDE.md` pointing at the test as the guard of the pedal promise.
