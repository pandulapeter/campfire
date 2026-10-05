# Stop marking `{key}` values the parser keeps and shows as invalid in the editor

**Decided (user, 2026-10-05):** option A.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt` (delete `isSpelledOutKey`; option B changes more),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHighlighterTest.kt`, `chordpro/CLAUDE.md`

Lane A. Independent of 01 and 02.

**Decision taken by default:** option A below (flag only what the parser drops). Option B is the alternative if the user
wants the mark to mean "the transposition cannot move this key".

## Problem

`ChordProHighlighter.isUnreadable` is documented as marking "a directive the parser reads a value out of, holding a value
it then drops … so that a line is marked exactly when the song comes out without what it says", and `chordpro/CLAUDE.md`
says the same ("A directive whose value the parser reads and then drops — a `{time}`, `{tempo}`, `{capo}`, `{duration}`,
`{key}` or `{transpose}` it cannot make sense of … — is one `INVALID` token over the whole line"). For `{key}` it marks
something else:

```kotlin
directive.name == "key" -> !value.isMovedChordName() && !ChordProTransposer.isSpelledOutKey(value)
```

The parser drops no key: `MetadataBuilder.consume` keeps any non-empty value (`"key" -> if (key.isNullOrEmpty()) key =
value`), and the song details screen, the cards, the editor preview and the PDF show it as written. Proven at ed4a1a5ce:
`{key: a-moll}`, `{key: Esz-dúr}`, `{key: D dorian}`, `{key: Gm (Dorian)}` and `{key: Dm (capo 2)}` each tokenize as one
`INVALID` token, while `summarize` reads each as the song's key unchanged. `ChordProHighlighterTest`'s `a directive whose
value cannot be read is marked whole` asserts `{key: Dm (capo 2)}` is `INVALID`.

`a-moll` is the ordinary Hungarian (and lowercase German) spelling of A minor, and Hungarian is one of the app's two
languages, so a Hungarian user filling in the key the way they write it gets an error-coloured line for a value the app
accepts and shows.

(What these keys share is that the transposition cannot move them: `ChordProTransposer.renameKey` leaves them as written,
so the same probe shows each unchanged after transposing by +2. That is a separate limitation of the transposer, not
of the highlighter; see option B.)

## Fix

- **A (recommended).** Remove the `key` branch from `isUnreadable`, so `{key}` falls into `else -> false`, keeping the mark's
  documented contract (the song comes out without what the line says). Remove `{key}` from the list in the KDoc if it is
  named and from the `chordpro/CLAUDE.md` highlighter sentence ("a `{time}`, `{tempo}`, `{capo}`, `{duration}` or
  `{transpose}` it cannot make sense of"). Check whether `isMovedChordName` and the `ChordProTransposer.isSpelledOutKey`
  import are still used elsewhere in the file (`isMovedChordName` is, by `chordsOfShownText` and the value tokens);
  `ChordProTransposer.isSpelledOutKey` is `internal` and called only here, so delete that one-line wrapper too.
- **B.** Keep marking, but make the mark honest and narrower: reword the KDoc and `chordpro/CLAUDE.md` to say `INVALID` on
  a `{key}` means "a key the transposition cannot move, which stays as written whatever the song is transposed to", and
  teach `ChordProTransposer.spelledOutKeyNoteLength` a lowercase root followed by a key word (`a-moll`, `c-dúr`, `fis-moll`)
  so the most common Central European spelling both transposes and stops being marked, without letting the
  lowercase-minor normalization (`ChordProChordNames.lowercaseMinorExpanded`) turn it into `Am-moll`. This touches the
  transposer and the notation conversion of the result (a lowercase `b`/`h` root in German notation), so it is a larger
  change with its own tests in `ChordProTransposerTest`; modes and parentheticals (`D dorian`, `Dm (capo 2)`) stay marked.

## Tests

Option A, in `ChordProHighlighterTest`: move `"{key: Dm (capo 2)}"` from `a directive whose value cannot be read is marked
whole` to `a directive whose value can be read is not marked`, and add `"{key: a-moll}"`, `"{key: Esz-dúr}"` and
`"{key: D dorian}"` there (that test asserts `spans(line).any { it.first == TokenType.INVALID }` is false, so
nothing else is needed). If `ChordProTransposer.isSpelledOutKey` ends up with no caller, delete it (it is `internal` and
used only by the highlighter).

## Manual check

In the editor type `{key: a-moll}` on a header line: it is coloured as an ordinary directive, not as an error. `{time:
5/7}` on the next line is still marked.
