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

The onboarding: a few questions, one a page, each shown on what it changes; a first run opens it, and Settings → About ("Set up Campfire again", under Help) opens it again.

### The pages

`OnboardingStep`, in this order, each an `OnboardingPage` — a place for the mark, a question, a line saying why, and the
answers:

- **Welcome**: under the greeting the Language button, which opens the Settings list in a sheet
  (`DialogType.AppLanguage`, `AppLanguageSheet`) so the rest can be read in the right language; then, closing the page,
  a tonal note with the settings mark, as wide as its text and centered, saying that every answer of the onboarding can
  be changed later in Settings — the one place it is said, so no later page repeats it.
- **Pick a look** (`LookStep`): the theme, its color and the sepia slider (the settings screen's own `UiModeChoice`,
  `ThemeColorChoice` and `BackgroundWarmthSlider`, the slider without its explanation). The onboarding is drawn in the
  app's theme, so the page itself shows each choice. A page of its own rather than the welcome's second half, so that
  neither is too long for a window while another page is nearly empty.
- **What do you play?**: always asked, and before the features, since which features are worth offering depends on it.
  The same instrument and tuning chips as everywhere else (`InstrumentChipRow`, opening `DialogType.Instrument` and
  `DialogType.Tuning` over the onboarding), centered, so the control is learnt once; a fresh installation shows the
  six-string guitar. Under them the answer is shown on what it changes: G, C, and D drawn for it
  (`OnboardingChordPreview`, where it has diagrams) and the open strings the tuner will listen for (where it has a
  tuning), crossfading as either is picked again.
- **What do you need?** (`FeaturesStep`): the chords, the chord diagrams, the metronome, the tuner, and the setlists as
  cards any of which can be kept, each the Features switch it stands for, in that list's order. The chord diagrams card
  is there only while the chords are on and for an instrument they are drawn for, ending in a G drawn for the instrument
  just picked in its tuning, collapsing and fading as it goes. The diagrams', the metronome's, and the tuner's cards say
  that each is also a screen of its own, since unticking one takes that screen too. There are no roles beyond the chords
  card's line that a singer leaves it off for lyrics only: a player sings too, and anyone may play from a list, so the
  page asks about the features rather than guessing them from the person.
