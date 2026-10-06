# Eighteenth review — chord diagrams, notation, PDF diagrams, setlist dates, editor, smallest screen

Reviewed commit **`dac1d9d59`** on `master` (clean tree). Scope: the 18 commits `29f24f7fe..dac1d9d59`:
- chord names → notes, the shape search and its tables, `{define}`/`{chord}`, the chord diagrams UI, synced shape
  choices;
- the PDF diagrams;
- Latin and Nashville/Roman notation;
- enforced setlist dates;
- the editor's shortcuts and repeated-details flags.

On top of that, the **smallest supported screen** (360 × 640 dp, ~330 dp above the keyboard) and hostile input.

Budget: full. The sweep ran in four stages:
1. Six area reviewers, plus two live runs:
   - **Android:** the 360 × 640 AVD, offline.
   - **Desktop:** a stress run with an isolated home in a throwaway worktree.
2. Five verifier-writers. The `:chordpro` lanes proved their findings with probe tests in their own worktrees.
3. Four challengers.
4. One more challenger for plans 39 and 57, written after the user's decisions.

## Headlines

1. **20 (high, data):** every old undated setlist, the bundled demo one included, becomes a ` (2)` conflict copy
   across synced devices.
   - 4.6.1 has nullable dates.
   - Each device dates its copy on the day it first reads it after the update, and the two dates differ.
2. **02 (medium, crash):** a `{define}` that repeats `frets` (or `fingers`) crashes the editor's Transpose with
   `IndexOutOfBoundsException`.
3. **01 / 12 / 37 (medium, hang/OOM):** a huge fret or key freezes or exhausts memory wherever the diagram is drawn —
   the song page, the editor preview and the PDF.
   - It can come from a `{define}`, e.g. `frets 3 2 0 0 0 99999999`.
   - It can also come from a synced keyboard shape, e.g. `"0 4 99999999"`.
   - The synced case breaks every launch.
4. **30 / 11 (medium, performance):** a song page's first composition runs the shape search on the main thread.
   - Desktop measured 491–604 ms on a 48-chord song, and 5–43 ms on every ordinary page.
5. **50 / 51 (high / medium, Android):** on a 360 × 640 phone held sideways with the keyboard up, the editor shows one
   line.
   - The window is also panned (no `adjustResize`), which pushes the app bar under the status bar.
6. **15 (medium):** a Latin-notation reader's save can write Latin names (`[Sol]`, `{define: Lam}`) into the file.
   - This happens when no lyric chord votes for Latin.
   - It breaks "every file is in the standard notation".
7. **14 / 10 (medium, wrong music shown):**
   - The Chord shapes sheet spells `Cm` as "C D# G" and `C7` as "C E G A#".
   - `C+5`, `Caug5` and `Cdim5` lose their third.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Bound the frets, base fret and keys a `{define}` may give | medium | A |
| 02 | Read a definition that repeats a value keyword as invalid | medium | A |
| 03 | Count a keyboard definition's keys from its German root | low-medium | A |
| 04 | Leave a fretted definition no move fits as written | low-medium | A |
| 05 | Carry a five-finger fingering through the text transposition | medium | A |
| 06 | Read the finger values and `base_fret` spelling the spec allows | low | A |
| 07 | Read a keyboard slash chord's bass back from its definition | low | A |
| 08 | Let a `{define}` outrank a `{chord}` | low | A |
| 10 | Keep the third of `C+5`/`Caug5`/`Cdim5`, read `C-5` as a flat five | medium | CD |
| 11 | Bound the fretted shape search and cache the default shape | medium/low | CD |
| 12 | Bound the keys and bass of a stored keyboard shape | medium | CD |
| 13 | Say truthfully how many keys a keyboard shape presses | low (docs) | CD |
| 14 | Spell the Chord shapes sheet's notes by degree | medium | CD |
| 15 | Convert every chord of a text typed in Latin notation | medium | CD |
| 16 | Read a single-spaced row of bare Latin notes as lyrics unless the sheet is Latin | low | CD |
| 57 | Parenthesize the extension of a Nashville number (`5(7)`) | low | CD |
| 20 | Take the cloud folder's copy of a setlist that differs only by its date | high | B |
| 21 | Date undated setlists under the repository's locks, from a fresh read | low | B |
| 22 | Prune empty instrument objects from the merged chord shapes | low | B |
| 23 | Read the day of a setlist date written with a time | low | B |
| 24 | Carry an unticked "Artist and song details" over to the new Key and Tempo options | low | B |
| 30 | Build a song page's first model without the shape search | medium/low | CD |
| 31 | Hold the keyboard's sounding capo to the capo range | low | CD |
| 32 | Draw a barre without fingers only where the shape needs one | low | CD |
| 33 | Key the Chord shapes sheet's cells by their chord | low | CD |
| 34 | List a chord once however the song spells it | low | CD |
| 35 | Wrap the Chord shapes sheet's names instead of clipping them | low | CD |
| 36 | Floor the PDF diagrams' lines at one printed pixel | low | CD |
| 37 | Bound what a chord diagram draws | low | CD |
| 38 | Shade the keyboard's pressed keys away from the white keys | low (a11y) | CD |
| 39 | Cut the Chords section between its rows of diagrams | low | CD |
| 50 | Move the Shortcuts chevron into the title row while a landscape keyboard is up | high | P |
| 51 | Declare `adjustResize` so the keyboard never pans the window | medium | P |
| 52 | Keep the Pick a date sheet open when the date row changes layout | low | P |
| 53 | Scroll the export preview's page buttons away with it on a phone | low | P |
| 54 | Drop a segmented row's check marks where a label would not fit | low | P |
| 55 | Keep a PDF diagram's names inside their column | low | P |
| 56 | Put the ChordPro reference above Revert in the editor menu | low | P |

