# Keep chords and annotations inside their line and their page

**Challenged:** amended — part 1's rule would not have fixed the line it was written for: `[C] [G] [Am] [F]` parses to
the text `"   "` with a chord on every space (`ChordProParser.parseLyrics`), so each chord's fragment is a single
ordinary space followed by its padding, and a line may break after an ordinary space and before a non-breaking one
(UAX #14 LB18 over LB12a) — the layout keeps taking that break, which leaves the chord at the end of a row over its one
space with its padding on the next row, and the clamp pulls it back onto the chord before it. The rule is now: the
whitespace at either end of a padded fragment becomes non-breaking, and the one break opportunity is a zero-width space
at the end of a fragment wherever the original text has a word boundary there. Part 2's annotation wrap is dropped: the
chord layouts and the line height are decided in composition without the width, and a second line of annotation would
be drawn over the lyrics under it, so an annotation wider than the column is clipped (its full text stays in the line's
content description). Part 2's "drop `previousChordEdge` back to the chord's own position" would itself draw a chord over
its neighbour; the clamp is now bounded by the previous chord's edge instead.

**Kind:** bug  ·  **Severity:** high (text drawn unreadably, and into the next song's page)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
(`padLyricsToFitChords`, the chord `drawBehind`, the annotation layouts),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt` (the pager page),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/` (a new test for the padding, see below),
`presentation/CLAUDE.md` where it describes chords over lyrics

## Problem

The live run at 9ab7ca54e, on a song with a chord-only line of 60 chords, two very long chord names, and a
100-character annotation without spaces, at 800 × 600 and 500 × 900 (frames `visual2/0011`, `0035`, `0038`, `0040` of
the run):

- on the chord-only line, at the end of every wrapped visual row the last chord is drawn **over** the one before it
  (`C` over `Am`, `Am` over `Dm`; the log shows a chord starting 25 px before the previous chord's end, and the two
  long names 240 px into each other);
- the annotation is drawn at its full width — 5.4 times the column at text size 2.5 — past the step button column, off
  the window at 500 × 900, and at 800 × 600 **into the next song's page** of the pager, where its tail stays while
  that song scrolls.

The lyrics are padded so that every chord has room under it:

```kotlin
private fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    paddingWidth: Float,
    measureWidth: (String) -> Float,
): ChordProLine.Lyrics {
    …
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        if (missingWidth > 0 && paddingWidth > 0) {
            repeat(ceil(missingWidth / paddingWidth).toInt()) { paddedLyrics.append(PADDING) }
        }
…
private const val PADDING = ' ' // Non-breaking space, so that the padding never gets trimmed or wrapped.
```

so a chord-only line becomes one unbreakable run of non-breaking spaces, which the text layout can only break at an
arbitrary character. The chords are then placed while drawing, each kept off the one before it and clamped to the
line's width:

```kotlin
val maxX = max(0f, size.width - chordLayout.size.width)
val position = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
val x = if (isRightToLeft) {
    (min(position, previousChordEdge) - chordLayout.size.width).coerceIn(0f, maxX)
} else {
    max(position, previousChordEdge).coerceIn(0f, maxX)
}
```

A chord whose place is past `maxX` is pulled back onto the chord before it, and a chord or annotation wider than the
line is drawn from `x = 0` at its full width: `drawBehind` is not clipped, and neither is a pager page. The layout's
own overflow check sees none of it, because the chords are drawn, not measured. A chord-only intro of a dozen chords
on a phone, wrapped once, is enough for the first case.

## Fix

Three parts, in this order of importance.

1. **Let the padded lyrics break between chords, and only there.** In `padLyricsToFitChords`, for each chord's fragment
   (the original text from its position to the next chord's):
   - where the fragment gets padding, replace the whitespace at its **start and end** with `PADDING` (the non-breaking
     space) before appending the padding, so that a chord, the space it sits on and the padding that makes room for it
     can never be split across two rows (a fragment's inner spaces stay ordinary, so `Hello world ` under one chord
     still wraps between its words);
   - after the fragment and its padding, if this is not the last chord and the original text has a word boundary at the
     fragment's end — `end == 0 || text[end - 1].isWhitespace() || text[end].isWhitespace()`, `end` being the next
     chord's position — append `BREAK_OPPORTUNITY` (`'\u200B'`, zero-width space, a new constant next to `PADDING`).
     Inside a word (`wo[C]n[G]der`) there is none, as now. A fragment with no padding still gets the break where the
     boundary is, since the one after it may start with a space that is now non-breaking;
   - the chord positions are indices into the padded text as now; the next chord's position is taken after the
     zero-width space, so every chord is at the start of whatever row a break sends it to.
   Measure the padding from the original fragment (the non-breaking space is as wide as a space). Nothing else reads the
   padded text: the content description is built from the original line (`withChordsInline`), `chordsOfShownText` is
   for comments, and nothing on the page is selectable. U+200B is default-ignorable, so HarfBuzz draws nothing for it
   whatever the font (the web's subset Inter and JetBrains Mono included), and ICU — Skia's paragraph and Android's
   line breaker alike — breaks after it (class ZW).
2. **Never draw wider than the line.** Wrap the body of the chord `drawBehind` in `clipRect { … }` (the whole `size`:
   each chord is drawn at its row's top, inside the line's height, so only what reaches past the end edge is cut). In
   the placement, never pull a chord back over the one before it: on a left to right line
   `x = max(previousChordEdge, min(max(position, previousChordEdge), maxX))`, and on a right to left one
   `x = min(previousChordEdge - width, max(min(position, previousChordEdge) - width, 0f))` — the clamp to the line
   still applies wherever it leaves the previous chord alone, and where it cannot, the chord stays where it belongs and
   is clipped at the edge. An annotation wider than the column is clipped the same way; wrapping it would need the
   column's width while the chord layouts and the line height are worked out in composition, and its second line
   would land on the lyrics of the row it hangs over. Its whole text stays in the line's content description.
3. **Clip the pager page** as the last line of defence: `Modifier.clipToBounds()` on each page's content in
   `SongDetailsScreen.kt`'s `HorizontalPager` block, so nothing a page draws can reach the next song's page. The page's
   own padding holds the chorus cards' shadows, the header pills that hang into the start padding and the fade, so
   nothing that belongs to the page is cut.

Document the break opportunity next to the sentence in `presentation/CLAUDE.md` that says the lyrics are padded to fit
the chords, and the clipping of an annotation wider than its column.

## Tests

`padLyricsToFitChords` is private; make it `internal` and add `ChordPaddingTest` in `presentation`'s `songDetails` test
package with a fake `measureWidth` (a fixed width per character) and chords wider than one character:

- `[C] [G] [Am] [F]` (text `"   "`): no ordinary space is left in the padded text, and there is one zero-width space
  after each of the first three chords' padding and none at the end; each chord position is the index just after the
  previous zero-width space;
- `wo[C]n[G]der`: no zero-width space anywhere;
- `[C]Hello [G]world`: one zero-width space after `C`'s fragment and its padding (whose trailing space is non-breaking),
  none at the end;
- `[C]Hello world [G]x` with a chord narrower than `Hello world `: the inner space stays an ordinary space;
- chord positions point at the first character of the fragments they belong to after padding.

The clip and the page clip are Compose and have no pure test.

## Manual check

Phone-sized window (500 × 900): a line of `[C] [G] [Am] [F] [C] [G] [Am] [F] [C] [G] [Am] [F]` wraps between chords
with none drawn over another; a `{comment}` or an annotation of 100 characters without a space stays inside the
column; nothing of a song shows on the next song's page while the pager settles.
