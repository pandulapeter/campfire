# 08 — Never transpose a bracket that is not a chord

## What the user sees

A song whose sections are marked with plain brackets — `[Intro]`, `[Break]`, `[Chorus 2x]`, `[Bass]`, `[Solo]` — is
transposed, and the labels are transposed too. At +3 semitones:

```
[Intro] [Break] [Chorus 2x] [Bass]   ->   [Intro] [Dreak] [Ebhorus 2x] [Dass]
```

`[Intro]` survives only because `I` is not a note letter. Every label that starts with A–H is rewritten into
nonsense, and a `b` right after that letter is eaten as a flat sign, so `[Ebony]` is read as an E flat and becomes
`[Gbony]`.

This is not only the viewer. Two paths make it permanent:

- **The editor's transpose action** writes the result straight to the file on disk
  (`TransposeChordProTextUseCase` -> `ChordProTransposer.transposeText`), so the labels are destroyed in the user's
  own library, and the change then syncs to every other device.
- **The accidentals preference** runs a "transposition" of zero semitones with a forced spelling, which is worth
  doing for respelling (`ChordProTransposer.transpose`, line 34: `if (semitones == 0 && preferFlats == null) return
  song`). With sharps forced, `[Ebony]` becomes `[D#ony]` without the user transposing anything at all.

A grid gets it too: in `{start_of_grid}` a word between the first and the last bar is a chord cell, so
`| Am . G | Coda |` at +2 comes out as `| Bm . A | Doda |`.

**This was raised in an earlier review and deliberately left alone. The user has now reopened it and chosen the
fix: leave a bracket unchanged unless `ChordProChordNames.isChordName` accepts the whole of it.** That is the
decision this plan implements; it is not open again.

## Cause

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt:22-24`, verified at HEAD
`984861e4`:

```kotlin
    /** Transposes a chord name, keeping an optional chord's parentheses and its bass note. */
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean) =
        ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }
```

Nothing asks whether `name` is a chord. `rewriteNotes` splits it at a `/` and hands each half to `transposeNote`,
`ChordProTransposer.kt:316-322`:

```kotlin
    private fun transposeNote(part: String, semitones: Int, preferFlats: Boolean): String {
        val noteIndex = noteIndices[part.getOrNull(0)] ?: return part
        val accidental = accidentals[part.getOrNull(1)]
        val suffixStartIndex = if (accidental == null) 1 else 2
        val transposedNoteIndex = (noteIndex + (accidental ?: 0) + semitones).mod(NOTE_COUNT)
        return (if (preferFlats) flatNames else sharpNames)[transposedNoteIndex] + part.substring(suffixStartIndex)
    }
