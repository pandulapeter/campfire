# Judge a section's inset by all of its lyrics so far against the edge the verses share, not by one line each

**Challenged:** amended — the section's inset is no longer the edge its first two lyrics share (within half a character) but every one of its lyrics so far, two at least, standing more than 1.5 characters to one side of the verses' edge. The two-line rule broke a chorus whose second line is wrapped further in, and one typed one space unevenly, both of which convert right today (probed: `Chorus:` then lines at 4, 8, 4, then flush verses; and lines at 4, 5, 4); a test for them is added.

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all (pasted text, text files, PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt` (`BodyWriter`),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetLook.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Covers review findings F1 and F2: one code site, one rule. Lane B, plan 1 of 7 (run first: plans 04 and 05 build on
the helpers this one adds).

## Problem

Commit b284feef5 ends a section where "the lyrics return to the verses' look". `BodyWriter.follow` in
`ChordSheetConverter.kt` judges that from **one line on each side**: the section's first lyric (`sectionLook`) against
the last lyric outside a section (`bodyLook`, snapshotted as `sectionBody` when the section opens):

```kotlin
fun follow(look: ChordSheetLook, text: String) {
    val opened = sectionLook
    if (environment != null && opened == null) sectionLook = look
    else if (environment != null && opened != null) {
        val body = sectionBody
        val ends = if (body == null) opened.differsInType(look) else opened.isSetApartFrom(body) && !look.isSetApartFrom(body)
```

`isSetApartFrom` counts an inset of more than 1.5 characters (`ChordSheetLook.kt`):

```kotlin
fun isSetApartFrom(other: ChordSheetLook) = differsInType(other) || abs(left - other.left) > maxOf(size, other.size) * 1.5
```

**F1 (plain text and PDF).** A chorus whose first lyric happens to start further in than the others (the lyric
indented under its first chord, a typo) counts as a chorus set apart by an inset. Its second lyric, flush with the
verses, "returns to the verses' look" and the chorus ends after one line; the rest becomes loose verse text. Proven:

```
Somebody told me
We were born to sing

Chorus:
   G       C
   Hey now you're a star
Get your game on, go play
Hey now you're a rock star

Verse 2:
It's a cool place
```

converts to `{start_of_chorus: Chorus}\n[G]Hey now [C]you're a star\n{end_of_chorus}\nGet your game on, go play\nHey now you're a rock star\n…`.

**F2 (PDF, Word).** A centred lyric sheet (Word, Pages, many church sheets) starts every line at
`(width − lineWidth) / 2`, and a right-aligned right-to-left sheet at `width − lineWidth`. The last verse line and the
chorus's first line differ by more than 1.5 characters whenever their lengths differ by three characters or more, so
the chorus counts as inset, and it ends at whichever later chorus line happens to have about the verse line's length.
Proven with every line centred around x = 300 at 6 units a character: the verse ending in `She was looking kind of
dumb` (left 219), the chorus opening with `Hey now, you're an all star, get your game on` (left 165): the chorus
ends after its first line, at `Go play, hey now you're a star` (left 210).

The `body == null` branch (no verse before the section) is plan 04's; leave its behaviour as it is here.

## Fix

An inset is a property of an edge, and an edge takes two lines to show:

- **The verses' edge**: the left edge every lyric outside a section since the last section shares, within half a
  character. A centred or right-aligned sheet has none, and then only the type can set a section apart. One verse
  lyric is its own edge (plain text, a one-line verse; `a chorus heading over the chorus's own words…` relies on it).
- **The section's inset**: a section is set apart by an inset only once it has two lyrics or more and every one of
  them so far starts more than 1.5 characters to the same side of the verses' edge. So the earliest an inset ends a
  section is at its third lyric, a chorus whose first lyric alone is indented (F1) is never set apart by it, and a
  chorus with a wrapped line hanging further in, or typed a space unevenly, still is. The type is still judged by the
  first lyric, as today (the existing test `a chorus set apart in bold ends where the lyrics return to the verses' type…`
  has a one-line bold chorus).
- A lyric **returns to the verses' look** where it is in the last verse lyric's type and, where the verses have an edge,
  within 1.5 characters of it.

Concretely:

1. `ChordSheetLook.kt`, in the companion:

   ```kotlin
   /** The left edge [looks] share within half a character; null where they start in different places, as a centred or right-aligned sheet's lines do. */
   fun sharedEdge(looks: List<ChordSheetLook>): Double? {
       val first = looks.firstOrNull() ?: return null
       return first.left.takeIf { looks.all { abs(it.left - first.left) <= maxOf(it.size, first.size) * 0.5 } }
   }
   ```

   Keep `isSetApartFrom` (used by `isRecall`).

2. `BodyWriter`: replace `bodyLook` / `sectionLook` / `sectionBody` with

   ```kotlin
   /** The lyrics outside a section since one last closed: the verses a section may be set apart from. */
   private val verseLooks = mutableListOf<ChordSheetLook>()
   private var isVerseInterrupted = false
   /** The open section's lyrics so far, and the verses where it opened. */
   private val sectionLooks = mutableListOf<ChordSheetLook>()
   private var sectionVerses: List<ChordSheetLook> = emptyList()
   ```

   - `open(type)`: `sectionLooks.clear(); sectionVerses = verseLooks.toList(); isVerseInterrupted = true` (plus what it
     does today). `openChorus()` sets `sectionVerses = emptyList()` where it set `sectionBody = null`.
   - `follow`, inside a section with `sectionLooks` non-empty: `ends = if (sectionVerses.isEmpty()) sectionLooks.first().differsInType(look)
     else isSetApart(sectionVerses, sectionLooks) && returnsToVerses(look, sectionVerses)`. Inside a section, a lyric that
     does not end it is added to `sectionLooks` (an empty one takes it, as `sectionLook = look` did).
   - `follow`, outside a section (including the lyric that just ended one, as today's `bodyLook = look` after the check):
     `if (isVerseInterrupted) { verseLooks.clear(); isVerseInterrupted = false }; verseLooks += look` (verses persist
     across a section that no verse follows, as `bodyLook` did).
   - The two judgements, private to `BodyWriter`:

     ```kotlin
     /** Whether [section] is set apart from [verses]: its first lyric in another type, or two lyrics or more all starting further in (or out) than the edge the verses share. */
     private fun isSetApart(verses: List<ChordSheetLook>, section: List<ChordSheetLook>): Boolean {
         val verse = verses.last()
         if (section.first().differsInType(verse)) return true
         val edge = ChordSheetLook.sharedEdge(verses) ?: return false
         if (section.size < 2) return false
         val margin = maxOf(section.maxOf { it.size }, verse.size) * 1.5
         val insets = section.map { it.left - edge }
         return insets.all { it > margin } || insets.all { it < -margin }
     }

     /** Whether [look] is back in the verses' type and, where they share an edge, at it. */
     private fun returnsToVerses(look: ChordSheetLook, verses: List<ChordSheetLook>): Boolean {
         val verse = verses.last()
         if (look.differsInType(verse)) return false
         val edge = ChordSheetLook.sharedEdge(verses) ?: return true
         return abs(look.left - edge) <= maxOf(look.size, verse.size) * 1.5
     }
     ```
   - `closeSection()` records `setApart` under the same judgement:
     `if (type != null && sectionLooks.isNotEmpty() && (sectionVerses.isEmpty() || isSetApart(sectionVerses, sectionLooks))) setApart.getOrPut(type) { sectionLooks.first() to sectionWords.toSet() }`
     (the `sectionVerses.isEmpty()` half keeps today's behaviour; plan 05 replaces it), then clears `sectionLooks`.

Options considered: (a) the reviewer's idea alone, inset only where the verses share an edge: fixes F2 but not F1, whose
verses do share one; (b) the section's edge from its first two lyrics, within half a character: fixes F1, but a
chorus whose second line is wrapped further in, or typed a space off, then never ends by its inset (both end right
today); (c) recommended: the verses' shared edge, and every section lyric so far, two at least, beyond it. It keeps
every existing test (`…set apart by an inset ends where the lyrics return to the verses' edge`: two flush verse lines,
two chorus lines at 4, flush third line ends it).

Known limit, as before b284feef5: a one-line inset chorus, or one whose first line is flush, never ends by its inset;
the next heading ends it.

`isSetApartFrom` in a later chorus heading's `isRecall` is unchanged.

Update the bullet in `convert/CLAUDE.md` ("A section whose lyrics are set apart…") to say: an inset counts only where
two of the section's lyrics or more, all of them so far, start more than a character and a half to one side of the
edge the verses share; a sheet whose verses share none (centred, right-aligned) sets a section apart by type alone.

## Tests

In `ChordSheetConverterTest`, beside the b284feef5 tests:

```kotlin
@Test
fun `a chorus whose first lyric alone starts further in is not set apart by an inset and keeps its lines`() {
    assertEquals(
        "Somebody told me\nWe were born to sing\n\n{start_of_chorus: Chorus}\n[G]Hey now [C]you're a star\nGet your game on, go play\n" +
            "Hey now you're a rock star\n\n{end_of_chorus}\n{start_of_verse: Verse 2}\nIt's a cool place\n{end_of_verse}\n",
        convert("Somebody told me\nWe were born to sing\n\nChorus:\n   G       C\n   Hey now you're a star\nGet your game on, go play\nHey now you're a rock star\n\nVerse 2:\nIt's a cool place"),
    )
}

@Test
fun `a centred sheet's chorus is not cut where a line happens to start where the last verse line did`() {
    fun centred(text: String, size: Double = 10.0) = ChordSheet.Line(text.mapIndexed { index, c ->
        ChordSheet.Span(c.toString(), 300.0 - text.length * 3.0 + index * 6.0, 300.0 - text.length * 3.0 + (index + 1) * 6.0, size)
    })
    val lines = listOf(
        centred("Song", size = 16.0),
        centred("Somebody once told me the world is gonna roll me"),
        centred("I ain't the sharpest tool in the shed"),
        centred("She was looking kind of dumb"),
        ChordSheet.Line(emptyList()),
        centred("Chorus:"),
        centred("Hey now, you're an all star, get your game on"),
        centred("Go play, hey now you're a star"),
        centred("Hey now, you're a rock star, get the show on"),
        centred("Get paid, and all that glitters is gold"),
        centred("Only shooting stars break the mold"),
    )
    assertEquals(
        "{title: Song}\n\nSomebody once told me the world is gonna roll me\nI ain't the sharpest tool in the shed\nShe was looking kind of dumb\n\n" +
            "{start_of_chorus: Chorus}\nHey now, you're an all star, get your game on\nGo play, hey now you're a star\n" +
            "Hey now, you're a rock star, get the show on\nGet paid, and all that glitters is gold\nOnly shooting stars break the mold\n{end_of_chorus}\n",
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
    )
}
```

Add one that passes today and must keep passing (the reason for the rule over every lyric, not the first two):

```kotlin
@Test
fun `an inset chorus with a wrapped line hanging further in, or typed a space unevenly, still ends where the verses' edge returns`() {
    val expected = "{end_of_chorus}\nIt's a cool place\nAnd it gets colder\n\n{chorus}\n"
    val wrapped = convert(
        "Somebody told me\nWe were born to sing\n\nChorus:\n    Hey now you're a star, get your game on\n        go play\n" +
            "    Hey now you're a rock star\nIt's a cool place\nAnd it gets colder\n\nChorus",
    )
    assertTrue(wrapped.endsWith("Hey now you're a rock star\n$expected"), wrapped)
    val uneven = convert(
        "Somebody told me\nWe were born to sing\n\nChorus:\n    Hey now you're a star\n     Get your game on\n" +
            "    Hey now you're a rock star\nIt's a cool place\nAnd it gets colder\n\nChorus",
    )
    assertTrue(uneven.endsWith("Hey now you're a rock star\n$expected"), uneven)
}
```

The first two fail today (outputs quoted under Problem). The three b284feef5 tests and the 9f6019317 tests must still pass
unchanged; run `./gradlew :chordpro:desktopTest` and `./gradlew :data:source:local:implementation:desktopTest`
(`DocumentGoldenTest`: `songbook-styled.cho` and `songbook-numbered.cho` must not change).
