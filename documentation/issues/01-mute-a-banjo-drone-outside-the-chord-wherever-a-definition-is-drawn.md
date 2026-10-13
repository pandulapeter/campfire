# Mute an open banjo drone whose note is not in the chord wherever a definition is drawn, so the reader and the editor's Transpose draw the same shape

**Kind:** bug (decision)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProDefinitions.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProDefinitionsTranspositionTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordProDefinitionsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordCells.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChordsTest.kt`,
`chordpro/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`

**Awaiting the user's decision** between (a) and (b) below; (a) is recommended, and the plan is written for it.

## Problem

The banjo's fifth string is a drone (`ChordInstrument.BANJO`'s `droneString = 0`): a shape only leaves it open or mutes
it. When a song is transposed, a `{define}` for the banjo is moved in two places, and they disagree about the drone.

The model path, which the song details page, the PDF, and the chord sheet draw from, mutes an open drone whose note
leaves the new chord, in `ChordProDefinitions.transposed` (`ChordProDefinitions.kt:124` at 72d48deb7):

```kotlin
val sounding = if (drone == null || moved.frets[drone] != 0 || chord == null || definition.instrument.tuning[drone] % 12 in chord.pitchClasses) {
    moved
} else {
    moved.copy(frets = moved.frets.mapIndexed { string, fret -> if (string == drone) null else fret })
}
```

The text path, which the editor's Transpose writes into the file, keeps the drone exactly as written
(`rewrittenLine`, ~:200, only replaces frets that are not null and keeps the drone where it is, by design, so that moving
back writes the line it was). `ChordProDefinitionsTranspositionTest` pins both:

```kotlin
assertEquals(ChordVoicing.Fretted(listOf(null, 4, 2, 3, 4), listOf(0, 3, 1, 2, 4)), move(c, 2).voicing)
...
assertEquals("{define: D frets 0 4 2 3 4}", ChordProDefinitions.rewrittenLine("{define: C frets 0 2 0 1 2}", "", rename, 2), "the text keeps it as written")
```

So a banjo player whose song defines `{define: C frets 0 2 0 1 2}`:

- transposes it +2 on the song details page and sees D as `x 4 2 3 4` (correct: the drone's G is not in D);
- transposes it +2 in the editor, which writes `{define: D frets 0 4 2 3 4}`; read back untransposed, nothing mutes the
  drone (`ChordProDefinitions.read` only refuses a *stopped* drone, ~:412), so the page draws `0 4 2 3 4`, the open g
  sounding under a D chord: a wrong note, and a different diagram from the one the reader showed for the same song.

The same happens to a hand-written `{define: D frets 0 4 2 3 4}`: drawn with the g drone ringing.

## Fix

**Options:**

- **(a) — recommended.** Keep the text as it is (its round trip is the point of `rewrittenLine`), and make the mute a
  rule of *drawing* a banjo definition rather than of moving it: wherever a definition is drawn, an open drone whose note
  is not one of the named chord's notes is drawn muted. Then the reader and the editor agree for every transposition, and
  a hand-written definition with a wrong open drone is drawn right too.
- (b) Write `x` into the drone's place in `rewrittenLine` when the move mutes it. Simple, but moving back can no longer
  write the line it was (`D frets x 4 2 3 4` back to C stays muted where the file had `0`), which breaks the documented
  "there and back writes the line it was" round trip, and a hand-written definition is still drawn with the wrong drone.

**For (a):**

1. In `ChordProDefinitions.kt`, add a public function next to `transposed`:

   ```kotlin
   /**
    * [definition] as it is drawn: a drone string ([ChordInstrument.droneString]) left open where its note is not one of
    * the chord the definition is named for is muted, since it would sound a note the chord does not have. The text keeps
    * it as written, so that a transposition there and back writes the line it was.
    */
   public fun sounding(definition: ChordDefinition): ChordDefinition {
       val voicing = definition.voicing as? ChordVoicing.Fretted ?: return definition
       val drone = definition.instrument.droneString ?: return definition
       if (voicing.frets[drone] != 0) return definition
       val chord = ChordProChords.parse(definition.name) ?: return definition
       if (definition.instrument.tuning[drone] % 12 in chord.pitchClasses) return definition
       return definition.copy(voicing = voicing.copy(frets = voicing.frets.mapIndexed { string, fret -> if (string == drone) null else fret }))
   }
   ```

   (Keep `fingers` untouched: the drone's finger is already 0 for an open string.) Note that `chord.pitchClasses` for a
   slash chord includes its bass; that is the same test `transposed` uses today, so the behaviour does not change.
2. Remove the mute from `transposed`: the `sounding` local and its `if` go, and the `Fretted` branch returns
   `definition.copy(name = name, voicing = moved, movedBy = definition.movedBy + move)`. The model then holds exactly what
   the text holds. Update the KDoc's last sentence to "A drone string ([ChordInstrument.droneString]) stays where it is,
   as the text keeps it; [sounding] is what mutes it where its note leaves the chord."
3. Call it at the two places a song's definitions become drawn shapes, both in `:presentation`:
   - `SongChords.kt`, inside `songChordsOf`: map the instrument's definitions through it before they are matched, so
     `definitionOf`, the `distinctBy` and `SongChord.definition` / `undrawnDefinition` all see the drawn shape:
     ```kotlin
     val definitions = song.metadata.definitions.filter { it.instrument == tuning.instrument }.map(ChordProDefinitions::sounding)
     ```
   - `ChordCells.kt`, `definitionCellsOf`: `song.metadata.definitions.map { ChordProDefinitions.sounding(it) }.map { definition -> … }`
     (or call it inside the existing lambda).
   Everything else that draws a definition (`selectShape` in `ChordSelection.kt`, the PDF's `PrintDocument.kt`, the
   chord sheet) reads `SongChord.definition` from `songChordsOf`, so it follows.
4. Docs: in `chordpro/CLAUDE.md`, the `ChordProDefinitions` paragraph's "(the banjo's drone not counted, which stays as
   it is and is muted on the page where its note leaves the chord, while the text keeps it as written)" becomes "(the
   banjo's drone not counted, which stays as it is, in the model as in the text; `sounding` mutes it where its note
   leaves the chord, which is how every definition is drawn, a hand-written one included)", and add `sounding` to the
   list of what the object does. In `presentation/.../ui/chords/CLAUDE.md`, where the song's definitions are described,
   add one clause: a banjo definition is drawn through `ChordProDefinitions.sounding`.

## Tests

- `ChordProDefinitionsTranspositionTest`, the banjo test: `move(c, 2).voicing` is now `Fretted(listOf(0, 4, 2, 3, 4), listOf(0, 3, 1, 2, 4))`,
  and `ChordProDefinitions.sounding(move(c, 2)).voicing` is `Fretted(listOf(null, 4, 2, 3, 4), …)`; G (`move(c, 7)`)
  keeps `0` in both; the `rewrittenLine` assertion stays. Add the parity assertion the bug is about: the definition parsed
  back from the rewritten line, run through `sounding`, equals `sounding(move(c, 2))` (frets, ignoring name spelling).
- `ChordProDefinitionsTest`: `sounding` of `{define: D frets 0 4 2 3 4}` (read with `definitionOf`) mutes the drone;
  `{define: G frets 0 0 0 0 0}` keeps it open; a guitar definition and a keyboard one come back unchanged.
- `SongChordsTest`: `songChordsOf(ChordProParser.parse("{define: D frets 0 4 2 3 4}\n[D]la"), ChordNotation.STANDARD, ChordTuning.BANJO)`
  gives `definition?.voicing` frets `[null, 4, 2, 3, 4]`; and the same song written in C with `{define: C frets 0 2 0 1 2}`
  transposed by 2 through `ChordProTransposer` (model path) gives the same frets.

## Manual check

On any platform, set the instrument to the banjo (standard tuning). Open a song holding
`{define: C frets 0 2 0 1 2}` and `[C]la`. Transpose +2 on the song details page: the D diagram shows the fifth string
muted. Then open the editor, use its Transpose +2, save, and reopen the song with no transposition: the D diagram is the
same, fifth string muted, while the file reads `{define: D frets 0 4 2 3 4}`. Transposing the editor back -2 writes
`{define: C frets 0 2 0 1 2}` again.
