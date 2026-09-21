# 41 · Transposing a song that declares no `{key}` up and back down in the editor respells it (`Eb Bb Cm` → `D# A# Cm`), and `G C D` goes up to `G# C# D#`

**Severity:** minor — wrong behaviour; the first half is editor only, and it is what gets written to the file (all platforms; every song without a `{key}`, which is most hand-written ones) · **Area:** `:chordpro` — `ChordProTransposer.prefersFlats` and the two tables of flat keys

## Symptom
1. A song in E flat with no `{key}` directive: `[Eb] [Bb] [Cm]`. In the editor, transpose up one: `E B C#m`. Transpose
   down one again: the file now reads `D# A# Cm`.
2. `[G] [C] [D]` transposed up one reads `G# C# D#` rather than `Ab Db Eb`, in the editor and in the viewer.
3. The same drift exists *with* a key for two keys the tables put on the wrong side: `{key: F#}` / `[F#] [B] [C#]` up
   and down comes back as `{key: Gb}` / `[Gb] [B] [Db]` — G flat major with a `B` where its `Cb` would be — and
   `{key: G#m}` comes back as `{key: Abm}`, a key of seven flats.

The viewer always transposes from the file, so it never drifts; it shows the second symptom, and the third one as
the key and the chords of a song transposed *into* those keys.

## Cause
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt:144-167` (as of `29820b93`).
With a key, the spelling is a property of the key the song arrives in, which is right. Without one it is a count of
the accidentals the chords have *now*:

```kotlin
var flats = 0
var sharps = 0
chordNames(song).forEach { name -> … }
return flats > sharps
```

so the answer depends on the previous step rather than on where the song is going (`E B C#m` has one sharp and no
flat, so the way down is written in sharps), and a song with no accidentals at all is a tie, which means sharps
whatever key it lands in. The third symptom is the tables (`:275-276`):

```kotlin
private val flatMajorKeys = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb")
private val flatMinorKeys = setOf("Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm", "Abm")
```

They list every key that *can* be written with flats, and since the lookup is by the flat name of the tonic, a key
that can be written both ways always comes out flat — `Abm` with seven of them rather than `G#m` with five.

## Fix
**The rule.** A song that declares no key is taken to be in the key of its first chord, minor if that chord is, and
that key goes through the lookup a declared key goes through: the spelling is decided by where the song *arrives*,
never by how it is spelled now. Transposition moves the first chord with everything else, so the way back from
anywhere arrives in the key the song started in, and gets the spelling of that key: + n then − n is the identity
for every song that is spelled the way its key is, which no rule that looks at the current spelling can promise
(it has forgotten the flats of `Eb Bb Cm` by the time the song is in E). The first chord is not always the tonic,
but it is within a fifth of it or its relative nearly always, and neighbours on the circle of fifths are on the same
side of it except right at C.

Keeping the file's own accidentals and counting only on a tie — the other candidate — was tried on paper and fails
the reviewer's own example one step later: `Eb Bb Cm` + 1 is `E B C#m` either way, and that file's own preference
is sharps. It also fails `C Bb F` + 6 − 6 (the way back from `F# E B` is written `C A# F`). The count survives only
as the answer for a song in which nothing is a chord name at all.

**The tables** have to give one answer per key for that promise to hold, so a key that can be written both ways is
put on the side with fewer accidentals in its signature — D flat and not C sharp major, B flat and not A sharp
minor, as today, but G sharp and not A flat minor. Of the two that tie at six, F sharp major moves to the sharps
(its three main chords are `F# B C#`; G flat major needs a `Cb`, which `flatNames` does not have, so it came out as
`Gb B Db`), and E flat minor stays where it is, since neither table serves it better (`Cb` on one side, `E#` on the
other) and plan 38's tests already pin it. `Cb` goes because no lookup can produce it. C major is added to the flat
side: a key with no accidentals has no spelling of its own, and the chords a song in C borrows are `Bb`, `Eb`, `Ab`
and the bass of `C/Bb`, where the sharp side of the key (`D7`, `E7`, `A7`) has no accidental in its names. A minor
stays with the sharps, for `Am/G#` and `Am/F#`. Without this, `C Bb F` + 2 − 2 comes back as `C A# F`.

