# Take no run of bold or italic lines standing before the song's first chord row or plain lyric for an unheaded chorus

**Challenged:** amended — a stanza break before the run now counts as a start too, so only the lines right under the title (no blank line, heading, chord row, or plain lyric before them) are the header's. As written, a lyric sheet with no chord rows that opens with its chorus in bold after a gap lost the chorus it gets today (probed: `{start_of_chorus}` today); a test pins it.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (PDF, Word)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetLook.kt` (`unlabelled`),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt` (`writeBody`, its call),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Review finding L1 (verified by probe, in a narrower form than reported). Lane B, plan 4 of 7; independent of 03–05
(another function), before 07 (same function).

## Problem

Commit 9f6019317 opens a chorus at any run of two lyrics or more set in bold or italic at the plain verses' size
(`ChordSheetLook.unlabelled`). The header lines a sheet sets in bold under its title are such a run when two of them
are left as lyrics: the title is taken (`extractTitle`), but a credit of the title's size and longer than it is kept
as a lyric, as is anything under it (an album, an arranger). Proven, every line 10 pt, chords bold:

```
Song Title                       ← bold
Bob Dylan and The Band           ← bold
From the album Blonde on Blonde  ← bold
G       C
Somebody told me
G       C
We were born to sing
```

gives `{title: Song Title}\n\n{start_of_chorus}\nBob Dylan and The Band\nFrom the album Blonde on Blonde\n{end_of_chorus}\n[G]Somebody…`.
With one bold credit line it is not hit (a run needs two). The reported `guessesTitle = false` path is not reachable
with bold type: its only caller, `JsonSongs`, passes plain text.

```kotlin
looks.forEachIndexed { index, look ->
    if (breaks[index]) finish()
    if (look == null) return@forEachIndexed
    val isApart = abs(look.size - size) < size * 0.05 && (look.isBold || look.isItalic)
    if (!isApart || run.isNotEmpty() && looks[run.first()]!!.differsInType(look)) finish()
    if (isApart) run += index
}
```

## Fix

The lines right under the title, ahead of everything sung, are the header's: a run counts only where a chord row, a
plain lyric, or a stanza break stands before its first line (a chord row over that first line counts, so a bold chorus
that opens the song with chords still opens one; a blank line after the title counts, so a lyric sheet that opens with
its chorus in bold after a gap, which a PDF's reading turns into a blank line, still opens one).

- `unlabelled(looks, breaks, rows)`: a new `rows: List<Boolean>`, whether a line is a chord row. Keep
  `var hasStarted = false`; in the loop, `if (breaks[index] || rows[index]) hasStarted = true` before the `look == null`
  return, and after computing `isApart`, `if (look != null && !look.isBold && !look.isItalic) hasStarted = true`. Record
  the run's start only `if (hasStarted)` at the moment its first line is added (keep a `runStarted` flag set when `run`
  goes from empty to one), and `finish()` adds `run.first()` only where it was.
- `writeBody` passes `rows = kinds.map { it == Kind.CHORD }`.

Options rejected: (a) a chord row or a plain lyric alone starts the song: drops the bold chorus a lyrics-only sheet opens
with after a gap; (b) a run with no chord row in or over it in a song that has chord rows is no chorus: it would also
drop a lyrics-only bold chorus in a song whose verses alone carry chords, which is common.

Known limit: credits set apart from the title by a gap of their own (a blank line between them) are a run after a
break, and still open a chorus, as today.

In `convert/CLAUDE.md`, add to the "With no heading, a stanza's run…" bullet: "a run right under the title, before any
blank line, heading, chord row, or plain lyric, is the header's (a credit, an album), not a chorus".

## Tests

```kotlin
@Test
fun `bold lines under the title before anything sung are its credits, not a chorus`() {
    fun line(text: String, bold: Boolean = false) = ChordSheet.Line(text.mapIndexed { index, c -> span(c.toString(), index * 6.0, (index + 1) * 6.0, bold = bold) })
    val chords = line("G       C", bold = true)
    val lines = listOf(
        line("Song Title", bold = true), line("Bob Dylan and The Band", bold = true), line("From the album Blonde on Blonde", bold = true),
        chords, line("Somebody told me"), chords, line("We were born to sing"),
    )
    assertEquals(
        "{title: Song Title}\n\nBob Dylan and The Band\nFrom the album Blonde on Blonde\n[G]Somebody [C]told me\n[G]We were [C]born to sing\n",
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
    )
}
```

And one that passes today and must keep passing:

```kotlin
@Test
fun `a lyric sheet opening with its chorus in bold after a gap under the title keeps the chorus`() {
    fun line(text: String, bold: Boolean = false, size: Double = 10.0) =
        ChordSheet.Line(text.mapIndexed { index, c -> ChordSheet.Span(c.toString(), index * 6.0, (index + 1) * 6.0, size, isBold = bold) })
    val blank = ChordSheet.Line(emptyList())
    val lines = listOf(
        line("Song", size = 16.0), blank, line("Hey now you're a star", bold = true), line("Get your game on", bold = true), blank,
        line("Somebody told me"), line("We were born to sing"), blank, line("It's a cool place"), line("And it gets colder"),
    )
    assertEquals(
        "{title: Song}\n\n{start_of_chorus}\nHey now you're a star\nGet your game on\n{end_of_chorus}\n\nSomebody told me\n" +
            "We were born to sing\n\nIt's a cool place\nAnd it gets colder\n",
        ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines)))).single(),
    )
}
```

The first fails today (output under Problem). `a stanza set in italic among plain verses is a chorus with no heading…` and the
`songbook-styled` golden (italic chorus with chord rows, `DocumentGoldenTest`) must still pass.
