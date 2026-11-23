# Keep a chord row the model drops only inside a document's song or straight after it, not every chord row below it

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Decided:** option (b), by the owner (2026-11-23).
**Files:** `libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongCrop.kt`,
`libs/chordsheet/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongCropTest.kt`,
`libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

Lane C, second of four (after 01, before 03). Shares `ChordSheetSongCrop.kt`, its test, and `convert/CLAUDE.md` with
01 and 03; 01 changes how `body` is found (first non-blank `SONG` line), which this plan builds on. Another session was
committing to `libs/chordsheet` when this was written: quotes are as at HEAD `79bb02af9`; re-locate by the quoted snippet.


## Problem

A document's song keeps every chord row from its first song line to its end, however far below the song it sits:

```kotlin
// libs/chordsheet/.../convert/ChordSheetSongCrop.kt:39-43
val body = origins[crop.scores.indexOfFirst { it.label == PageLineLabel.SONG }]
return song.filter { line ->
    val origin = line.origin ?: return@filter true
    origin in kept || line.text.isBlank() && origin in span || origin >= body && ChordSheetConverter.isChordRow(line.text)
}
```

`convert/CLAUDE.md` gives the reason ("a chord row from the song's first song line on (the model reads a lone closing
chord as a page number)"), and `ChordSheetSongCropTest`'s ``a chord row in the song's body is kept even where the model
reads it as clutter`` pins it: an `Outro` heading, then `G` scored `JUNK`, kept. The `cropped` KDoc does not mention the
rule, and the page path (`PageTextCrop`) has none.

But a printed web page's chord diagrams drawn as characters (`G`, `×`, `3`, `o`, …, `Am`, …), which the trainer now
labels `JUNK` (commit `1c174786c`, `lines.py`'s `diagram_runs`), sit under the song. The marks go, but every chord name
among them is a chord row past `body`, so it stays. A probe through `ChordSheetConverter.convert(..., pageScores)` with a
four-line song followed by "Chords used" and a diagram block of `G`, `Am`, and `D` (each name and mark scored `JUNK`)
converted to:

```
{title: Amazing Grace}

[G]Amazing grac[C]e, how sw[G]eet the sound
[G]That saved a wret[D]ch like me
G
Am
D
```

Three stray chord-only lines end the song, which then show as a closing progression that the song does not have.

## Fix

Options:

- **(a)** Keep a dropped chord row only between the first and last kept line (`origin in span`, `span` as it is).
  Simplest and symmetric with the blank-line rule, but loses the lone closing chord the rule exists for, and breaks the
  existing test's intent.
- **(b) Recommended.** Keep a dropped chord row inside the span, and after the last kept line only while every line
  between it and the last kept line is blank or another such chord row: an outro's chord row (or a run of them) right
  under the song stays; a chord row after anything else the crop dropped ("Chords used", a diagram mark) does not.
  This keeps what the rule was written for, drops a diagram block's names past its first mark, and the first diagram
  name directly under the song (no heading between) is the only stray left, which is no worse than today.
- **(c)** Drop the rule and trust the model. The documented failure (a lone closing chord read as a page number) comes
  back; not recommended without a retrain that fixes it.

For (b): walk the song's lines after `kept.max()` in order; keep a chord row while no non-blank line the crop drops has
been passed. Inside the span the rule stays `origin >= body && isChordRow`. Write the rule into `cropped`'s KDoc
(why: the model reads a lone closing chord as a page number) and update `convert/CLAUDE.md`'s crop bullet ("a chord row
inside the song or straight after it").

## Tests

In `ChordSheetSongCropTest`:

- Keep ``a chord row in the song's body is kept even where the model reads it as clutter`` passing as is (the `G` after
  `Outro` is straight after the last kept line).
- New: the song above, then a `JUNK` "Chords used" line and a diagram block (`G`, `×`, `3`, `2`, `o`, `Am`, `×`, `o`,
  `2`, `D`, all `JUNK`) converts as the song alone.
- New: a `JUNK` chord row between two kept song lines is still kept.
