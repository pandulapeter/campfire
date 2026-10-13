# Give the onboarding footer's buttons the room the page dots leave, so that Start playing stays on one line on a 360dp phone

**Kind:** bug  ·  **Severity:** low–medium  ·  **Platforms:** phones (Android, iOS, and the web on a phone)
**Challenged:** amended — the placement left undefined what happens when the two slots and the dots cannot all fit (a larger font or interface scale, which the plan itself says makes it worse); it now falls back to today's even split, centered dots, with a test. Also spelled out that the custom `Layout` places relatively (start/end, as the rest of the chrome does) and that the dots' own animated width (pill 8↔24dp, a dot shrinking out as an answer takes a page away) is read every measure pass, not once.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingBottomBar.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterLayout.kt` (new, if the pure placement is extracted), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterLayoutTest.kt` (new), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`

**Drop this plan if the label fits on one line at 360dp at font scale 1.0 in English and Hungarian** (see Manual check for how to look); the numbers below are worked out from the code and the fonts, not from a device.

## Problem

`OnboardingBottomBar.kt` lays the footer out as two equal boxes around the page dots:

```kotlin
) = Row(
    modifier = modifier.widthIn(max = ONBOARDING_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
    ) { /* Back (TextButton), or Skip / Close (FilledTonalButton) on the first page */ }
    OnboardingPageIndicator(
        steps = steps,
        currentStep = currentStep,
    )
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Button(onClick = onNext) { /* Next, Start playing on a first run's last page, Done from Settings */ }
    }
}
```

The dots (`OnboardingPageIndicator.kt`) are 8dp each with 4dp padding on either side, the current one 24dp: `(n − 1) × 16 + 32` dp. With Dropbox configured and the chords and the tuner on, `onboardingSteps` shows all six pages (WELCOME, FEATURES, INSTRUMENT, NOTATION, AT_A_GLANCE, SYNC), so the dots take 112dp; without Dropbox, five pages, 96dp.

On a 360dp phone the row is 328dp inside its padding, so each box gets `(328 − 112) / 2 = 108`dp (116dp with five pages). `Button(onClick = onNext)` is the overload with `ButtonDefaults.ContentPadding` (24dp start and end in Material 3 1.12.0-alpha03) and `labelLarge` text (14sp, medium, 0.1sp tracking). Measured with Roboto at weight 500 (the font the screenshot tool sets the Android build in, `tools/screenshots/build/fonts/Roboto.ttf`): "Start playing" is about 80dp, so the button wants about 129dp; "Zenéljünk" about 62dp, so about 111dp; "Kihagyás" in the tonal pill about 106dp. With 108dp, English "Start playing" (and Hungarian "Zenéljünk" with six pages) does not fit: the text wraps to two lines inside a taller button, on exactly the page a first run ends on. A larger font scale makes it worse, and the box cannot grow, since the weights split the width evenly whatever the buttons hold.

The onboarding is not rendered by `tools/screenshots` (nothing under `tools/screenshots/src` mentions it), so there is no offscreen image to check against today.

## Fix

Options:

1. **Recommended: let the buttons take the width the dots leave, and keep the dots centered only while both sides fit.** Replace the `Row` with a `Layout` whose placement is a small pure function (`OnboardingFooterLayout.kt`):

   ```kotlin
   /**
    * Where the dots start in a footer [width] wide: centered while the widest the start and end slots can be
    * ([startSlotWidth], [endSlotWidth]) both fit beside them with [gap], else moved away from the wider slot as far as
    * the narrower one allows, so that neither button is squeezed.
    */
   internal fun onboardingDotsStart(width: Int, dotsWidth: Int, startSlotWidth: Int, endSlotWidth: Int, gap: Int): Int
   ```

   Where `startSlotWidth + gap + dotsWidth + gap + endSlotWidth > width` (a larger font or interface scale, a longer translation), it returns today's placement, the dots centered, and each button gets the half its side has, wrapping as it does now: moving the dots cannot make room that is not there, and centered is what keeps them from moving between pages.

   The buttons and the dots are placed with `placeRelative`, start and end rather than left and right, as everywhere else in the app's chrome. `dotsWidth` is the indicator as measured in this pass: it animates (the pill's 8↔24dp, a dot expanding or shrinking as an earlier answer brings a page back or takes it away), and the dots follow it smoothly whether centered or moved.

   The slot widths are the widest each slot's content can be across the pages — Back, Skip, or Close at the start; Next, Start playing, or Done at the end — measured once (a `SubcomposeLayout`, or measuring the labels with `rememberTextMeasurer` plus the button paddings), not the button on screen, so that the dots do not move as Back crossfades with Skip or Next with Start playing ("the dots and Next never move", the KDoc and `ui/screens/onboarding/CLAUDE.md`). The start button is placed at the start, the end one at the end, each measured with the room between its edge and the dots. On a wide window nothing changes: centered, as today. At 360dp with six pages: the start slot is at most the tonal "Kihagyás" pill (about 106dp) and the end slot about 129dp; `106 + 112 + 129 = 347` is still more than 328, so this alone does not fit Hungarian with six pages either — which is why it goes together with 2.
2. Smaller horizontal padding for the two buttons, `PaddingValues(horizontal = 16.dp, vertical = 8.dp)` (Material's small button padding): "Start playing" then wants about 113dp, the tonal "Kihagyás" about 90dp. On its own it still does not fit "Start playing" in an even 108dp split, but with 1 it does: `90 + 112 + 113 = 315 ≤ 328`, the dots' own 4dp padding being the gap, so `gap` can be 0 (anything above 6dp would not fit Hungarian with six pages).
3. Fewer or smaller dots on a narrow footer. Rejected: the dots are the user's only count of the pages left.

Recommended: 1 and 2 together. Keep the tonal pill and the text button as they are otherwise. Check that the buttons still meet the 48dp minimum touch target (they do: the padding change is horizontal only).

Update the footer sentence of `ui/screens/onboarding/CLAUDE.md` ("Back and the way out share one slot of the footer, so the dots and Next never move"): the slots are as wide as their widest label, and the dots are centered between them where both fit and moved toward the narrower slot where they do not.

## Tests

`OnboardingFooterLayoutTest.kt`, over `onboardingDotsStart`:

- slots that fit on both sides center the dots (`(width − dotsWidth) / 2`);
- a wide end slot moves the dots toward the start, leaving exactly `gap` to it, and never past `startSlotWidth + gap`;
- the result is the same for every pair of labels a slot can hold (it depends on the widest only), so the dots do not move between pages;
- slots that cannot all fit even with the dots moved (e.g. 328, 112, 140, 150) give the centered start, `(width − dotsWidth) / 2`;
- with the numbers above (328, 112, 90, 113, and a gap of 0 — the dots' own 4dp padding already separates them — as pixels at density 1) everything fits without overlap, with 13dp to spare.

## Manual check

On the smallest supported screen (the 360 × 640dp emulator, see the "Smallest supported screen" recipe) with a Dropbox key configured, the chords and the tuner on, and font scale 1.0: start a first run (or Settings → the onboarding, then a fresh installation for the first-run labels) and page to the last page. "Start playing" must be on one line, as must "Skip" on the first page, in English and then in Hungarian ("Zenéljünk", "Kihagyás"). On a tablet or a desktop window, the dots must sit in the middle of the footer and not move as the pages change.
