# 51st review: regression of aca41acce..286971fe4

**Reviewed commit:** 286971fe4 on `master` (HEAD 581e5e6a5 adds only a prepare-release skill change).
**Angle:** regression review of the range aca41acce..286971fe4: the capo and chord-name rework (sounding vs shapes,
ask-once dialog, setlist entry copies), the new chord tables for every common chord on the ukulele, the mandolin, the
banjo, and the guitar's alternate tunings, the Shuffle order, the editor's TAB and ABC highlighting, clickable shared
links, and the Microsoft Store languages.
**Lean sweep:** three Opus area reviewers and a Sonnet check of the changed areas' `CLAUDE.md` files; no live run.

## Headlines

1. **A song printed to PDF with its chords named as they sound comes back a whole step high** (plan 01): the PDF prints
   a bare capo over sounding chords, and the import reads every capo chart's chords as shapes. Found beside it, older
   than the range: **the PDF's key and capo row is not read back at all in Romanian, Italian, and Hebrew**, nor in
   Spanish, German, and French where it holds a time signature (or, in Spanish, a transposition) (plan 05), so a capo song printed in
   those languages comes back two semitones low.
2. **About 180 of the new chord-table shapes drop the root needlessly** and are shown as the chord's default on the
   ukulele, the mandolin, and the banjo (plan 04).
3. **Shuffle broke two Songs-screen rules:** the app bar and list inset jump in one frame when the list loses or gains
   its headers (plan 02), and the order is reshuffled and the list sent to the top on any `ON_START`, such as returning
   from the file picker or mid selection (plan 03).

## Fixed during the review

