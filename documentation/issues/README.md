<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# The 45th review

Reviewed at `54829e6fd` on `master`. Angle: capo and degrees, as a player (a regression of `6df7888d0..54829e6fd`: the
sounding names with the file's capo counted in, shapes fingered above the effective capo, the tools' capo on the
Chords and Scales tabs, degrees, the PDF's capo and tempo). Lean: three Opus area reviewers and a Sonnet check of the
changed `CLAUDE.md` files, no live run.

## Headlines

- Tabs ignore a capo override: from a setlist with capo 0 on a `{capo: 2}` file, or with a capo put on a file without
  one, a tab is played a few semitones off while the chords around it are right (01).
- A song's own open `{define}` under a capo override is drawn ten frets up the neck, and a file's own shape moved up under a lower one (02).
- Under a capo, open-string rings show a disc of the wrong surface color on cards in the dark themes (03); the banjo's
  drone disagrees between the scale diagram and the chord rows of the same sheet (04).
- The setlists screen says "every setlist is archived" for a few frames when the first setlist arrives, and the songs
  screen "no songs match" when the first songs do (05).
- Decided by the user: the Transposition control and the PDF name the inferred key, as the app bar does (06).

## Fixed during the review

| Commit | Message |
| --- | --- |
| `c0f47c541` | Move a tab's frets when the transposition cancels the song's capo, as it moves them for any other transposition. |
| `188635afd` | Keep the Setlists bar and its open search as an import into a library with no setlists ends. |

## Index

| # | Plan | Severity | Lane |
| --- | --- | --- | --- |
| 01 | Move a tab's frets by the capo the player puts on (`tabCapoFor`, screen and PDF) | medium | A |
| 02 | Draw a song's own shape under a capo other than its own only where that capo moves it down the neck | low | B |
| 03 | Erase the capo bar inside open-string rings on screen; keep the PDF's white fill | low | B |
| 04 | Spike the banjo's drone above a capo in scale diagrams; keep the bar off its row | low | C |
| 05 | Derive the song and setlist placeholders from the list values alone | low | D |
| 06 | Name the inferred key on the Transposition control, its menu, the read only line, and the PDF | low | A |

## Lanes

| Lane | Area | Plans, in order | Files owned |
| --- | --- | --- | --- |
| A | playing and export | 01, 06 | `ui/playing/SongPlayback.kt`, `screens/songDetails/{SongPages,SongDetailsPage,SongDetailsTitle}.kt`, `songView/model/{SongLyricsModel,SongHomeKey}.kt`, `songView/firstSection/RememberSongPlayingControls.kt`, `rendering/SongRenderer.kt`, `screens/export/ExportController.kt`, `CampfireViewModel.kt`, `chordpro/…/chords/ChordProTransposer.kt` (KDoc), tests `SongPlaybackTest`, `SongRendererTest`, `SongDetailsTitleTest`; docs `ui/playing/CLAUDE.md`, `screens/songDetails/CLAUDE.md`, `ui/print/CLAUDE.md`, `chordpro/…/chords/CLAUDE.md` |
| B | chord drawing | 02, 03 | `ui/chords/{SongChords,ChordDiagram}.kt`, `songView/SongChordsSection.kt`, `print/PrintRenderer.kt` (a comment at most), `SongChordsTest`, new desktopTest `ui/chords/ChordDiagramDrawingTest.kt`; docs `ui/chords/CLAUDE.md` |
| C | scales | 04 | `ui/scales/{ScaleDiagram,ScaleDiagramGeometry}.kt`, `ScaleDiagramGeometryTest`; docs `dialogs/scales/CLAUDE.md` |
| D | list state | 05 | `state/ListPlaceholders.kt`, `screens/setlists/ListedSetlists.kt`, `screens/songs/SongGroups.kt`, `state/setlists/SetlistsController.kt`, `state/songs/SongListState.kt`, `launch/LaunchState.kt` (KDoc), `ListPlaceholdersTest`; docs `launch/CLAUDE.md` |

Paths are under `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/` (tests under the same
package in `commonTest`, the drawing test in `desktopTest`) unless they start with `chordpro/`.

**Merge order:** D, C, B, A. The lanes share no file, so the order only puts the small, self-contained lanes first and
the largest last: A changes the song page's plumbing and the export, which the others never read, and its two plans run
in one lane because both edit `ExportController.kt`, `SongPages.kt`, `SongDetailsPage.kt`, and `SongLyricsModel.kt`.

**Shared-file rules:**

- No file is owned by two lanes; each plan's **Files:** line is its lane's boundary. A lane that finds it needs a file
  another lane owns stops that plan and reports it rather than editing.
- Within a lane the plans run in order and the later one re-locates code by the earlier one's result: in A, 06 builds
  on 01's `SongPlayback` in `ExportController.preparePrintSource` and on its renamed `tabCapo` plumbing; in B, 03's
  `ui/chords/CLAUDE.md` sentence goes after 02's, in the same paragraph ("A chord is named as it sounds…").
- `ui/chords/CLAUDE.md` is past its package word budget already; 02 and 03 add a clause each and nothing more.
- `ui/playing/CLAUDE.md` (01) and `ui/print/CLAUDE.md` (06) belong to lane A only; 03 documents the PDF's white fill in
  `ui/chords/CLAUDE.md`, not in `ui/print/CLAUDE.md`.
- 03 (as amended) clips the rings out of the strings and the capo bar inside `drawFretted`, with no new parameter, so
  `ScaleDiagram.kt` (lane C) and `PrintRenderer.kt` need no change for it.

## Decisions

- **06** — decided by the user on 2026-10-26: **yes**, the Transposition control and the PDF fall back to the inferred
  key. The plan also covers the read only line, which shows the same values as text where the control is not drawn;
  **the user agreed on 2026-10-26** (one rule wherever the key is named).
- **02**, the reverse case (a `{capo: 2}` file's G `{define}` drawn as a barred A at fret 2 from a setlist with capo 0,
  rather than the table's open A) is left as it is: it is the definition followed through a move of the sounding
  chords, as a reader's transposition moves it today. **The user decided on 2026-10-26, against the recommendation: open
  shapes there** — a capo override lower than the file's draws the player's or the app's shape; 02 is widened for it.
- **04** reads the banjo's capo as spiking the drone (5 + capo, as the chord rows already draw it) rather than leaving
  it open at 5: the only reading under which a table shape's open drone note is in the shape's key. **The user agreed on
  2026-10-26.**

## Challenge

Ran on 2026-10-26, two fresh Opus challengers (lane A; lanes B, C, D), plus the regression read of the review's fixes.

- **01 amended** — a capo put on a song with none moves a tab's open strings below the capo, so the run goes up an
  octave (the retuner says so with its notice; a run too wide stays as written): documented, with a `SongRendererTest`
  case. Lane A also corrects two KDoc lines c0f47c541 left slightly stale (`rewriteBy`'s "and the tabs too where nobody
  is named to read them", `SongRenderer`'s "leaves a song none of them move exactly as it is").
- **02 amended** — the "moved down by exactly the capo" check compares with `capo.mod(12)`, since at capo 12 the
  definition does not move; a ukulele C at capo 12 test added.
- **03 amended** — a different fix: a `ClipOp.Difference` clip of the rings around the strings and the bar instead of
  an offscreen layer and an erase; no new parameter, no layer per diagram in long lists, the PDF right as is.
- **04 amended** — the drone row's veil starts at the cell of the drone's own first fret, so a fret-5 note under the
  capo is veiled, without overlapping the full-height veil.
- **05 amended** — step 3a: while `screenData`'s count differs from the list's, the placeholder keeps its last value,
  so an import that ends before the list is rebuilt shows no "empty" placeholder in between.
- **06 sound.**
- **Regression read of `54829e6fd..188635afd`**: sound; tests pass.

## Checked and found solid

The tools' capo (`toolCapo`) is local, clamped, and never leaks into a song's capo or back; a capo chart's key and a
leading `{transpose}`; sounding spelling; degrees (minor keys, slash chords, enharmonics); a round trip of the capo
steppers writes nothing; the launch hold cannot hang (`isLibraryRead` is true on Idle and Failure); the warnings commit
`59185673c` changes no behaviour; the onboarding's instrument circle; the filter chips and Reset in the sheet header;
the PDF's capo and tempo order; the feature switches; strings in both languages; the capo animations. The docs check
found no false claims in the changed `CLAUDE.md` files (`idleTimerDisabled` is a platform API's name, a false positive
of `check_doc_names.py`).

## Dropped after verification

- The Songs bar latch lagging one frame (`SongsScreen`'s `rememberHasLoadedLibrary`): nothing is open or moves in that
  frame; harmless.
- 03's first proposal, moving the erase into the shared `drawChordDiagram` for every caller: the PDF page is drawn
  straight onto its bitmap with no layer and read back as luminance with the alpha ignored, so an erased ring would
  print black. The plan keeps the PDF's white fill.
- 05's songs side was flagged "verify before including": confirmed by reading (`songGroups` is built
  `flowOn(Dispatchers.Default)` and `songListPlaceholder` reads `screenData` beside it, `isImporting` only in its empty
  branch), so it is in 05, not dropped.
