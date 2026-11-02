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
(`0b7e5c0a0..6c1d50019`, 38 commits). Executed on 2026-11-02: plans 03, 05, 08, 10, 11, 12, 13, 14, 15, 16, and 20
landed, and 06 landed without `ChordSheetLook`'s per-column inset (it needed a field on `Rendered`, which the plan
forbids). Plan 02's step 2 landed later with the Hebrew face read through an expect/actual `scriptFontFamilies()`
(Compose resources' `Font` on Android, desktop, and iOS, `preloadFont` on the web, the only target that has it) rather
than `preloadFont` from `commonMain`; 04 landed after its 19 comma test names were renamed, and 01 with its step 4 re-derived
from what each golden guarded. Plan 07 landed after a replan, batch by batch, the Campfire export's own reading rules
deleted once the general reader read its PDFs nearly as well.

Plan 13 left these starts in `CampfireViewModel`'s `init`, each needing a reorder or a parameter the size check refuses:
`FontScaleController.startEcho`, `DialogHost.startClosingWithSong`, `SyncController.startRestoring`,
`SavedDialogRestorer.start`, `ImportController.startQueue`, the three `FirstRunController` launches, and
`EditorSession.startRecovery` (moving it failed `EditorSessionTest`); and the navigation listeners of `editorSession`,
`metronomeController`, `importController`, and `firstRunController`.

Corpus scores (61 documents, after 05): overall 0.778; lines 0.84, chords 0.79, sections 0.76, songs 0.81, header
0.68. Lowest: `oh_susanna_text` 0.10, `die_gedanken_sind_frei` 0.17, `tell_me_ma` 0.37, `jingle_bells_page3` 0.39.

Plan 20, measured on 2026-11-02 (Kotlin compile of `:presentation`, three runs, median): a cold build takes 19.6 s on
desktop, 17.1 s on Android, 17.2 s on the iOS simulator, and 13.3 s on wasmJs. An edit to a screen's body
(`SongsScreen.kt`) takes 21.5 s on desktop, 15.5 s on iOS, and 14.1 s on wasmJs; an ABI edit (`SongListItem.kt`) runs 17.3,
83.7, and 65.9 s; a body edit in `CampfireViewModel.kt` 18.1 s. Six edit runs took 66–100 s, longer than a cold build of
the whole module, so their cost is incremental compilation or the daemon misbehaving, not the module's size. No split
can make an edit cheaper than the ~20 s a full cold build takes. **Measured, not split.**
