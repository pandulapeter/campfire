# Share the spare room of the onboarding footer between both sides of the dots, so the last dot no longer touches Start playing

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all (phones)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingBottomBar.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/OnboardingFooterLayoutTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`

## Problem

Where the dots cannot be centered, they are pushed against the wider slot with no gap of their own
(`OnboardingFooterLayout.kt` and `OnboardingBottomBar.kt`, at 72d48deb7):

```kotlin
internal fun onboardingDotsStart(width: Int, dotsWidth: Int, startSlotWidth: Int, endSlotWidth: Int, gap: Int): Int {
    val centered = (width - dotsWidth) / 2
    if (startSlotWidth + gap + dotsWidth + gap + endSlotWidth > width) return centered
    return centered.coerceIn(startSlotWidth + gap, width - endSlotWidth - gap - dotsWidth)
}
```

```kotlin
// No gap of its own: the dots' padding already keeps them apart from the buttons.
val dotsStart = onboardingDotsStart(width = width, dotsWidth = dots.width, startSlotWidth = startSlotWidth, endSlotWidth = endSlotWidth, gap = 0)
```

On a 360dp phone in English the footer is 328dp wide, the start slot (Skip / Back) about 90dp, the dots 112dp, and the end
slot (Start playing) 113dp, which leaves 13dp. The coercion puts the dots flush against the end slot, so the only room
between the last dot (the pill, on the last page) and **Start playing** is the dot's own 4dp padding, while the 13dp of
spare room all sits on the Back side (live run: `12-onb6.png`; in Hungarian, "Zenéljünk", the same at `16-hu-p6.png`).

## Fix

Ask for a real gap, and where the full gap does not fit on both sides, split the spare room between the two sides instead
of falling back to the center:

```kotlin
internal fun onboardingDotsStart(width: Int, dotsWidth: Int, startSlotWidth: Int, endSlotWidth: Int, gap: Int): Int {
    val centered = (width - dotsWidth) / 2
    val spare = width - startSlotWidth - dotsWidth - endSlotWidth
    if (spare < 0) return centered
    // A gap as wide as both sides can be given, which is the asked one where there is room for it.
    val sideGap = minOf(gap, spare / 2)
    return centered.coerceIn(startSlotWidth + sideGap, width - endSlotWidth - sideGap - dotsWidth)
}
```

and in `OnboardingBottomBar` pass `gap = FOOTER_DOTS_GAP.roundToPx()` with `private val FOOTER_DOTS_GAP = 8.dp`, replacing
the "No gap of its own" comment with one saying the gap is on top of the dots' own 4dp padding and shrinks, evenly on both
sides, where a phone has less room. The dots still never move between pages (every page asks with the same slot widths),
and `spare < 0` keeps today's centered fallback for a large font or interface scale. Update the KDoc of
`onboardingDotsStart` and the onboarding `CLAUDE.md` sentence "moved toward the narrower slot where they do not" to "moved
toward the narrower slot where they do not, the room left over shared between their two sides".

## Tests

In `OnboardingFooterLayoutTest`:

- Keep `slots that fit on both sides center the dots`, `a wide end slot moves the dots toward the start, leaving exactly the
  gap to it`, `a wide start slot moves the dots no further than the end slot allows`, `the dots stay where they are whichever
  label a slot shows`, and `slots that cannot all fit leave the dots centered` (unchanged results: their spare room is at
  least twice the gap, or negative).
- Change `start playing and skip fit beside six dots on a 360dp phone` to call with `gap = 8` and assert the 13 spare
  are shared: `start - 90 >= 6` and `328 - 113 - (start + 112) >= 6` (the function returns 97: 7 and 6).
- Add `room for less than the gap is shared between the two sides`: `width = 300, dotsWidth = 100, startSlotWidth = 90,
  endSlotWidth = 100, gap = 8` → `start == 95`.

## Manual check

On the 360 × 640dp emulator in English, go through the onboarding to the last page: there is as much room between the pill
and **Start playing** as between Back and the first dot, and Start playing is still on one line. Check the first page (Skip
in the start slot) and Hungarian too. At the largest system font size the dots fall back to centered as before.
