# Read the first tempo and time signature the song can use, and never a negative capo, as the highlighter already does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProMetadataFields.kt` (optional, see Fix),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProMetadataFieldsTest.kt`, `chordpro/CLAUDE.md`

## Problem

The parser keeps the first non-empty header `{tempo}` / `{time}` and the last integer `{capo}`
(ChordProParser.kt:607-617 and :653 at 8ee010b36):

```kotlin
fun consume(written: String, isInBody: Boolean) {
    if (isInBody && hasHeaderLine) return
    if (!isInBody) hasHeaderLine = true
    if (value.isNullOrEmpty()) value = written          // first non-empty, not first readable
}
…
"capo" -> value.toIntOrNull()?.let { capo = it }       // any Int, negative included
```

while the editor's highlighter marks exactly the values the reader cannot use as `INVALID` ("as good as missing";
ChordProHighlighter.kt:150-155):

```kotlin
directive.name == "time" -> ChordProTime.parse(value) == null
directive.name == "tempo" -> ChordProTempo.parse(value) == null
directive.name == "capo" -> value.toIntOrNull()?.takeIf { it >= 0 } == null
```

Verified with a probe test: `{tempo: Moderato}` + `{tempo: 96}` parses to tempo `Moderato` (so `ChordProTempo.parse`
gives null and the click falls back to the default tempo, and the Song defaults sheet shows "Moderato"); `{time:
waltz}` + `{time: 3/4}` to time `waltz` (no time signature); `{capo: 2}` + `{capo: -1}` to capo `-1` (presentation
coerces it to 0, the PDF prints "Capo: -1"). In each case the editor paints the unusable line red and the usable one
as fine — the opposite of what the song ends up with. The highlighter's KDoc promises "a line is marked exactly when
the song comes out without what it says". The settled rule (first `{tempo}`/`{time}`, last `{capo}` counts) is kept:
this only makes a line the song cannot read not count.

## Fix

In `ChordProParser`:

```kotlin
private class ChangeableValue(private val isReadable: (String) -> Boolean = { true }) {
    …
    fun consume(written: String, isInBody: Boolean) {
        if (isInBody && hasHeaderLine) return
        if (!isInBody) hasHeaderLine = true
        val current = value
        // A value the song cannot read is as good as missing (the editor marks it so), and the next readable one stands in.
        if (current.isNullOrEmpty() || (!isReadable(current) && isReadable(written))) value = written
    }
}
```

with `private val tempo = ChangeableValue { ChordProTempo.parse(it) != null }` and
`private val time = ChangeableValue { ChordProTime.parse(it) != null }`; `key` keeps the default (a key is never
invalid — settled). An empty line is unreadable to both predicates, so it still declares nothing and the "empty line
in the header blocks the body" rule is unchanged; a header with only unreadable lines still yields the first one (and
no tempo), as today. Capo: `"capo" -> value.toIntOrNull()?.takeIf { it >= 0 }?.let { capo = it }` (last readable wins).
`MetadataBuilder` is shared by `parse` and `summarize`/`parseMetadata`, so the library scan follows automatically.

`ChordProMetadataFields.set` (the Song defaults sheet) needs no change to stay correct — it rewrites one header line of
the field and drops the other header lines, so the result is the same text whichever of them it picks. To keep its
KDoc true ("The line the parser reads the value from is rewritten where it stands"), make its `effectiveIndex` for
`TEMPO`/`TIME` prefer the first header line `ChordProTempo.parse`/`ChordProTime.parse` accepts
(`header.declaring().firstOrNull { readable } ?: header.declaring().firstOrNull() ?: …`) and for `CAPO` the last
non-negative one. Recommended, but drop that part if it complicates `set`.

In `chordpro/CLAUDE.md`, where the parser's reading of key/tempo/time is described ("a song that changes key, tempo or
time signature is in the one its first `{key}`, `{tempo}` or `{time}` names"), add: the first one it can read — a
`{tempo}` or `{time}` the highlighter marks invalid counts as missing, and so does a negative `{capo}`.

## Tests

In `ChordProParserTest`: `an unreadable tempo or time signature does not hide a readable one` (`{tempo: Moderato}` +
`{tempo: 96}` → `96`; `{time: waltz}` + `{time: 3/4}` → `3/4`; `{tempo: fast}` alone → `fast`, unchanged; `{tempo:}` +
`{tempo: 96}` → `96`, unchanged; header `{tempo:}` + body `{tempo: 96}` → `""`, unchanged), and `a negative capo is
ignored` (`{capo: 2}` + `{capo: -1}` → 2; `{capo: -1}` alone → null). Check `summarize(text).metadata` agrees. If the
optional `set` change is made, a case in `ChordProMetadataFieldsTest`: setting tempo 100 on `{tempo: Moderato}\n{tempo:
96}` rewrites the `96` line and drops the other.

## Manual check

In the editor, write `{tempo: Moderato}` and `{tempo: 96}` in the header: the first is red, and the song details
screen's tempo stepper reads 96.