None: no finding was simple enough to fix on the spot.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | [Mark a printed capo over chords named as they sound, and import it as the shapes the capo plays](01-print-a-sounding-capo-so-the-import-reads-it-back.md) | medium | P |
| 02 | [Animate the songs app bar and list inset when the list loses or gains its section headers](02-animate-the-songs-app-bar-when-the-list-loses-or-gains-headers.md) | medium-low | S |
| 03 | [Take a new Shuffle order on returning to the app only after a real stay away, and never mid selection](03-take-a-new-shuffle-order-only-after-a-real-stay-away.md) | low | S |
| 04 | [Prefer chord shapes that keep the root, and replace the picked table rows that drop it needlessly](04-keep-the-root-in-the-searched-chord-shapes.md) | medium | T |
| 05 | [Read back the PDF's playing details row in every language the app prints it in](05-read-back-the-pdf-playing-details-in-every-app-language.md) | medium | P |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| T | chord tables | 04 | `chordpro/.../chordpro/voicings/` (main and test), its `CLAUDE.md` |
| S | Songs screen and Shuffle | 02 → 03 | `ui/screens/songs/` (`SongsScreen.kt`, `KeepCardsInPlaceUnderTick.kt`, `CLAUDE.md`), `ui/components/appBar/SearchBarMotion.kt`, `ui/CampfireApp.kt`, `ui/state/LibraryState.kt`, `ui/state/ShuffleReturn.kt` (new) and its test, `tools/screenshots/.../screens/songs/SongHeaderlessBarTest.kt` (new) |
| P | print and PDF import | 05 → 01 | `chordpro/.../chordpro/convert/` (main, test, `CLAUDE.md`), `chordpro/.../chordpro/chords/ChordProTransposer.kt` and its test, `ui/print/` (`PrintLayout.kt`, `PrintDocument.kt`, `CLAUDE.md`), `ui/screens/export/` (`ExportController.kt`, `PrintLabelsResource.kt`), every `values*/strings.xml`, `presentation/src/desktopTest/.../print/PdfRoundTripTest.kt`, `presentation/src/commonTest/.../print/PrintLayoutTest.kt` |

(`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.)

**Merge order: T, S, P.** T is self-contained table content and the search. S is presentation-only. P goes last
because it touches the most shared files (all eight `strings.xml`, the converter and transposer every import runs
through), so its merge meets the final state of everything else.

**Shared files:** `strings.xml` is owned by P alone. No plan names `ui/CLAUDE.md`; if S or P finds it must change it,
it is merged word by word, every sentence of both sides kept. A lane touching a file outside its row stops and reports.

## Decisions (taken by the user on 2026-11-01: D1 (a), D2 5 minutes, both as recommended)

- **D1 — plan 01, how a sounding-named capo prints and imports.** Recommended: **(a)** print
  `Capo: 2 (chords as they sound)` (a new string in all eight languages) and import it as the capo chart it came
  from: the capo kept, the chords and key moved down by it to the shapes in the standard notation (the converted
  text brought out of the Latin or German one first, since `transposeText` moves no Latin name), tab frets kept as
  printed (a `capo` parameter on `ChordProTransposer.transposeText`, mirroring `transpose`'s). A 5.2.x import of the
  new PDF reads it as today, a whole step high. It keeps the capo the reader played with
  and matches what a shapes print imports to. Alternative (a1): import it as no capo (sounds right too, but drops the
  capo and needs tab frets raised). Rejected: (b) stop printing the capo over sounding names, which reverses
  fd1c29082's deliberate choice.
- **D2 — plan 03, how long away counts as returning to the songs list** (on the wall clock, since a sleeping
  phone stops the monotonic one). Recommended: **5 minutes**, long enough
  that a trip to the system file picker or a share sheet never reshuffles, short enough that putting the phone down
  and coming back does. Alternative: 30 seconds, which only filters out recreations and quick glances.

## Challenge

Ran on all five plans (one Opus challenger, writing only this folder; probes in an untracked test at 286971fe4,
deleted). All five amended, none dropped:

- **01** — the sounding capo's chords are moved in the standard notation: `transposeText` leaves Latin chords and keys
  as they are (probe: `{key: Fa#m}[Fa#m]…[Re]…` unchanged at -2), and the converter's output is still in the
  printed notation, so every reader in Latin would have kept the bug. Also: where the marker is read (before `capo()`
  strips it), a length limit on the translations, Latin, German, and Hebrew test cases, and how 5.2.x reads it.
- **02** — the card held under a selection tick needs the inset's travel added (the inset moves the whole viewport),
  no longer "check, and maybe"; the snap is an `animateFloatAsState` `snap()` spec; the test also checks that no
  collapsed first row is kept after a sort change or the first song arriving in an empty library.
- **03** — the wall clock instead of `TimeSource.Monotonic`, which stops while Android, iOS, and macOS sleep.
- **04** — the search orders rooted shapes first instead of dropping the rootless ones, which the shape picker offers;
  the default check covers chords reached by moving a filed shape; the presentation tests and goldens are run.
- **05** — the new time and tempo words get a value check on a line of their own too (probe: `Time: something odd`
  alone is read as `{time: something odd}`), so `Metro: …` near the top stays a lyric.

D1 keeps (a), with the notation step; D2 keeps 5 minutes, measured on the wall clock.

## Checked and found solid

- **Capo and chord names** across the song view, diagrams, degrees, Scales, notation, tabs, and the PDF (shapes
  naming round-trips: `Key: F#m   Capo: 2 (Em shapes)` imports correctly in en, hu, es, de); the ask-once dialog's
  gating and its persisted defaults; the setlist entry copy on all four add paths.
- **Chord tables:** 13,555 entries checked for chord tones, bass, span, the banjo's drone, uniform shifts, no changed
  default of any chord that had a shape at aca41acce, and JVM method size.
- **Shuffle:** order stability, cross-platform determinism (FNV-1a and SplitMix64 over the UTC hour), and the
  persistence fallback; other sorts never see the hour (`GetScreenDataUseCaseImpl` passes 0).
- **Editor highlighting:** TAB and ABC ranges, CRLF, and the incremental state.
- **Shared links:** `SharedLinkList` click gating.
- **Onboarding:** the hand-over to the ask-once questions.
- **Docs:** the `CLAUDE.md` files of the changed areas hold no false claims; the stale-name flags were all false
  positives.

## Dropped after verification

- **Capo overrides saved by 5.2.1 now change the sounding key.** Intended by 61971509e; no migration for a tiny user
  base. The release notes should say so.
- **345 typed guitar and drop D entries mute an interior string** (`Cadd9 x 3 2 x 3 0`): standard player shapes.
- **Microsoft Store bare language codes** are valid; the next Partner Center submission will ask for listings under
  them. Operational, not code.
- **Narrowed from the reviewer's plan 04:** tightening `ChordVoicingSearch.omissions` so the root may go only where the
  chord minus its fifth still outnumbers the strings. 384 typed ukulele rows (chords-db: C9 `0 2 0 1`, C69 `0 2 0 0`)
  voice their chords that way on purpose, and about 80 picked rows' chords have no rooted shape within four frets, so
  the search would lose them. Plan 04 makes the search prefer the root instead, and tests the doubling rule (0 typed
  rows break it) rather than the fifth rule.

## Measurements

- Picked chord-table rows that leave out the root (rows not in `42982c6ee^`): 336 in all; 92 also sound another note
  twice on non-drone strings (ukulele 11, mandolin 25, mandolin GDGD 41, banjos 15); about 90 keep the fifth where the
  chord without it fits the strings; typed rows that double a note while rootless: 0.
- PDF header row read back (converter probe at 286971fe4): en and fr yes; ro, it, and he no; es, de, and fr no once the
  row holds a time signature, es also with a transposition.
