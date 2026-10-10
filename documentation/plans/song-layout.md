<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Song layout as a designer would set it — implementation plan

Written 2026-10-10 against `51f8bd98e`. The song details screen lays a song out the way a designer would by hand:
the fewest pages, as today, and among the layouts with that many pages the one that reads best — no section header
stranded over a single line, no lone line continuing a verse at the top of a column, cuts between stanzas rather than
inside them, columns ending close together — decided by a score rather than by the greedy fill and the pixel-exact
evening out that decide it now. Two more things a designer does with a song that is a few lines too long for its last
page are part of it, not options: **columns narrower than the readable minimum** wherever the song's own lines allow
it and that saves a page, and **text a few percent smaller** wherever that saves a page. Nothing of this touches what
the screen shows a song of one page, how it is stepped through, or the PDF.

The evidence this plan rests on is a simulation of the three over the 327 songs of `tools/screenshots/library` with
approximate heights (about 43dp per chorded line at the default size, no tab staves, no narrow pairs), which is why
the counts below are indicative and the comparisons between algorithms are what to trust:

| Laptop window of 1333 × 837dp at 105% text, 281 songs of more than one page | Today | Scored |
|---|---|---|
| Pages in total | 586 | 586 |
| Pieces of one line | 396 | 63 |
| Pieces of two lines | 334 | 330 |
| Cuts in total | 680 | 428 |
| How far apart a page's column bottoms end, in chorded lines | 0.8 | 1.4 |

The 63 pieces of one line that remain are forced: those songs fill their pages exactly, and the alternative is a page
more. On a phone (360 × 640dp, one column) the pieces of one line go from 621 to 218 and the cuts from 1180 to 860, at
equal page counts. One scrolling song in five ends on a page under a quarter full (20 of 281 on the laptop, 31 of 144
on the iPad at 90%, 33 of 318 on a 1024 × 768dp tablet); a 5% smaller text takes that page away for about half of them
(16 of 34, 20 of 37 and 34 of 50 of the songs whose last page is under a third full), and a fourth column of about
300dp makes 77 of the iPad's 144 two-page songs one page, at about five wrapped lines per song.

## 1. What is being built

1. **A score for a layout** (`SectionGrid.kt`): points for every stub a cut leaves, for every cut, for every wrapped
   line a narrower column costs and for the empty space at the end of every column on a page that is not the last,
   the page count still deciding first (see 2). Every choice the layout makes between two ways of setting the same
   song — where its columns end, how many columns a page has, whole sections or cut ones, a pair of narrow sections
   side by side or stacked — is made by (pages, score), lowest first.
2. **The flows decided by that score** (`flowLikeAMagazine`, `flowIntoPages`): a dynamic program over where every
   column ends, the way TeX breaks paragraphs into lines, in place of the greedy fill and the binary search for the
   lowest cap that stand there now (see 3). The grid it returns is the same `SectionGrid`, so nothing above the grid —
   `arrange`, the rows, the snapping, the step buttons, the dots, the glides — changes.
3. **Columns narrower than `MIN_COLUMN_WIDTH`** (`SongSectionsLayout.kt`): a count of columns that only fits at a
   width between 288dp and 384dp (times the text scale) is a candidate wherever a song's lines mostly fit such a column
   without wrapping, taken only where it saves a page — a song that then fits the screen in one row is read without
   stepping at all (see 4).
4. **Text fitted to the page** (`SongSectionsLayout.kt`, `SongLyrics.kt`): a song that is a few lines over a page
   is set at most 5% smaller where that saves a page, decided in the same search as the columns and drawn through a
   scaled layer rather than by composing the song again, so the first frame is already the fitted one (see 5).

### What is already there, and what is not

- The three decisions this plan replaces are all in `SectionGrid.kt`: `flowLikeAMagazine` fills the columns of a row
  greedily (`columnEnd`, `fill`), binary-searches the lowest cap the row still fits under in its columns (`lowestCap`)
  and fills again under it (`rowOf`), first-fit in both passes, so a cut that strands one line is as good to it as a
  cut between two stanzas; `flowIntoPages` does the same for a single column, with a pair of narrow sections side by
  side; and `searchSegment` in `SongSectionsLayout.kt` takes, of every column count from the most down to two, the
  fewest pages and the most columns on a tie, and the whole sections (`flowIntoRows`) only where they take strictly
  fewer pages still.
