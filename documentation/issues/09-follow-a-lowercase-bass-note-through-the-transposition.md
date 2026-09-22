# 09 — Follow a lowercase bass note through the transposition

## What the user sees

A chart written the Central European way, where a minor chord is its root in lowercase, also writes the bass note
after the `/` in lowercase: `D/f#`, `A/c#`, `C/h`. Transposing such a song moves the root and leaves the bass where
it was, so every slash chord in the song becomes a chord nobody wrote:

```
[D/f#] +2  ->  [E/f#]      (should be [E/g#])
[A/c#] +2  ->  [B/c#]      (should be [B/d#])
```

The same in a grid: `{start_of_grid}` with `| D/f# | G |` at +2 comes out as `| E/f# | A |`.

And a chord whose *root* is lowercase as well is not touched at all — `[d/f#]` at +2 stays `[d/f#]`, while the
`[a]` next to it correctly becomes `[b]`. The bass note is what breaks the recognition of the whole name.

The user is left with a song in the new key whose bass notes are in the old one, written to disk if it was the
editor's transpose action.

## Cause

`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt:75-78`, verified at HEAD
`984861e4`:

```kotlin
    private fun noteEnd(name: String, index: Int): Int {
        if (name.getOrNull(index) !in 'A'..'H') return -1
        return if (name.getOrNull(index + 1)?.let { it in "#b♯♭" } == true) index + 2 else index + 1
    }
```

`noteEnd` accepts capitals only, and it is the one answer both the split and the recognition ask.

`ChordProChordNames.kt:38-42` — the split:

```kotlin
    fun notes(name: String): List<String> {
        val chord = unwrapped(name)
        val separator = chord.lastIndexOf('/')
        return if (separator >= 0 && noteEnd(chord, separator + 1) >= 0) listOf(chord.substring(0, separator), chord.substring(separator + 1)) else listOf(chord)
    }
```

For `D/f#` the `noteEnd` at the character after the slash is `-1`, so the name is **not** split: `rewriteNotes`
hands the whole `"D/f#"` to `transposeNote`, which reads `D` and carries `"/f#"` along as the chord's suffix. The
root moves, the bass rides along unchanged.

`ChordProChordNames.kt:26-33` — the recognition, same call:

```kotlin
                '/' -> {
                    val digits = digitsEnd(name, index + 1)
                    if (digits > index + 1) index = digits else return noteEnd(name, index + 1) == name.length
                }
```

so `isChordName("D/f#")` is **false** today (verified: `D/F#` yes, `D/f#` no, `A/c#` no, `Am7/G` yes).

That is also what breaks the lowercase *root*. `lowercaseMinorExpanded`, `:55-63`, expands `d/f#` to `Dm/f#` and
then asks:

```kotlin
        if (!isChordName(expanded)) return null
```

`isChordName("Dm/f#")` is false for the same reason, so the expansion is refused and
`ChordProTransposer.keepingLowercaseMinors` (`ChordProTransposer.kt:142-144`) falls through to
`rename(name)` on the raw `"d/f#"`, whose first character is not in `noteIndices` — nothing happens.

This sits immediately next to the recent lowercase-minor work (`ChordProChordNames.lowercaseMinorExpanded` /
`lowercaseMinorFolded`, and `ChordProTransposer.keepingLowercaseMinors`), which is deliberate and must survive:
`isChordName("a")` has to stay **false**, or every `a` and `b` in the lyrics of a tab chord row becomes a chord.
Only the note *after a slash* may be lowercase.

Reproduced at HEAD against the compiled desktop classes:

| input | call | output |
| --- | --- | --- |
| `[D/f#] [d/f#]` | `transposeText(…, 2, null)` | `[E/f#] [d/f#]` |
| `{start_of_grid}\| D/f# \| G \|` | `transposeText(…, 2, null)` | `\| E/f# \| A \|` |
| `[d/f#]e` | `transpose(parse(…), 2, null)` | chord `d/f#` |
| `D/f#` | `isChordName` | `false` |

## The change

Two edits in `ChordProChordNames`, plus the case-folding that keeps the file's own spelling. Nothing outside that
file changes.

### 1. A bass note may be lowercase

Add a second entry point next to `noteEnd`, so that the root's rule stays exactly as strict as it is:

