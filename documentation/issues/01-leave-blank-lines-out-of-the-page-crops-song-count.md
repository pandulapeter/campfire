# Leave blank lines out of the page crop's song count and its first song line, as the gate does

**Challenged:** amended — blank lines are also never the title or artist line (`bestLineOf`) nor a document song's
unsure header line (`ChordSheetSongCrop`'s `header`), which would otherwise still let an untrained blank stretch the
kept span; a test for each.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/CropRule.kt`,
`libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/PageCrop.kt`,
`libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/PageSong.kt`,
`libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/PageLineLabel.kt`,
`libs/pagesong/src/commonMain/kotlin/com/pandulapeter/campfire/pagesong/LineText.kt` (only to reuse `isSpace`),
`libs/pagesong/src/commonTest/kotlin/com/pandulapeter/campfire/pagesong/CropRuleTest.kt`,
`libs/pagesong/src/desktopTest/kotlin/com/pandulapeter/campfire/pagesong/PageSongTest.kt`,
`libs/pagesong/CLAUDE.md`,
`libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongCrop.kt`,
`libs/chordsheet/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetSongCropTest.kt`,
`libs/chordsheet/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/importing/PageSongLines.kt` (KDoc only)

Lane C, first of four (01, 02, 03, 04, in that order). Shares `ChordSheetSongCrop.kt`, its test, and
`convert/CLAUDE.md` with 02 and 03, and the crop's behaviour with 04's `PageCorpusTest`. Another session was committing
to `libs/chordsheet` when this was written: quotes are as at HEAD `79bb02af9`; re-locate by the quoted snippet.

## Problem

The model was never trained on blank lines: the trainer labels them `BLANK` (`tools/page-model/teacher.py` ~130,
`align_documents.py`, `synthetic.py`), which `gate.py`'s `tensors` turns into target `-100`, ignored by
`CrossEntropyLoss(ignore_index=-100)`:

```python
# tools/page-model/gate.py:67
targets = torch.tensor([LABELS.index(label) if label in LABELS else -100 for label in example["labels"]], dtype=torch.long)
```

and the gate scores and counts only printed lines:

```python
# tools/page-model/gate.py:121, 126
printed = [index for index, line in enumerate(example["lines"]) if line["text"].strip() and example["labels"][index] != "IGNORE"]
...
is_song = sum(1 for index in found if predicted[index] == "SONG") >= MIN_SONG_LINES
```

The app's crop counts every line, blank ones included, whatever the untrained output says of them:

```kotlin
// libs/pagesong/.../CropRule.kt:36
val holdsSong = kept.count { labels[it] == PageLineLabel.SONG } >= MIN_SONG_LINES
// CropRule.kt:44
headerLines = if (holdsSong) headerLinesOf(listOfNotNull(titleLine, artistLine), kept, kept.first { labels[it] == PageLineLabel.SONG }) else emptyList(),
```

and a document's song uses the same first `SONG` line for its chord-row rule:

```kotlin
// libs/chordsheet/.../convert/ChordSheetSongCrop.kt:39
val body = origins[crop.scores.indexOfFirst { it.label == PageLineLabel.SONG }]
```

`cropOf` never sees the lines' text (`PageCrop.of(scores)` takes only scores), so it cannot tell a blank line apart.

Measured with the shipped weights on the 616 labelled pages of the page corpus (a probe, since deleted): the model
labels 7,921 of the 25,085 blank lines `SONG`, and keeps 15,564 of them. On 171 pages the first `SONG` line is a blank
one; on 12 of those `headerLines` differ from what the gate's rule gives (a "Title / blank / lyrics" page whose blank
is labelled `SONG` reads the title as running straight into the song, so `headerLines` is empty and the title is not
read as the header). On 1 page (`8db9da5633f9`) `holdsSong` is true only thanks to blank lines: a page with fewer than
four printed `SONG` lines is cropped to them instead of converted whole, and a page read from its link is accepted
instead of refused (`PageCropperImpl.songLinesOf` answers null only for `!crop.holdsSong`). `PageCrop.of` with two text
and two blank `SONG` lines answers `holdsSong = true` today.

The docs claim otherwise: `cropOf`'s KDoc says "by the same rule the gate scored the model with", `PageLineLabel.SONG`'s
says "A line of the song: lyrics, chords, tabs, section headings, the blank lines between them", and
`libs/pagesong/CLAUDE.md` says the crop "is `gate.py`'s `unsure` rule".

## Fix

1. `cropOf` takes which lines are blank: `internal fun cropOf(scores: List<PageLineScores>, lineCount: Int, blankLines: Set<Int>)`.
   A blank line is one the gate's `line["text"].strip()` empties: every code point Python's `str.isspace` (`LineText.kt`'s
   `isSpace`, on the raw text, not `visibleTextOf`: a line of only format characters is printed to the gate).
   - `PageSong.crop` computes it from `lines` (the first `MAX_LINES`).
   - `PageCrop.of(scores)` gains a required `blankLines: Set<Int>` (indices into `scores`). Its callers at HEAD:
     `ChordSheetSongCrop.cropped` and `CropRuleTest` (`grep -rn "PageCrop.of\|cropOf(" --include='*.kt'`).
2. In `cropOf`, a blank line is never kept by the model's say: `kept` is filtered to non-blank lines before
   `holdsSong`, before the first song line `headerLinesOf` receives, and in `keptLines`. Every caller already puts back
   the blank lines between its first and last kept line (`PageTextCrop.keptText`, `ChordSheetSongCrop`'s `span`), which
   is the "pick up blanks only between kept lines" rule; state it in `PageCrop.keptLines`' KDoc ("never a blank line
   where the model sees a song: the caller keeps the blank lines between kept ones") and `PageSongLines.keptLines`'.
   Where the model sees no song, `keptLines` stays every line.
   - The same holds for `titleLine` and `artistLine`: `bestLineOf` picks among non-blank lines only (the gate's `title`
     and `artist` are printed lines too). A blank line labelled `TITLE` would otherwise become the title line where the
     page has no better one, be added back by `PageTextCrop` (`namedLines`) and stretch the span the blanks are put
     back in, and reach `headerLines` though `PageTitle.of` reads nothing from it.
   - Alternative: let `cropOf` itself add the blanks between the first and last kept line, so `keptLines` is complete.
     Not recommended now: the callers would still add blanks around the title and artist lines they add, so the rule
     would live in three places instead of two; plan 04 is where the duplication is taken on.
3. `ChordSheetSongCrop.cropped`: an origin is blank where every `Rendered` line of that origin has blank text; pass
   those (as indices into `origins`) to `PageCrop.of`, and take `body` as the first non-blank `SONG` line. Leave the
   blank origins out of `header` too (`crop.scores.indices.filter { isHeader(...) }` reads the raw scores, not the
   crop): a blank line far below the song given 2% of being the title would otherwise widen `span`, keeping every blank
   line up to it (and, after 02, deciding where "after the last kept line" starts).
4. Docs: `cropOf`'s KDoc may then keep "the same rule the gate scored the model with"; `PageLineLabel.SONG` drops "the
   blank lines between them" and says blank lines are never labelled (the model was not trained on them);
   `libs/pagesong/CLAUDE.md`'s **The crop** bullet says blank lines are left out of it as the gate leaves them out;
   `convert/CLAUDE.md`'s crop bullet only if its wording now misleads.

Then, if `local.properties` names `campfire.pageCorpus` and `campfire.importCorpus`, run
`./gradlew :data:source:local:implementation:desktopTest --tests '*PageCorpusTest' --tests '*ImportCorpusTest'` before
and after the change (in a worktree, pass both with `-P`), and report `PageCorpusTest`'s printed summary and the
`ImportCorpusTest` report's totals (`.runs/report.txt`) for both. Expect page counts to move by a handful, not more; a
large drop in title/artist hits or song lines kept means the fix is wrong, not the gate.

## Tests

- `CropRuleTest`: a page of `junk, song(blank), song, junk, song(blank), song, junk` holds no song and keeps every
  line; the same with four printed `SONG` lines holds one and its `keptLines` has no blank index; `title, song(blank),
  junk, song, song, song, song` with the blank labelled `SONG` gives `headerLines == [0]`, as the gate's rule (first
  printed `SONG` line is 3, the title does not run into it); update the existing calls for the new parameter
  (`emptySet()` keeps their meaning); a blank line labelled `TITLE` with a higher title score than the only printed
  `TITLE` line is not `titleLine`, and with no printed one `titleLine` is null.
- `PageSongTest`: a line of spaces and a no-break space is blank, a line of a zero-width space alone is not (the gate's
  `strip()`), through an internal seam such as `blankLinesOf(lines)` rather than through a model's output.
- `ChordSheetSongCropTest`: a song whose blank lines are scored `SONG` among only three printed song lines (with a
  `JUNK` line in it, so cropped and whole differ) is converted whole; and, calling `ChordSheetSongCrop.cropped`
  directly (internal, same module; `Rendered(text, DoubleArray(0), ChordSheet.Line(...), origin = i)`), since the
  converter trims trailing blank lines and a conversion would pass either way: a song followed by blank, `JUNK`, blank,
  and a blank scored junk 0.95 / title 0.05 returns no line after the song's last line.
