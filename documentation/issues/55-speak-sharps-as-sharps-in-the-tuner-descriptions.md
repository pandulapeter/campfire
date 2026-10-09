# Speak sharps as sharps in the tuner's screen reader descriptions

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/NoteNames.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/SpokenNoteName.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerStrings.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/NoteNamesTest.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

Every description the tuner gives a screen reader names the note with `noteNameWithOctave`
(`tuner/NoteNames.kt:15-27` at b5c8ed3b5):

```kotlin
private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
internal fun noteName(note: Int, notation: ChordNotation): String = ChordProNotation.shownName(SHARP_NAMES[note.mod(SEMITONES)], notation)
internal fun noteNameWithOctave(note: Int, notation: ChordNotation) = "${noteName(note, notation)}${noteOctave(note)}"
```

so a sharp is spoken as the `#` character: TalkBack reads "G#4" as "G hash four" or "G number four", VoiceOver as "G
number sign four". The users are `TunerDisplay.kt:75-77` (the reading) and `TunerStrings.kt:53-54` (each string chip's
`tuner_string` "String %1$d, %2$s" description); plans 52 and 53 add more (the announcement, the tone).

What `noteName` can produce, checked against `ChordProNotation.shownName`: only sharps (never flats), and

- STANDARD, NASHVILLE, ROMAN: `C# D# F# G# A#` (a numbering names a lone note in letters);
- GERMAN: the same — `noteToGerman` only rewrites `B` to `H` and `Bb` to `B`, so `A#` stays `A#`;
- LATIN: `Do# Re# Fa# Sol# La#`.

So a template around the name without its `#` covers every case, and no per-note table is needed — except that
Hungarian speaks letter sharps as one word with a suffix (Cisz, Disz, Fisz, Gisz, Aisz), which is wrong for the
solfège syllables ("Doisz"), where Hungarian says the sign's name, `kereszt` (the word Settings already uses:
`settings_accidentals_sharps` "Kereszt (#)").

## Fix

1. A pure function in `NoteNames.kt`, next to `noteNameWithOctave`:

   ```kotlin
   /**
    * [noteNameWithOctave] as it is spoken: a sharp is said by [sharp], given the name without its sign, since a screen
    * reader reads `#` as the character rather than as the note, and the octave is a word of its own ("G sharp 4"
    * rather than "sharp4").
    */
   internal fun spokenNoteNameWithOctave(note: Int, notation: ChordNotation, sharp: (String) -> String): String {
       val name = noteName(note, notation)
       return "${if (name.endsWith('#')) sharp(name.dropLast(1)) else name} ${noteOctave(note)}"
   }
   ```

2. A value Composable in a new file `tuner/SpokenNoteName.kt` (header copied from a sibling) that supplies the
   language's sentence for this note (the lambda of the pure function is not Composable, so the sentence is resolved
   first):

   ```kotlin
   /** [spokenNoteNameWithOctave] in the app's language: the solfège syllables take the sign's name, letters a suffix. */
   @Composable
   internal fun spokenNoteName(note: Int, notation: ChordNotation): String {
       val name = noteName(note, notation)
       val sharp = if (name.endsWith('#')) {
           stringResource(
               if (notation == ChordNotation.LATIN) Res.string.tuner_note_sharp_syllable else Res.string.tuner_note_sharp_letter,
               name.dropLast(1),
           )
       } else {
           null
       }
       return spokenNoteNameWithOctave(note, notation) { sharp ?: it }
   }
   ```

3. Use it for every description, never for what is drawn:
   - `TunerDisplay.kt`: every `noteNameWithOctave(…)` inside the description(s) (`description` /
     `exactDescription`, plan 52's announcement text, plan 53's tone sentence) becomes `spokenNoteName(…)`. The note
     label keeps `noteName` / `noteOctave`.
   - `TunerStrings.kt:53-54`: the chip keeps `name` for its `Text`, and its description becomes
     `stringResource(Res.string.tuner_string, tuning.strings.size - index, spokenNoteName(note, notation))`.
   - `ReferencePitchSetting.kt` (lane A's) names only A, which has no sharp: leave it.

Strings, in the tuner block of both files after `tuner_string`:

```xml
<!-- A sharp note as a screen reader says it, given its name without the sign: "C sharp", or for the syllables of the
     Latin notation, "Do sharp". -->
<string name="tuner_note_sharp_letter">%1$s sharp</string>
<string name="tuner_note_sharp_syllable">%1$s sharp</string>
```

```xml
<!-- A sharp note as a screen reader says it, given its name without the sign: "C sharp", or for the syllables of the
     Latin notation, "Do sharp". -->
<string name="tuner_note_sharp_letter">%1$sisz</string>
<string name="tuner_note_sharp_syllable">%1$s kereszt</string>
```

The argument is the app's own note name, so `stringResource` is right.

Docs: `ui/tuner/CLAUDE.md`, the `NoteNames.kt` bullet: add "and, for screen readers, with the sharp spoken
(`spokenNoteName`: "C sharp", Hungarian "Cisz", and "Do kereszt" for the syllables)".

## Tests

Extend `NoteNamesTest`:
- `` `a sharp is spoken by the template given its name without the sign` `` —
  `spokenNoteNameWithOctave(68, ChordNotation.STANDARD) { "$it sharp" }` is `"G sharp 4"`;
  `spokenNoteNameWithOctave(66, ChordNotation.LATIN) { "$it kereszt" }` is `"Fa kereszt 4"`;
  `spokenNoteNameWithOctave(70, ChordNotation.GERMAN) { "${it}isz" }` is `"Aisz 4"`.
- `` `a natural note is spoken by its name, then its octave` `` — `spokenNoteNameWithOctave(59, ChordNotation.GERMAN) { error("") }`
  is `"H 3"`, and `(40, STANDARD)` is `"E 2"`.

## Manual check

TalkBack (English): Tuner tab, play or hum a G sharp, touch the display: "G sharp 4, …". Switch the app to Hungarian:
"Gisz 4, …". Settings → chord notation Latin: "Sol kereszt 4". Choose an instrument whose strings include a sharp (or
check a natural one reads unchanged: "String 6, E2").
