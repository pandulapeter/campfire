# Read a chorus heading as a recall only in a song whose verses stand outside sections, never where every section is headed

**Challenged:** amended — dropping the `sectionVerses.isEmpty() ||` half alone broke a real recall that works today (probed): `Verse 1:` headed, a bold `Chorus:` ended by the unheaded plain verse after it (plan 04's rule), then `Chorus:` over the next plain verse gives `{chorus}` today and would give a written-out chorus. A section that ended because its lyrics returned to the verses' look (or to plain type) was shown to be set apart, so it is recorded too; a test pins the recall.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (pasted text, text files, PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt` (`BodyWriter.closeSection`),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Review finding L4 (verified by probe). Lane B, plan 3 of 7, after 03 (which rewrites the condition quoted here).

## Problem

Commit b284feef5 reads a chorus heading as a recall (`{chorus}`) when the lyric under it is "neither set in the look
the song set its chorus apart in nor singing its words". The look is recorded in `closeSection`:

```kotlin
if (type != null && opened != null && sectionBody?.let { opened.isSetApartFrom(it) } != false) setApart.getOrPut(type) { opened to sectionWords.toSet() }
```

`sectionBody` is null in a song that heads every section, since no lyric stands outside one, so `!= false` records the
first chorus's look **unconditionally**: nothing showed it was set apart from anything. A later `Chorus:` heading over
a written-out chorus with new words in another look (a last chorus typed flush, a different style) becomes `{chorus}`,
and its lines fall outside any section. In such a song the verses carry headings of their own, so lyrics under a
chorus heading are always that chorus. Proven in plain text:

```
Verse 1:
Somebody told me
We were born to sing

Chorus:
    Hey now you're a star
    Get your game on

Verse 2:
It's a cool place
And it gets colder

Chorus:
Hey now you're a rock star
Get the show on, get paid
```

ends `…{end_of_verse}\n{chorus}\nHey now you're a rock star\nGet the show on, get paid\n`. In a PDF (first chorus bold,
last one italic) it ends `{chorus}\n\n{start_of_chorus}\nHey now you're a rock star\n…`: a recall followed by the
unheaded chorus 9f6019317 opens.

## Fix

Record `setApart` only for a section shown to be set apart: from verses that were seen, or by its lyrics returning to
the verses' look (plain type where none were seen) and so ending it. After plan 03 the condition reads
`sectionVerses.isEmpty() || isSetApart(sectionVerses, sectionLooks)`; it becomes

```kotlin
isReturn || sectionVerses.isNotEmpty() && isSetApart(sectionVerses, sectionLooks)
```

where `isReturn` is true only for the `closeSection` that `follow` makes when a lyric ends the section
(`closeSection(isReturn = true)` there; every other caller, a heading, a comment, the song's end, passes nothing:
`fun closeSection(isReturn: Boolean = false)`). With verses seen, `isReturn` implies `isSetApart` already; it matters
only where none were, which is the case this plan is about: a song that heads every section has its chorus closed by
the next heading, so nothing is recorded and a later chorus heading over new words is that chorus written out, while
a song whose chorus is followed by an unheaded verse still records it, and a chorus heading over a later verse still
recalls it.

A bare chorus heading followed by another heading or the song's end still writes `{chorus}` (that path does not use
`isRecall`). The b284feef5 test `…a chorus heading over a verse recalls it` keeps passing: its chorus opens after the
unheaded verse `Somebody told me`. The unheaded chorus `openChorus` opens (no verses, by construction) is recorded
where the lyrics return to plain, as it is today unconditionally.

Alternatives: (a) drop the `isEmpty() ||` half alone: loses the recall above; (b) treat the lyrics of headed
non-chorus sections as the verses (so `sectionVerses` is never empty in a headed song): a last chorus typed flush then
looks exactly like a verse, and is recalled all the same.

In `convert/CLAUDE.md`, extend "A chorus heading over lines neither set in the look the song set its chorus apart in
nor singing its words recalls it" with ", once the song showed that look apart: against verses outside any section,
or by the lyrics returning from it (in a song that heads every section, lyrics under a chorus heading are that
chorus)".

## Tests

```kotlin
@Test
fun `in a song that heads every section, a chorus heading over new words in another look is that chorus written out`() {
    val result = convert(
        "Verse 1:\nSomebody told me\nWe were born to sing\n\nChorus:\n    Hey now you're a star\n    Get your game on\n\n" +
            "Verse 2:\nIt's a cool place\nAnd it gets colder\n\nChorus:\nHey now you're a rock star\nGet the show on, get paid",
    )
    assertEquals(
        "{start_of_verse: Verse 1}\nSomebody told me\nWe were born to sing\n\n{end_of_verse}\n{start_of_chorus: Chorus}\nHey now you're a star\n" +
            "Get your game on\n\n{end_of_chorus}\n{start_of_verse: Verse 2}\nIt's a cool place\nAnd it gets colder\n\n{end_of_verse}\n" +
            "{start_of_chorus: Chorus}\nHey now you're a rock star\nGet the show on, get paid\n{end_of_chorus}\n",
        result,
    )
}
```

And one that passes today and must keep passing:

```kotlin
@Test
fun `a chorus heading over the plain verse after a bold chorus that the verse ended recalls it, in a song that heads its first verse`() {
    fun line(text: String, bold: Boolean = false, size: Double = 10.0) =
        ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 6.0, (index + 1) * 6.0, size, isBold = bold) })
    val blank = ChordSheet.Line(emptyList())
    val lines = listOf(
        line("Song", size = 16.0), line("Verse 1:"), line("Somebody told me"), line("We were born to sing"), blank,
        line("Chorus:"), line("Hey now you're a star", bold = true), line("Get your game on", bold = true), blank,
        line("It's a cool place"), line("And it gets colder"), blank,
        line("Chorus:"), line("The ice we skate"), line("Is getting pretty thin"),
    )
    assertEquals(
        "{title: Song}\n\n{start_of_verse: Verse 1}\nSomebody told me\nWe were born to sing\n\n{end_of_verse}\n{start_of_chorus: Chorus}\n" +
            "Hey now you're a star\nGet your game on\n{end_of_chorus}\n\nIt's a cool place\nAnd it gets colder\n\n{chorus}\n" +
            "The ice we skate\nIs getting pretty thin\n",
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
    )
}
```

The first fails today (output under Problem). The three b284feef5 tests must still pass.