40 plans.

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|------|------|---------------------------|-------------|
| A | `:chordpro` definitions | 01, 02, 06, 05, 04, 07, 03, 08 | `ChordProDefinitions.kt`, `model/ChordDefinition.kt`, `ChordProParser.kt` (definitions), their tests; **one call** in `ChordProNotation.normalized` (03) |
| CD | `:chordpro` theory/notation, then song details and diagrams | 10, 11, 12, 13, 14, 15, 16, 57, 30, 31, 34, 33, 32, 37, 36, 38, 35, 39 | `ChordProChords.kt`, `ChordVoicings*.kt`, `ChordProNotation.kt` (`convertText`), `ChordProNashville.kt`, `ChordSheetConverter.kt`, `ui/chords/*`, `ui/components/ChordDiagram.kt`, `ui/dialogs/ChordShapesSheet.kt`, `ui/screens/songDetails/*`, the `drawChordDiagram` call in `PrintRenderer.kt`, tests |
| B | data, sync | 20, 21, 22, 23, 24 | `:data:*`, `:domain:*` as named, `SyncEngine`/`SyncRepositoryImpl` and tests |
| P | editor, dialogs, export, settings, PDF layout, Android shell | 51, 50, 56, 52, 53, 54, 55 | `ui/screens/songEditor/*`, `ui/dialogs/Dialogs.kt`, `ui/dialogs/ExportScreen.kt`, `ui/components/SegmentedChoice.kt`, `ui/print/PrintLayout.kt`, `app/android/src/main/AndroidManifest.xml` |

Lane CD is one serial lane, not two, for three reasons:
- **Shared data class:** plans 14, 30, 31 and 34 all edit `SongChord` and `songChordsOf` in `SongChords.kt`.
- **The cache:** plan 30 needs plan 11's cache and its `ChordVoicings.needsSearch`.
- **Plan 39 comes last:** it rebuilds the Chords section that 30–38 change.

