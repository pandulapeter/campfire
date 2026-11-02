<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# 54th review: the plans left

Reviewed at `c7807015a`; the angle and the full record are in the `54th-review` memory and in git history
(`0b7e5c0a0..6c1d50019`, 38 commits). Executed on 2026-11-02: plans 03, 05, 08, 10, 11, 12, 13, 14, 15, and 16
landed, and 06 landed without `ChordSheetLook`'s per-column inset (it needed a field on `Rendered`, which the plan
forbids). The plans below stopped where the plan itself was wrong or the lane ran out of room; none of them was
improvised past that point.

| # | Plan | Landed | Why it stopped |
|---|---|---|---|
| 01 | Replace the Linux-only golden renders | steps 1–3 | Step 4(a) expects "There is a house in New Orleans" on the demo song's first screen at 360 × 640 dp; it comes after Chords, Scales, Picking pattern, and Intro, off the first page. Steps 5–7 wait on a corrected step 4. |
| 02 | Make the unit tests independent of the host | step 1 | Step 2 reads the Hebrew face with `preloadFont` from `commonMain`, but Compose resources 1.12.2 has it only in `webMain`; it needs an expect/actual the plan does not describe. |
| 04 | Run the storage contract tests on the iOS simulator | nothing | Kotlin/Native rejects commas in backticked test names, and the module's `commonTest` has 18 (7 files), so `iosSimulatorArm64Test` cannot compile; rename them first, and add a rule against new ones. |
| 07 | Restructure the converter into explicit stages | steps 1–2 | Step 3: `writeBody` / `BodyWriter` decide by what they already wrote, through private helpers on the private `Rendered`; reaching the nested `Song` / `Part` model byte-identically is a redesign the plan does not spell out. Steps 7–8 need Campfire exports in the corpus (no `campfire/` folder yet). Corpus score unchanged at 0.778. |
| 20 | Measure the `:presentation` build | nothing committed | Measured under memory pressure from a parallel lane (daemon OOM, ABI-edit compiler crash); numbers not trustworthy. Repeat on an idle machine. |

Plan 13 left these starts in `CampfireViewModel`'s `init`, each needing a reorder or a parameter the size check refuses:
`FontScaleController.startEcho`, `DialogHost.startClosingWithSong`, `SyncController.startRestoring`,
`SavedDialogRestorer.start`, `ImportController.startQueue`, the three `FirstRunController` launches, and
`EditorSession.startRecovery` (moving it failed `EditorSessionTest`); and the navigation listeners of `editorSession`,
`metronomeController`, `importController`, and `firstRunController`.

Corpus scores (61 documents, after 05): overall 0.778; lines 0.84, chords 0.79, sections 0.76, songs 0.81, header
0.68. Lowest: `oh_susanna_text` 0.10, `die_gedanken_sind_frei` 0.17, `tell_me_ma` 0.37, `jingle_bells_page3` 0.39.
