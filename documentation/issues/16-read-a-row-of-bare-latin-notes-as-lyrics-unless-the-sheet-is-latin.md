# Read a row of bare Latin note names ("La La La", "Do Re Mi") in a converted sheet as lyrics unless the sheet otherwise writes Latin chords

**Challenged:** amended — a bare-note row whose tokens stand two or more spaces apart stays a chord row, so an all-major Latin sheet (I–IV–V campfire songs) is not turned into lyrics; test added.
**Kind:** bug (import heuristic)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`, `chordpro/CLAUDE.md`

## Problem

`ChordSheetConverter` takes a Latin name as a chord anywhere:

```kotlin
private fun chord(value: String): Boolean {
    val name = ascii(value).removeSurrounding("(", ")")
    return isChordOrLatinName(name) ||
        name.firstOrNull() in 'a'..'h' && ChordProChordNames.isChordName(name.replaceFirstChar { it.uppercase() })
}
…
private fun isChordOrLatinName(name: String) = ChordProChordNames.isChordName(name) || ChordProChordNames.latinExpanded(name) != null
```

and in `convertSong` a candidate row with more than one chord token is a chord row
(`candidates[index]!!.count { chord(it.text) } > 1`), which also sets `hasUnambiguousChords` for the whole sheet. So
a sung line written in title case — "La La La", "Do Re Mi", "Si Si" — imported from plain text, PDF or Word becomes a
row of chords and its words are lost into brackets over the next line. Probe at dac1d9d59:

- `"La La La\nque bonita"` → `[La]que [La]bo[La]nita`;
- `"Am C\nHello world\nLa La La\nque bonita"` → `[Am]Hel[C]lo world\n[La]que [La]bo[La]nita` (an English sheet).

`ChordSheetConverterTest` pins the opposite for one input, `convert("Do Re Mi\n1 4 5")` == `"[Do]1 [Re]4 5 [Mi]\n"`.

## Fix

A row whose chord tokens are all **bare** Latin notes (the note with nothing after it: `Do`, `Re`, `Mi`, `Fa`,
`Sol`, `La`, `Si`, their accented and capitalised spellings — i.e. `ChordProChordNames.latinNoteLength(token) ==
token.length`) is a chord row only where the same sheet writes a Latin chord that says more than a note — a quality,
a number, an accidental or a bass (`Lam`, `Sol7`, `Fa#`, `Re/Fa#`): a Romance-language sheet of nothing but bare
major triads is rare, while a lyric of note words is not.

In `convertSong`:

```kotlin
// A row of bare note words is as likely a sung "La la la" as a row of major chords, so it counts as chords only in a
// sheet whose other rows show it is written in Latin.
val hasLatinChords = lines.any { line -> tokens(line.text).any { isLatinChordBeyondNote(it.text) } }
val candidates = lines.map(::chordTokens).map { row ->
    row?.takeUnless { !hasLatinChords && it.filter { word -> chord(word.text) }.all { word -> isBareLatinNote(word.text) } }
}
```

That alone would turn the commonest Latin sheet there is — a three-chord campfire song, `Do      Fa      Sol` over
its lyric with no minor anywhere — into lyrics, so a bare-note row is also kept as chords where it is **positioned**:
where two of its tokens stand two or more spaces apart (`row.zipWithNext().any { (a, b) -> b.index - (a.index + a.text.length) >= 2 }`,
`Token.index` already holds each token's column), which is how chords are set over the syllables they fall on and
how a sung line is never written. So the condition becomes
`!hasLatinChords && !isPositioned(row) && it.filter { … }.all { … isBareLatinNote … }`. A row of single-spaced bare notes
(`La La La`, `Do Re Mi`) is still lyrics in a sheet with no richer Latin chord.

with `isBareLatinNote(t) = ascii(t).removeSurrounding("(", ")").let { ChordProChordNames.latinNoteLength(it) == it.length }`
and `isLatinChordBeyondNote(t) = ascii(t).removeSurrounding("(", ")").let { ChordProChordNames.latinExpanded(it) != null && !isBareLatinNote(it) }`.
The `chordTokens(it) == null` checks on the neighbouring line in the `Kind.CHORD` condition keep calling
`chordTokens`, which is fine (a lyric there is still a lyric).

Drop-this-plan option (a decision, see the report): keep today's behaviour, since a bare-note row is ambiguous by
construction, as the bare tuning line `E A D G B E` is documented to be.

Not part of this plan: the same ambiguity inside a `{start_of_tab}` block (`ChordProTabTransposer.chordWords` taking
"La La La" as a chord row, which a standard-to-standard `convertText` then rewrites to `A  A  A`). A lyric inside a tab
environment is outside what that environment holds, and `chordpro/CLAUDE.md` already documents a bare note row there
as chords ("a bare `E A D G B E` is six chord names and is transposed as such").

`chordpro/CLAUDE.md`, the `ChordSheetConverter` paragraph, after "a line of a single chord is one where …": add
"a row of bare Latin note names (`La La La`) is one only in a sheet that writes some Latin chord with more than its
note, or where its names stand apart the way chords are set over a lyric, since otherwise it is as likely sung".

## Tests

`ChordSheetConverterTest`:
- `convert("La La La\nque bonita")` == `"La La La\nque bonita\n"` (escaped prose, or whatever the lyric path writes;
  assert no `[`);
- `convert("Am C\nHello world\nLa La La\nque bonita")` keeps `La La La` as a lyric line;
- replace the pinned `convert("Do Re Mi\n1 4 5")` by an input with Latin evidence, e.g. `convert("Lam Re Mi\n1 4 5")`,
  and assert it is still converted into bracketed chords (take the exact string from a run);
- `convert("Do Sol Lam\ncanta\nLa La La\nque bonita")` reads both rows as chords (the sheet is Latin);
- a positioned all-major sheet keeps its chords: `convert("Do        Fa        Sol\nCielito lindo, de la sierra")`
  holds `[Do]`, `[Fa]` and `[Sol]` and no `Do        Fa` lyric line (take the exact string from a run).

## Manual check

Import a `.txt` file holding "Am C\nHello world\nLa La La\nque bonita": the song shows "La La La" as a lyric line under
the first one.
