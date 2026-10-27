# Never break an onboarding footer button's label: move the dots to a line of their own where the row cannot hold them

**Kind:** bug (layout, large text and longer languages)  ·  **Severity:** medium (the first thing a new user sees)  ·  **Platforms:** all (phones)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingBottomBar.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingPageIndicator.kt` (its dot sizes, if the width function lives beside them),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterLayoutTest.kt`,
`presentation/src/commonMain/composeResources/values-es/strings.xml`, `presentation/src/commonMain/composeResources/values-ro/strings.xml`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TextFit.kt` (new, shared with plans 08, 11, 12),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/AppTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/ScaleSheetTitleTest.kt`,
`tools/screenshots/src/test/resources/goldens/onboarding-last-page.png` (only if `GoldenRendersTest` shows it changed)

**Decision (user):** German "Überspringen" is the one label that still overflows the row at the default size (its
first page's slot is reserved on every page). Recommended: keep the word, so German on a 360 dp phone gets the dots on
their own line; the alternative "Auslassen" fits but is not what German apps say for skipping a setup.

## Problem

The footer (`OnboardingBottomBar`) is one `SubcomposeLayout` row: the start slot (Back, or Skip / Close on the first
page), the dots, and the end slot (Next, or Start playing / Done on the last page). The slots are as wide as their
widest candidate label, and the dots stand where `onboardingDotsStart` puts them:

```kotlin
val spare = width - startSlotWidth - dotsWidth - endSlotWidth
if (spare < 0) return centered
```

Where they do not fit, the dots are centered and the buttons are measured into what is left of the row:

```kotlin
}.single().measure(looseConstraints.copy(maxWidth = dotsStart.coerceAtLeast(0)))
...
}.single().measure(looseConstraints.copy(maxWidth = (width - dotsStart - dots.width).coerceAtLeast(0)))
```

so a label wraps, and a one-word label breaks between its letters. On a 360 dp phone the row is 328 dp and the seven
dots take 128 dp (`DOT_SIZE` 8 dp, the pill 24 dp, 4 dp padding each side), leaving the two slots 200 dp. Measured
(Inter Medium, `labelLarge`; filled buttons 16 + 16 dp padding, the text button 12 + 12, 58 dp minimum width), slots
plus dots against 328 dp:

| | en | hu | ro | de | es | fr |
|---|---|---|---|---|---|---|
| 1.0 | 307 | 321 | 328 | **347** | **343** | 309 |
| 1.3 | **342** | **360** | **369** | **393** | **389** | **344** |

Live run, `sheets/onboarding-01-100.png`: German "Übersprin/gen" on the first page; `onboarding-07-100.png`: Romanian
"Hai să / cântăm" and Spanish "Empezar a / tocar" on two lines; at 1.3 (`onboarding-07-130.png`, `onboarding-01-130.png`)
German "Losleg/en", Spanish "Siguien/te" and "Empez/ar a tocar", and English "Start / playing". `OnboardingFooterTest`
guards only English at 1.0, so none of this fails a test. At 1.3 English fails too: shorter strings alone cannot fix it.

## Fix

### 1. Layout (`OnboardingBottomBar.kt`, `OnboardingFooterLayout.kt`)

- Decide whether the row holds everything from what does not animate: the two slot widths (already measured from
  every candidate label, so the decision is the same on every page) and the widest the dots can be,
  `OnboardingStep.entries.size` dots with the pill: `(entries.size - 1) * (DOT_SIZE + 8.dp) + PILL_WIDTH + 8.dp`
  (expose a function beside `DOT_SIZE` / `PILL_WIDTH` in `OnboardingPageIndicator.kt`, e.g.
  `onboardingDotsMaxWidth(count: Int): Dp`). Using the animated dots' width would flip the layout while a dot grows in
  as a page is ticked on.
  A pure function in `OnboardingFooterLayout.kt`, tested: `onboardingFooterIsStacked(width, startSlotWidth, dotsMaxWidth, endSlotWidth, gap): Boolean`
  = `startSlotWidth + dotsMaxWidth + endSlotWidth + 2 * gap > width`.
- **Beside** (as now): unchanged, except that Back / Skip and Next / Start playing are measured at their own width
  (`looseConstraints`), never capped by the dots, since the decision guarantees the room.
- **Stacked**: the dots centered on a line of their own above the buttons, `FOOTER_DOTS_GAP` (8 dp) above the buttons'
  line; the start button at the start and the end button at the end of that line, each at its own width. The footer
  grows by the dots' height plus the gap (16 dp).
- **Travel between the two** (a window resized across the threshold, the system text size changed while the onboarding
  is open): one `Animatable` progress, as `ToolInstrumentSetting`'s `ToolLineMeasurePolicy` does, held across passes,
  animated with `MaterialTheme.motionScheme.defaultSpatialSpec()`, and the lookahead laid out at the target
  (`if (isLookingAhead) target else progressTowards(target)`). The dots' x and y and the footer's height are lerped
  by it; the buttons keep their x and move down by the height the footer gains. The first frame is laid out at the
  target with no animation (the `Animatable` is created at it), per the memory "no incidental animations".
- `onboardingDotsStart` keeps deciding where the dots stand beside the buttons; its `spare < 0` branch is then only the
  dots' own animation overshooting, which the stacked layout makes unreachable at rest. Keep it rather than add a case.
- Past even the stacked line (200% text in German: "Überspringen" and "Loslegen" need ~374 dp), the labels still wrap;
  out of scope.

### 2. Strings

So that the default size keeps one row on a 360 dp phone where a better word is also shorter:

| File | Key | Now | New | Row at 1.0 |
|---|---|---|---|---|
| es | `onboarding_start_playing` | Empezar a tocar | ¡A tocar! | 313 → fits |
| ro | `onboarding_start_playing` | Hai să cântăm | Să cântăm! | 323 → fits |

(Widths with the 16 dp of gaps. "Să cântăm!" measures 71.4 dp without the "!", ~75 with it.) German keeps
"Überspringen" (see the decision), so German at 1.0 is stacked. French and Hungarian fit at 1.0 as they are.

### 3. `ui/screens/onboarding/CLAUDE.md`, "Step list and footer"

Replace "dots are shifted toward the narrower slot (`onboardingDotsStart`) so Start playing stays on one line on a
360dp phone" with "dots are shifted toward the narrower slot (`onboardingDotsStart`); where the slots and the widest
dots cannot share the line (large text, longer languages), the dots travel to a line of their own above the buttons
(`onboardingFooterIsStacked`), so a label is never broken".

## Tests

- `OnboardingFooterLayoutTest` (`desktopTest`): `` `slots and dots that fit beside each other share the line` ``,
  `` `a pixel more than the line stacks the dots` ``.
- New shared helpers, `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TextFit.kt` (MPL header,
  KDoc each; plans 08, 11, and 12 use them):

  ```kotlin
  /** The app's languages, every one a user can pick. */
  internal val APP_LANGUAGES = AppLanguage.entries - AppLanguage.SYSTEM_DEFAULT

  /** The layout of the text the node [matcher] finds, its own `Text` rather than the merged button around it. */
  internal fun AppTestScope.textLayout(matcher: SemanticsMatcher): TextLayoutResult

  /** Whether the whole text is shown: no line cut short with an ellipsis, none dropped past the maximum. */
  internal val TextLayoutResult.isWhole: Boolean

  /** Whether a line ends between two letters of one word, which reads as two words. */
  internal val TextLayoutResult.breaksAWord: Boolean
  ```

  `textLayout` reads `SemanticsActions.GetTextLayoutResult` as `OnboardingFooterTest` does now. `isWhole` is
  `!multiParagraph.didExceedMaxLines && !didOverflowWidth && !didOverflowHeight`, and must **not** use
  `isLineEllipsized`: Skiko's paragraph (`ui-text-desktop`, `SkiaParagraph.skiko.kt`) answers it `false` for every
  line, so on the desktop JVM the tests run on it never sees a cut (the live run's cut labels all reported
  `didExceedMaxLines` and never an ellipsized line). Say so in its KDoc. `breaksAWord` checks, for each line but the
  last, whether the character before `getLineEnd(i, visibleEnd = true)` and the one at it are both letters.
- `ScaleSheetTitleTest` asserts `assertFalse(layout.isLineEllipsized(layout.lineCount - 1), …)`, which for that reason
  can never fail: replace it with `assertTrue(layout.isWhole, …)`.
- `AppTest.kt`: a first run speaks the system's language, which `runAppTest` pins to English. Pin it to
  `appearance.language` instead (`Locale.setDefault(Locale.forLanguageTag(appearance.language.id))`, English by
  default, so existing tests are unchanged), and update the KDoc's "which the test pins to English".
- `OnboardingFooterTest`: replace the English-only test with, for each of `APP_LANGUAGES` at text scale 1.0 and 1.3
  (`runAppTest(isFirstRun = true, appearance = TestAppearance(language = it, textScale = scale))`):
  - on the first page, `onboarding_skip` and `onboarding_next` each on one line (`lineCount == 1`) and whole;
  - on the last page (`openOnboardingLastPage`), `back` and `onboarding_start_playing` the same;
  - the dots (found by their "Step N of N" description as now) overlap neither button: either beside both
    (`back.right <= dots.left`, `dots.right <= button.left`) or wholly above them (`dots.bottom <= button.top`);
  - Start playing within the window.
  Name it `` `the footer's labels stay whole and on one line in every language at the default and a large text size` ``.
  Twelve app starts; fails on HEAD for de, es, ro at 1.0 and every language at 1.3.
- `GoldenRendersTest`'s `onboarding-last-page.png` is English at 1.0, which stays beside; if it changes, regenerate it
  as `tools/screenshots/CLAUDE.md` says and look at it.