```kotlin
    /**
     * The same as [noteEnd] for the note after a `/`. A chart that writes its minor roots in lowercase writes the
     * bass note that way too (`D/f#`, `C/h`), and unlike a root there is nothing a lowercase letter there could be
     * instead: the slash has already said a note follows. The root stays capitals only, since a lowercase one is
     * how the same charts write a minor chord, see [lowercaseMinorExpanded].
     */
    private fun bassNoteEnd(name: String, index: Int): Int {
        val letter = name.getOrNull(index) ?: return -1
        if (letter.uppercaseChar() !in 'A'..'H') return -1
        return if (name.getOrNull(index + 1)?.let { it in "#b♯♭" } == true) index + 2 else index + 1
    }
```

and call it from the two places that look after a slash — `isChordName`'s `'/'` branch (`:29`) and `notes` (`:41`):

```kotlin
                '/' -> {
                    val digits = digitsEnd(name, index + 1)
                    if (digits > index + 1) index = digits else return bassNoteEnd(name, index + 1) == name.length
                }
```

```kotlin
        return if (separator >= 0 && bassNoteEnd(chord, separator + 1) >= 0) listOf(chord.substring(0, separator), chord.substring(separator + 1)) else listOf(chord)
    }
```

`uppercaseChar()` rather than a second range test, because `'b'` is both a note letter and a flat sign and the
existing accidental set already spells that ambiguity out one line below; keeping one expression means the two
cannot drift.

### 2. Fold the case back after the rewrite

`rewriteNotes` is the single place every rewriter — the transposition, `ChordProNotation.noteToGerman` and
`noteFromGerman` — goes through, so the folding belongs there and nowhere else.
`ChordProChordNames.kt:44-47` becomes:

```kotlin
    fun rewriteNotes(name: String, rewrite: (String) -> String): String {
        val rewritten = notes(name).joinToString("/") { note -> rewriteNote(note, rewrite) }
        return if (isParenthesized(name)) "($rewritten)" else rewritten
    }

    /**
     * [rewrite] applied to one note of a chord name, in capitals however the file spells it, and folded back to the
     * case it was written in. Every rewriter here knows the capital letters only, and a chart that writes `D/f#`
     * has to get `E/g#` back rather than `E/G#`: the file keeps its own convention, the same way
     * [ChordProTransposer.transposeText] keeps a lowercase minor root.
     */
    private fun rewriteNote(note: String, rewrite: (String) -> String): String {
        val letter = note.firstOrNull() ?: return rewrite(note)
        if (!letter.isLowerCase()) return rewrite(note)
        val rewritten = rewrite(letter.uppercaseChar() + note.substring(1))
        val first = rewritten.firstOrNull() ?: return rewritten
        return first.lowercaseChar() + rewritten.substring(1)
    }
```

With that, `transposeChord("D/f#", 2, preferFlats = false)` splits into `D` and `f#`, transposes `F#` to `G#` and
writes back `g#`: `D/f# -> E/g#`. And `lowercaseMinorExpanded("d/f#")` now gets a `true` out of
`isChordName("Dm/f#")`, so `keepingLowercaseMinors` expands, transposes and folds the whole thing:
`d/f# -> e/g#`.

### 3. What this makes true elsewhere, for free

- **German notation.** `ChordProNotation.toGerman("C/h")` currently refuses the name at its `isChordName` gate
  (`ChordProNotation.kt:38`); now it accepts it, uppercases the bass to `H` in `rewriteNote`, maps it to `B` and
  folds back to `b`. Check `ChordProNotation.isGermanName` (`:54-56`) while implementing: it asks
  `notes(chord).any { it.startsWith(GERMAN_B_NATURAL) }` with an uppercase `"H"`, so a song whose only `H` is a
  lowercase bass note (`C/h`) still does not mark the file as German-notated. Decide and **write down** which it
  should be. Recommended: make it `any { it.uppercase().startsWith(GERMAN_B_NATURAL) }` — a chart that writes
  `C/h` is as German as one that writes `H7`, and leaving it out would read that file's `B` chords as B natural.
- **`isWrittenInFlats`** (`ChordProTransposer.kt:274-286`) counts accidentals per note and tests
  `noteIndices.containsKey(note.getOrNull(0))`, which is capitals only. A lowercase bass note still does not vote.
  Leave it: the root of every chord votes, which is enough, and plan 08 narrows that list further.

