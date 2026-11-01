# Take the titles' type from a later title than the document's first only where that type stands out by its size

**Challenged:** amended — a body-size candidate is still taken where the contents list two of its openings or more. As written, a book of body-size bold titles under a larger cover with a contents page lost its titles' type (`byType` false), so the contents alone split it and the type filter the documented artist index relies on ("in a book with a title type, in that type") no longer applied: each song would also be cut at its credit line. With the contents check, `songbook-numbered` stays on the code path it takes today rather than reaching the same output another way.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongSplit.kt` (`reference`),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`;
check only: `data/source/local/implementation/src/desktopTest/resources/document/songbook-numbered.cho` and
`songbook-styled.cho` (`DocumentGoldenTest`), which must not change

Review finding F4. Lane B, plan 6 of 7; independent of 03–07.

## Problem

Commit cdadecb14 lets a cover in a type of its own leave the split to "the first title whose type opens two songs of
different titles" (`ChordSheetSongSplit.reference`):

```kotlin
private fun reference(titles: List<Lead>, medianSize: Double): Lead? {
    val first = titles.firstOrNull() ?: return null
    if (titles.count { opensSong(it, first, medianSize) } >= 2) return first
    return titles.firstOrNull { candidate ->
        titles.filter { opensSong(it, candidate, medianSize) }.distinctBy { words(it.line.text) }.size >= 2
    } ?: first
}
```

A single song whose title is set larger and whose later pages open with a bold line of the body's size (`Repeat
Chorus`, `Middle eight`, `Last verse`; `opensSong` takes a body-size bold lead not followed by bold lyrics) finds two
such leads of different words, and that type becomes the titles' type: the song is split into three, titled `Repeat
Chorus` and `Middle eight`. Proven with a 14 pt bold title, 10 pt bold chord rows and plain lyrics: `{title: My Song}…`,
`{new_song}\n{title: Repeat Chorus}…`, `{new_song}\n{title: Middle eight}…`.

## Fix

A body-size bold line is how a sheet sets a chorus or a part's name as often as a title; the first title's own test
(`count >= 2`, kept) already admits it where the document opens with that type. The fallback, which looks past the
first title, takes a type that stands out by its size or is a heading, or, of the body's size, one whose openings the
contents list (a part name is never in a contents page):

```kotlin
private fun reference(titles: List<Lead>, medianSize: Double, listed: Set<Place>): Lead? {
    val first = titles.firstOrNull() ?: return null
    if (titles.count { opensSong(it, first, medianSize) } >= 2) return first
    return titles.firstOrNull { candidate ->
        val opened = titles.filter { opensSong(it, candidate, medianSize) }
        val standsOut = candidate.line.line.isHeading || size(candidate.line.line) > medianSize * 1.2
        (standsOut || opened.count { it.place in listed } >= 2) && opened.distinctBy { words(it.line.text) }.size >= 2
    } ?: first
}
```

In `cuts`, compute `val index = ChordSheetIndexTitles.of(pages, ::key)` before `reference` (it does not depend on it)
and pass `index?.titles.orEmpty()`.

`songbook-numbered.pdf` (20 pt cover, bold 10 pt numbered titles each at the top of its page, a contents page) keeps
today's path: the contents' titles are those page leads, three of which open a song by type, so the bold 10 pt type is
still the titles' type. Verify its golden output is unchanged.

Options: (a) recommended, the above; (b) the size test alone: a book of body-size bold titles under a larger cover with
contents is split by its contents alone, and an artist index then cuts each song at its credit too; (c) keep
body-size candidates but require three distinct openings: a long song with three part names breaks again; (d) keep
them where their openings are numbered rising (`numbers`): narrower, and a numbered book usually has contents anyway;
(e) look past the first title only where its page holds no chord row (a cover, not a song): keeps a body-size songbook
with a cover and no contents split, but a lyrics-only song (no chord rows anywhere) with bold part names still splits.

Known limits: a songbook of body-size bold titles under a larger cover with no contents page is no longer split (D2);
and, older than cdadecb14, a song whose **title** is bold at the body's size and whose later pages lead with bold part
names still splits, through the `count >= 2` test.

In `convert/CLAUDE.md`, the split bullet: "The titles' type is the first one opening two songs of different titles" →
"…the first one opening two songs of different titles that stands out by its size, or whose openings the contents
list".

## Tests

```kotlin
@Test
fun `a song whose later pages open with bold part names of the body's size stays one song`() {
    fun line(text: String, bold: Boolean = false, size: Double = 10.0) =
        ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 6.0, (index + 1) * 6.0, size, isBold = bold) })
    val chords = line("G       C", bold = true)
    fun page(vararg lines: ChordSheet.Line) = ChordSheet.Page(lines.toList())
    val result = ChordSheetConverter.convert(
        ChordSheet(
            listOf(
                page(line("My Song", bold = true, size = 14.0), chords, line("Somebody told me"), chords, line("We were born to sing")),
                page(line("Repeat Chorus", bold = true), chords, line("Hey now you're a star"), chords, line("Get your game on")),
                page(line("Middle eight", bold = true), chords, line("It's a cool place"), chords, line("And it gets colder")),
            ),
        ),
    )
    assertEquals(1, result.size, result.joinToString("{new_song}\n"))
    assertTrue(result.single().startsWith("{title: My Song}"), result.single())
    assertTrue("[G]It's a [C]cool place" in result.single(), result.single())
}
```

Fails today (three songs). `a cover set in a type of its own leaves the split to the type the song titles share`
(2.0 titles over a 1.0 body) and the other cdadecb14 tests must still pass.
