# Twenty-fifth review

Reviewed commit: `d650f3cb0` on `master`, with a clean tree; uncommitted `tools/screenshots/` work (five modified files and a new `Promo.kt`) appeared during the review from outside it — it must be committed or set aside before execution, and plan 23 edits `Fakes.kt` (its `applyPendingNow` override moved from line 110 to about 116). Angle: the work landed since the twenty-fourth review
(`fc1a089c2..d650f3cb0`, 34 commits) — song selection mode, Duplicate song, right click, tag colors and their sync,
the scored song layout, per-song metronome accents, tempo changes between songs, the reworked tuner, and the UI polish
rounds. Budget: standard — five area reviewers, four plan writers that verified every finding against `d650f3cb0`
(lanes S and M with probe tests, since deleted), and three challengers.

## Headlines

1. **01 — the first sync run after an upgrade from 4.7.0 wipes every device's tag colors and per-song accents.**
   4.7.0 passes `tags` and `beatLevels` through and stores them in its sync base; this build reads them as values it
   owns, finds none in the upgraded device's preferences, and uploads their removal. Proved through a whole sync run.
2. **11 — a setlist created from the selection's Choose setlists sheet shows as not holding the ticked songs**, and two
   taps on it empty it.
3. **10 — a bulk tag or language edit puts its own snapshot back into the song list**, over an edit or a deletion made
   to one of its songs while it was still writing.
4. **20 — the tuner can hold the wrong octave for as long as a bass string rings**, since the new octave guard drops
   every correct reading once the first one was an octave off.
5. **30 — text fitting skips the scales that would save a page** where lines only just wrap, which is where it pays
   off most.

## Index

| #  | Plan                                                                                   | Severity | Lane |
|----|----------------------------------------------------------------------------------------|----------|------|
| 01 | Stop the first sync after an upgrade from 4.7.0 from wiping tag colors and song accents | high     | S    |
| 10 | Re-read the files a bulk edit changed under the library lock                          | medium   | L    |
| 11 | Record what a setlist held at its first tap in the selection sheet                    | medium   | L    |
| 12 | Compare tags to remove by tag key                                                      | low      | L    |
| 13 | Finish a bulk tag or language write once it has started                               | low      | L    |
| 14 | Export several songs as a zip even when one is chosen                                 | low      | L    |
| 15 | Draw the tag palette's check in the higher contrast of black and white                | low      | L    |
| 20 | Let an octave heard near the strike's level move the tuner's target                   | medium   | M    |
| 21 | Draw the heard bar's accents while a song change waits for the barline                | low      | M    |
| 22 | Track every outstanding next-bar change in the metronome engine                       | low      | M    |
| 23 | Remove the unused `Metronome.applyPendingNow` and fix the stale metronome and tuner docs | low   | M    |
| 30 | Bound the fit-scale search by the unwrapped height                                    | medium   | U    |
| 31 | Delay the chord cell's press highlight until a tap is likely                          | low      | U    |
| 32 | Reserve the page pill's widest width in the short-window export preview               | low      | U    |
| 40 | Fix the chord sheet's file name and the metronome mark's color in the docs            | low      | Z    |
| 41 | Drop the removed editing menu and edit button from KDocs (includes finding 44)        | low      | Z    |
| 42 | Update components' CLAUDE.md for where the cover search and tags are edited           | low      | Z    |
| 43 | Correct ui/CLAUDE.md on the editor preview and the menu's first entry                 | low      | Z    |
| 45 | Say in the root CLAUDE.md that the tuner sheet opens from the song's tuner button     | low      | Z    |
| 46 | Add the missing serial commas to the docs written since the last review               | low      | Z    |

## Lanes

| Lane | Area                                   | Plans, in order        | Files owned |
|------|----------------------------------------|------------------------|-------------|
| S    | Sync                                   | 01                     | `data/sync/implementation/**` |
| M    | Metronome and tuner                    | 22, 23, 21, 20         | `metronome/**`, `tuner/**`, `tools/screenshots/.../Fakes.kt`, presentation `ui/metronome/**`, `ui/screens/MetronomePanelState.kt` |
| L    | Library and selection                  | 10, 11, 12, 13, 14, 15 | `data/repository/**`, `domain/**` (export use case), presentation `ui/state/SelectionEditing.kt`, `SongMetadataEditing.kt`, `ui/dialogs/SelectionSetlistsSheet.kt`, `TagColorButton.kt`, `ui/screens/setlists/SetlistSongToggle.kt`, `ui/screens/export/ExportController.kt`, `ui/components/Contrast.kt`, `ChordDiagram.kt`, `TagColor.kt` and their tests |
| U    | Song details and export screen         | 30, 31, 32             | presentation `ui/screens/songDetails/SongSectionsLayout.kt`, `TextFitting.kt`, `SongChordsSection.kt`, `ui/screens/export/ExportScreen.kt`, `PageButtons.kt` and their tests |
| Z    | Docs                                   | 40, 41, 42, 43, 45, 46 | KDoc in the files 41 names, every `CLAUDE.md` and `README.md` sentence its plans quote |

**Merge order: S, M, L, U, then Z.** S carries the one high finding and is independent. M and L are independent of
each other. U is after L only because both touch `ui/screens/export/CLAUDE.md`. **Z is cut from `HEAD` after the
other four merged**, since 46 rewrites sentences in CLAUDE.md files that 01, 11, 13, 14, 15, 30, 31 and 32 also edit;
cut late, its plans match the landed text and nothing conflicts.