- Everything the score needs is already measured. `SongUnits` knows every chunk's section (`unitSections`), where a
  section may be cut (`isCuttableBefore`, from `sectionChunkStarts`, which keeps a line on either side of a cut and
  never starts a piece with a blank line or ends one with a comment), which sections are on a card (`cardStarts`)
  and which head a timing stretch (`isTiming`). `SectionMeasurements.height(unit, width)` gives a chunk's height at
  any width, cached, and `maxWidth(unit)` the width at which nothing in it wraps, so the lines a width wraps are the
  difference of the two heights in line heights. `SongTextMeasurements.lyricsLineHeight` is the height of one line
  of lyrics.
- What the score does not have yet: which chunks are lines of lyrics as opposed to a row of chord diagrams, a comment
  or an empty line (`RenderSection.Lines` keeps `itemKinds` for `sectionChunkStarts`, but `SongUnits` only keeps the
  `isCuttableBefore` it derives from them), and the height of a chorded line in pixels, which is the unit empty space
  is counted in.
- The column count is bounded by `MIN_COLUMN_WIDTH * fontScale` (384dp) in `widthColumnCountFor`, and nothing asks
  how wide a song's lines are before deciding it: a song of short lines gets the same three columns on a laptop as a
  song whose every line is a sentence.
- The text scale reaches `SongLyrics` as `fontScale` and is applied by composing every line in a style scaled by it
  (`lyricsStyle`, `chordStyle`, `headerStyle`), so a different scale is a different composition and a different set of
  `SectionSizes`. The sections are placed by `SongSectionsLayout` inside a `LookaheadScope`, with `animateBounds` or
  a glide, and placed with an offset (`glideOf`) but no layer.
- `tools/screenshots`' `Website.kt` picks a text scale per song for the website's screenshots "at which the page ends
  exactly where a section does, never inside one": a hand-made version of 5, which this plan should make unnecessary.
- The PDF (`ui/print/PrintLayout.kt`) has a column flow of its own, which keeps a section that fits a column whole and
  otherwise runs it on; it is not touched here (see 10).

### Defaults this plan assumes — veto any of them before the work starts

1. **The fewest pages still wins over everything.** The score only chooses between layouts of the same page count.
   A layout with an orphan on two pages beats a clean one on three, always; what the forced cases get instead is a
   smaller text (5).
2. **Slack stays at the end of the song.** The pages before the last are filled as full as the score allows and the
   last page takes what is left, as today; a layout that spreads the slack over every page so that all of them are
   equally full is not built, since the first screen of a song should hold as much of it as it can for a reader who
   steps through it. (The columns of one page are still evened out against each other.)
3. **The smaller text is silent and bounded.** The layout may set a song up to 5% smaller than the text size the
   reader chose, in steps of 2.5%, never below `UserPreferences.MIN_FONT_SCALE`, and only where that takes a page
   away. The stepper and the menu keep showing the reader's own size; there is no switch for it, as there is none
   for the cutting of sections.
4. **Narrow columns only ever save a page, and only for a song that fits them.** A count that needs columns narrower
   than `MIN_COLUMN_WIDTH` is never taken on a tie, and it is not a candidate at all where more than a quarter of the
   song's lines of lyrics would wrap in it. The floor is `NARROW_COLUMN_WIDTH = 288.dp` times the text scale, three
   quarters of the minimum. A phone held upright stays one column (360dp holds no two columns of 288dp); the default
   desktop window of 800 × 600 can become two columns of about 360dp where that saves a page.
5. **The weights are constants, not preferences.** The points in 2 are `private const val`s next to the flow, tuned
   on the demo songs and the screenshot library; nothing in Settings changes them.
6. **A row of chord diagrams is never an orphan.** The stub penalties are for lines of lyrics only; a Chords section
   cut between its rows, a grid, a tab system, a comment or the song's own first section pay for the cut and nothing
   else.

## 2. The score

A layout is scored in **points**, where one point is what a column pays for ending one chorded line short of the
screen: the unit is `lineHeight`, the height of a line of chords over a line of lyrics, handed to the flows by
`SongSectionsLayout` as `2 * textMeasurements.lyricsLineHeight` (what `SongDetailsPage` takes a chorded line to be for
`ReadingWindow.overlap`). The flows take, besides what they take today:

- `lineHeight`: the unit above, in pixels.
- `lineCount(unit)`: how many lines of lyrics a chunk is — 1 for a line of lyrics with or without chords, 0 for an
  empty line, a comment, a row of chord diagrams, a grid, a tab system or the first section's controls. `SongUnits`
  gets it from the sections it is built over (`RenderSection.Lines.itemKinds`, `CONTENT` under a `Lines` section only).
