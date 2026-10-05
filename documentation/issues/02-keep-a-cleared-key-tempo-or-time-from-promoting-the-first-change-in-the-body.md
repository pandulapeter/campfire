# Keep a cleared key, tempo or time signature from promoting the song's first mid-song change to its default

**Decided (user, 2026-10-05):** option A.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProMetadataFields.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt` (only if the body-start helper is
put there),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProMetadataFieldsTest.kt`,
`chordpro/CLAUDE.md`

Lane A, after 01 (which makes `TEMPO` and `TIME` behave like `KEY` in the same two places). If 01 was not applied, do
this for `KEY` alone.

**Decision taken by default:** fix it in the parser and in `set` (option A below). Drop this plan if the user prefers
option B (leave it and document it).

## Problem

Clearing the key in the Song defaults sheet of a song that modulates gives the song the key it modulates *to*.
`ChordProMetadataFields.set` drops the first declaring `{key}` line and keeps every later one:

```kotlin
index !in indices || (field == Field.KEY && index != effectiveIndex) -> kept += line
index == effectiveIndex && newValue != null -> kept += ...   // a null newValue drops the line
```

and the parser takes the first non-empty `{key}` anywhere in the file:

```kotlin
"key" -> if (key.isNullOrEmpty()) key = value
```

Proven at ed4a1a5ce: `{title: T}\n{key: G}\n{time: 4/4}…[C]la\n{key: A}…[G]la` with `set(KEY to null)` summarizes with key
`A`, and leaving an empty line in place does not help either: `{key: }\n[C]la\n{key: A}\nla` summarizes with key `A`,
since an empty `{key}` "takes back nothing another line said". The existing test codifies the behaviour:

```kotlin
assertEquals("{title: T}\n[G]La\n{key: A}\n[A]La", ChordProMetadataFields.set(text, mapOf(Field.KEY to "")))
```

After 01 the same holds for `TEMPO` and `TIME`: clearing the tempo of a song with a mid-song `{tempo: 140}` makes 140 the
song's tempo, which the click then plays from the first bar. The user asked for "no declared value" and got the bridge's.

## Fix

Options:

- **A (recommended).** Tell the song's value from a mid-song change by *where* the line stands, which the parser already
  knows for `{transpose}` (its `Transposition.startBody`: the body begins at the first line that is not blank or a `#`
  comment, the first `{start_of_…}`, or the first directive that makes a block):
  - **Parser.** For `key`, `tempo` and `time`, a line in the header (before the body begins) is the song's: the first
    non-empty one of them counts. A line in the body counts only where *no* line of that field at all — an empty one
    included — stood in the header; it is then the song's (a file that writes its metadata at the bottom keeps working).
    So an empty header `{key: }` now means "no key declared" against a body modulation, while the template's empty line
    followed by a filled one in the header still reads the filled one. Implementation: give `MetadataBuilder.consume`
    an `isInBody: Boolean` parameter (passed through the `meta` recursion), make `Transposition.isInBody` readable
    (`var isInBody = false; private set`), and pass `transposition.isInBody` from both call sites (`scan`'s
    `metadata.consume(directive)` and `handleDirective`'s `else -> metadata.consume(directive)`; both already call
    `startBody()` for an environment or block directive before reaching it). Track a `hasHeaderLine` flag per field.
  - **`ChordProMetadataFields.set`**, for the three fields that change in the body (01's `isChangedInTheBody`): find
    the body start of `lines` with the same rule (a small helper; put it in `ChordProSyntax` next to
    `metadataInsertionIndex` if the parser can share it, else private here, and make it skip selector-suffixed
    directives as the parser does). The line rewritten is the one the parser reads: the first declaring header line,
    else the first header line of the field (the template's empty one), else the first declaring body line, else none
    (insert into the header as today — `metadataInsertionIndex` is safe there, since no line of the field exists).
    Other header lines of the field are dropped (the parser reads past them); body lines are always kept. **A blank
    value** removes the rewritten line as today *unless* it is a header line and a declaring body line of the field
    remains, in which case the line is kept as an empty directive in its own spelling (`rewritten(field, "")` gives
    `{key: }`, `{meta: key }`) so the body line stays a change. Clearing a value that only a body line declares (no
    header line) still drops that line and promotes the next body one — a file with no header to keep an empty line in;
    say so in the KDoc.
- **B.** Leave the behaviour and document in `chordpro/CLAUDE.md` that clearing a field whose song changes it mid-song
  makes the first change the song's value. Cheapest; the Song defaults sheet then shows the promoted value after the save,
  which at least tells the user.

Side effect of A to note in the docs: a `{key}`, `{tempo}` or `{time}` typed in the body of a song whose header still
holds the template's empty line of that field is a change, not the song's value (it used to be the song's). Filling the
field in Song defaults fills the header line, as it does today.

Docs (option A): in `chordpro/CLAUDE.md`'s parser bullet, after "a song that changes key, tempo or time signature is in
the one its first … names" (01's wording) add "— the first in the header, a line in the body counting only for a song
whose header has no line of that field at all, an empty one included, which is how a cleared value stays cleared"; and in
the `ChordProMetadataFields` bullet describe the header/body split and the empty line kept on clearing. Update the KDoc
of `set` and of `MetadataBuilder.consume`'s `key` comment to match.

## Tests

`ChordProParserTest`: `{key: }\n[C]la\n{key: A}\nla` reads no key (null or blank, as `valueOf` treats it); `{key: }\n{key:
G}\n[C]la` reads `G`; `[C]la\n{key: G}` reads `G`; the same three for `tempo` and `time`, through `parse`, `summarize`
and `parseMetadata`. `{transpose}` tests must still pass (shared body tracking).

`ChordProMetadataFieldsTest`: replace the clearing assertion in `the key is set on the line the song starts in and leaves
its modulations alone` with `"{title: T}\n{key: }\n[G]La\n{key: A}\n[A]La"`, and assert that its summary has no key; a song
with no later `{key}` still loses the line entirely (`a blank value removes the field` stays as is); a header duplicate
`{tempo: 90}\n{tempo: 100}\nLa` set to `120` gives `{tempo: 120}\nLa`; `[C]la\n{key: G}` set to `A` gives `[C]la\n{key: A}`;
a body-start helper test asserting it agrees with the parser on the cases above (a `#` comment, a blank line, a
`{start_of_verse}`, a `{c: …}` and a lyric line each starting or not starting the body).

## Manual check

Song with `{key: G}` in the header and `{key: A}` before the last chorus: open Song defaults, clear the key, save. The song
details screen shows no key, and the file holds `{key: }` in the header and `{key: A}` where it was. Repeat with a
mid-song `{tempo: 140}`: after clearing the tempo, the song card and the song's first section show no tempo of the file's
own, not 140.
