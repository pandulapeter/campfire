<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give back a tab's chord rows exactly when a text goes through another notation and back

**Kind:** data change on save  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/tabs/ChordProTabTransposer.kt`
(`rewriteChordLine`, `replaceKeepingColumns`), `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProNotation.kt`
(`convertText`), `libs/chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/GeneratedSongPropertyTest.kt`
(`withChordRowSpacingFolded`, which hides it today)

## Problem

`ChordProNotation.convertText` promises that converting a text out of a notation and back returns it (folded
accidentals, a German `Bb`, and Latin lowercase minors aside), and `ChordProStaffNames` leaves a tab's string names
alone where they would not come back exactly. A row of chord names inside `{start_of_tab}` does not keep that promise:
`replaceKeepingColumns` keeps every later name in its column, which a longer name can only do by taking a space next to
it, and a shorter name gives one back. The two directions are not inverse, since a row with one space and a row with
two between names can become the same row:

```
{sot}            STANDARD → LATIN → STANDARD
Eb G             Mib Sol            Eb  G      (a space gained)
Eb               Mib                Eb         (a trailing space lost)
{eot}
```

The editor converts its field to the reader's notation on open and back to the standard one on save, so a Latin reader
who opens and saves such a song without touching that row changes the file, and sync sends the change to every device.
Found by `GeneratedSongPropertyTest` (the notation round trip, seed 5); only a tab chord row whose names are one space
apart, or end in spaces, is affected. Staff lines, bracketed chords, grids, and the text transposition's pitches are
exact. (The text transposition has the same asymmetry, `Eb G ` → `E  G#` → `Eb G`, but promises only pitches.)

## Options

1. **Check the way back, as the staff names do.** In `convertText`'s `rewriteTab`, convert each chord row, convert the
   result back with the inverse rename, and keep the column-keeping result only where that gives the row back; else
   rename word for word with no column change (always reversible, since the rename is a bijection on names), or leave
   the row as written. Small, local, matches the existing staff-name rule; a cramped row shows its later names a column
   off in the other notation.
2. **Make `replaceKeepingColumns` injective**: never take the last space before a name or the line's end, and on the
   way back give one back only where the name had grown. It cannot know that without state, so in practice this means
   never taking a space at all, which drops column keeping for every row.
3. **Weaken the promise**: document that a tab chord row's spacing may change, and keep the test's folding. Cheapest,
   but a no-op open and save then still rewrites the user's file.

## Recommendation

Option 1, falling back to a word-for-word rename (not to leaving the row as written, which would show standard names
in a Latin field). Add an explicit test for the three rows above, then drop `withChordRowSpacingFolded` from
`GeneratedSongPropertyTest` so the round trip compares the models whole again.