- `isStanzaBreakBefore(unit)`: whether a cut in front of this chunk falls after an empty line or in front of a
  comment — a place the writer of the song already marked as a boundary. From the same kinds.
- `wrappedLines(unit, columnCount)`: how many extra line heights the chunk takes at that width compared with its
  `maxWidth`, `(height(unit, width) - height(unit, maxWidth)) / lyricsLineHeight` rounded, 0 for everything but a
  line of lyrics. A chorded line that wraps counts twice, since its continuation carries a chord row too, which is
  the intent: that wrap is uglier.

The penalties, each a named constant in `SectionGrid.kt`:

| What | Points | Why |
|---|---|---|
| A piece of a cut section holding one line of lyrics (`STUB_PENALTY`) | 12 | A header over one line, or one line continuing a verse at the top of a column, is what a designer never lets through. |
| A piece holding two lines (`PAIR_PENALTY`) | 4 | Better than one, still a stub. |
| Any cut (`CUT_PENALTY`) | 2 | A section in one piece reads best. |
| A cut after an empty line or in front of a comment, instead of the above (`STANZA_CUT_PENALTY`) | 0.5 | The writer marked the boundary; cutting there is almost free. |
| Cutting a section of four lines or fewer at all (`SHORT_SECTION_PENALTY`, on top of the cut) | 6 | A four-line verse in two halves is never worth a line of space. |
| Each column of a page that is not the last, per `((screen - column height) / lineHeight)²` (`EMPTY_PENALTY`, 1 per square line) | 1 | One short line is nearly free, three are expensive: the square is what spreads the slack over a page's columns rather than dumping it on one. |
| The columns of one page ending apart, `0.5 × (spread / lineHeight)²` (`SPREAD_PENALTY`) | 0.5 | Lined up bottoms look set; a line or two of rag is fine. Only in the evening-out pass (3.3), where it is the one thing besides the cuts that varies. |
| Each wrapped line height (`WRAP_PENALTY`) | 0.75 | Only tells column counts apart: at one width every layout wraps the same. |

The pieces: a section cut between two columns is as many pieces as it has columns; the first piece carries the
header and the stub penalty is paid by any piece, first, middle or last, by its `lineCount` sum. A section not cut
pays nothing. The empty space of a column is counted from its content's bottom, the padding of a cut card included,
to the screen (`maxRowHeight`), and never on the last page of a stretch, which is the end of the song.

Two layouts are compared by `(pages, score)`: pages as `SectionGrid.pageCount(...)` counts them today (a row taller
than the screen as every page it is paged through in), score as a `Double` summed over every stretch. This is the
one comparison the plan introduces, and it is used everywhere `searchSegment` chooses today: between column counts,
between the sections whole and the sections cut, and — new — between the readable column counts and the narrow ones
(4) and between the text as it is and the text fitted (5).

## 3. The flow (`SectionGrid.kt`)

### 3.1 Fewest pages first

The page count a column count can reach is still found by the greedy fill, which is optimal for the count: `fill`
under `maxRowHeight` from the first unit, every column as full as it goes, gives `K` columns and so `P = ceil(K /
columnCount)` pages, and nothing else is tried for fewer. `columnEnd` and `fill` stay for that, unchanged.

### 3.2 The dynamic program

`flowLikeAMagazine` then places the columns of the first `P - 1` pages and the last page by a dynamic program over
the chunks: `best[k][i]` is the lowest score of the first `k` columns holding the chunks `[0, i)`, `k` from 0 to
`P * columnCount`, `i` from 0 to the unit count, and the transition is a column `[j, i)` that fits `maxRowHeight`
with its paddings — the same height arithmetic as `columnEnd`'s, a column starting inside a section with its card's
padding, one ending inside a section with it, a section gap in front of every section start but the first — ending
where a column may end (a section start, or a cuttable chunk with a height, as `isBreak` says). The cost of the column
is its cut penalties (every piece in it, by `lineCount`, stanza breaks and short sections as in 2) and, for `k` below
`(P - 1) * columnCount`, its empty space squared; the columns of the last page pay no empty space. The answer is the
lowest `best[k][unitCount]` over `k` in `((P - 1) * columnCount, P * columnCount]`, walked back through a `back` table
into the column of every chunk, the pages being every `columnCount` columns.