**Shared files:** the directory `CLAUDE.md` files. Each plan quotes the sentence it changes and edits only that one;
re-locate it by its text. `ui/dialogs/CLAUDE.md` is touched by 11, 13, 15 (different paragraphs of "The selection
mode's sheets") and 46; `ui/CLAUDE.md` by 13, 43, 46; `ui/screens/songDetails/CLAUDE.md` by 30, 40, 46;
`ui/chords/CLAUDE.md` by 31, 40, 46; `ui/screens/export/CLAUDE.md` by 14, 32, 46; `data/sync/implementation/CLAUDE.md`
by 01 and 46. No plan touches `strings.xml`.

## Decisions

- **14 — exporting the selection's songs as Zip when only one is left chosen.** (b) **recommended:** always write a
  zip for the selection's songs, as the screen already says; (a) relabel the format, preview and message as ChordPro
  once one song is left. **Decided 2026-10-11: (b), always a zip.**
- **23 — `Metronome.applyPendingNow`, which nothing in the app calls since the pending-tempo UI went.** (a)
  **recommended:** remove it end to end; (b) keep it, documented as unused. **Decided 2026-10-11: (a), remove it.**

## Checked and found solid

- **Selection mode:** Back, Escape and browser history order; the mode ending when Songs leaves the top and in
  performance mode; pruning only on whole-library data; `BulkLabelDraft`'s tri-state; reading hidden ticked songs from
  the unfiltered list; Duplicate song naming and collisions; `onContextClick` consuming only a mouse secondary press.
- **Tag colors:** one `tagKey` rule for storing and looking up; local `preferences.json` forward and backward
  compatible with 4.7.0; two devices merging tag colors; renames and deletions moving accents.
- **Scored layout:** `ColumnFlow`'s DP and `evenOut`, `LayoutScoring` against `SectionGrid.score`, `flowIntoPages`,
  narrow columns, text fitting's rounding and hit testing, the grid cache key, the Android home-gesture fix.
- **Metronome and tuner:** next-bar change timing in the sequencer, Android's `heardFrame`, the high-pass filter's
  coefficients and allocation, every preset within `maxLag`, beat haptics' threads, `SystemBrowser` on macOS, the
  desktop touch reflection fallback.
- **Strings and conventions:** every new key in both languages with matching placeholders, `textResource` for user
  text, plurals for counts; 323 backticked names in the changed docs exist in the code.

## Dropped after verification

- Nothing was dropped. Two proposed fixes were replaced:
  - **20:** the reviewer's "take the octave after N answers in a row" fails the B3 recording, whose dying string gives
    five octave-down answers in a row. The plan uses the level relative to the strike instead, which passes all nine
    recordings.
  - **01:** the reviewer's change inside `localDocument` was replaced by stripping the base of fields its writer did
    not own, which gives the same result with less code.
- **Narrowed:**
  - **22:** the window is the output queue before the barline (about 100 ms), not B's whole first bar.
  - **14:** it only arises when several songs were ticked and all but one are unticked or unreadable, since one ticked
    song opens the single-song export.

## Challenge

Ran on all 20 plans, with three challengers that wrote none of them. 15 came back sound and 5 amended; none was
dropped.

- **11:** the record is now taken at a setlist's first tap, not when it is first listed. `createSetlistWithSongs`
  writes the setlist empty before adding the songs, so a frame could freeze "holds none", which is the original bug.
- **12:** the test spells the decomposed tag as an escape, so a copy step that normalizes text cannot hide the bug.
- **30:**
  - Two narrow sections paired side by side on a one-column page count at half their height, so the bound stays a
    true lower bound.
  - Each unit is read at `maxOf(measured width, maxWidthOf)`, which hits the cache instead of measuring again on
    every pinch frame.
- **31:** the quick-tap pulse's job lives outside `pointerInput`, and `isPressed` is cleared at every new first down,
  so a cancelled pulse cannot leave a cell lit. Consumption of the up is unchanged.
- **32:** the reserve is measured with every digit replaced by the font's widest digit, since a platform's default
  font may ignore `tnum`.

Checked and sound:
- **01:** retries, a run dying midway, a downgrade to 4.7.0 and back, an index from a newer version, and the run
  after the upgrade (no re-upload loop).
- **20:** all nine recordings at ratios from 0.35 to 0.95, noise added, and the octave-pair presets.
- **22:** the new test fails on the old engine.
- **46:** all 52 phrases exist exactly once at `d650f3cb0`.

## Manual checks owed

- **01:** two devices, one on 4.7.0 and one on this build, against a real Dropbox folder. Color tags and accent songs
  on the new one, sync both, upgrade the old one, sync it, and check that the colors and accents survive on both.
- **10:** on the web, bulk-tag 300 songs and delete one already processed before the write ends.
- **11:** create a setlist from the selection's Choose setlists sheet, and check that it shows ticked and disabled.
- **20:** a bass E1 and a banjo's octave pairs through the laptop and phone microphones.
- **21 and 22:** page between songs of different time signatures with the click playing, near a barline.
- **31:** a flick that starts on a chord diagram on a phone.
- **32:** a phone on its side in the export preview, paging from 9 to 10.
