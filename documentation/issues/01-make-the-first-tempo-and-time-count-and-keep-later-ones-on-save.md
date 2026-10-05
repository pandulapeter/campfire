# Make the first `{tempo}` and `{time}` the song's, and keep the later ones when the Song defaults sheet writes them

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProMetadataFields.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProMetadataFieldsTest.kt`,
`chordpro/CLAUDE.md`, root `CLAUDE.md` (Metronome section, the "Where a tempo lives mirrors the transposition" bullet),
`data/model/CLAUDE.md` (only the `Song` sentence about `{capo}`, see Fix 3)

Lane A, first of 01–03: apply before 02, which builds on the same lines of both source files.

## Problem

The root `CLAUDE.md` (Metronome: "The first `{tempo}` and `{time}` count") and `data/model/CLAUDE.md` ("its first
`{tempo}` rounded, its first `{time}` as written") say a song is played at its first tempo and time signature, a later
one being a change mid-song the way a later `{key}` is a modulation. The code does the opposite, and the Song defaults
sheet then destroys the mid-song changes.

`ChordProParser.MetadataBuilder.consume` — used by `parse`, `summarize` and `parseMetadata` alike — keeps the **last**
non-empty value:

```kotlin
// A later key is a modulation from where it stands; the song is in the key it starts in.
"key" -> if (key.isNullOrEmpty()) key = value
"capo" -> value.toIntOrNull()?.let { capo = it }
// An empty line, the new song template's, says nothing rather than taking back what another one said.
"tempo" -> if (value.isNotEmpty()) tempo = value
"time" -> if (value.isNotEmpty()) time = value
```

`ChordProMetadataFields.set` picks the line to rewrite the same way, and drops every other line of the field except
for `KEY`:

```kotlin
val declaring = indices.filter { !lines[it].value().isNullOrEmpty() }
val effectiveIndex = if (field == Field.KEY) declaring.firstOrNull() ?: indices.firstOrNull() else declaring.lastOrNull() ?: indices.lastOrNull()
...
index !in indices || (field == Field.KEY && index != effectiveIndex) -> kept += line
index == effectiveIndex && newValue != null -> kept += ...
```

Proven at ed4a1a5ce with a throwaway test on
`{title: T}\n{key: G}\n{time: 4/4}\n{tempo: 90}\n{capo: 1}\n[C]la\n{key: A}\n{time: 3/4}\n{tempo: 140}\n{capo: 3}\n[G]la`:

- `summarize` and `parse` both give key `G` but time `3/4` and tempo `140`: the song list, the click, the read-only
  line and the app bar all use the song's last section's values;
- `set(TIME to "6/8")` deletes the header's `{time: 4/4}` and rewrites the mid-song `{time: 3/4}` to `6/8`;
- `set(TEMPO to null)` (clearing the field in Song defaults) deletes both `{tempo}` lines.

So a user with a song that changes metre or tempo halfway sees the wrong defaults, and one save from the Song defaults
sheet silently rewrites the body of their file.

## Fix

1. **Parser** (`ChordProParser.MetadataBuilder.consume`): keep the first non-empty value, the way `key` does, and say
   why in the comment:

   ```kotlin
   // A later key, tempo or time signature is a change from where it stands; the song starts in the first one. An
   // empty line, the new song template's, says nothing rather than taking back what another one said.
   "key" -> if (key.isNullOrEmpty()) key = value
   "tempo" -> if (tempo.isNullOrEmpty()) tempo = value
   "time" -> if (time.isNullOrEmpty()) time = value
   ```

   (`value` is already trimmed; an empty one assigns `""`, which the next non-empty line replaces, exactly as for `key`.)

2. **`ChordProMetadataFields.set`**: treat `TIME` and `TEMPO` like `KEY` — the line rewritten is the first declaring
   one (or the first empty template line where none declares), and every other line of the field is kept. Introduce a
   private `val Field.isChangedInTheBody get() = this == Field.KEY || this == Field.TEMPO || this == Field.TIME` (name
   it as you see fit) and use it in both places that test `field == Field.KEY`. Update the comment above `declaring`
   and the KDoc of `set` and of `Field` ("A later `{key}` is a modulation…") to name tempo and time signature too.

3. **`{capo}` stays last-wins**, as `title`, `artist` and every other single-valued header field are: the ChordPro
   spec defines the capo as a setting of the whole song, not one that changes mid-song, so a second `{capo}` is a
   duplicate rather than a change, and `set` keeps dropping the duplicates. No code change; document it: in the root
   `CLAUDE.md` Metronome bullet change "The first `{tempo}` and `{time}` count;" to "The first `{tempo}` and `{time}`
   count, a later one being a change mid-song the way a later `{key}` is a modulation (the capo is the song's as a
   whole, so its last `{capo}` counts, as for any other header field);", and in `data/model/CLAUDE.md` change "and its
   `{capo}`" to "and its last `{capo}`".

4. **`chordpro/CLAUDE.md`**: in the parser bullet, "a song that changes key is in the key its first `{key}` names"
   becomes "a song that changes key, tempo or time signature is in the one its first `{key}`, `{tempo}` or `{time}`
   names"; in the `ChordProMetadataFields` bullet, "the last one that says anything, or for `key` the first, since a
   later `{key}` is a modulation" becomes "the last one that says anything, or for `key`, `tempo` and `time` the
   first, since a later one is a change mid-song", and "(but never another `{key}`)" becomes "(but never another
   `{key}`, `{tempo}` or `{time}`)".

## Tests

`ChordProParserTest`: a song with `{time: 4/4}{tempo: 90}` in the header and `{time: 3/4}{tempo: 140}` after a lyric
line reads time `4/4` and tempo `90` from `parse`, `summarize` and `parseMetadata`; the template case
`{tempo: }\n{tempo: 96}` still reads `96` (an existing assertion in `ChordProMetadataFieldsTest` already covers
`{tempo: 96}\n{tempo: }`). Also assert `{capo: 1}…{capo: 3}` reads `3`, pinning the documented last-wins.

`ChordProMetadataFieldsTest`, next to `the key is set on the line the song starts in and leaves its modulations alone`:
for the same text, `set(TIME to "6/8")` rewrites only the header line and leaves `{time: 3/4}` in the body; the same for
`TEMPO`. Leave the clearing assertion for 02 (it changes it).

## Manual check

In the editor write a song with `{time: 4/4}` and `{tempo: 90}` in the header and `{time: 3/4}` `{tempo: 140}` after the
first verse; save. The song card and the song details' first section show 90 and 4/4, and the click plays four beats at
90. Open Song defaults, set the time signature to 6/8 and save: the header line is 6/8, the mid-song `{time: 3/4}` and
`{tempo: 140}` are still in the file.