### What this does *not* change

`isChordName("a")`, `isChordName("h7")`, `isChordName("f#")` all stay **false**. A lowercase *root* is still read
only through `lowercaseMinorExpanded`, which is what keeps a chord row of a tab from reading the words `a` and
`b` as chords.

## Tests

`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNamesTest.kt`, next to
`the bass follows the last slash` (line 35) and `a lowercase root is a minor chord` (line 41):

1. `a bass note may be written in lowercase` — `isChordName("D/f#")`, `isChordName("A/c#")`, `isChordName("C/h")`,
   `isChordName("Dm/f#")` all true; `notes("D/f#") == listOf("D", "f#")`.
2. `a lowercase root is still not a chord on its own` — `isChordName("a")`, `isChordName("h7")`,
   `isChordName("f#")`, `isChordName("break")` all false. This is the guard on the rule above.
3. `a lowercase minor with a lowercase bass expands` — `lowercaseMinorExpanded("d/f#") == "Dm/f#"`.

`ChordProTransposerTest.kt`, next to `the root note and the bass note are both transposed` (line 39) and
`lowercase minors are transposed and keep their spelling in the text` (line 160):

4. `a lowercase bass note is transposed and stays lowercase` —
   `assertEquals("E/g#", ChordProTransposer.transposeChord("D/f#", 2, preferFlats = false))` and
   `assertEquals("[E/g#] [e/g#]", ChordProTransposer.transposeText("[D/f#] [d/f#]", 2, preferFlats = false))`.
5. `a lowercase bass note round-trips` — the same text up 2 and back down 2 is the input, byte for byte.
6. `a grid cell with a lowercase bass note is transposed` —
   `"{start_of_grid}\n| D/f# | G |\n{end_of_grid}"` at +2 gives `| E/g# | A |`.
7. `a lowercase bass note is transposed on the model` — through `transpose(ChordProParser.parse("[D/f#]a"), 2)`.
8. `a capitalised bass note stays capitalised` — the existing `Am7/G -> Bm7/A` must not start writing `a`.

`ChordProNotationTest.kt`:

9. `a lowercase bass note is read and written in German notation` — `toGerman("C/b")` is `C/b`… decide the exact
   expectations from the rule in section 3 and assert both directions (`toGerman`, `fromGerman`) plus
   `isGermanName("C/h")`.

`ChordProSerializerTest.kt` — run it: `parse(serialize(parse(x))) == parse(x)` now covers a name shape it did not
before.

## Verification

```
./gradlew :chordpro:desktopTest
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

Manual, on any one platform (`./gradlew :app:desktop:run`):

1. Write a song with `[D/f#]`, `[d/f#]`, `[A/c#]` and `[Am7/G]`, transpose it up and down in the viewer: the bass
   notes follow the roots and keep their case.
2. Do the same with the editor's transpose action and check the saved file.
3. With the German notation preference on, open a song using `[C/h]`: it must read as `C/H` and transpose as
   `C/B`.

## Docs

`chordpro/CLAUDE.md`, the `ChordProNotation` bullet (lines 147-150) — this sentence says which lowercase spellings
the module understands, and now has to name the bass note too:

> The same charts often
> write a minor chord as its root in lowercase (`a` for `Am`, `h` for `Hm`), and that is read as the minor chord it
> stands for, in either notation; a lowercase `h` marks a song as German like an uppercase one. The model spells them
> out; the editor's transposition keeps the file's lowercase.

Add: the note after a `/` may be written in lowercase for the same reason (`D/f#`), and it is transposed and
respelled like any other note and folded back to the case the file used. If section 3's recommendation is taken,
say that a lowercase `h` in the bass marks a song as German as well.

`chordpro/CLAUDE.md`, the `ChordProTransposer` bullet (line 105) already says "follows the bass note after `/`" —
that sentence becomes true for the first time for a lowercase one and needs no rewording, but read it again after
the change to make sure it still is the whole truth.

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNames.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotation.kt` (only if section 3's
  `isGermanName` recommendation is taken)
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProChordNamesTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing. **Plan 08 depends on this one** — its gate is `isChordName`, which does not accept `D/f#` until this
lands — so this goes first.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free; `:chordpro` has no dependencies at all.
- A change to the dialect belongs in a test first.
- The per-module `CLAUDE.md` is part of the change.
