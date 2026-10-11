# Reserve the page pill's widest width in a short window's export preview, so the page does not re-fit when its text widens or on the first frame

**Challenged:** amended — the reserved width no longer relies on `tnum`, which the platforms' default fonts are not guaranteed to have (one without it ignores the feature, and "Page 10 of 11" could then outgrow a reserve measured as "Page 11 of 11"): it is measured with every digit replaced by the font's widest, and the dp is rounded up so the pill is never a pixel wider than its reserve.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (a short window: a phone on its side, a short desktop or browser window)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/PageButtons.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`

## Problem

In a short window (`SHORT_WINDOW_HEIGHT`) with the options beside the preview, the PDF page sits beside a column holding
the page pill and the save button, and that column is as wide as whichever of the two is wider, read from their
measured sizes (`ExportScreen.kt` at d650f3cb0):

```kotlin
var pageButtonsWidth by remember { mutableStateOf(0.dp) }          // ~L379
var saveButtonWidth by remember { mutableStateOf(0.dp) }
...
isShortWindow -> PageFit(end = maxOf(pageButtonsWidth, saveButtonWidth) + PAGE_MARGIN * 2, bottom = bottomInset + PAGE_MARGIN)   // ~L415
...
PageButtons(
    modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(PAGE_MARGIN)
        .onSizeChanged { pageButtonsWidth = with(density) { it.width.toDp() } },   // ~L509
```

The pill's width is its text's (`PageButtons.kt`, `PageButtonsPill`:
`Text(stringResource(Res.string.print_page, page + 1, pageCount), style = MaterialTheme.typography.bodySmall)` between two
`IconButton`s), and `print_page` is "Page %1$d of %2$d". Two things follow, both against the root CLAUDE.md's rule that a
change is either animated or right from the first frame:

1. Turning from page 9 to page 10 of a 10+ page PDF (or 99 to 100) widens the text, so the pill widens, `PageFit.end`
   grows, and the page shrinks and re-centres in one frame (`PageFit` is read directly by `PrintPages`'s `fitArea()`,
   with no animation). With proportional digits even "Page 1 of 12" and "Page 4 of 12" differ by a pixel or two, so
   the page twitches on ordinary page turns too.
2. `pageButtonsWidth` starts at 0dp and the pill is not composed at all until there are pages (`isVisible` includes
   `hasPages`), so the first frame of a loaded preview fits the page with no pill column, and the frame after
   `onSizeChanged` reports the pill shrinks it. The same happens whenever the pill comes back after the PDF format is
   left and chosen again.

## Fix

Reserve the widest the pill can be for this `pageCount`, known in composition rather than measured after layout, and
draw the pill at that width so it no longer changes size by itself:

1. In `PageButtons.kt`, give the count's `Text` tabular figures
   (`style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")`), so that where the font has them
   every digit is as wide as every other and the text does not wobble inside the pill as the pages turn. Do not rely on
   it for the reserved width: the app sets no font of its own for this text, so it is each platform's default (Roboto,
   SF, the desktop's and the browser build's own), and a font without a `tnum` table ignores the feature silently.
2. Add an `internal` composable in `PageButtons.kt`, e.g. `@Composable internal fun pageButtonsWidth(pageCount: Int): Dp`,
   that measures, with a `rememberTextMeasurer()` in that same style and keyed on what it measures, the widest the
   count can read for this `pageCount` whatever the font: measure the ten digits `"0"`…`"9"` once, take the widest, and
   measure `stringResource(Res.string.print_page, pageCount, pageCount)` with every digit in it replaced by that one
   (the page number never has more digits than the count, and the format's own text holds no digits in either
   language: "Page %1$d of %2$d", "%1$d. oldal / %2$d"). It returns the two icon buttons' width (48dp each, Material's
   minimum interactive size, as `IconButton` takes), the `Arrangement.spacedBy(8.dp)` gaps, and the text's width,
   converted to dp rounded up (`ceil(px / density)`, so the `Dp` taken back to pixels is never a pixel short).
   Use the same text width as the pill's text width (`Modifier.widthIn(min = …)` on the `Text` with
   `textAlign = Center`), so what is reserved and what is drawn are one number. Keep a single source for the 48dp and
   8dp (private constants) so they cannot drift.
3. In `ExportScreen.kt`, replace `pageButtonsWidth`'s `mutableStateOf(0.dp)` and the `onSizeChanged` that feeds it with
   `val pageButtonsWidth = pageButtonsWidth(pageCount)` (computed where `pageCount` is known, before `pageFit`). Keep
   `saveButtonWidth` as it is: its label crossfades between Save and Cancel inside one box (`SaveButton.kt`,
   `CrossfadedLabel`), so its width does not change, and in both languages the pill is the wider of the two, so the
   reserved pill width is what decides the column from the first frame. If the executor finds the save button can be
   the wider (check the Hungarian `print_save` and `cancel`), seed its width the same way rather than leaving it at 0dp.
4. Update the comment above `isShortWindow` (~L374–376: "…which is as wide as the wider of the two: the pill's width
   depends on its text.") and `ui/screens/export/CLAUDE.md` (~L80–81: "as wide as the wider of the two, measured, since
   the pill's width is its text's") to say the pill is set as wide as its text at its widest for the PDF's page count,
   every digit counted as the widest one, so the page beside it does not move as the pages are turned.

Alternative: keep `onSizeChanged` and only round the measured width up to a step. Rejected: it still leaves the first
frame wrong and still jumps at a step boundary.

## Tests

None: the width comes from text measurement in a composable, which the project does not unit test.

## Manual check

On a phone on its side (or a desktop window under 480dp tall), export a setlist whose PDF has at least ten pages. Open
the export screen: the first frame of the preview already has the page clear of the pill. Turn pages from 1 to 12 with
the pill's buttons and the arrow keys: the page beside the pill keeps its size and position on every turn, including 9
to 10. Switch the format to the library files and back to PDF: the page appears at its final size. Check in Hungarian
too.
