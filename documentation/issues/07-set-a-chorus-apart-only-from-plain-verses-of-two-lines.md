# Open an unheaded chorus only where the song has a plain verse of two lines or more to set it apart from

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetLook.kt` (`unlabelled`),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Review finding L2 (verified by probe). Lane B, plan 5 of 7, after 06 (same function).

## Problem

`ChordSheetLook.unlabelled` (commit 9f6019317) says "a song set wholly in one has nothing to set apart", but tests it
by the existence of **one** plain lyric:

```kotlin
val plain = looks.filterNotNull().filter { !it.isBold && !it.isItalic }.takeIf { it.isNotEmpty() } ?: return emptySet()
val size = plain.map { it.size }.sorted()[plain.size / 2]
```

A song set wholly in italic (or bold) with one plain body-size line, a URL footer, a credit, a `Repeat x2`, turns
every italic stanza into a chorus. Proven: an italic song of two stanzas and a plain `www.example.com/songs` at the
end gives `{title: Song}\n\n{start_of_chorus}\n[G]Somebody [C]told me\nWe were born to sing\n\nHey now you're a star\nGet your game on\n{end_of_chorus}\n\nwww.example.com/songs\n`
(both stanzas in one chorus, since only a change of type ends it).

## Fix

The plain verses must be verses: require a run of two plain lyrics or more within one stanza (the same `breaks`, a
chord row not breaking it), the measure the chorus run is held to. Compute it in the same pass shape as the chorus
runs, before them:

```kotlin
var plainRun = 0
val hasVerse = looks.indices.any { index ->
    if (breaks[index]) plainRun = 0
    val look = looks[index]
    if (look != null) plainRun = if (!look.isBold && !look.isItalic) plainRun + 1 else 0
    plainRun >= 2
}
if (!hasVerse) return emptySet()
```

The size median stays computed over every plain lyric. Update the KDoc ("bold or italic where the song has plain
lyrics" → "…where the song has a plain verse of two lines or more").

Options: (a) recommended, the above; (b) plain lyrics outnumbering the set-apart ones: wrong for a short plain verse
and a long chorus repeated three times.

In `convert/CLAUDE.md`, the "With no heading, a stanza's run…" bullet: "…at the size of the song's plain verses (a
plain run of two lines or more, so a footer or a credit in plain type under an italic song is none)".

## Tests

```kotlin
@Test
fun `a song set wholly in italic with one plain footer line has no chorus`() {
    fun line(text: String, italic: Boolean = true, size: Double = 10.0) =
        ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 6.0, (index + 1) * 6.0, size, isItalic = italic) })
    val lines = listOf(
        line("Song", size = 16.0),
        line("G       C"), line("Somebody told me"), line("We were born to sing"), ChordSheet.Line(emptyList()),
        line("Hey now you're a star"), line("Get your game on"), ChordSheet.Line(emptyList()),
        line("www.example.com/songs", italic = false),
    )
    assertEquals(
        "{title: Song}\n\n[G]Somebody [C]told me\nWe were born to sing\n\nHey now you're a star\nGet your game on\n\nwww.example.com/songs\n",
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
    )
}
```

Fails today (output under Problem). The 9f6019317 test (two plain verse lines before the italic run) and the
`songbook-styled` golden must still pass.
