<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Move a tab's frets by the capo the player puts on, so that a capo override keeps a tab's pitch as it keeps the chords'

**Challenged:** amended — the octave a capo put on above the file's takes an open-string tab to is now said and tested (probed: no capo, capo 3, `e|--0--3--|` reads `e|--9---12--|`).

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (song page and PDF)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongPlayback.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPages.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPage.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongLyricsModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/rendering/SongRenderer.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/playing/CLAUDE.md`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProTransposer.kt` (KDoc only),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/playing/SongPlaybackTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/rendering/SongRendererTest.kt`

## Problem

A capo override keeps the pitch of the chords (the shapes are the sounding chords moved down by the effective capo,
`shapeCapoFor`), but a tab is moved only as if the file's own `{capo}` were on. Only the file's capo is held out of
the tab's frets:

`ui/playing/SongPlayback.kt:51` at 54829e6fd:

```kotlin
internal val SongPlayback.soundingCapo get() = soundingTransposition - transposition
```

`soundingTransposition` is `wrapTransposition(transposition + capo.songFret)`, so `soundingCapo` is the file's capo (an
octave off where the wrap took one), and `ChordProTransposer.transpose` moves tab frets by `shift - capo`
(`val fretShift = shift - capo`), i.e. by the reader's transposition alone. `ExportController.preparePrintSource` mirrors
it:

```kotlin
val transposed = content?.let { withContext(Dispatchers.Default) { songRenderer.transposedSong(it.text, transposition, spelling, tabs = tabs, capo = transposition - playedTransposition) } }
```

What the player hears:

- A `{capo: 2}` G-shape file with a riff `e|--0-2-3--|`, opened from a setlist with capo 0: the chords become open A
  shapes (sounding A, right), the tab stays `0-2-3`, played open: two semitones flat.
- A file with no capo and a device capo of 3: the shapes are fingered above capo 3 (sounding as written, right), the tab
  is unchanged and is now played above capo 3: three semitones sharp.

This breaks the root rule "a capo put on keeps the pitch". `ui/playing/CLAUDE.md` says "Tabs stay as written, counted
from the capo: `soundingCapo` is held out of their frets", which is only true while the effective capo is the file's.

## Fix

A written tab fret `f` sounds at `open + songFret + f`. Played above the effective (shape) capo `E`, after the reader's
transposition `t`, the fret is `f + t + songFret - E`. The transposer moves frets by `soundingTransposition - capo`, so
the semitones to hold out are `soundingCapo - songFret + E`; the wrap cancels out of that difference.

1. In `SongPlayback.kt`, replace `soundingCapo` with:

   ```kotlin
   /**
    * The semitones of [soundingTransposition] a tab's frets are not moved by: a tab is counted from the capo it is played
    * above, so it moves by the reader's transposition and by the difference between the file's capo and the one played
    * at ([shapeCapoFor]), which keeps its pitch as a capo override keeps the chords'. The octave the wrap took is counted
    * in, so that the frets move by the unwrapped amount.
    */
   internal fun SongPlayback.tabCapoFor(preferences: UserPreferences?) = soundingTransposition - transposition - capo.songFret + shapeCapoFor(preferences)
   ```

   (The keyboard's `shapeCapoFor` is 0; it hides tabs anyway, so its value does not matter there.)
2. Rename the plumbing from `soundingCapo` to `tabCapo` and fix its KDoc: `SongPages.kt`
   (`soundingCapo = playback.soundingCapo` → `tabCapo = playback.tabCapoFor(userPreferences)`), `SongDetailsPage.kt`
   (the parameter, its `@param` "How many of [transposition]'s semitones a tab's frets are not moved by, see
   `SongPlayback.tabCapoFor`", and the `transposeSong(inputs.text, inputs.transposition, inputs.soundingCapo, …)` call),
   `SongLyricsInputs.soundingCapo` in `SongLyricsModel.kt`, and the `@param capo` KDoc of `SongRenderer.transposedSong`
   ("…`SongPlayback.tabCapoFor`: a tab's frets are counted from the capo played, so they move by the rest alone").
3. In `ExportController.preparePrintSource`, build the playback the screen builds instead of repeating its arithmetic:
   `val playback = SongPlayback(transposition = playedTransposition, capo = effectiveCapo, tempo = effectiveTempoOf(…))`
   (the tempo it already reads), then `transposition = playback.soundingTransposition`,
   `shapeCapo = playback.shapeCapoFor(preferences)` (equal to today's `if (playsWithoutCapo) 0 else capo ?: effectiveCapo.songFret`,
   since a default capo's `fret` is its `songFret`), and `capo = playback.tabCapoFor(preferences)` in the
   `transposedSong` call. Keep the existing comments true; the one above `transposed` stays.
4. `ChordProTransposer.transpose`'s `@param capo` KDoc: make it general rather than "the song's own `{capo}`": "How many
   of [semitones] a tab's frets are not moved by: a capo's, counted in to name the chords that sound. A tab's frets are
   counted from the capo, so they move by the rest alone, which is negative where the capo played is higher than the
   one the chords are named for, while every chord name, a tab's included, moves by all of it." No behaviour change in
   `:chordpro`.
5. Docs: `ui/playing/CLAUDE.md` line "Tabs stay as written, counted from the capo: …" becomes "**Tabs keep their pitch
   under any capo**: counted from the capo they are played above, they move by the transposition and by the file's capo
   less the effective one (`tabCapoFor`, `ChordProTransposer`'s `capo`), an octave up where a capo above the file's would
   take a fret below it." `chordpro/…/chords/CLAUDE.md`'s sentence "A
   move's `capo` (a file's capo counted in to name the chords that sound) moves every chord name but no tab fret, tabs
   being counted from the capo." becomes "A move's `capo` (what a capo stands for of the move) moves every chord name but
   no tab fret, tabs being counted from the capo."

Expected and kept: where the capo played is higher than the one the tab is counted from, its frets move down, and a
tab with an open string (or any fret below the difference) cannot be played at that octave above the capo, so
`ChordProTabTransposer` / `ChordProTabRetuner` move the whole run an octave up (their `octaveOffset`; the retuner's
path says "Moved up an octave to stay on the neck", the bare transposer's path, with no tab player, says nothing), and a
run too wide for that is shown as written. Probed at 188635afd: no capo, capo 3, `e|--0--3--|` / `B|--1--|` reads
`e|--9---12--|` / `B|--10--|`. Nothing to add for it; step 5's `ui/playing/CLAUDE.md` line names it in one clause.

Note: review fix c0f47c541 made `transpose(song, 0, capo = n)` move tab frets (the early return now needs `capo == 0`),
which this plan relies on for the "transposition cancels the capo" case.

Also correct two comments the review's fix `c0f47c541` left slightly stale, now that a capo alone moves a tab's
frets: `ChordProTransposer.rewriteBy`'s KDoc ("…and the tabs too where nobody is named to read them") and
`SongRenderer`'s note that it "leaves a song none of them move exactly as it is". Add both files to **Files** if absent.

## Tests

`SongPlaybackTest` (`./gradlew :presentation:desktopTest`), a new case "a tab keeps its pitch under a capo override":
for the test's `{capo: 2}` song on guitar, `soundingTransposition - tabCapoFor(guitar)` (the fret move) is `0` with no
override, `2` from a setlist capo of 0, and for a song with no capo and a capo override of 3 it is `-3`; with a reader
transposition of 5 on the capo-2 file and no override it is `5` (the wrap cancels). And `SongRendererTest`: a
`{capo: 2}` song with a tab `e|---3---|` rendered with `transposition = 2, capo = 0` (the setlist capo 0 case) reads
`e|---5---|`, and a song with no capo and a tab `e|--0--3--|` rendered with `transposition = 0, capo = 3` (a capo of 3
put on) reads `e|--9---12--|`, moved an octave up rather than off the neck. Rename any test reference to `soundingCapo`.