Runs after 40 and is written against its result (and 38's).

1. `ChordProTransposer.kt`, the tables (`:275-276`):

   ```kotlin
   // The keys that are written with flats, by the name `flatNames` gives their tonic; every other key is written
   // with sharps. A key that can be written both ways is on the side that needs fewer accidentals (Db and Bbm, but
   // G#m), and F# is written with sharps because the fourth degree of Gb is a Cb, which `flatNames` has no name for.
   // C has no accidentals to go by, and is here for the chords a song in C borrows: Bb, Eb, Ab, and the bass of C/Bb.
   private val flatMajorKeys = setOf("C", "F", "Bb", "Eb", "Ab", "Db")
   private val flatMinorKeys = setOf("Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm")
   ```

2. `ChordProTransposer.kt`, replace `prefersFlats` (`:143-167`) and delete the private `chordNames(song)` (`:169-178`),
   which 38's `writtenChordNames` replaces — it also hears the rows of chord names above a tab, so a song whose only
   chords are written there has a key too:

   ```kotlin
   /**
    * Whether flats should be preferred when writing the chords of this song after the given transposition.
    *
    * It is a property of the key the song arrives in and never of how it is spelled now, which is what makes a
    * transposition and the one that undoes it return the text they started from: the way back arrives in the key
    * the song was in. A song that declares no key is taken to be in the key of its first chord. Only a song in
    * which nothing is a chord name is spelled by the accidentals it is written with.
    */
   fun prefersFlats(song: ChordProSong, semitones: Int): Boolean {
       val names = writtenChordNames(song).toList()
       // A declared key is believed whatever it looks like (`A minor`); a bracketed word has to be a chord first,
       // or a song that opens with a `[Chorus]` somebody wrote without its `*` would be in C.
       val key = song.metadata.key?.let(::keyOf)
           ?: names.firstNotNullOfOrNull { name -> name.takeIf(ChordProChordNames::isChordName)?.let(::keyOf) }
       return key?.transposedBy(semitones)?.prefersFlats ?: isWrittenInFlats(names)
   }

   /** The key a `{key}` or a chord name stands for, or null where it does not start with a note. */
   private fun keyOf(name: String): Key? {
       val root = ChordProChordNames.notes(name.trim()).first()
       val noteIndex = noteIndices[root.getOrNull(0)] ?: return null
       val accidental = accidentals[root.getOrNull(1)]
       val suffix = root.substring(if (accidental == null) 1 else 2).trimStart()
       return Key(
           tonic = (noteIndex + (accidental ?: 0)).mod(NOTE_COUNT),
           isMinor = suffix.startsWith("m") && !suffix.startsWith("maj", ignoreCase = true),
       )
   }

   /** A key as far as the spelling goes: the pitch class of its tonic, and which of the two modes it is in. */
   private class Key(val tonic: Int, val isMinor: Boolean) {

       val prefersFlats get() = if (isMinor) flatNames[tonic] + "m" in flatMinorKeys else flatNames[tonic] in flatMajorKeys

       fun transposedBy(semitones: Int) = Key(tonic = (tonic + semitones).mod(NOTE_COUNT), isMinor = isMinor)
   }

   private fun isWrittenInFlats(names: List<String>): Boolean {
       var flats = 0
       var sharps = 0
       names.flatMap(ChordProChordNames::notes).forEach { note ->
           if (noteIndices.containsKey(note.getOrNull(0))) {
               when (accidentals[note.getOrNull(1)]) {
                   1 -> sharps++
                   -1 -> flats++
               }
           }
       }
       return flats > sharps
   }
   ```

   `trimStart()` on the suffix is what reads `{key: A minor}` as minor and leaves `{key: C major}` major (`major`
   starts with `maj`); today both are major. `private const val BASS_NOTE_SEPARATOR` has no user left in the
   transposer after this (40 took the other one) — delete it.

3. Nothing else changes. `transpose` and `transposeText` already ask `prefersFlats` for the song they are handed —
   after 38 the normalized one, so a German-notated song's first chord `H` is a B and its `B` a B flat — and a
   forced spelling (`preferFlats`, the accidentals preference) still overrides all of this. Do **not** make the
   text path remember anything between two taps: the editor hands over the current text every time, and the rule
   above is what makes that enough.

4. Two expectations written by plan 38 move with the tables, both for a song arriving in C major: its test 13
   (`the key of a German notated song is read the German way`) now ends in `[Db7]c` rather than `[C#7]c`, and step 3
   of its Verify reads `C G Db7`. Update the test in the same change.

## Tests
`ChordProTransposerTest.kt`:

1. Replace `without a key the accidentals of the chords decide` with `without a key the first chord stands in for it` —
   `prefersFlats(parse(x), n)`: `"[G]a [C]b [D]c"`, 1 → true (A flat); `"[Eb]a [Bb]b [Cm]c"`, 1 → false (E);
   `"[Bb]a [Eb]b [C]c"`, 1 → false (B; the old test's input, which answered true and wrote B major as `B E Db`);
   `"[F#]a [C#]b [C]c"`, 1 → false; `"[Am]a [F]b"`, 3 → true (C minor); `"[C/E]a [F]b"`, 5 → true (F, the root and
   not the bass); `"{sot}\n  G   C\ne|--3--0--|\n{eot}"`, 1 → true (the row above a tab).
2. `a word in brackets that is not a chord is not the key` — `"[*Intro]a [N.C.]b [Bridge]c [Em]d"`, 2 → false
   (F sharp minor; `Bridge` read as B would arrive in D flat and answer true).
3. `a song with no chord name in it is spelled the way it is written` — `"[Bbx!]a [Ebx!]b"`, 1 → true;
   `"[F#x!]a"`, 1 → false; `""`, 1 → false.
4. `a key that can be written both ways is written the way that needs fewer accidentals` — with `{key: …}` and one
   chord: `E`, 2 → false (F sharp, was G flat); `Em`, 4 → false (G sharp minor, was A flat minor); `C`, 1 → true
   (D flat); `Am`, 1 → true (B flat minor); `Dm`, 1 → true (E flat minor); `D`, −2 → true (C); `Bm`, −2 → false
   (A minor).
5. `the mode of a key may be spelled out` — `"{key: D minor}\n[Dm]a"`, 0 → true; `"{key: D major}\n[D]a"`, 0 → false.
6. `the reviewer's two songs` — `transposeText("[Eb]a [Bb]b [Cm]c", 1) == "[E]a [B]b [C#m]c"` and − 1 on that gives the
   original; `transposeText("[G]a [C]b [D]c", 1) == "[Ab]a [Db]b [Eb]c"`; and
   `transpose(parse("[G]a [C]b [D]c"), 1).chordNames() == listOf("Ab", "Db", "Eb")`.
7. `transposing up and back down returns the text` — for every `n` in `1..11` and every text below,
   `transposeText(transposeText(text, n), -n) == text` (all of them were run through a model of the rule):
   `"[Eb]a [Bb]b [Cm]c"`, `"[G]a [C]b [D]c"`, `"[C]a [Bb]b [F]c"`, `"[C]a [C/B]b [C/Bb]c [Ab]d"`,
   `"[Am]a [Am/G#]b [Am/G]c [Am/F#]d"`, `"[F#]a [B]b [C#]c"`, `"[G#m]a [E]b [B]c [F#]d"`, `"[Dm]a [Bb]b [C]c [A7]d"`,
   `"[Bbm]a [Gb]b [Db]c [Ab]d"`, `"[Ebm]a [Abm]b [Bb7]c"`, `"[Db]a [Gb]b [Ab]c"`, `"[E]a [A]b [B7]c [C#m]d"`,
   `"{key: F#}\n[F#]a [B]b [C#]c"`, `"{key: G#m}\n[G#m]a [E]b"`, `"{key: Eb}\n[Eb]a [Bb/D]b [Cm]c"`. Plan 38's German
   `"{key: Dm}\n[Dm]a [B]b [C/H]c [A7]d"` is asserted the same way for `n` in `listOf(1, 2, 9, 11)` only: those are the
   moves that leave a B natural in the song, and 38 writes every other result in English on purpose.
8. `a song spelled against its key is respelled on the way back, which is the limit of the rule` —
   `transposeText(transposeText("[C]a [F#dim]b", 2), -2) == "[C]a [Gbdim]b"`. It pins the known limit so that a change
   of the tables shows up here.

Existing cases that must stay green unchanged: `flats are preferred when the key transposes into a flat key`,
`a forced spelling overrides the one the song asks for`, `transposing by an octave keeps every chord name` (A minor
by 12 has no accidentals to spell), `a tab inside a section moves its frets…` (`Am` + 2 arrives in B minor).

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. `./gradlew :app:desktop:run`, accidentals preference on "As written". A song `[Eb]a [Bb]b [Cm]c` with no key: in the
   editor tap transpose up, then down — the text is the original again. Repeat up to + 5 and back.
3. A song `[G]a [C]b [D]c`: + 1 in the viewer shows `Ab Db Eb`.
4. A song `{key: E}` / `[E] [A] [B]`: + 2 in the viewer shows the key `F#` and `F# B C#`, in the header and in the row
   of the Songs list alike.
5. With the preference on Flats or Sharps everything is spelled that way regardless, as before.
6. The compile checks for the other three targets (common code only).

## Docs
- `chordpro/CLAUDE.md`, the `ChordProTransposer` bullet: "Chooses sharps or flats from the song's key, …" becomes
  "Chooses sharps or flats from the key the song *arrives* in, never from how it is spelled now — a song that
  declares no key is taken to be in the key of its first chord — which is what makes the editor's transposition and
  the one that undoes it return the text they started from; a key that can be written both ways is written the way
  that needs fewer accidentals (`F#` and `G#m`, `Db` and `Bbm`), and C major borrows its chords from the flat side.
  Follows the bass note after `/`, …".
- `documentation/file-format.md`: in the **Metadata** bullet, after `key`, nothing; add one sentence after the list
  ("Campfire understands the core of the format: …"), before "### Setlists": "A transposition spells its chords
  the way the key it arrives in is written, so a `{key}` is worth declaring; a song without one is taken to be in the
  key of its first chord."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/CLAUDE.md`
- `documentation/file-format.md`

## Depends on
40 (`ChordProChordNames.isChordName` / `notes`) and 38 (`writtenChordNames`, and the normalized model that makes a
German song's first chord mean what it says); through them 37 and 39.
