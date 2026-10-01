# Head a bare tab or grid in the PDF "Tab" / "Grid", the way the song viewer does

**Kind:** bug (viewer and PDF disagree)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt` (visibility of `areAll`), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`, `presentation/CLAUDE.md`
**Challenged:** amended — the chords-off case needs no code (`sectionRows` already drops the heading of a section whose lines all print nothing), the tests are spelled out against the test file's helpers, and the existing tests that hold a bare grid paragraph are shown to be unaffected.

## Problem

The viewer heads an unlabelled paragraph that is nothing but tablature or a grid with the kind's name
(`SongLyrics.kt`, `ChordProBlock.Section.header`):

```kotlin
SectionType.Paragraph -> when {
    lines.areAll<ChordProLine.Tab>() -> defaultLabels.tab
    lines.areAll<ChordProLine.Grid>() -> defaultLabels.grid
    else -> UNNAMED_SECTION_HEADER
}
```

The PDF heads every paragraph with nothing (`PrintLayout.kt`, `sectionRows`):

```kotlin
// The viewer heads an unnamed paragraph with nothing but its fold toggle, which a page has no use for.
SectionType.Paragraph -> null
```

A bare, unlabelled `{start_of_tab}` … `{end_of_tab}` is parsed as an implicit `Paragraph` with a null label (a labelled
one carries its label as the section's, `SectionBuilder.openLineMode`, and is already headed in both). So it reads
"Tab" on screen and has no heading on paper, and the comment above is only true of paragraphs of lyrics.

## Fix

In `PrintLayout.kt`'s `sectionRows`, make the paragraph branch mirror the viewer:

```kotlin
// A paragraph of nothing but tablature or a grid is a bare environment, which names itself, as on screen; one of
// lyrics is headed on screen by its fold toggle alone, which a page has no use for.
SectionType.Paragraph -> when {
    section.lines.areAll<ChordProLine.Tab>() -> labels.sections.tab
    section.lines.areAll<ChordProLine.Grid>() -> labels.sections.grid
    else -> null
}
```

`labels.sections` is the viewer's `DefaultSectionLabels`, which has `tab` and `grid`. `areAll` is a
`private inline fun <reified T : ChordProLine> List<ChordProLine>.areAll()` at the bottom of `SongLyrics.kt`; make it
`internal` and import it the way `labelOf` is already imported from that package.

What follows needs no code, and is why the fix is this small:
- With chords off, tab and grid lines print no rows, and `sectionRows` already returns nothing for a section with lines
  none of which prints anything ("A section whose every line the options hide leaves out its label too"), so the new
  heading goes with them, as the viewer drops such a section whole.
- `tabRows` prints a tab line's own label only where it differs from `sectionLabel`; an unlabelled bare tab has no line
  label, so nothing is printed twice. (A second, labelled tab environment in the same implicit paragraph would print
  "Tab" and then its own label; the viewer shows only "Tab" there. That edge predates this change and is left alone.)
- A continuation half (`isContinuation`) is still not headed again.

Add "on screen and on paper" (or equivalent) to the `presentation/CLAUDE.md` sentence "A paragraph that holds nothing
*but* one of the two still gets the default "Tab" / "Grid" heading".

## Tests

In `PrintLayoutTest` (the `song()` helper wraps lines in a `Verse`, so pass `blocks` explicitly):
- `song(emptyList(), listOf(ChordProBlock.Section(SectionType.Paragraph, null, listOf(ChordProLine.Tab("e|---0---|")))))`
  lays out a text `"Tab"` in the label style (bold, gray 90) above the staff; with `PrintSettings(showChords = false)`
  neither `"Tab"` nor the staff is printed.
- The same with `ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("G"), GridToken.Bar("|")))` and `"Grid"`.

Existing tests stay green: `aGridLineBreaksBetweenBars` lays out a bare grid paragraph but keeps only texts of size 20
(the grid style), and the new heading is the label style at size 19; `lyricsOnlyOmitsChordsTabsGridsAndComments…` has
lyrics in its paragraph; `PrintRendererTest`'s document (whose grid shares the "Guitar" paragraph its tab opened, so it gains nothing) is checked only for
page count ≥ 4 and image sizes.

## Manual check

Export a song with a bare, unlabelled `{start_of_tab}` and a bare `{start_of_grid}` to PDF and compare the headings with
the song details screen.
