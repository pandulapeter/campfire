# End a section with no verse before it only where the lyrics return to plain type, so a backing vocal in italic stays in it

**Challenged:** amended — the branch it changes is also the one an unheaded chorus (`openChorus`, 9f6019317) ends by, so its `convert/CLAUDE.md` bullet ("ended by a change of type") is updated too, and the change for it is stated: an italic unheaded chorus now runs on through a bold line until the lyrics return to plain type.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt` (`BodyWriter.follow`),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Review finding F3. Lane B, plan 2 of 7, after 03 (which renames the state this plan touches: `sectionLooks` /
`sectionVerses`; on the reviewed code the same branch reads `if (body == null) opened.differsInType(look)`).

## Problem

Commit b284feef5 ends a section that has no verse before it at any change of type (`BodyWriter.follow`,
`ChordSheetConverter.kt`):

```kotlin
val ends = if (body == null) opened.differsInType(look) else opened.isSetApartFrom(body) && !look.isSetApartFrom(body)
```

`body` is null for every section of a song that heads all its sections (no lyric ever stands outside one), and for the
chorus 9f6019317 opens with no heading. In such a song, one whole line set in italic or bold inside a plain chorus (a
backing vocal `(hey now, hey now)`, a `Spoken:` line, an emphasised word line) ends the chorus there; its later lines
stand outside any section. Proven, every line 10 pt plain except the italic one:

```
Song (16 pt)
Verse 1:
Somebody told me
We were born to sing

Chorus:
Hey now you're a star
(hey now, hey now)            ← italic
Get your game on, go play
Hey now you're a rock star

Verse 2:
It's a cool place
```

gives `{start_of_chorus: Chorus}\nHey now you're a star\n{end_of_chorus}\n(hey now, hey now)\nGet your game on, go play\n…`.

## Fix

With no verse to compare with, the one look known to be the verses' is plain type: a section with no verse before it
ends only where its first lyric is set apart by type (bold or italic) and the new lyric is in plain type (neither).
In the `sectionVerses.isEmpty()` branch:

```kotlin
val opened = sectionLooks.first()
(opened.isBold || opened.isItalic) && !look.isBold && !look.isItalic
```

So a plain chorus under a heading never ends by look (the next heading ends it, as before b284feef5), and a bold or
italic one ends at the first plain lyric, as the CLAUDE.md rule and the 9f6019317 test (an italic run ended by
`It's a cool place`) want; an italic line inside a bold chorus, or a bold one inside an italic chorus, no longer ends it.

Options: (a) recommended, the above; (b) leave the rule and exempt a line wholly in parentheses: catches the backing
vocal only, not `Spoken:` or an emphasised line, and adds a text heuristic; (c) defer the end until a second lyric
confirms the new type, writing `{end_of_…}` back into `output` at the first one: right for every case but needs
look-ahead or rewriting `output`, more than this needs.

The same branch ends the chorus `openChorus` opens with no heading (it sets `sectionVerses = emptyList()`), whose
verses are known to be plain (`unlabelled` requires plain lyrics), so the rule is the same there: today an italic
unheaded chorus ends at a bold line and the bold lines are left loose (they are never opened as a chorus of their own,
since `openUnlabelled` runs before `follow` closes the first); after this it runs on until the lyrics return to plain
type. In the "With no heading, a stanza's run…" bullet of `convert/CLAUDE.md`, "ended by a change of type" becomes
"ended where the lyrics return to plain type".

In `convert/CLAUDE.md`, replace "with no verse before it, only a change of type ends it (a centred sheet's lines each
start elsewhere)" with "with no verse before it, only a return from bold or italic to plain type ends it (a centred
sheet's lines each start elsewhere, and a line in another type inside it is a backing vocal or an aside)".

## Tests

```kotlin
@Test
fun `a backing vocal in italic inside a chorus of a song that heads every section stays in the chorus`() {
    fun line(text: String, italic: Boolean = false, size: Double = 10.0) =
        ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 6.0, (index + 1) * 6.0, size, isItalic = italic) })
    val lines = listOf(
        line("Song", size = 16.0),
        line("Verse 1:"), line("Somebody told me"), line("We were born to sing"), ChordSheet.Line(emptyList()),
        line("Chorus:"), line("Hey now you're a star"), line("(hey now, hey now)", italic = true), line("Get your game on, go play"),
        line("Hey now you're a rock star"), ChordSheet.Line(emptyList()),
        line("Verse 2:"), line("It's a cool place"),
    )
    assertEquals(
        "{title: Song}\n\n{start_of_verse: Verse 1}\nSomebody told me\nWe were born to sing\n\n{end_of_verse}\n{start_of_chorus: Chorus}\n" +
            "Hey now you're a star\n(hey now, hey now)\nGet your game on, go play\nHey now you're a rock star\n\n{end_of_chorus}\n" +
            "{start_of_verse: Verse 2}\nIt's a cool place\n{end_of_verse}\n",
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
    )
}
```

Fails today (output under Problem). `a stanza set in italic among plain verses is a chorus with no heading…` must still
pass unchanged.
