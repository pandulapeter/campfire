<!--
  This file is part of Campfire.
  Copyright (c) Pandula Péter 2017-2026.
  https://github.com/pandulapeter/campfire

  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
  If a copy of the MPL was not distributed with this file, You can obtain one at
  https://mozilla.org/MPL/2.0/.
-->
# Keep the file's own `{capo}` in a song a keyboard player who keeps the key exports as played

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — every capo reading traced (only keyboard + keeps-the-key with a file `{capo}` changes); the PDF now differs from the file for that reader, stated and justified; the label's "Capo 0" named a capo this reader is never shown; exact test strings and the existing test that turns into a null case.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongAsPlayed.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongAsPlayedTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`

**Awaiting the user's decision (README, Decisions).** Drop this plan if the user chooses to keep writing the
keyboard's chords as they sound.

## Problem

Reviewed at `a59cc76c3` (and after `Leave a transposition, capo, or tempo hidden … out of a song exported as played.`,
which added the `areChordsShown` and metronome checks around these lines):

```kotlin
val capo = if (playback.isCapoShownFor(preferences)) playback.capo.fret else 0
val semitones = if (areChordsShown) wrapTransposition(playback.soundingTransposition - capo) else 0
val writtenCapo = capo.takeIf { areChordsShown && it != playback.capo.songFret }
```

On the keyboard with Capo keeps the key, the capo is hidden (`isCapoShownFor` false), so `capo` is 0. For a file with
`{capo: 2}` in Em and no override at all, `soundingTransposition` is 2 (`pitchCapo` is the file's capo), so `semitones`
is +2 and `writtenCapo` is 0: the export screen offers "As played here" (on by default) labelled only "Capo 0"
(`AsPlayedOption.asPlayedLabels` leaves out a zero transposition), and the shared file loses `{capo: 2}` and its
chords become F#m barre chords. The guitarist it goes to loses the capo shapes though the pianist changed nothing.

## Fix

Options:

- **(a, recommended)** Where the capo is hidden, treat the file's own capo as the one played: `val capo = if
  (playback.isCapoShownFor(preferences)) playback.capo.fret else playback.capo.songFret`. `semitones` is then the
  reader's transposition alone (`soundingTransposition` = transposition + songFret under keeps-the-key), `writtenCapo`
  is null, and a pianist with no override exports nothing rewritten; with a transposition the file keeps its capo and
  its chords move by it, which still sounds as here under either capo reading. A capo override the pianist cannot see
  is never written, which matches "an override nobody is shown is neither named nor reset".
- (b) Keep writing sounding chords, but label the change as the transposition it is. Rejected by default: it still
  rewrites a file nobody changed.

Every other reading is untouched, since the new branch is only taken where `isCapoShownFor` is false (keyboard and
keeps-the-key): guitar either way and the keyboard without keeps-the-key keep `capo = playback.capo.fret`. With the
chords switched off, `semitones` is 0 and `writtenCapo` null either way (`1fb15e270`). A keyboard file with no `{capo}`
(`songFret` 0) gives the same result as before (capo 0, `semitones` the transposition, nothing written for the capo),
so only a file that names a capo changes. Both readers of the file still hear what the pianist hears: a standard
reader raises the moved chords by the file's capo (transposition + `songFret` = `soundingTransposition` under
keeps-the-key), and a Campfire reader who keeps the key counts the same file capo as pitch.

The PDF for this reader still prints the sounding chords with no capo (`withCapo(isShown = false)`, names at
`nameCapoFor` 0), so the file and the PDF now differ here; that is intended: the PDF is the pianist's own sheet, the
file is for another player, and the export copies the stored capo rather than writing one. The README's "the file
leaves out exactly what the PDF does" stays true of the switches (chords, metronome), which are what it was about. The
label now agrees with the file: today it reads "Capo 0", naming a capo this reader is never shown; afterwards it names
the transposition alone, or the switch is not offered at all.

Update `songAsPlayedOf`'s KDoc (it now says "none for a keyboard player who keeps the key, whose chords are then written
as they sound") and the export `CLAUDE.md` sentence "the shapes above the capo shown, `{capo}` that capo" to say the
file's capo stays where none is shown.

## Tests

In `SongAsPlayedTest` (its `keyboard` preferences and `capoChart` exist):
- keyboard, `capoKeepsKey = true`, no override (`playback(capo = 2, capoKeepsKey = true)`) and a hidden capo override
  (`playback(capo = 4, capoKeepsKey = true)`): `songAsPlayedOf` is null for both. The second replaces the existing
  `a keyboard player who keeps the key writes the chords as they sound, with no capo` test, which pinned
  `{key: A}\n[A]One [D]two\n`;
- keyboard, `capoKeepsKey = true`, `playback(transposition = 2, capo = 4, capoKeepsKey = true)`:
  `{title: Song}\n{key: A}\n{capo: 2}\n[A]One [D]two\n` (capo kept, chords moved by the transposition only), and
  `songAsPlayedOf(...).capo` is null, so the label names only the transposition;
- keyboard, `capoKeepsKey = true`, on a file with no `{capo}` and a capo override: null (unchanged from today).