**Order inside the lanes:**
- **Lane A:** plans 01 and 07 before 03 (03 re-roots through 07's `keysShape`). Plans 05 and 04 share one null-returning
  move helper. Plan 06 comes after 01 and 02, so that its `base_fret` alias is bounded and counted as a repetition.
- **Lane P:** plan 51 comes before 50, because 50's manual check assumes the pan is gone. Plans 50 and 56 then follow
  each other, since both edit `SongEditorScreen.kt` and the same `presentation/CLAUDE.md` editor bullets.

**Merge order: A, B, CD, P.**
- **A first:** it is the base `:chordpro` lane, and CD's plan 15 edits the same file (`ChordProNotation.kt`) in another
  function.
- **B next:** it is independent of the UI lanes.
- **CD, then P:** both touch `presentation/CLAUDE.md`, and P also touches the root `CLAUDE.md`, the manifest and the
  most screens.

### Shared files

| File | Lanes | Rule |
|------|-------|------|
| `chordpro/CLAUDE.md` | A, CD | Each edits its own paragraphs (definitions vs chords/voicings/notation/Nashville). Merge word by word and keep both. |
| `presentation/CLAUDE.md` | CD, P | CD: Chord diagrams, SongLyrics and Chord shapes sheet. P: editor, export, segmented rows. |
| Root `CLAUDE.md` | B, CD, P | B: Sync bullet (20) and setlist-date sentence (21). CD: notation (15, 57) and Chords section (39). P: short-window bullet (50). |
| `ChordProNotation.kt` | A (03: `normalized`'s `rewriteDefinition`), CD (15: `convertText`'s shortcut) | Different functions; a three-way merge. |
| `strings.xml` (both languages) | whichever plan adds a key | Add to both `values/` and `values-hu/`; keep both sides' keys. |
| `SyncEngineTest.kt` | B only | Plan 20 adds a constructor argument to about 60 constructions through a shared `NoSetlistComparison` fake. |

## Decisions (taken 2026-10-06)

- **D-20 (plan 20):** a setlist pair equal apart from its date takes the **cloud folder's copy**, with no conflict
  copy.
  - Plus **step 4**: also when the other device really edited the setlist and this one only dated its undated copy.
  - Cost: a date set on purpose on both sides offline keeps only the cloud's.
- **D-30 (plan 30):** the first frame is built without the search. A chord the tables lack shows its empty frame on
  first sight in a session, and its dots fill in a moment later. This breaks "first frame already right" for those
  chords; the user accepted it.
- **D-50 (plan 50):** below 240 dp of room above the keyboard, the control row hides while typing, and a Shortcuts
  chevron goes in the title row. This partly reverses the 2026-10-03 choice.
- **D-56 (plan 56):** the ChordPro reference moves above Revert, so the destructive entry stays last. This overrides
  the user's own placement from 4ca47bda2.
- **D-05 (plan 05):** the text transposition keeps a fingering up to 5 fingers. This changes the pinned test "a
  fingering the move leaves out goes with its keyword".
- **D-10 (plan 10):** `C-5` = C E Gb.
- **D-14 (plan 14):** where degree spelling needs a white-key enharmonic, the sheet falls back to the plain name
  (`C#` reads "C# F G#").
- **D-57 (plan 57):** **parenthesize** Nashville extensions that start with a digit (`5(7)`, `b7(7sus4)`). This was
  the user's choice over the recommended "keep and document". Sub-defaults taken: minor `6-7`/`2-7` stay as they are;
  optional `(G7)` becomes `(5(7))`.
- **D-16 (plan 16):** apply the amended rule. This changes the pinned test `Do Re Mi\n1 4 5`.
- **D-24 (plan 24):** the new Key and Tempo boxes inherit an unticked details box. For such a user, the overview's
  `(Key: X)` and a later `{transpose}`'s key line follow too.
- **D-cut (plan 39):** **write a plan now** to cut the Chords section between its diagram rows. This was the user's
  choice over "keep and revisit"; it reverses the documented "whole and uncuttable".
- **Defaults taken, not asked:**
  - 02: a repeated keyword is Invalid, rather than drawn with its last value.
  - 38: the pressed shade is mixed away from the white keys, not given a marker.
  - 53: the page pill is kept on a one-page PDF.
  - 39: screen readers get one stop per row of diagrams.

## Challenge

Every plan was challenged by an agent that did not write it.

**Amended:** 03, 04, 14, 15, 16, 20, 21, 24, 30, 31, 32, 33, 34, 38, 50, 54, 55. Each records what changed in its
`**Challenged:**` line. The main changes:
- **20:** gained step 4 and its tests.
- **30:** stated the relayout honestly and became a user decision.
- **32:** the barre rule was replaced. The original still put barres on Em and A, which was checked against all 221
  table shapes.
- **38:** the direction test and the fractions were corrected. Compose mixes colors in Oklab, and the original
  picked the wrong side in the Campfire dark theme.
- **15:** now also counts a `{define}`'s Latin name, so files the bug already wrote are healed.
- **16:** narrowed to single-spaced rows, so spaced-out Latin chord rows stay chords.

**Sound:** the others.

**Plans 39 and 57** were challenged after the decisions, and both were amended:
- **39:**
  - the slot cap was raised from 24 to 48, since 48 chords can be 48 rows on a phone at a large text size;
  - a cell wider than the block is clamped and clipped;
  - an unbounded width is guarded;
  - the per-row semantics come from the width the block is measured at;
  - the diagrams get a text measurer with a cache;
  - the test claims were corrected.
- **57:** it now also fixes `ChordProTabWrapper.isChordLine`, whose bracket trim would have broken `5(7)` in a preformatted chord row (and already breaks `C(add9)` there); the manual check was corrected.

**Dropped by the challenge:** none.

## Checked and found solid

- **First-one-counts for repeated header fields:** consistent across the parser, the highlighter,
  `ChordProMetadataFields.set` and the library scan.
- **Chord ids:** shared across spellings, with no collisions found. A stored choice is the fret string, so a table
  change cannot alter it.
- **The search:** deterministic on JVM, Native and wasm.
- **Setlist dates:**
  - written exactly once (200 undated files: 1.2 s on the first launch, nothing written after);
  - malformed files left alone;
  - unknown fields kept;
  - imports compare ignoring the date;
  - replacing a setlist keeps its day.
- **Chord shape sync:** the three-way merge works, a change schedules a run, and the run's own write does not.
- **The PDF:**
  - diagram names stay out of the selectable text;
  - Nashville and Latin text is right;
  - the feature switches gate the options;
  - a 92-page, 30-song export renders in 1.9–4.9 s, peaking at 826 MB on the desktop.
- **Latin collisions:** `Fadd9`, `Faug`, `Dim`, `Solo`, `Fade` and the like are not read as Latin chords. German
  round trips hold.
- **Screen readers:** the diagrams' content descriptions are right, and the Chord shapes sheet's buttons are labelled
  per chord.
- **Chord shape insertion:** placement, CRLF line endings, duplicate detection and one undo step all work.

## Dropped after verification

- **Bare Latin notes inside `{start_of_tab}` read as chords:** this half of the bare-Latin finding is the documented
  rule; a bare-note row in a tab block is chords, as the tuning-line precedent says.
- **"Page 1 holds only the controls" (desktop) and the landscape step re-showing the Chords header (Android):** both
  are the documented uncuttable section. They were not dropped as problems; plan 39 takes them up by the user's
  decision.
- **Noise:** one desktop capture showed the metronome playing, which could not be reproduced.
- **Pre-existing, out of scope:**
  - a `FocusRequester is not initialized` warning from `SongKeyboardShortcuts.kt` after back-then-open;
  - What's new still shows the 4.7.0 text, which is release work.

## Measurements

Desktop, unless noted:

| Case | Result |
|------|--------|
| 48 altered chords, guitar | 491–604 ms on the main thread at first open (frame gap 0.6–1.0 s); 270 ms per notation change |
| Same song, ukulele / keyboard | 20 ms / 0.6 ms |
| Ordinary song page, 1 000-song library | 5–43 ms; RSS 682 MB |
| 12-note chord on the guitar | 293 ms just to answer "no shape" (probe) |
| 30-song PDF with diagrams | 92 pages; layout 41–70 ms, render 1.9–4.9 s, 7.7–8.1 MB; peak RSS 826 MB |
| 200 undated setlists | first launch `loadSetlists` 1.2 s and 206 writes; later launches 0.21 s and none |
| Android 360 × 640, editor with keyboard | portrait field 185 dp (Shortcuts collapsed) / 86 dp (open); landscape 48 dp |
| Android keyboard diagram contrast, dark theme | pressed white key vs white key 1.35:1 |

## Manual checks owed

Each plan's own "Manual check" section is the full recipe. The ones a release would be blocked by:
- **20:** two devices on 4.6.1 with synced setlists. Update them on different days; expect no ` (2)` files anywhere.
- **02 / 01 / 37:** hostile `{define}`s in the editor. Transpose does not crash, and the preview and page stay
  responsive.
- **12:** a hostile keyboard value in a synced `preferences.json`. The app opens.
- **50 / 51:** the 360 × 640 AVD, editor in landscape with the keyboard up:
  - three lines show;
  - the chevron works;
  - `dumpsys` says `adjust=resize`;
  - repeat on an API 28/29 emulator;
  - every text-field sheet still clears the keyboard.
- **30:** a 48-chord song opens at once and its dots fill in without moving anything; an ordinary song opens as
  before. Check on the desktop and on a phone.
- **39:** the phone held sideways and an 800 × 600 desktop:
  - every Down press lands on a whole row of diagrams;
  - page 1 is not controls-only;
  - fold/unfold and the pencil still work;
  - TalkBack reads each row.

The others are UI checks on the device named in each plan: 03, 04, 05, 06, 07, 08, 10, 11, 14, 15, 16, 21, 22, 23,
24, 31–36, 38, 52–57.
