# Take no page whose repeated lines each recur on three pages or more, a chorus, for a contents page

**Kind:** bug  ·  **Severity:** medium-high  ·  **Platforms:** all (PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetIndexTitles.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`;
check only: `songbook-numbered.cho` and `songbook-styled.cho` under
`data/source/local/implementation/src/desktopTest/resources/document/` (`DocumentGoldenTest`), which must not change

Review finding F5. Lane B, plan 7 of 7, after 08 (same split, separate file).

## Problem

Commit cdadecb14 finds a contents page as one whose title-shaped lines are mostly found on other pages
(`ChordSheetIndexTitles.of`):

```kotlin
val indexPages = entries.indices.filter { pageIndex ->
    val matched = entries[pageIndex].filter { entry -> pagesByKey.getValue(entry.key).any { it != pageIndex } }
    matched.size >= 3 && matched.size * 2 >= entries[pageIndex].size &&
        matched.flatMap { entry -> pagesByKey.getValue(entry.key) - pageIndex }.toSet().size >= 3
}.toSet()
```

In a lyrics-only song (no chord rows, so every lyric is title-shaped) whose four-line chorus is printed on every page,
a page of a short verse and the chorus has half its lines found on three other pages: it is a contents page. A page
with a longer verse is not, and there the chorus lines, "listed" by the contents pages and standing under no chord row,
become song titles: the song is shredded, each chorus line a song of its own. Proven with four pages (three verse lines
+ the chorus on three of them, five verse lines + the chorus on the last): five songs, the last four
`Hey now you're an all star`, `Get your game on, go play`, `Hey now you're a rock star`, `Get the show on, get paid`.

## Fix

A contents entry names one song, found on its own page (twice at most: a title and a lyric singing it, or a page
repeating it); a chorus line recurs on every page the chorus is printed on. Require most of a contents page's matched
lines to be found on one or two other pages each:

```kotlin
val pointed = matched.count { entry -> (pagesByKey.getValue(entry.key) - pageIndex).size <= 2 }
matched.size >= 3 && matched.size * 2 >= entries[pageIndex].size && pointed * 2 > matched.size &&
    matched.flatMap { entry -> pagesByKey.getValue(entry.key) - pageIndex }.toSet().size >= 3
```

The existing cases keep it: in `a songbook in one type is split where its contents list a title…`, Alpha is on one
other page, Beta on two (its title and the lyric singing it), Gamma on one; each fixture's titles are on one page.
Update the KDoc ("a chorus printed again, points at one page alone" → add "…, and a chorus's lines each recur on every
page it is printed on, where an entry names one or two").

Options: (a) recommended, the above; (b) require the matched entries' first pages to differ (a chorus's lines all
first recur on one page): breaks on a chorus whose wording varies, and on two songs to a page; (c) require the entries
in page order: true of most contents but not of an alphabetical index.

In `convert/CLAUDE.md`, the contents bullet: "most of its lines, at least three, found again on three other pages or
more" → "…found again, each on one or two other pages (a chorus's lines recur on every page it is printed on), and on
three other pages or more together".

## Tests

```kotlin
@Test
fun `a lyrics-only song whose chorus is printed on every page is not taken for a songbook with contents`() {
    fun line(text: String, size: Double = 10.0) = ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 6.0, (index + 1) * 6.0, size) })
    val chorus = listOf("Hey now you're an all star", "Get your game on, go play", "Hey now you're a rock star", "Get the show on, get paid").map { line(it) }
    fun page(vararg verse: String) = ChordSheet.Page(verse.map { line(it) } + chorus)
    val result = ChordSheetConverter.convert(
        ChordSheet(
            listOf(
                ChordSheet.Page(listOf(line("Star Song", size = 16.0)) + page("Somebody once told me", "The world is gonna roll me", "I ain't the sharpest tool").lines),
                page("So much to do so much to see", "So what's wrong with taking the back streets", "You'll never know if you don't go"),
                page("It's a cool place and they say it gets colder", "You're bundled up now wait till you get older", "But the meteor men beg to differ"),
                page(
                    "The ice we skate is getting pretty thin", "Somebody told me so", "We were born to sing",
                    "The water's getting warm so you might as well swim", "My world's on fire how about yours",
                ),
            ),
        ),
    )
    assertEquals(1, result.size, result.joinToString("{new_song}\n"))
    assertEquals(4, Regex("Get the show on, get paid").findAll(result.single()).count(), result.single())
}
```

Fails today (five songs). The cdadecb14 contents tests (`a songbook in one type is split where its contents list a
title…`, `the numbers a songbook counts its songs by…`) and both songbook goldens must still pass.
