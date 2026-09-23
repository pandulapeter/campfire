# 06 — A key written as `G major` or `A minor` is never transposed

**Severity:** wrong key shown (all platforms) · **Area:** `:chordpro` (`ChordProTransposer`, `ChordProParser.scan`)

**Read, not run.** Found by reading the transposer at HEAD (2065e47f). The unit tests reproduce it.

## What the user sees

A song whose header says `{key: G major}` (or `A minor`, `Bb major`, `E minor`, `H-Dur`; `a-moll` is out of scope, see
below) transposed +2: every chord moves, but the key in the transposition control and in the song list stays
`G major`. The editor's Transpose action leaves `{key: G major}` in the file while moving every chord under it, so the
file then declares a key the song is no longer in. `{key: G}` / `{key: Am}` work.

## Cause

Every rename of a key goes through `transposeChord`, which returns anything that is not a chord name as it was —
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt:30-33`:

```kotlin
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }
    }
```

`G major` is not a chord name (`ChordProChordNames.isChordName`, `ChordProChordNames.kt:15-36`: `G`, then a space,
which no part of a chord name may be). The model renames the key with it (`rewriteChords` `:71`), and so does the
text (`transposeKeyLine` `:386-389`: `rename(key)`). Yet the same file already understands those words for choosing
the spelling — `keyOf` (`:270-276`):

```kotlin
    private fun keyOf(name: String): Key? {
        val root = ChordProChordNames.notes(name.trim()).first()
        val noteIndex = noteIndices[root.getOrNull(0)] ?: return null
        val accidental = accidentals[root.getOrNull(1)]
        val suffix = root.substring(if (accidental == null) 1 else 2).trimStart()
        return Key((noteIndex + (accidental ?: 0)).mod(NOTE_COUNT), suffix.startsWith("m") && !suffix.startsWith("maj", ignoreCase = true))
    }
```

so `A minor` is taken for a minor key when deciding sharps or flats, and then not moved.

## The change

Invoke the **`code-style`** skill before the first edit. The existing test
`a key that is not a chord name is left alone` (`ChordProTransposerTest.kt:235-239`, `{key: Dm (capo 2)}` stays) must
keep passing, so the rule is narrow: a note, optionally a space or a hyphen, and one of the words a key is spelled
out with.

In `ChordProTransposer.kt`:

```kotlin
    /**
     * [rename] applied to a key: a chord name is renamed whole, and a key spelled out in words (`G major`, `A minor`,
     * `Bb-Dur`) has its note renamed and its words kept. Anything else — `Dm (capo 2)` — is handed to [rename] as it
     * is, which leaves what is not a chord name alone.
     */
    internal fun renameKey(key: String, rename: (String) -> String): String {
        val noteLength = spelledOutKeyNoteLength(key) ?: return rename(key)
        return rename(key.substring(0, noteLength)) + key.substring(noteLength)
    }

    /** The length of the note a key spelled out in words starts with, or null for a key that is not one. */
    private fun spelledOutKeyNoteLength(key: String): Int? {
        if (key.firstOrNull() !in 'A'..'H') return null
        val noteLength = if (key.getOrNull(1)?.let { it in ACCIDENTAL_SIGNS } == true) 2 else 1
        val word = key.substring(noteLength).trimStart { it.isWhitespace() || it == '-' }
        return noteLength.takeIf { word.lowercase() in keyWords }
    }

    private const val ACCIDENTAL_SIGNS = "#b♯♭"
    private val keyWords = setOf("major", "minor", "maj", "min", "dur", "moll")
```

The note has to start with a capital: a lowercase root is how a Central European chart writes a *minor chord*
(`a` = `Am`), and `ChordProNotation.normalized` would expand `a` into `Am` and write `Am-moll`. A key written
`a-moll` is therefore left as it is, as today. `Gmaj` (no space) is a chord name and already works; `G maj` is
handled here.

Use it in the three places a key is renamed:

1. `rewriteChords` (`:71`; `rewriteAt(0).rename` after plan 02):
   `metadata = song.metadata.copy(key = song.metadata.key?.let { key -> renameKey(key, rename) })` — which covers the
   viewer's transposition, the accidentals preference, `ChordProNotation.toGerman` (`B major` shown as `H major`),
   the parser's normalization (`H major` in a German file read as `B major`) and `renderKey`.
2. `transposeKeyLine` (`:386-389`): `rename(key)` → `renameKey(key, rename)`. The value length bookkeeping there
   (`valueEnd - key.length`) is on the original value and is unaffected.
3. `ChordProParser.scan` (`:98-102`) normalizes the key of the summary on its own; for parity with `parse`
   (`ChordProNotation.normalized` → `rewriteChords`):

   ```kotlin
        val key = expandedKey?.let { key ->
            ChordProNotation.withAsciiAccidentals(if (isGermanNotated || isGermanKey) ChordProTransposer.renameKey(key, ChordProNotation::fromGerman) else key)
        }
   ```

   `ChordProNotation.fromGerman(String)` is `internal`, reachable as a function reference from the parser.

A key spelled in words does not *vote* for German notation (`isGermanKey`, `writtenChordNames`) — `isGermanName("H-Dur")`
stays false in both `parse` and `scan`, so the two agree; a German file whose chords use `H` still converts its
`H-Dur` key.

## Tests

`chordpro/src/commonTest`, `./gradlew :chordpro:desktopTest`:

- `ChordProTransposerTest`
  - `a key spelled out in words is transposed` — `transposeText("{key: G major}\n[G]a", 2, false)` ==
    `"{key: A major}\n[A]a"`; `{key: A minor}` +3 → `{key: C minor}`; `{key: Bb-Dur}` +2 → `{key: C-Dur}`;
    `{key: F# minor}` −1 → `{key: F minor}`; on the model, `transpose(parse("{key: G major}\n[G]a"), 2).metadata.key == "A major"`.
  - `a key spelled out in words keeps choosing the spelling` — `prefersFlats(parse("{key: D minor}\n[Dm]a"), 0)` is
    true (it already is; the test pins it).
  - `a lowercase key stays as written` — `{key: a-moll}` +2 unchanged.
  - The existing `a key that is not a chord name is left alone` keeps passing.
- `ChordProNotationTest`: `a key spelled out in words is written in German notation` —
  `ChordProNotation.toGerman(parse("{key: B major}\n[B]a")).metadata.key == "H major"`; and
  `parse("{key: H major}\n[H]a").metadata.key == "B major"` ==
  `ChordProParser.summarize("{key: H major}\n[H]a").metadata.key`.

## Verification

1. Confirm first: `{key: G major}` with a few chords, viewer +2. **Before:** the control and the list say `G major`.
2. After: `A major` in the control; the song list shows it (it goes through `renderKey`).
3. Editor → Transpose +2 → save: the file says `{key: A major}`.
4. German notation on with `{key: B major}`: `H major`.

## Docs

`chordpro/CLAUDE.md`, the `ChordProTransposer` bullet, after "follows the bass note after `/`, understands German `H`,":
add "moves a key spelled out in words (`G major`, `A minor`, `Bb-Dur`) by its note and keeps the words
(`renameKey`), which is also what the notation and the library scan use for the key,".

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`
- `chordpro/CLAUDE.md`

## Depends on

Nothing functionally. Lands **after 02** (both edit the key line of `rewriteChords`) and before 04/08, which edit other
parts of `ChordProParser.scan`.
