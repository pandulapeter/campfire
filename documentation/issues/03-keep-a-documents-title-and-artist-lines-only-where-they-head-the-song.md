# Keep a document's unsure title and artist lines only where they head the song, with the page crop's one threshold

**Challenged:** amended — option (b) now keeps every line labelled `TITLE` or `ARTIST` wherever it is (not only
`crop.titleLine` / `artistLine`), limiting only the lines labelled otherwise that the 2% rule adds; a test pins it.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongCrop.kt`,
`libs/chordsheet/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongCropTest.kt`,
`libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/CropRule.kt`,
`libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/PageCrop.kt`,
`libs/pagesong/CLAUDE.md`

Lane C, third of four (after 02, before 04). Shares `ChordSheetSongCrop.kt`, its test, and `convert/CLAUDE.md` with 01
and 02, and `CropRule.kt`, `PageCrop.kt`, and `libs/pagesong/CLAUDE.md` with 01. Another session was committing to
`libs/chordsheet` when this was written: quotes are as at HEAD `79bb02af9`; re-locate by the quoted snippet.

## Problem

A document's song keeps every line with a 2% chance of being the title or the artist, wherever in the song it is:

```kotlin
// libs/chordsheet/.../convert/ChordSheetSongCrop.kt:36-37
val header = crop.scores.indices.filter { isHeader(crop.scores[it]) }
val kept = (crop.keptLines + header).map { origins[it] }.toSet()
// ChordSheetSongCrop.kt:46-49
private fun isHeader(scores: PageLineScores) = scores.label == PageLineLabel.TITLE || scores.label == PageLineLabel.ARTIST ||
    scores[PageLineLabel.TITLE] + scores[PageLineLabel.ARTIST] >= UNSURE

private const val UNSURE = 0.02f
```

A printed web page's running header ("Amazing Grace - John Newton | Songs.example"), repeated at the top of each printed
page, is exactly the line the model gives a few percent of being the title: on page 2 it sits inside the song and stays
in it. A probe through `ChordSheetConverter.convert(..., pageScores)` with such a line (junk 0.95, title 0.03, artist
0.02) between two halves of a song converted to:

```
[G]Amazing grac[C]e, how sw[G]eet the sound
[G]That saved a wret[D]ch like me
Amazing Grace - John Newton | Songs.example
[C]I once was [G]lost but now am found
```

The page path keeps only `PageCrop.titleLine` and `artistLine` (the surest line of each, `PageTextCrop.cropped`), so the
two crops disagree. And the threshold is a second copy of `:libs:pagesong`'s `UNSURE` (`CropRule.kt:20`,
`internal const val UNSURE = 0.02`, a `Double` compared after rounding to four decimals), here a `Float` compared
unrounded.

## Fix

Options:

- **(a)** Use only `crop.titleLine` and `crop.artistLine` (and `headerLines`), as the page path does. One rule for both
  paths, but it drops what the 2% rule was added for: a title the model is unsure of
  (``a line the model is a little unsure is clutter is kept and so is a title it is a little unsure of`` in
  `ChordSheetSongCropTest`, a title scored junk 0.9 / title 0.1, which `bestLineOf` never picks), so a document song
  loses its header more often.
- **(b) Recommended.** Keep every line labelled `TITLE` or `ARTIST` wherever it is, as today, and a line labelled
  otherwise that the 2% rule adds (its title and artist chance together at least 2%) only before the song's first
  `SONG` line (`body`, the first non-blank one after 01): the header the converter reads is above the song, and a line
  inside or below it that the model reads as clutter but gives a few percent of being the title is a running
  header, not the song's. Do not narrow the labelled lines to `crop.titleLine` and `crop.artistLine` (the page path's
  pair): a second line labelled `TITLE` inside the song is most often a lyric naming the song (a chorus repeating the
  title) that the model also gave too little chance of being the song to keep, and `headerLinesOf`'s KDoc keeps such a
  line for that reason (a misjudged song line must never be lost). The existing test keeps passing, and the running
  header goes. Blank origins stay out of `header` as 01 leaves them.

Either way, one threshold: expose `:libs:pagesong`'s rule instead of copying it, e.g. a public
`PageLineScores.isUnsure(vararg labels: PageLineLabel)` (or `PageCrop.isUnsureHeader(index)`) that rounds as `cropOf`
does, used by `cropOf` for `SONG + METADATA` and by `ChordSheetSongCrop` for `TITLE + ARTIST`; delete
`ChordSheetSongCrop`'s private `UNSURE`. Update `cropped`'s KDoc ("the lines it reads as the title and the artist"),
`convert/CLAUDE.md`'s crop bullet ("every line with a 2% chance of being the title or the artist" becomes "… above the
song"), and `libs/pagesong/CLAUDE.md` if a public helper is added.

If `local.properties` names `campfire.importCorpus`, run
`./gradlew :data:source:local:implementation:desktopTest --tests '*ImportCorpusTest'` before and after and report the
totals and the documents whose score moved (`.runs/report.txt` lists the delta against the previous run).

## Tests

In `ChordSheetSongCropTest`:

- New: a song with a line scored junk 0.95 / title 0.03 / artist 0.02 between its verses converts without that line.
- New: the same line above the song's first song line is kept, so the converter still reads the header from it.
- New: a line labelled `TITLE` (title 0.9, junk 0.1) between two verses, which is not `crop.titleLine` (a surer
  `TITLE` line heads the song), is still kept.
- The existing unsure-title test passes unchanged (option b).

If a public helper is added to `:libs:pagesong`, `CropRuleTest` covers its rounding (0.01996 keeps, as the existing
``a chance that rounds to the threshold keeps the line as in the gate`` does for the song).
