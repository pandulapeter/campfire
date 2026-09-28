# Keep the song's title readable beside the text-size stepper in performance mode on a narrow screen

**Challenged:** amended — option A is implementable, but the plan now spells out the mechanics: the cover is drawn inside the title's `AnimatedContent` `Row` (`song?.coverArtUrl?.takeIf { isCoverArtEnabled }`), so a computed `showsCoverInBar` boolean must gate both that and the cover term of `otherAppBarContentWidth`; and the stepper's width should be a named constant exported from `SongDisplayControls.kt` (button 36 + value 44 + button 36 = 116 dp, plus `APP_BAR_STEPPER_END_PADDING` 8 = 124) rather than a magic number. The plan's arithmetic checks out (360 - 52 - 4 - 124 = 180 without the cover, 128 with it).

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** phones in portrait, narrow desktop and web windows
**Decision D2:** option A (drop the cover in performance mode when the title would get less than 160 dp).
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt`,
`presentation/CLAUDE.md`

## Problem

Since 42c970dd, performance mode puts the text-size stepper into the app bar unconditionally:

```kotlin
actions = {
    // Performance mode leaves the bar with nothing else in it, and the text size is the one setting left
    // to it, so it stays in reach at the top of the screen rather than scrolling away with the header.
    if (isPerformanceModeEnabled) {
        LiveFontScaleControls(
            modifier = Modifier.padding(end = APP_BAR_STEPPER_END_PADDING),
            viewModel = viewModel,
        )
    }
```

The stepper is about 124 dp with its padding. Before that commit, `usesInlineControls` compared the title's remaining
width with `MIN_TITLE_WIDTH` (160 dp) and moved the steppers into a sheet otherwise; that check was deleted along with
the sheet, and `otherAppBarContentWidth` never counted this stepper. On a 360 dp phone reading a setlist song with a
cover, the title is left about 120 dp — "Good Riddance (Time of Your…" becomes "Good Riddan…" — on the one screen that
is used on stage, where the title is how the player confirms which song is up. It is narrower still in split screen or
a 320 dp window.

## Fix

Options:

- **A (recommended):** in performance mode, leave the cover out of the app bar when the title would otherwise be left
  less than `MIN_TITLE_WIDTH` (restore that 160 dp constant). The cover (`APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP`) is
  decoration there; the title is what says which song is up, and the stepper is what performance mode keeps the bar
  for. On a 360 dp phone that gives the title about 170 dp back. Decide it from `settledWidth`, like
  `songActionsMaxWidth`, and for every song of the pager at once (the cover is already reserved for the whole pager),
  so that nothing moves during a navigation transition or a page change.
- **B:** under the same condition, draw the stepper in the header instead of the bar (it then scrolls away with the
  header, which the bar placement was meant to avoid).
- **C:** leave it; performance mode is about the text, and a cut title is still recognisable.

Mechanics for A: `songActionsMaxWidth` is unused in performance mode (the song actions are not drawn), so the performance-mode title room is `appBarWidth - APP_BAR_NAVIGATION_WIDTH - APP_BAR_END_PADDING - stepperWidth - APP_BAR_STEPPER_END_PADDING`, and `showsCoverInBar = !isPerformanceModeEnabled || thatRoom - (APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP) >= MIN_TITLE_WIDTH`; export the stepper's drawn width from `SongDisplayControls.kt` as an `internal` constant (`STEPPER_WIDTH`, from `BUTTON_WIDTH * 2 + VALUE_MIN_WIDTH`) instead of a literal. Use `showsCoverInBar` where the `Row` draws the cover and where `otherAppBarContentWidth` adds the cover, and keep the existing `songs.any { it.coverArtUrl != null }` whole-pager reservation. The `presentation/CLAUDE.md` sentence to amend is in the `SongDisplayControls.kt` entry ("there the one stepper is in the bar (`LiveFontScaleControls`)").

For A or B, count the stepper's width (`LiveFontScaleControls` plus `APP_BAR_STEPPER_END_PADDING`) in the width the
title is left in performance mode — `otherAppBarContentWidth` currently counts the song actions, which performance mode
does not show — and name the rule in `presentation/CLAUDE.md` where the details screen's app bar is described.

## Tests

If the width decision is factored into a pure function (`fun showsCoverInPerformanceMode(appBarWidth, otherContentWidth): Boolean`),
test its boundary in `:presentation`'s `commonTest`; otherwise none (composable layout).

## Manual check

Android phone at 360 dp (or the desktop window at 360 px wide), performance mode on, a setlist song with a cover and a
long title: at least 160 dp of title is visible; the stepper still changes the text size. A tablet: unchanged.