The inner loop stops at the first `i` whose column no longer fits, as `columnEnd` does, so the work is the unit count
times the columns of the song times the chunks a column holds, under a million operations for a long song at the
widest window, which the grid search does once per settled width (`SectionMeasurements.grid`) and the pinch once per
frame as today. A stretch of the song that cannot be cut and is taller than the screen on its own (a tab system at a
large text size) is a single column row of its own and paged through, exactly as `filledColumns == 0` handles it now:
such a stretch is placed before the program runs, which then runs on the chunks between two of them.

### 3.3 Evening out

A page's columns are evened out as a second pass over that page alone, in place of `lowestCap`: the lowest cap the
page's chunks still fit under in its number of columns is found by the same binary search, each probe being the
program of 3.2 over the page with the cap as the hard limit and the column count exact; then the program is run again
at that cap, at the cap plus one, two and three `lineHeight`s, and at the screen, and of those the layout with the
lowest `cut penalties + SPREAD_PENALTY × (spread / lineHeight)²` wins, spread being its tallest column less its
shortest. That is what lets a column end a line short rather than strand one, and still lines the bottoms up where
no cut is the price. The last page takes the fewest columns that hold it, tried from one up with the heights of that
width as today (`rowOf`'s fallback order), and is evened out the same way.

### 3.4 A single column (`flowIntoPages`)

The same program with one column per page, so pages and columns are one thing: `P - 1` pages with the empty space
penalty, the last without, a page that cannot be cut to fit (a tab system, a pair) and would leave the one before it
under half full still running that page on past the screen, as today. The pair of narrow sections is one more
transition: at a chunk that starts a section `s` where `s` and `s + 1` are both `isNarrow`, the program may take both
sections whole as one block of `max(height(s, 2 columns), height(s + 1, 2 columns))`, which is a row of two columns
joining the page (`joinsPrevious`), paying no cut; the stacked alternative is the ordinary transitions. The rule that a
pair is never cut, and that where it does not fit what is left of a page it is stacked wherever its first section can
start there, falls out of the costs: stacking and cutting pays cut penalties, pairing pays none, and the empty space
decides between them, so the three tests of `SectionPagingTest` about pairs should pass or need only their numbers
adjusted.

### 3.5 Whole sections, stretches and songbooks

`flowIntoRows` stays as it is and stays a candidate: `searchSegment` scores its grid too (no cuts, the empty space of
its rows' columns, its wraps) and takes it where `(pages, score)` is lower, so a song whose sections happen to fill
the columns whole keeps them whole where today the cut layout wins the tie by being evened out. The stretches a change
of tempo or time signature cuts the song into (`timingStarts`) are laid out one after the other as today, each with a
last page of its own; the score of the song is their sum. A song of more than `MAX_CUT_SECTION_COUNT` sections keeps
`flowIntoRows` alone, as today.

## 4. Columns narrower than the minimum

`widthColumnCountFor` keeps giving the count of readable columns, `MIN_COLUMN_WIDTH` wide at least. A second count,
`narrowColumnCountFor`, is the count at `NARROW_COLUMN_WIDTH * fontScale` (288dp), and the candidates `searchSegment`
tries become every count from the narrow one down to two — but a count above the readable one is a candidate only
where, at its width, no more than a quarter of the song's lines of lyrics wrap (`wrappedLines` over the units with a
`lineCount`, against their number), and it is taken only where its `(pages, score)` has strictly fewer pages than the
best readable count, never on a tie, whatever its score. `searchColumnCount` — the whole-section search for the fewest
columns that fit the song in one row — gets the same extended range under the same two rules, which is the biggest
win: a song that needs a few lines more than three readable columns hold is read in four narrow ones without a single
step.

The width of a column at a count is `columnWidthFor` as today; `maxColumnCount` in `SectionGridKey` becomes the narrow
count, so the key changes exactly when the candidates do. The step buttons' inset and the full width are searched as
today, each with the extended range.

## 5. Fitting the text

### 5.1 The decision

After the columns are decided for the text as it is, `searchSegment` asks whether a smaller text takes a page away:
for each `fitScale` of 0.975 and 0.95 (`FIT_SCALE_STEPS`), while `fontScale * fitScale >= MIN_FONT_SCALE`, the same
search is run with every height read at `width / fitScale` and multiplied by `fitScale` — a line at a smaller size in
a column is the same line at the full size in a proportionally wider column, so `SectionMeasurements.height(unit,
round(width / fitScale))` already holds what it needs, and no second composition is measured — with `maxRowHeight`
and `availableHeight` unchanged. The first scale whose best layout has fewer pages than the unscaled best wins; if
none does, the text stays as it is. The paddings that do not scale with the text (`CARD_PADDING`, the header pill's
`HEADER_VERTICAL_PADDING`, the diagrams' `FRETTED_HEIGHT`) are predicted a few pixels large by this, which only ever
errs towards keeping the page: a prediction that turns out wrong at 5.2 costs a slightly smaller text with the same
pages, and nothing else.

Fitting is only ever tried for a song that is stepped through — never for one read without stepping, which fits
already — and never in the editor's preview (`canCutSections` is false there). The scale chosen is part of
`DecidedGrid` (`fitScale`), so `SectionMeasurements.grid` caches it with the grid.

### 5.2 The drawing

The fitted song is not composed again at the smaller style. `SongSectionsLayout` measures every chunk at
`round(unitWidth / fitScale)` and places it with `placeWithLayer` scaling it by `fitScale` from its top start corner
(`transformOrigin` at 0, 0), taking `round(placeable.height * fitScale)` of the layout's height and `unitWidth` of its
width; `arrange` is given the scaled heights. A scaled layer transforms both the drawing and the hit testing, so the
fold chevrons and the steppers in the song's first section keep working, and the text is drawn through the canvas
transform rather than from a bitmap, so it is as sharp as at any other size. The glide offsets (`glideOf`) and
`animateBounds` work on the placement as before: the lookahead pass measures at the same scaled width, so what it
reports is the fitted size.

`fitScale` is 1 wherever the layout is not paged, where the song fits without it and everywhere the preview is
drawn, and placing with a layer at scale 1 is placing without one, so the one branch costs nothing where it does not
apply.

### 5.3 What it means for the rest of the screen

- The stepper, the menu and `UserPreferences.fontScale` say the reader's size; `SongHeaderNote`, the app bar and the
  playing controls are composed at the reader's size, not the fitted one, since they are not part of the pages.
- A pinch changes `fontScale` per frame; the fit is decided again with every settled grid, so during a pinch the
  layout may switch between fitted and not as pages come and go, which is one more grid change and glides like the
  rest. The `ReadingAnchor` keeps the reader's place through it as through any grid change.
- The PDF export is unaffected (`PrintLayout` reads `PrintSettings.fontSize`).
- `tools/screenshots`' per-song text scales (`Website.kt`) are kept as they are until the screenshots are retaken
  with this plan landed, at which point the comment there about choosing a scale to avoid cuts should go.

## 6. Corner cases, and what each one does

- **A section whose every cut leaves a stub** (a three-line verse that does not fit what is left of a column): the
  program leaves the column short and starts the verse on the next one, paying the empty space, unless that costs a
  page — then it pays the stub, since pages decide first. Today it always cuts.
- **A chorus on a card cut at a stanza break**: the pieces keep the card's padding at the cut, the cut costs 0.5 plus
  the stubs, so a card is cut between stanzas rather than inside one where both fit.
- **The song's first section** (the playing controls, the key, the tempo) and the Chords section have `lineCount` 0,
  so cutting the diagrams between two rows costs a cut and nothing else; the first section is never cut (one chunk).
- **A tab section at a large text size** whose one system is taller than the screen: a row of its own, paged through,
  placed before the program as today (3.2).
- **A narrow count that saves a page only with a wrapped chorus**: taken if under a quarter of the lines wrap, as
  rule 4 of the defaults says, since the page is worth it; the chorus's card is then as wide as the column.
- **A narrow count on a tie** (same pages as the readable one): never taken, whatever it scores.
- **The fitted text on a song that is one line over**: 2.5% is tried first and usually enough; 5% at most. A song two
  pages over stays at the reader's size: the fit only ever removes the last page.
- **A pinch down to `MIN_FONT_SCALE`**: no fit is tried below it, so the floor the reader was promised holds.
- **The editor's preview**: no cutting, no narrow columns, no fitting, as today.
- **A songbook** (more than 200 sections): whole sections, readable columns, no fitting; nothing changes.

## 7. Tests (`:presentation`'s `desktopTest`)

The fixtures in `SectionGridFixtures.kt` get a `lineHeight` (100, the height of a chorded line in every fixture) and
line counts (every chunk one line, every section header-less, unless a test says otherwise), so the existing tests of
`SectionCuttingTest` and `SectionPagingTest` keep running. What changes and what is added:

- `a section is cut to even the columns out` becomes **a section is not cut to even the columns out where the cut
  would strand a line**: the same two sections, the second kept whole in the second column, the first column a line
  short. A new test, **a section is cut to even the columns out where the cut falls between two stanzas**, has an
  empty line in the middle of the second section and expects the cut there.
- **no piece holds a single line while a column can be left short instead**, over the random songs of `rows are
  never taller than the screen and hold the song in its order`: for every page but the last, a piece of one line
  exists only where filling the column without it would cost a page (checked by re-running the greedy count).
- **pages are the fewest the greedy fill reaches**: the scored grid's `pageCount` equals the greedy one's for every
  random song, every column count.
- **the whole sections win a tie**, in `SongSectionsLayout`'s terms: a song whose sections fill two columns exactly
  is not cut.
- **a narrow column count is taken only where it saves a page**, **and not where more than a quarter of the lines
  wrap**, **and never on a tie**: over a fixture whose heights at the narrow width are given by a lambda.
- **the fitted text is tried only where it takes a page away**, **never below the minimum scale**, **and the smaller
  step first**: the search given heights by width that shrink a page's worth at 0.975.
- `SectionPagingTest`'s pair tests keep their expectations, with numbers adjusted where the empty-space penalty, not
  the old rule, now decides; `SectionGridTest` (`flowIntoRows`, `arrange`, `searchColumnCount`) is unchanged but for
  the extended column range.

## 8. Order of work

1. `SongUnits`: `lineCount`, `isStanzaBreakBefore`; `SongSectionsLayout`: `lineHeight` and `wrappedLines` handed to
   the flows. Nothing decides differently yet.
2. `flowLikeAMagazine` as 3.1–3.3, with its tests; the demo songs and the screenshot library checked by eye at the
   laptop and tablet sizes against the website's screenshots.
3. `flowIntoPages` as 3.4, with its tests; checked on the phone and the 800 × 600 desktop window.
4. `searchSegment` comparing by `(pages, score)` everywhere (3.5), the whole-section tie included.
5. Narrow columns (4), with its tests and the `SectionGridKey` change.
6. Fitting the text (5): the decision in `searchSegment`, `DecidedGrid.fitScale`, the scaled placement, its tests.
7. `ui/screens/songDetails/CLAUDE.md` (the layout paragraphs: the fewest pages, the score, the narrow columns, the
   fit), the root `CLAUDE.md`'s line about the pure helpers tested, and `documentation/TO_DO.md`.
8. The website's screenshots retaken (`store-screenshots`), the per-song scale hack in `Website.kt` removed if the
   fit makes it unnecessary.

## 9. Checks owed by hand

- **House of the Rising Sun** in the default desktop window (800 × 600, default text): now two columns of about
  360dp where that saves a page; every page full to within a couple of lines, no verse cut 3 + 1; the stepping, the
  dots and the pedal keys as before.
- **The three website songs** at the laptop and iPad sizes: I'll Be There for You's Chorus 2 whole in the third
  column or the second column a line short; Accidentally in Love's Verse 2 and Verse 4 cut 3 + 2 or whole; Still Alive
  as the screenshot shows it (Verse 2 in one column is what `Website.kt` tuned the scale for).
- **A song one line over a page** (take Blowin' in the Wind at the laptop size) fitted at 97.5% and one page; the
  stepper still says 100%; pinching down and up switches the fit off and on with a glide, no jump.
- **A song of short lines** (Accidentally in Love on the iPad at 90%) in four columns on one page, read without
  stepping; a song of long lines (Still Alive) staying at three.
- **The fitted text's sharpness** on every platform: Android, iOS, the desktop (macOS and, if at hand, Windows) and the
  web, at 95%, zoomed in on a screenshot: glyphs as crisp as at 100%, and the fold chevrons and steppers answering a
  tap in the fitted first section.
- **A pinch through many layouts** returns to the row it started in (`ReadingAnchor`), fitted layouts included.
- The 360 × 640dp phone: one column, pairs still side by side, no stub where a page can end a line short.

## 10. Not in this plan

- Spreading a song's slack over every page so that all of them are equally full (default 2).
- The PDF's column flow (`PrintLayout`), which keeps its own rules; it could take the same score later, and the
  `ui/print/CLAUDE.md` notes should say so when it does.
- A switch, a setting or a per-song override for any of the three; a stepper label that says the fitted size.
- Changing where a section may be cut (`sectionChunkStarts`): a line on either side stays the rule, the score decides
  how much a stub costs.
- Folding the Chords section or a repeated chorus to save a page: the song's own `{chorus}` recall and the reader's
  folds are what do that today, and the layout does not fold anything on its own.
