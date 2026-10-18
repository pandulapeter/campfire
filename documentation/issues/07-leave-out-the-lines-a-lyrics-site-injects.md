# Leave out the lines a lyrics site injects into a copied text

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`
**Challenged:** amended — "the sheet has structure" is plan 01's test, defined once and reused rather than written a
second time; the share count glued to the last lyric (`…the end12Embed`), the shape a Genius copy usually ends in,
is covered.

## Problem

Copying lyrics from Genius, the largest lyrics site, brings two lines that are not the song: `You might also like`
on a line of its own in the middle of the text, and a last line `12Embed` or `Embed` (the number is the page's
share count). Nothing filters them: `convertSong` classifies both as lyrics inside whatever section is open, so a
song ends with a chorus whose last line is `12Embed`.

Ultimate Guitar's page furniture (`Difficulty:`, `Author: …`, `View official tab`, `Last update:`, a rating) also
survives a whole-page copy. It is many lines in no fixed shape, so it is **not** part of this plan; `CLAUDE.md` says
so.

## Fix

In `convert`, on the rendered lines, after plan 06's double-spacing pass and before `expandLabels`, and only when the
sheet has structure (so a poem that ends in "Embed" is untouched). "Has structure" is the expression plan 01 computes
in `classify` (`hasUnambiguousChords || lines.any { ChordSheetHeadings.parse(it.text) != null }`): move it into one
private `hasStructure(lines, candidates)` that both call, rather than a second definition here.

- drop a line whose trimmed text is exactly `You might also like` (case-insensitive);
- drop the last non-blank line when it matches `^[0-9]*Embed$`; where `[0-9]*Embed` ends the last non-blank line
  straight after a letter or punctuation, with no space (`…the end12Embed`, `…the end.Embed`), cut that suffix off
  the line (a sung "we embed" has its space and a lowercase `e`, and is left alone).

Keep the blank-line structure around a dropped line (collapse two blanks into one, as `collapse` does).

## Tests

- `the lines a lyrics site injects into a copy are left out`: `[Verse 1]` / lyrics / `You might also like` / lyrics
  / `[Chorus]` / lyrics / `12Embed` → neither line in the output, the chorus closed cleanly.
- `a poem that ends in Embed is left alone`: four prose lines ending in `Embed` → escaped prose, line kept.
- `a share count glued to the last lyric is cut off`: `[Chorus]` / `Sing it to the end12Embed` → `Sing it to the end`;
  `[Chorus]` / `This is what we Embed` keeps its last word.
