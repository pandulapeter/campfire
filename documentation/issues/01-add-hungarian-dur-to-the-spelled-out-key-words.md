# Add the Hungarian `dúr` to the words a key can be spelled out in, so `{key: C-dúr}` is transposed and converted

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** sound
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNotationTest.kt`,
`chordpro/CLAUDE.md`

## Problem

A key spelled out in words (`G major`, `Bb-Dur`, `A-moll`) is moved by its note and keeps its words. Whether a key
is one is decided by `ChordProTransposer.spelledOutKeyNoteLength`, which only accepts the words in one set:

```kotlin
private fun spelledOutKeyNoteLength(key: String): Int? {
    if (key.firstOrNull() !in 'A'..'H') return null
    val noteLength = if (key.getOrNull(1)?.let { it in ACCIDENTAL_SIGNS } == true) 2 else 1
    val word = key.substring(noteLength).trimStart { it.isWhitespace() || it == '-' }
    return noteLength.takeIf { word.lowercase() in keyWords }
}
...
private val keyWords = setOf("major", "minor", "maj", "min", "dur", "moll")
```

`moll` is the same word in German and Hungarian, but the major word is spelled with an accent in Hungarian: `dúr`
(`C-dúr`, `B-dúr`). That spelling is not in the set, so `C-dúr` is handed to the chord renamer whole, which is not a
chord name and so is left alone. The app is localized in Hungarian, and `ChordSheetConverter` turns a Hungarian
`Hangnem: C-dúr` line of an imported document into `{key: C-dúr}`, so such keys do reach the library.

`renameKey` is the one place every key rename goes through — the transposition on the model and on the text, the
notation conversion and the library scan (see `chordpro/CLAUDE.md`, `ChordProTransposer` entry) — so all of these
go wrong at once. Verified with a probe test at a5798b31b:

| Call | Result | Expected |
|---|---|---|
| `ChordProTransposer.transposeText("{key: C-dúr}\n[C]a", 2)` | `{key: C-dúr}` (the chord moves to `D`) | `{key: D-dúr}` |
| `ChordProTransposer.transpose(ChordProParser.parse("{key: C-dúr}\n[C]a"), 2).metadata.key` | `C-dúr` | `D-dúr` |
| `ChordProParser.summarize("{key: H-dúr}\n[H]a").metadata.key` (German chart) | `H-dúr` | `B-dúr` |
| `ChordProParser.parse("{key: H-dúr}\n[H]a").metadata.key` | `H-dúr` | `B-dúr` |
| `ChordProNotation.convertText("{key: B-dúr}\n[B]a", STANDARD, GERMAN)` | `{key: B-dúr}\n[H]a` | `{key: H-dúr}\n[H]a` |

The same calls with `Dur` give the expected answers (`B-Dur`, `{key: H-Dur}`). So a Hungarian reader who transposes a
song sees the chords move and the key stay; one who reads in German notation sees `B-dúr` over `H` chords; and the
song list shows a German chart's key in the wrong note.

(The reviewer's probe `summarize("{key: H-dúr}\n[C]a")` is not a valid test of this: with no `H` chord the file is
read as standard notation, and `H-Dur` stays `H-Dur` there too. Use a chart with an `H` chord, as in the table.)

## Fix

In `ChordProTransposer.kt`, add `"dúr"` to `keyWords`:

```kotlin
private val keyWords = setOf("major", "minor", "maj", "min", "dur", "dúr", "moll")
```

`word.lowercase()` already folds `Dúr` / `DÚR`. Nothing else shares the list: `keyOf` (used by `prefersFlats`) only
looks at the root and whether the suffix starts with `m`, which `dúr` does not, so `C-dúr` is already read as major
there. `ChordSheetConverter` keeps a labelled `Hangnem:` value as written and needs no change.

Do **not** touch the lowercase rule: `a-moll` (a lowercase note) is left as written on purpose, and the test
`a lowercase key stays as written` pins it. `c-dúr` stays as written for the same reason.

Update the `ChordProTransposer` entry in `chordpro/CLAUDE.md` so its example list reads
`(`G major`, `A minor`, `Bb-Dur`, `C-dúr`)`, and the KDoc of `renameKey` the same way.

## Tests

In `ChordProTransposerTest`, extend `a key spelled out in words is transposed` (or add a sibling test):

```kotlin
assertEquals("{key: D-dúr}\n[D]a", ChordProTransposer.transposeText("{key: C-dúr}\n[C]a", 2, preferFlats = false))
assertEquals("D-dúr", ChordProTransposer.transpose(ChordProParser.parse("{key: C-dúr}\n[C]a"), 2).metadata.key)
assertEquals("{key: C-Dúr}", ChordProTransposer.transposeText("{key: B-Dúr}", 1, preferFlats = false))
```

and one for the German reading of the key, in `ChordProNotationTest` (next to the existing key tests there) or in
`ChordProTransposerTest`:

```kotlin
assertEquals("B-dúr", ChordProParser.summarize("{key: H-dúr}\n[H]a").metadata.key)
assertEquals("B-dúr", ChordProParser.parse("{key: H-dúr}\n[H]a").metadata.key)
assertEquals("{key: H-dúr}\n[H]a", ChordProNotation.convertText("{key: B-dúr}\n[B]a", ChordNotation.STANDARD, ChordNotation.GERMAN))
```

Run `./gradlew :chordpro:desktopTest`.

## Manual check

On any platform, create a song with `{key: C-dúr}` and a `[C]` chord. In the song details, transpose up two
semitones: the key line reads `D-dúr`. In the editor, use the transpose action: the `{key}` line becomes
`{key: D-dúr}`. Switch Settings to German notation and open a song with `{key: B-dúr}`: the key reads `H-dúr`.
