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

Welcome carries the one note that everything can be changed in Settings; no later page repeats it.

- **Instrument** uses the app-wide pill (`InstrumentPill`).
- **Features** cards are coarse switches, in order: chords, scales, metronome, tuner. Chords also switch chord
  diagrams and the Chords tab, scales also switch scale suggestions (one write each), and setlists are left to Settings, to keep the
  list short. No roles (singer / player) are asked: the page asks about features, not people.
- **Notation**: only with chords on. The preview (Amazing Grace) is in E so German turns B into H and a C#m shows the
  accidentals; accidentals aren't asked, but the preview uses Settings' (`onboardingPreviewChordNames`).
- **Song layout**: pages or one column that scrolls by itself (`SongLayoutChoices`, the cards the first song opened
  asks with where this page was never reached); being shown answers it (`markSongLayoutChosen`).
- **Sync**: only where the build can connect Dropbox.
- Song card details are not asked; their defaults follow the features.
- Whichever page is last carries the first run's iOS clipboard link card (`OnboardingClipboardLinkCard`, see
  `ui/firstRun/CLAUDE.md`) under its answers, given to it through `LocalOnboardingPageFooter`; an onboarding skipped
  before that page offers the link in a snackbar instead.
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

- Pages sit above center, or scroll with faded edges, max `ONBOARDING_CONTENT_WIDTH`. A window both taller and wider
  than a phone lifts the bar off the bottom edge (`onboardingBottomMargin`, growing with both; never while the bar
  spans the window, so not on an upright phone). The mark is dropped under `ONBOARDING_MARK_MIN_HEIGHT` (animated when
  resized, absent from the start in a short window).
- Page turns follow the pager position, never timers (a held gesture holds the mark halfway); the question layer only
  moves sideways, never scales, since the mark tracks its place.
- The mark is one shared element over the pager; neighbour pages are composed so it knows its target on the first frame.
  It fades into the top edge with the page under it as that scrolls (`onboardingMarkScrolled`), never cut off there.
