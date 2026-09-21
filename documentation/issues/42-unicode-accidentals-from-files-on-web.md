# 42 · `♯` and `♭` written in a song file reach the screen as they are: missing glyphs on the web, and a song that flips between `B♭` and `Bb` when it is transposed, everywhere

**Severity:** minor (the missing glyphs: web only, files that spell accidentals with the Unicode signs — typical of files exported from notation software; the flip between the two spellings: all platforms) · **Area:** `:chordpro` — `ChordProNotation.normalized`, `ChordProParser.scan`; nothing in `:presentation`

**Status:** the missing glyphs were not reproduced — no browser was available to the review or to this plan. The
evidence is the project's own: commit `a1fec638` ("Fix missing accidental symbols") took `♭` / `♯` out of four strings
in both `strings.xml` files and removed the README's "Flat and Sharp symbols don't always appear on web, depending
on the browser". The second half of the symptom is plain from the code and holds on every platform.

## Symptom
1. A file writes `{key: F♯m}` and `[B♭]`. With no transposition and Accidentals on "As written", the chord rows of
   the viewer, the key in the details header, the key next to the song in the Songs and Setlists lists and the
   editor's transpose stepper all draw the two signs in the proportional font. On the web that font is the one
   Compose ships — it draws with its own Skia and cannot reach the browser's fonts, see
   `presentation/.../ui/theme/MonospaceFontFamily.kt` — and those are the glyphs `a1fec638` stopped relying on.
2. Transpose the same song by one semitone, or choose Flats or Sharps in Settings: every chord is now written with
   `#` and `b`, because the transposer only ever writes ASCII. One song reads `B♭` or `Bb` depending on a stepper,
   on all four platforms.

Tabs and the editor's source text are not affected: they are set in the bundled JetBrains Mono, whose subset keeps
the accidentals on purpose (`MonospaceFontFamily.wasmJs.kt`), and the editor has to show the file as it is.

## Cause
The transposer *reads* the two signs (`chordpro/.../ChordProTransposer.kt:287-292`, the `accidentals` map) and
`ChordProNotation` does too, but nothing folds them on the way to the screen when there is nothing to transpose:

```kotlin
// presentation/.../ui/CampfireViewModel.kt:969-980, renderSong
val transposed = if (semitones == 0 && spelling.accidentals == UserPreferences.Accidentals.ORIGINAL) {
    parsed
} else {
    transposeChordPro(parsed, semitones, spelling.accidentals)
}
```

and even a transposed chord keeps a sign in its quality (`[Cm7♭5]` + 2 is `Dm7♭5`), since only the two notes of a
name are rewritten.

## Fix
**The decision: fold the signs into `#` and `b` in the model, on every platform, never in the file — and do not
bundle a font for them.**

- A font would not fix the second symptom, which is not about the web at all: the app's own spelling of an
  accidental is ASCII — in everything the transposer writes, in the accidentals preference, and since `a1fec638` in
  its strings — so the signs of a file are the odd ones out on every screen.
- On the web a fallback font is a download that arrives after the first frames (the song is drawn with holes until
  then), costs bytes for two glyphs, and puts a glyph of another face into a chord row; the font the build already
  has with those glyphs is a monospaced one.
- The fold costs nothing, needs no platform code, and the file keeps every byte: the only writer of chords is
  `transposeText`, which never goes through the model.

**Where.** Plan 38 made the parser the place where a song is brought into the app's own notation
(`ChordProNotation.normalized`, and the key in `scan`), so that *nothing outside `:chordpro` has to know*. ASCII
accidentals are part of that notation, and folding them there covers every reader at once — `renderSong` with or
without a transposition, the editor's preview, `Song.key` for `renderKey` and the lists, the editor's stepper —
where the reviewer's suggestion (always run a rewrite in `renderSong` / `renderKey`) needs a new use case, two call
sites in the view model, and still misses the stepper. Runs after 41 and is written against the result of 38–41.