- **How do you write chords?**: only with the chords on; the five notations as cards with their names alone (in two
  columns where they fit, `NOTATION_TWO_COLUMN_WIDTH`), the two lines of Amazing Grace above them rewritten in each
  (`OnboardingSongPreview`, as wide as its lines and centered; the examples and the explanations are Settings'). The
  song is in E, so that German turns its B into an H, with a C#m that the accidentals spell; it has no title and names
  no key, to save the room, the key only counted from by the numberings. The accidentals are not asked, but the preview
  spells its chords with the ones Settings has, as a song page does (`onboardingPreviewChordNames`, tested).
- **Your songs, everywhere** (`SyncStep`): only where the build can connect Dropbox; the sync section's three stages.

What a song card says after its artist is not asked: it is a detail Settings → Songs changes, and its defaults already
follow the features picked here (the key goes with the chords, the tempo with the metronome until it is picked, see
`ui/CLAUDE.md`).

There is no summary page: the last page's Next is Start playing (Done from Settings) — or still Next while an import
waits behind the onboarding (`importReport` not null, `isImportWaiting`), since what the last page leads to is then
that import's screen, which is pushed as the onboarding leaves; the label crossfades whenever it changes.
`onboardingSteps` is worked out from the preferences on every recomposition, so unticking the chords takes the
notation's page and dot away at once; `nextOnboardingStep` and `previousOnboardingStep` count by the steps' order, so a
page an answer took out of the list still leads on and back. Every step has a dot of its own (`OnboardingPageIndicator`),
shown or not, so a dot an answer takes away shrinks out from between its neighbours and one it brings back grows in
there. The pill follows the pager's position (`pagerSelection`), one dot shrinking as the next grows, so it moves with a
swipe or a predictive back rather than catching up once the step has changed.

### Every answer is written as it is given

There is no draft: each tap goes through the setter the settings screen uses, and each page shows what the app is set to
now, which is why every way out only closes the screen, why the colors picked take hold of the onboarding itself, and why
going through it again from Settings needs no state of its own. The instrument page writes the one instrument the
whole app reads (`UserPreferences.instrument`, see `ui/CLAUDE.md`) and the tuning picked for it, so it always shows one
picked: the guitar on a fresh installation, and whatever was set since on a second visit, its tuning kept, so drop D
stays drop D.

### One screen, one pager, one footer

`CampfireDestination.Onboarding(isOpenedFromSettings)` is one screen, drawn edge to edge on a `ScreenSurface` like the
editor: a `HorizontalPager` of the pages, with no bar above it, and `OnboardingBottomBar` under
all of them (Back, the dots, and Next, which is Start playing on a first run's last page and Done on one opened from
Settings). The way out is in the footer of the first page alone, in Back's place, which that page has no use for: Skip
on a first run, Close when opened from Settings, a tonal pill crossfading with Back as the first page is left; past it
the way out is back through the pages, or on through them, every answer being written already. The page is
`Navigator.onboardingStep`, as the settings tab is `settingsTab`: `navigateBack` steps back through the pages before it
leaves the screen (`isOnboardingBackToPreviousStep`), which covers the desktop's Escape, and a `NavigationBackHandler`
of the screen's own claims the system's gesture and the browser's Back while there is a page to go back to, the pager
following a predictive back with the finger inside one scroll, as the settings tabs do. The pager also takes swipes, a
mouse drag, and a trackpad's sideways scroll, and the page one settles on becomes the step (`showOnboardingStep`) —
but not a page passed while the pager is still on its way to the step (`PagerFollowingStep`), which a second Next taking
over from the first would otherwise turn back to. The step is saved with the back
stack (`SavedStateStore`), so a process ended behind the onboarding comes back on the page it was left on, every answer
kept. On the web the screen is one history entry at the address of the screen under it (`BrowserRoutes`), so it has no
address of its own: a reload while it is still owed opens it again on its first page, over the songs rather than the
address the page was at (the start up puts it on the stack before `navigateOnLaunch` looks), and neither a reload nor a
Forward opens it once it is finished, skipped, or closed.

`FirstRunController.openOnboardingOnStartUp` opens it as the demo library is settled, while the launch screen is still
up, so the app is uncovered on it; it is skipped where a dialog is up or an import is waiting (`canShowOnboarding`). It
is opened on every start up until it has been finished or skipped (`isOnboardingPending`, see `ui/firstRun/CLAUDE.md`),
so an app ended in the middle of it starts it again, the answers given so far kept. A
consent answered while it is on top does not open Settings over it; the Dropbox page says how it went. That holds where
the app did not live through the consent page too: Android's restored onboarding is back on that page, and the web,
whose answer is a new start up on the songs, puts the onboarding back on it (`SyncConsentOrigin`, over the settings
screen's About tab where that was what opened it), or moves an owed onboarding the start up opened meanwhile
from its first page to it (`syncConsentAnswerOf`), rather than opening Settings.

### Layout

A page is centered while it fits and scrolls with faded edges while it does not, never wider than
`ONBOARDING_CONTENT_WIDTH`; the mark is left out under `ONBOARDING_MARK_MIN_HEIGHT`, its place collapsing and the mark
shrinking into it and fading as a window is made that short, and both coming back the same way (a window that starts
short starts without them). The pages are kept about as long
as each other, every one fitting the desktop's default window: a page that outgrows the rest is split or laid out in
columns rather than left to scroll.

### Turning the pages

A turn is more than the pager's slide: each page is drawn as two layers that follow the pager's position
(`LocalOnboardingPageOffset`, read in the draw phase), the question lagging behind its page and fading
(`onboardingHeaderMotion`), and the answers moving a little ahead of it while they shrink back and fade
(`onboardingAnswersMotion`, both tested). The question's layer is only ever moved sideways, never scaled, since it holds
the mark's place and the mark follows where that place is.

### The mark

**The mark is one shared element, not one per page** (`OnboardingMark`): each page keeps an empty place for it
(`markModifier`, reporting where that place is), and the screen draws the mark over the pager, at the place of the page
shown or between the places of the two pages a turn is between (`onboardingMarkFrame`, `onboardingMarkTop`, tested). So
the circle stays centered while the pages slide under it and only moves up or down where their places differ, its icon
shrinking out as the next grows in. It is always a circle: the pages are told apart by their icons, not by shapes. All
of it follows the pager's position rather than a timer, so a swipe or a predictive back held halfway holds the mark
halfway; the pages next to the one shown are composed (`beyondViewportPageCount`) so the mark knows where it is going
before a turn's first frame. Back and the way out share one
slot of the footer, so the dots and Next never move. Each slot is as wide as its widest label on any page, and the dots
are centered between the two where both fit and moved toward the narrower slot where they do not, the room left over
shared between their two sides (`onboardingDotsStart`), so that Start playing stays on one line on a 360dp phone; where
even that leaves too little room, at a large font or interface scale, the dots stay centered and each button gets the half its side has.