```

The only test is the first character against `noteIndices` (`ChordProTransposer.kt:399-408`, which maps `C D E F G
A B H`), and everything after the note letter — and its accidental — is carried along as "the suffix". So
`"Break"` is B + `"reak"`, and `"Ebony"` is E flat + `"ony"`.

Reproduced at HEAD against the compiled desktop classes:

| input | call | output |
| --- | --- | --- |
| `[Intro] [Break] [Chorus 2x] [Bass] [Ebony]` | `transposeText(…, 3, null)` | `[Intro] [Dreak] [Ebhorus 2x] [Dass] [Gbony]` |
| `[Ebony] tower` | `transposeText(…, 0, false)` | `[D#ony] tower` |
| `{start_of_grid}\| Am . G \| Coda \|` | `transposeText(…, 2, null)` | `\| Bm . A \| Doda \|` |
| `[Break]a [Bass]b` | `transpose(parse(…), 2, null)` | chords `C#reak`, `C#ass` |

The sibling path already does the right thing, which is the strongest argument that this one should.
`ChordProNotation.kt:32-40`:

```kotlin
    /**
     * Rewrites one chord name, its bass note included. A word that is not a chord — `N.C.`, or a `[Bridge]` somebody
     * wrote without the `*` that makes it an annotation — is returned as it was, which is what keeps this from
     * turning prose starting with a `B` into prose starting with an `H`.
     */
    fun toGerman(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name, ::noteToGerman)
    }
```

The German notation has asked the question since it was written. The transposition never has.

## The change

One gate, in `transposeChord`, mirroring `ChordProNotation.toGerman`. **Recommended over gating at the call sites**:
there are six of them (see below), they are reached through three different `rename` lambdas, and a rule that lives
in one function cannot drift between them — which is the same reason `ChordProChordNames` exists at all.

### 1. The gate

`ChordProTransposer.kt:22-24` becomes:

```kotlin
    /**
     * Transposes a chord name, keeping an optional chord's parentheses and its bass note.
     *
     * A word that is not a chord name is returned as it was. Brackets are how a chart marks its parts as often as
     * they hold chords — `[Intro]`, `[Break]`, `[Chorus 2x]` — and every one of those that starts with a note letter
     * would otherwise be moved, with the `b` after an `E` eaten as a flat sign. [ChordProNotation.toGerman] has
     * asked the same question of the same names since it was written.
     */
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }
    }
```

`transposeNote` itself needs no change: with the gate above it, it is only ever reached for a name `isChordName`
has already accepted, and the `noteIndices` lookup stays as the last line of defence.

### 2. Every caller, checked

All six reach `transposeChord` through a `rename` lambda and are therefore covered by the one gate. Walk them when
implementing, because each has its own idea of what a "name" is:

1. **Lyrics chords, model** — `rewriteLine`, `ChordProTransposer.kt:296-301`. Already skips `chord.isAnnotation`;
   now also skips a label somebody wrote without the `*`.
2. **Lyrics chords, text** — `rewriteLyricsLineChords`, `:325-347`. It decides `isChord` from
   `content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)` and then rebuilds the bracket around
   `rename(content)`. With the gate, `rename` returns `content` unchanged and the bracket is rebuilt byte for byte
   — the leading and trailing spaces inside it are re-appended from the original. Verify that in a test: a bracket
   that is not a chord must come out **identical**, spaces included.
3. **Grid cells, model** — `rewriteLine`, `:303-311`, through `ChordProSyntax.cellChords` (a `~`-joined cell is
   several chords, each gated on its own).
4. **Grid cells, text** — `transposeGridLine`, `:349-368`. Note that this is where `[Coda]`-style margin labels
   that happen to sit *between* two bars are rewritten; `ChordProSyntax.parseGridTokens` only calls the words
   outside the first and last bar `Text`.
5. **The key** — `rewriteChords`, `:62` (model) and `transposeKeyLine`, `:375-378` (text). A `{key: Dm}` is a
   chord name and still moves; a `{key: Dm (capo 2)}` is not one and now stays, which is right — today it becomes
   `{key: Em (capo 2)}` only by accident of the suffix rule.
6. **Tabs** — `ChordProTabTransposer.transpose`, `:30-41`. The fret lines never go through `rename`. The chord
   rows do, but `rewriteChordLine` (`ChordProTabTransposer.kt:106-114`) already refuses a line unless **every**
   word on it is a chord or a marker, so the gate is a no-op there — **except** on the `hasBrackets` branch
   (`:109`), which routes to `rewriteLyricsLineChords` and is now gated like any other lyrics line.

`transposedOffset` (`:155-173`) never calls `transposeChord`; it compares the two texts. The gate only makes
fewer lines change, which sends more of them down the `before == after` short-circuit in `transposedColumn`
(`:186`). Add a caret test anyway — see Tests — because a line of labels that no longer changes at all is exactly
the case that used to shift the caret.

### 3. A label must not vote on the spelling either

`isWrittenInFlats`, `:274-286`, counts the accidentals of every written name to pick sharps or flats when the song
declares no key:

```kotlin
        names.flatMap(ChordProChordNames::notes).forEach { note ->
            if (noteIndices.containsKey(note.getOrNull(0))) {
```

`[Ebony]` votes for flats there, and `[Chorus]` for neither only because `C` has no accidental. Filter the names
the same way the key fallback right above it already does (`:257`,
`name.takeIf(ChordProChordNames::isChordName)`):

```kotlin
        names.filter(ChordProChordNames::isChordName).flatMap(ChordProChordNames::notes).forEach { note ->
```

This changes nothing for lowercase minors: by the time `prefersFlats` sees a model, the parser (model path) or
`ChordProNotation.withLowercaseMinorsExpanded` (text path, `:119`) has already spelled them out.

### What this does *not* change

`ChordProHighlighter` still colours `[Break]` as a chord. That is deliberate and stays: the editor's colouring says
"this is a bracket the viewer will lift out of the lyrics", which is true of `[Break]` whether or not it names a
chord, and making it grey would tell the user their label is wrong. Finding 14 changes the highlighter for
annotations only.

`ChordProChordNames.isChordName` itself is not loosened or tightened here — with one exception that is **not** in
this plan: it does not accept a lowercase bass note today, so `D/f#` is not a chord name and this gate would stop
transposing it. That is why this plan depends on plan 09.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`, next to
`names that are not chords are returned unchanged` (line 56) and `a lowercase word in brackets is not a chord`
(line 173):

1. `a section label in brackets is not transposed` —
   `assertEquals("[Intro] [Break] [Chorus 2x] [Bass] [Ebony]", ChordProTransposer.transposeText("[Intro] [Break] [Chorus 2x] [Bass] [Ebony]", 3))`,
   and the same through `transpose(ChordProParser.parse(…), 3)`.
2. `a forced spelling leaves a label alone` —
   `assertEquals("[Ebony] tower", ChordProTransposer.transposeText("[Ebony] tower", 0, preferFlats = false))`.
   This is the case the accidentals preference hits without anybody transposing anything.
3. `a label keeps the spaces inside its brackets` — `"[ Break ]la"` at +2 comes back identical.
4. `a grid cell that is not a chord is left alone` — extend
   `a grid keeps its margin labels and moves every chord of a cell` (line 186) or add a case:
   `"{start_of_grid}\n| Am . G | Coda |\n{end_of_grid}"` at +2 must keep `Coda` while `Am` and `G` move.
5. `a key that is not a chord name is left alone` — `"{key: Dm (capo 2)}"` at +2 comes back unchanged, while
   `"{key: Dm}"` becomes `"{key: Em}"`.
6. `a real chord is still transposed` — a regression net over the shapes `isChordName` accepts, so that the gate
   cannot quietly stop transposing anything: `C`, `Am7/G`, `C#m7b5`, `Bsus4`, `(Em)`, `F#m`, `Gadd9`, `Cmaj7`,
   `Am(no3)`, `D7sus4/A`, `Bb/D`, `H7`, `A-`, `D♭`, `G♯m`, and — once plan 09 has landed — `D/f#`.
7. `a caret keeps its place on a line of labels that did not change` — `transposedOffset` over
   `"[Break]la\n[C]lo"` transposed by 1: an offset inside the first line must map to itself, and an offset on the
   second line must follow the chord that grew. Use the existing `transposed(before, semitones, offset)` helper
   at the bottom of the file.
8. `a tab chord row still moves and its labels do not` — a `{start_of_tab}` holding a row of chord names above a
   staff plus a `Riff 1` line above that: the chord row moves, the `Riff 1` line is byte for byte (it already is,
   via `chordWords` returning null — this is the regression net for the `hasBrackets` branch).

`ChordProNotationTest.kt` — nothing new; its own gate is what this copies. Run it to confirm the two paths still
agree.

## Verification

```
./gradlew :chordpro:desktopTest
```

and, since `:domain:implementation` and `:data:source:local:implementation` read the module:

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Manual, on any one platform (`./gradlew :app:desktop:run` is quickest):

1. Write a song holding `[Intro]`, `[Break]`, `[Chorus 2x]`, `[Bass]`, `[Ebony]` and a few real chords.
2. Transpose it in the viewer: only the chords move.
3. Transpose it in the **editor** and save: the file on disk still has its labels.
4. Settings -> accidentals -> force sharps, then flats: the labels do not move either way.

## Docs

`chordpro/CLAUDE.md`, the `ChordProTransposer` bullet (line 103-105) — this sentence lists what the transposition
leaves alone and no longer lists everything:

> - `ChordProTransposer` — moves chords by semitones, on the model (the viewer) or directly on the text keeping every
>   byte of formatting (the editor's transpose action). Chooses sharps or flats from the song's key, follows the bass
>   note after `/`, understands German `H`, and leaves annotations alone.

Add the new rule next to the annotations: a bracket is only moved when `ChordProChordNames.isChordName` accepts the
whole of it, so a `[Break]` or a `[Chorus 2x]` somebody wrote without the `*` is left where it is — the same
question `ChordProNotation` has always asked.

`chordpro/CLAUDE.md`, the `ChordProSyntax` bullet (line 38-39) — already untrue at HEAD and worth correcting in the
same commit, since it names the very rule this plan turns into the gate:

> - `ChordProSyntax` — the shared low-level rules (the directive and chord regexes, `chordNameRegex` for "is this whole
>   word a chord and not a word that starts with a letter", …

There is no `chordNameRegex` anywhere in the repository; that rule is `ChordProChordNames.isChordName`, which is a
linear scan and not a regex, and it lives in its own file.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

**Plan 09, which must land first.** `ChordProChordNames.isChordName("D/f#")` is `false` at HEAD (verified), so a
gate added before 09 would stop transposing every chord with a lowercase bass note — a regression in the same
commit that fixes the labels. Land 09, then this.

## Rules

- Load the `code-style` skill before the first edit: KDoc on declarations, `//` inside statements, "why, not what",
  trailing commas.
- `commonMain` stays JVM-free. `:chordpro` has no dependencies at all and must keep having none.
- A change to the dialect belongs in a test first (`chordpro/CLAUDE.md`, last line).
- The per-module `CLAUDE.md` is part of the change, not a follow-up.
