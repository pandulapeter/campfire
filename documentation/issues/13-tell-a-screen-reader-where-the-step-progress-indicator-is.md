# Tell a screen reader where the step progress indicator is

**Challenged:** amended — `stepper.stopProgress` is `stopProgress(scrollState.value, …)`, so reading it in composition
(as the plan's `stateDescription` and `current` did) recomposes the indicator on every pixel scrolled, not once per stop
as the plan said; it is now read through a `derivedStateOf` of the rounded stop, so only a change of stop recomposes.
The snippet used `textResource`, which the root `CLAUDE.md` keeps for text somebody else wrote; two numbers are
formatted by the localization plugin's `stringResource(Res.string.x, a, b)`, as the plan's prose already said.

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/StepProgressIndicator.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`, `presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/CLAUDE.md` where it describes the indicator

## Problem

`StepProgressIndicator` (at 9ab7ca54e) is a bare `Canvas`:

```kotlin
Canvas(modifier = modifier.clipToBounds()) {
    val alpha = visibility
    if (alpha == 0f) return@Canvas
    …
```

It carries no semantics, so TalkBack and VoiceOver find nothing between the two step buttons, and a user of either has
no way to hear how far into the song they are — the very thing the dots show a sighted reader. The step buttons
themselves are described.

## Fix

Add a progress semantics node on the same modifier, from values read in composition only when the stop changes:

```kotlin
// The stop the mark is nearest, which changes once per stop rather than once per pixel scrolled.
val currentStop by remember(stepper) { derivedStateOf { stepper?.stopProgress?.roundToInt()?.coerceAtLeast(0) ?: 0 } }
val description = if (isShown) stringResource(Res.string.song_details_step_progress, currentStop + 1, stopCount) else null
Canvas(
    modifier = modifier
        .clipToBounds()
        .semantics {
            if (isShown && description != null) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = currentStop.coerceAtMost(stopCount - 1).toFloat(),
                    range = 0f..(stopCount - 1).toFloat(),
                )
                stateDescription = description
            }
        },
)
```

(`isShown` and `stopCount` are the ones the composable already has; `ProgressBarRangeInfo(current, range, steps = 0)` is
the Compose 1.12 constructor.) The drawing keeps reading `stopProgress` in `draw`. A new string
`song_details_step_progress` — `Row %1$d of %2$d` / `%2$d sorból a(z) %1$d.` (the executor picks the Hungarian wording
that reads naturally; it is a sentence with two numbers, not a plural) — in both files, read with the localization
plugin's `stringResource(Res.string.song_details_step_progress, currentStop + 1, stopCount)`, not `textResource`,
which is for text somebody else wrote. While the header above the first stop is read, `stopProgress` is negative and the
stop is taken as the first. Where a song is read in sections rather than rows, the word is still "row" in the sense the
indicator's KDoc uses ("its rows, or its sections where it is read in a single column"); if the executor prefers, a
second string for sections keyed on `stepper.isSteppedByRow`.

## Tests

None; semantics and strings.

## Manual check

Android emulator with TalkBack on: open a song that scrolls, swipe to the dots between the step buttons; TalkBack
reads "Row 1 of 8" and, after Next row, "Row 2 of 8".