1. `ChordProNotation.kt` — add

   ```kotlin
   /**
    * A chord name with its accidentals written the way the app writes them, `#` and `b`, wherever in the name they
    * stand: `B♭m7♭5` is `Bbm7b5`. A file may spell them with the signs of music notation, and keeps them; on the way
    * to the screen they are folded, because everything the app spells itself is ASCII — a transposed chord, a forced
    * spelling — and a song must not read `B♭` or `Bb` depending on whether it has been moved, and because the font
    * the web build draws with has neither glyph.
    */
   internal fun withAsciiAccidentals(name: String) = name.replace(SHARP_SIGN, '#').replace(FLAT_SIGN, 'b')

   private const val SHARP_SIGN = '♯'
   private const val FLAT_SIGN = '♭'
   ```

   and replace 38's one-line `normalized` with

   ```kotlin
   /**
    * [song] in the app's own notation: English note names, whichever of the two notations its file is written in,
    * and ASCII accidentals. A song that is already there, which is nearly every one, is returned as it is.
    */
   internal fun normalized(song: ChordProSong): ChordProSong {
       val names = ChordProTransposer.writtenChordNames(song).toList()
       val isGermanNotated = names.any(::isGermanName)
       if (!isGermanNotated && names.none { name -> SHARP_SIGN in name || FLAT_SIGN in name }) return song
       val rename = { name: String -> withAsciiAccidentals(if (isGermanNotated) fromGerman(name) else name) }
       return ChordProTransposer.rewriteChords(
           song = song,
           rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
           rename = rename,
       )
   }
   ```

   `fromGerman(song)` and `isGermanNotated(song)` stay: `transposeText` uses them. The order inside `rename` does
   not matter (`noteFromGerman` already takes `B♭` for a spelled-out B flat). A row of chord names above a tab is
   folded like any other chord — the signs are one character wide like what replaces them, so no column moves — and
   a staff line, a note to the player, the lyrics, an `[*annotation]` and a `{comment}` are text and are left alone.

2. `ChordProParser.scan`, the end of it as 38 wrote it: the key is folded whether or not the song is German-notated.

   ```kotlin
   val declared = metadata.build()
   val isGermanKey = declared.key?.let(ChordProNotation::isGermanName) == true
   val key = declared.key?.let { key ->
       ChordProNotation.withAsciiAccidentals(if (isGermanNotated || isGermanKey) ChordProNotation.fromGerman(key) else key)
   }
   return ChordProSummary(metadata = declared.copy(key = key), hasChords = hasChords)
   ```

3. Do **not** touch `transposeText` / `rewriteText`: a chord the editor's transposition moves is written with the
   names of `sharpNames` / `flatNames` as it always was, and whatever it does not move — `♭5` in a quality, a chord
   inside an annotation, the lyrics — stays the user's. Do not remove the two signs from the transposer's
   `accidentals` map or from `ChordProChordNames`: the text path and `parseAsWritten` still meet them. Nothing in
   `:domain`, `:data` or `:presentation` changes, and no font is added.

Not covered, on purpose: a `♭` in lyrics, a comment, a title or an annotation is the user's text and is drawn as
written; on the web it is one case of a wider limit (any character the bundled font lacks, `♪` included), which is
not this issue.

## Tests
`ChordProParserTest.kt`:

1. `the accidental signs of a file are read into the ones the app writes` —
   `parse("{key: F♯m}\n[B♭]a [F♯m7♭5/C♯]b [*B♭ only]c ♭\n{sog}\n| E♭ . |\n{eog}")`: key `F#m`; the chords of the lyrics
   line `Bb`, `F#m7b5/C#` and the annotation `B♭ only`, untouched; the line's text ends in ` ♭`; the grid chord `Eb`.
2. `the chord names above a tab are folded and the rest of the tab is not` —
   `parse("{sot}\nTuning: E♭ A♭\n  B♭    F♯\ne|--0--3--|\n{eot}")` has the tab lines
   `listOf("Tuning: E♭ A♭", "  Bb    F#", "e|--0--3--|")`.
