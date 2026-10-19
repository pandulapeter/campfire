<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Keep a tab's B string a B natural when a song is edited in German notation and saved

**Kind:** bug (regression since 4.8.0)  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProStaffNames.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProNotationTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/CLAUDE.md` (only if a rule there changes)
**Challenged:** amended — the byte-exact round-trip guard would still skip a file's `H|` beside `B|` (its `H` comes back as `B`), so A now drops that guard for German instead of keeping it; A is pinned to German only (Latin keeps today's code); `respelled` is left unchanged, since the viewer's retuner uses it; the shown `Bb|` in a German editor is named as a consequence.

## Problem

`ChordProStaffNames.respelledStaffNames` (reviewed at `b4325e323`) renames a tab's string names between notations,
one system (a run of staff lines) at a time. The editor opens a file with `STANDARD → reader's notation` and saves with
`reader's notation → STANDARD` (`SongRenderer.editorTextOf` / `fileTextOf` in `:presentation`, through
`ChordProNotation.convertText`). The two directions are not symmetric:

```kotlin
val moved = respelledSystem(system, staff, from, to) ?: return@forEach
if (from == ChordNotation.STANDARD && respelledSystem(moved, staff, to, from) != system) return@forEach
```

Opening skips a whole system whose rename does not come back byte for byte. That happens whenever any name in it is
written with a sign (`G♯`, `E♭`, since `respelled` folds signs to `#`/`b`), in Latin (`Sol`, `Mi`), or as `H`/`h`, and
not only in the indentation case the KDoc describes. The system's natural `B|` is then left as `B|` in a German editor,
where `B` is the B flat, and saving reads it as German and writes `Bb|`:

```
file:   e|---0---|  B|---1---|  G♯|---0---|  E|---2---|  B|---3---|  E|-------|
saved:   e|---0---| Bb|---1---|  G#|---0---|  E|---2---| Bb|---3---|  E|-------|
```

Confirmed during the review with a probe through `convertText` (STANDARD → GERMAN → STANDARD). Staff names are the
tab's tuning (`tabs/CLAUDE.md`), so the viewer then retunes those strings a semitone and shows wrong frets. 4.8.0
returned the same text unchanged. It takes a German-notation reader, a tab naming a string with a sign, a Latin name, or
`H` beside a `B`, and an edit saved.

## Fix

The rule to keep: whatever the opening direction leaves in the editor must be read back as the notes the file meant.

**A: in German, rename per name, and only the bare B.** Only German reads a name differently from the standard
notation, and only a bare `B`/`b` (B natural in a file, B flat in German); `H`, `Bb`, `B#`, `Hb`, signed names
(`G♯`, `E♭`), Latin names, and numbers read the same note in both. So, in `respelledStaffNames` when `GERMAN` is
`from` or `to`:

- `STANDARD → GERMAN` (editor open) renames a bare `B`/`b` to `H`/`h` and keeps every other name exactly as written,
  sign included (`Bb|` stays `Bb|`, no longer shown as `B|`).
- `GERMAN → STANDARD` (editor save, and `ChordProParser.parse(text, GERMAN)` through `normalized`) renames a bare
  `B`/`b` to `Bb`/`bb` and a bare `H`/`h` to `B`/`b`, and keeps every other name exactly as written.
- Bars are aligned as `respelledSystem` aligns them today. Opening never changes a name's length (`B`→`H`), so it never
  moves a column; only a typed `B` growing to `Bb` on save does, as today.
- **Drop the byte-exact round-trip guard for German** (keep it for Latin). With A it would still skip any system with a
  file's `H|` in it (`H` opens as `H` and comes back as `B`), leaving that system's `B|` to be saved as `Bb|` — the bug.
  The shrinking edge it was written for no longer exists in German. Tab-character indentation stays skipped in both
  directions (`respelledSystem` returns null), which is symmetric and so already meaning-safe.
- **Latin keeps today's code path unchanged** (every name renamed, the system skipped where it does not come back
  exactly): Latin reads `B` as B natural, so a skipped Latin system is saved with its meaning; its editor shows `mi`/`Sol`
  by design (`a staff renamed in Latin keeps its bar…`). Numberings never reach here (`namesFrom`/`namesTo` are standard).
- **Leave `respelled` as it is**: `ChordProTabRetuner.asWritten` uses it for the viewer, which never goes through
  `respelledStaffNames`, so what a German viewer shows (`H|`, `E♭|` via `withAccidentalSigns`) does not change.

Consequences to accept: a standard file's `H|` opens as `H|` and saves as `B|` (same note; a file is written in standard
notation); a half-step-down tuning's `Bb|` now shows as `Bb|` in a German editor, not `B|` (same note, read right by
the German save). Keep the KDoc of `respelledStaffNames` true: say which names are renamed in German and that only
Latin and tab-indented systems are left as written.

**B (rejected): make the save direction skip symmetrically.** A fresh system typed in German with a `♯` and a `B`
meaning B flat would be saved as a B natural — it trades one silent change of meaning for another.

## Tests

In `ChordProNotationTest`:
- extend `every staff converted to a notation and back is returned as it was` with systems mixing `B|` with `G♯|`, with
  `E♭|`, with `Sol|`, and a lowercase `b|` with `G♯|` — for GERMAN the round trip must return the text exactly; LATIN
  keeps passing for the cases it passes today (do not add the signed ones to LATIN, whose skip folds signs on save);
- opening `e|…/B|…/G♯|…` in German shows `e|`, `H|`, `G♯|`, and saving that returns the file;
- a standard file's `H|` beside `B|` round-trips through German to `B|` / `B|`, never `Bb|`;
- a standard `Bb|` opens in German as `Bb|` and saves as `Bb|`;
- the existing `strings named in the notation the field is typed in are saved in the standard one` (typed `B|`, `H|`,
  `h|` → `Bb|`, ` B|`, ` b|`), `the names of a staff are converted with the chords above it, and back`, the Latin
  alignment test, and `ChordProTabsTest`'s German retuning (`H|` shown) still pass unchanged.
