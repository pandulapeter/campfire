<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/voicings

- Every table shape must sound exactly the chord it is filed under, below the twelfth fret, holdable
  (`ChordVoicingTablesTest`, which also pins the rules on rootless shapes and five-fret spans). **Table order is
  written, not computed** (most comfortable first). Adding to a table never changes the shape a chord was shown with
  (a moved shape a new one would come before is filed under its chord). The search never goes past four frets and
  offers rootless shapes only after every one that keeps the root.
- Each table ends in shapes picked from the search for the chords a chart names that it had none for (the rules in
  `ChordVoicingTables`' KDoc); no player has reviewed them.
- **Every chord a seven-note scale stacks has a table shape in every key and fretted tuning** (`ScaleChordsTest`): the
  scale sheet draws only the tables'.
- A table is read on its first use (a `lazy`); `ChordVoicings.prepare` reads it ahead, off the main thread. The stored
  choice format arrives through sync, so `read` is tolerant.
- `ChordTuning`: its `id` must match `:tuner:api`'s `InstrumentTuning` ids. **A chord name names the chord that
  sounds.** A tuning has its own table or shifts one by a single interval. `drawsDefinitions` (standard and ukulele low
  G) is where a `{define}`, which has no tuning, is drawn.
- **The banjo's fifth string is a drone**: only open (if its note is in the chord) or muted, never fretted or moved.
- `ChordProDefinitions`: frets stay within 24 and keys wrap past four octaves, so no file can make a diagram draw
  without end. The instrument is the selector's, else by string count (four a ukulele, five a banjo, a mandolin only
  with `{define-mandolin}`). Last `{define}` per chord and instrument wins. `sounding` mutes a banjo drone outside the
  chord, so reader and editor draw one shape. A rewritten line changes only its numbers.
