# Accept a chord row that ends in a playing note or whose chords carry a footnote star

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`
**Challenged:** amended — stripping stars inside `chord()` would also have changed the public `isChordLine`, the
ChordPro detection, and the parenthesized-chord majority, against step 4; the star is now read only where a chord
row's tokens are. The row's annotation is kept out of the token list every other rule reads. The dots are decided.

## Problem

A row is a chord row only when every token is a chord or furniture:

```kotlin
private fun furniture(value: String) = value in listOf("|", "||", "|:", ":|", "-", "/", "%", "N.C.", "NC", "(", ")") || repeat.matches(value)
private fun chordTokens(line: Rendered): List<Token>? = tokens(line.text).takeIf { words ->
    words.any { chord(it.text) } && words.all { chord(it.text) || furniture(it.text) }
}
```

Ultimate Guitar's rows routinely end in a parenthesized playing note or carry a footnote star, and any such token
makes the whole row a lyric, so its chords are never set and the row sits in the song as text. Probed:

- `G          D        (let ring)`, `Am      (hold)`, `G   D   Em   C   (palm mute)` → lyric rows over unmerged
  lyrics.
- `Em               C*` with `*C played with open G` below → the row is a lyric; the footnote line too (fine).
- `G      D   ...` and `G (fade out)` → lyrics.

## Fix

1. In `chordTokens`, allow one trailing annotation: after at least one chord token, a run of tokens that starts with
   `(` and ends with `)` (or a single `(word)` token), of at most four words, none of them a chord. Return the chord
   tokens and keep the annotation's text apart (a small private `ChordRow(tokens, annotation)` class: the tokens
   `isPositioned`, `isStyledChordLine`, the bare-note guard, and `classify`'s counts read must not include the
   annotation's `(let` / `ring)`, or `isStyledChordLine`'s capital-letter check would fail on it) so that
   `merge` and `bracketed` append it after the line as plain escaped text, set apart by one space: `[G]I walked
   alone [D]until the dawn (let ring)`; a row without lyrics under it → `[G] [D] (let ring)`.
2. A token that is a chord name followed by `*` or `**` is that chord **in a chord row**: a private
   `rowChord(text) = chord(text.trimEnd('*'))` is what `chordTokens` tests its tokens with, and `bracketed`/`merge`
   write the name without the stars. `chord()` itself is untouched: it also decides `isChordLine`, `isChordPro`'s
   bracket count (`[C*]`), `convertsParentheses`, and styled inline runs, none of which this plan means to change. The
   footnote line `*C played with open G` keeps its leading star and stays a lyric.
3. Add `...`, `…`, and `.` to `furniture`. As with the other furniture, `merge` writes only the chords (the dots
   are dropped from a merged line) and `bracketed` writes the row as it reads (`[G] [D] ...`). This does make the
   public `isChordLine("G D ...")` true, which is right for its callers (the Word grid, the shared text's name).
4. `isChordLine` (public, used by `SharedTextName.firstPlainLine` and the Word grid detection) keeps its strict
   meaning; only `chordTokens` learns the annotation.

`convertsParentheses` must not count the annotation as a parenthesized chord: it already only counts parentheses
whose content is a chord, so `(let ring)` is harmless, but add the case to the test.

## Tests

- `a chord row may end in a parenthesized playing note`: the three UG rows above over lyrics → chords set, note kept
  at the end of the merged line; `Am (hold)` alone → `[Am] (hold)`.
- `a starred chord is the chord`: `Em  C*` over a lyric → `[Em]… [C]…`; the footnote line `*C played with open G`
  stays a lyric line.
- `trailing dots are furniture`: `G  D ...` over a lyric → both chords set, no dots written; alone → `[G] [D] ...`.
- `isChordLine` stays strict about stars and notes: `isChordLine("Em C*")` and `isChordLine("G D (let ring)")` are
  false.
- `a parenthesized note does not make the sheet one of parenthesized chords`: the row above plus lyrics with `(Am)`
  inside them in a minority → the inline `(Am)` stays text.
