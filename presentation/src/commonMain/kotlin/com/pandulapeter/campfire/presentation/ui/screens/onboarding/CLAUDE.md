<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/onboarding

A few questions, one a page, each shown on what it changes. A first run opens it; Settings → About → Help ("Set up
Campfire again") opens it again.

## The pages

`OnboardingStep` order: Welcome (language, plus the one note that everything can be changed in Settings — no later page
repeats it), Pick a look, What do you play?, What do you need?, How do you write chords?, Your songs, everywhere.

- **Instrument is asked before features**, which depend on it. Uses the app-wide chips (`InstrumentChipRow`).
- **Features** cards are the Features switches, in order; chord diagrams only with chords on and an instrument that has
  them. No roles (singer / player) are asked: the page asks about features, not people.
- **Notation**: only with chords on. The preview (Amazing Grace) is in E so German turns B into H and a C#m shows the
  accidentals; accidentals aren't asked, but the preview uses Settings' (`onboardingPreviewChordNames`).
- **Sync**: only where the build can connect Dropbox.
- Song card details are not asked; their defaults follow the features.
- Pages are about equally long, each fitting the desktop's default window (split or use columns).

## Every answer is written as it is given

No draft: each tap goes through the settings screen's setter and each page shows the current setting. So every way out
only closes the screen, picked colors restyle the onboarding itself, and a second visit needs no state (its tuning is
kept: drop D stays drop D).

## Step list and footer

- `onboardingSteps` is recomputed from preferences every recomposition (unticking chords removes the notation page and
  dot at once); `nextOnboardingStep` / `previousOnboardingStep` count by step order so removed pages still lead on.
- Every step has a dot, shown or not, so dots shrink out / grow in between neighbours. The pill follows the pager
  position (`pagerSelection`), so swipes and predictive back move it continuously.
- No summary page. Last page's Next is Start playing (Done from Settings), or Next while an import waits
  (`isImportWaiting`), whose screen is pushed as the onboarding leaves.
- **Finishing is celebrated** (Start playing, Done): `Navigator.isOnboardingFinishCelebrated` zooms the
  onboarding away (`onboardingFinishTransition`) under rising embers (`OnboardingCelebration`); other ways out slide.
- The way out (Skip on a first run, Close from Settings) is only in the first page's footer, in Back's slot. Back and the
  way out share a slot so the dots and Next never move. Slots are as wide as their widest label on any page; dots are
  shifted toward the narrower slot (`onboardingDotsStart`) so Start playing stays on one line on a 360dp phone.

## One screen, one pager

- `CampfireDestination.Onboarding(isOpenedFromSettings)` is one screen on a `ScreenSurface`: a `HorizontalPager` and
  `OnboardingBottomBar`, no top bar.
- The page is `Navigator.onboardingStep`. Back steps through pages first (`isOnboardingBackToPreviousStep` for Escape; a
  screen-owned `NavigationBackHandler` for gestures/browser Back, following predictive back as the settings tabs do).
- The page a swipe settles on becomes the step (`showOnboardingStep`), but not one passed while the pager is heading to
  the step (`PagerFollowingStep`), or a second Next would be turned back.
- The step is saved with the back stack (`SavedStateStore`), so process death resumes on the same page.
- Web: one history entry at the address of the screen under it (`BrowserRoutes`). A reload while still owed reopens it
  on page one over the songs; once finished, skipped, or closed, neither reload nor Forward opens it.

## Start up and sync consent

- `FirstRunController.openOnboardingOnStartUp` opens it as the demo library settles, behind the launch screen; not
  while a dialog is up or an import waits (`canShowOnboarding`). It opens on every start until finished or skipped
  (`isOnboardingPending`, see `ui/firstRun/CLAUDE.md`).
- A sync consent answered while it is on top does not open Settings; the Sync page shows the result. Also when the app
  didn't survive the consent page: Android restores onto that page; the web puts the onboarding back on it
  (`SyncConsentOrigin`), or moves an owed onboarding opened meanwhile to it (`syncConsentAnswerOf`).

## Layout and motion

- Pages sit above center, or scroll with faded edges, max `ONBOARDING_CONTENT_WIDTH`. The mark is
  dropped under `ONBOARDING_MARK_MIN_HEIGHT` (animated when resized, absent from the start in a short window).
- Page turns are two layers following the pager position (`LocalOnboardingPageOffset`, draw phase): the question lags
  and fades (`onboardingHeaderMotion`), the answers lead, shrink, and fade (`onboardingAnswersMotion`). The question
  layer only moves sideways, never scales, since the mark tracks its place.
- **The mark is one shared element** (`OnboardingMark`) drawn over the pager: each page reserves a place
  (`markModifier`), and the mark sits at, or between, those places (`onboardingMarkFrame`, `onboardingMarkTop`). Always a
  circle; pages differ by icon. Everything follows pager position, not timers, so a held gesture holds the mark
  halfway; neighbour pages are composed (`beyondViewportPageCount`) so the mark knows its target on the first frame.