3. `summarize folds the accidental signs of the key` — `summarize("{key: B♭}\n[F]a").metadata.key == "Bb"`,
   `summarize("{key: F♯m}").metadata.key == "F#m"`; and add `"{key: F♯m}\n[A]a"` to the texts of
   `parseMetadata matches the metadata of a full parse`.
4. `a German notated file may spell its flats with the sign` — `parse("{key: B}\n[H7]a [B♭]b [H♭]c")`: key `Bb`, chords
   `B7`, `Bb`, `Bb`.
5. `a song with no sign in it is handed out as it was parsed` — `parse("[Bb]a [F#]b")` equals
   `parseAsWritten("[Bb]a [F#]b")`.

`ChordProNotationTest.kt`:

6. `accidental signs are folded wherever they stand in a name` — `withAsciiAccidentals`: `B♭` → `Bb`, `F♯m7♭5/C♯` →
   `F#m7b5/C#`, `Bb` → `Bb`, `N.C.` → `N.C.`, `""` → `""`.
7. `a file written with accidental signs is read in German notation like any other` —
   `toGerman(parse("{key: B♭}\n[B♭]a [B]b")).chordNames() == listOf("B", "H")`, key `B`.

`ChordProTransposerTest.kt`:

8. `a song reads the same whether or not it has been moved` — `val song = parse("[B♭]a [E♭]b")`:
   `song.chordNames() == listOf("Bb", "Eb")`, `transpose(song, 2).chordNames() == listOf("C", "F")`,
   `transpose(song, 12).chordNames() == listOf("Bb", "Eb")`.
9. `transposing text leaves the signs it does not move` —
   `transposeText("{key: B♭}\n[B♭]a [Cm7♭5]b [*♭]c", 2) == "{key: C}\n[C]a [Dm7♭5]b [*♭]c"`, and
   `transposeText(text, 0)` is the same instance as `text`.

`ChordProSerializerTest.kt`:

10. add `"{key: F♯m}\n[B♭]a"` to what `serializing a parsed song and parsing it again yields the same model` checks
    (a folded model is a fixed point of the fold).

## Verify
1. `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest`.
2. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`; import a file `{title: Signs}` / `{key: F♯m}` / `[B♭]la [F♯m7♭5]la`.
   The Songs row shows the key `F#m`, the viewer `Bb` and `F#m7b5`, with no empty boxes; + 1 and back reads the same
   way throughout. Open it in the editor: the source still says `B♭` (in the monospaced font, which has the glyph),
   the preview says `Bb`, and saving without editing offers nothing to save.
3. The same on `:app:desktop:run`: the viewer reads `Bb` before and after a transposition.
4. Export the library and look at the file: `♭` and `♯` are still in it.
5. The compile checks for Android and iOS (common code only).

## Docs
- `chordpro/CLAUDE.md`, the `ChordProParser` bullet, after 38's sentence about the app's own notation: "The same
  step folds `♯` and `♭` in chord names and the key into `#` and `b`, which is how everything the app spells itself
  is written and all the web build's font can draw; the file keeps them, since the only thing that writes chords,
  `transposeText`, works on the text." In the `ChordProNotation` bullet add `withAsciiAccidentals` to what it holds.
- `documentation/file-format.md`, the **Content** bullet: add "`♯` and `♭` are understood wherever `#` and `b` are;
  Campfire shows them as `#` and `b`, and leaves them in the file."
- `presentation/CLAUDE.md`: none — the sentence about the monospaced subset keeping the accidentals stays true, since
  the editor and the tabs show text as written.

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`
- `chordpro/CLAUDE.md`
- `documentation/file-format.md`

## Depends on
38 (`normalized`, `writtenChordNames`, the key in `scan` — this plan extends all three) and, because they edit the
same files and 38's code is rewritten by it, 40; 41 only for the order (the same test file). Through them 37 and 39.
