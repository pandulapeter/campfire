# Make the navigation labels' auto size shrink on desktop, iOS, and the web, where it never does

**Kind:** bug (large text; platform)  ·  **Severity:** medium  ·  **Platforms:** desktop, iOS, web (every Skiko platform); Android works
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ShrinkToFit.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationChrome.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/ShrinkToFitTest.kt` (new),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationLabelsTest.kt` (new),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TextFit.kt` (from plan 10)

**Runs after plan 10** in lane L (it uses plan 10's `TextFit.kt`). No string changes, so no user decision is needed: once
the auto size works, every language's labels fit at 130%.

## Problem

At text scale 1.3 on a 360 dp phone the bar's labels are cut: German "Werkze…" (`tools`), "Einstellu…" (`settings`),
Spanish "Cancion…", "Reperto…", "Herrami…" (`sheets/songs-130.png`, `tools-130.png`). Enlarged, the cut labels are drawn
at exactly the size of the ones that fit ("Songs"), so nothing was shrunk.

The label is meant to shrink first (`NavigationChrome.kt`, landed as `e4561d897`):

```kotlin
private fun NavigationItemLabel(text: String) {
    val fontSize = LocalTextStyle.current.fontSize
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = fontSize * MIN_NAVIGATION_LABEL_SCALE,
            // The style's own size, or the auto size would grow a short label to fill its item.
            maxFontSize = fontSize,
        ),
    )
}
```

With an ellipsis overflow, `TextAutoSize.StepBased` (Compose Foundation 1.12.2, `TextAutoSize.kt`) judges a single line
too wide only by `isLineEllipsized(0)`:

```kotlin
private fun TextLayoutResult.didOverflowByEllipsize(): Boolean =
    when (lineCount) {
        0 -> false
        1 -> isLineEllipsized(0)
```

and Skiko's paragraph never says a line is ellipsized (`ui-text-desktop` 1.12.2, `SkiaParagraph.skiko.kt`):

```kotlin
override fun isLineEllipsized(lineIndex: Int) = false
```

So on desktop, iOS, and the web every size "fits", the label is laid out at the largest, and the ellipsis cuts it. The
live run's text layouts agree: the cut labels report `didExceedMaxLines` and never an ellipsized line. On Android,
whose paragraph answers truthfully, the shrink works. The floor would fit every language at 1.3: at 75% of 1.3 the
widest labels are about 81 dp ("Einstellungen", "Herramientas") in an 84 dp item.

The app's two other auto sizes (`TunerStrings.kt`, `TunerDisplay.kt`) use `softWrap = false` with the default clip,
judged by `didOverflowWidth`, and work everywhere.

## Fix

**Option A (recommended): an auto size that asks what every platform answers.** New `ui/components/ShrinkToFit.kt`:

```kotlin
/**
 * Shrinks text that does not fit from [maxFontSize] down to [minFontSize] at most, in [stepSize] steps, before the
 * text's overflow (an ellipsis) cuts it. Compose's own [TextAutoSize.StepBased] judges an ellipsized line by
 * `isLineEllipsized`, which Skiko (desktop, iOS, the web) always answers false, so there it never shrinks anything;
 * a line past the text's maximum is what every platform reports.
 */
internal data class ShrinkToFit(
    private val minFontSize: TextUnit,
    private val maxFontSize: TextUnit,
    private val stepSize: TextUnit = 0.25.sp,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit =
        largestFittingSize(minFontSize.toPx(), maxFontSize.toPx(), stepSize.toPx()) { size ->
            !performLayout(constraints, text, size.toSp()).isCut
        }.toSp()
}

/** Whether a layout leaves text out: lines past its maximum, or a line wider or taller than its room. */
private val TextLayoutResult.isCut get() = multiParagraph.didExceedMaxLines || didOverflowWidth || didOverflowHeight

/** The largest of [smallest], [smallest] + [step], … up to [largest] that [fits], or [smallest] where none does. */
internal fun largestFittingSize(smallest: Float, largest: Float, step: Float, fits: (Float) -> Boolean): Float
```

`largestFittingSize` is a binary search over the steps (at most ~10 layouts for 12 sp → 9 sp in 0.25 sp steps, as
`StepBased`'s); pure, so it is tested without text. A `data class` gives the equality `TextAutoSize` asks for.

`NavigationChrome.kt`: `autoSize = ShrinkToFit(minFontSize = fontSize * MIN_NAVIGATION_LABEL_SCALE, maxFontSize = fontSize)`,
and drop the `TextAutoSize` import. Keep `maxLines = 1` and the ellipsis, which now cuts only past the floor (200%).

`ui/components/CLAUDE.md`: one line, "Auto-sized text with an ellipsis uses `ShrinkToFit`, never `TextAutoSize.StepBased`,
which never shrinks on Skiko (its paragraphs never report an ellipsized line)." `ui/chrome/CLAUDE.md`: if it does not say
it yet, "a label that does not fit its item shrinks to 75% before it is cut (`NavigationItemLabel`)".

**Option B:** `softWrap = false` with the default clip in `NavigationItemLabel`, as the tuner's auto sizes do. One line,
but past the floor a label is clipped at its item's edge mid-letter instead of ending in an ellipsis.

**Not recommended: shorter translations** (German "Optionen" for Einstellungen, "Extras" for Werkzeuge): English and
the German labels fit once the shrink works, and "Einstellungen" is what every German app calls Settings. At 1.0
"Einstellungen" (83 dp in 84) fills its item edge to edge, which is fine.

Related, not fixed here: the selected tool's name in the Tools button group (`ui/chrome/ToolsButtonGroup.kt`) is a plain
ellipsized `Text` cut at 1.3 in every language ("Metron…" in English); `ShrinkToFit` could serve it too, if the user
wants a follow-up.

## Tests

- `ShrinkToFitTest` (`desktopTest`), on `largestFittingSize`: `` `the largest size that fits is chosen` ``
  (fits below 10.6 with 9..12 step 0.25 → 10.5), `` `text that fits at the largest size keeps it` ``,
  `` `text that fits at no size gets the smallest` ``.
- `NavigationLabelsTest` (`tools/screenshots`, package `…presentation.ui.chrome`):
  `` `every navigation label is whole at a large text size in every language` ``: for each of `APP_LANGUAGES`,
  `runAppTest(appearance = TestAppearance(language = it, textScale = 1.3f))` on the Songs list; for `songs`,
  `setlists`, `tools`, and `settings`, the label's own text node (`hasText(string(…))` with `useUnmergedTree = true`,
  and not the Songs screen's title: pick the one with an ancestor that has a click action and is in the bar, e.g. the
  lowest by `boundsInRoot.top`) is whole by `TextFit.kt`'s `isWhole`. Fails on HEAD for de and es. The same test at
  text scale 2 would still cut German; leave it out.
