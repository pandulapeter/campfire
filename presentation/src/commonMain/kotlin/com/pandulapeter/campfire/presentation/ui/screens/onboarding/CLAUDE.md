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

The onboarding: a few questions, one a page, each shown on what it changes; a first run opens it, and Settings → General ("Set up Campfire again") opens it again.

### The pages

`OnboardingStep`, in this order, each an `OnboardingPage` — a mark, a question, a line saying why, and the answers:

- **Welcome**: a small Language button under the greeting, which opens the Settings list in a sheet
  (`DialogType.AppLanguage`, `AppLanguageSheet`) so the rest can be read in the right language; then the theme, its
  color and the sepia slider (the settings screen's own `UiModeChoice`, `ThemeColorChoice` and `BackgroundWarmthSlider`,
  the slider without its explanation). The onboarding is drawn in the app's theme, so the page itself shows each choice.
- **What do you need?** (`FeaturesStep`): the chords, the setlists, the metronome, and the tuner as cards any of which can
  be kept, each the Features switch it stands for. There are no roles: a player sings too, and anyone may play from a
  list, so the page asks about the features rather than guessing them from the person.
- **What do you play?**: only with the chords or the tuner on, its line naming which of them the answer is for. Guitar,
  Ukulele, Mandolin, Piano or keyboard, and Something else; Bass, Banjo, and Violin too with the tuner on
  (`onboardingInstruments`), since with it off they would only be Something else by another name. Under them the
  tunings of the one picked (`OnboardingTuningChoice`, `tuningsOffered`: its own, and the standard tuning of the
  guitar's seven and eight strings and the bass's five), asked with the tuner off too, since the diagrams depend on it.
  With the chords on, the chord diagrams switch follows, a G drawn beside it for the instrument picked, where the
  diagrams are drawn for the instrument in that tuning; a tuning they are not drawn for says so in its place, and an
  instrument they are never drawn for leaves it empty.
- **How do you write chords?**: only with the chords on; the five notations as cards with their names alone, the
  two lines of Amazing Grace above them rewritten in each (`OnboardingSongPreview`; the examples and the explanations are
  Settings'). The song is in E, so that German turns its B into an H; it has no title and names no key, to save the room,
  the key only counted from by the numberings.
- **What matters at a glance?**: what a song card says after the artist, for the Songs screen and, with the setlists on,
  for the Setlists screen (`UserPreferences.songCardDetails`, `setlistCardDetails`, see `ui/CLAUDE.md`): each a card drawn
  by the lists' own `SongListItem` over the chips of `CardDetailsChoice` (the key only with the chords on and the tempo
  only with the metronome), the card as wide as the narrowest column its list would lay it out in
  (`minSongColumnWidth`), so it narrows as it is told to say less. Always asked, since the duration goes with no feature.
- **Your songs, everywhere** (`SyncStep`): only where the build can connect Dropbox; the sync section's three stages.

There is no summary page: the last page's Next is Start playing (Done from Settings). `onboardingSteps` is worked out
from the preferences on every recomposition, so unticking the chords and the tuner takes their pages and dots away at
once; `nextOnboardingStep` and `previousOnboardingStep` count by the steps' order, so a page an answer took out of the
list still leads on and back. Every step has a dot of its own (`OnboardingPageIndicator`), shown or not, so a dot an
answer takes away shrinks out from between its neighbours and one it brings back grows in there.

### Every answer is written as it is given

There is no draft: each tap goes through the setter the settings screen uses, and each page shows what the app is set to
now, which is why every way out only closes the screen, why the colors picked take hold of the onboarding itself, and why
going through it again from Settings needs no state of its own. `OnboardingInstrument` is not a preference either:
picking one or one of its tunings writes the tuner's tuning and the chord diagrams' instrument, which is Other for a
tuning the diagrams are not drawn for (`chordInstrumentFor`: the shapes are the standard tuning's, the ukulele's low G
included), and the page reads its answer back out of the two (`onboardingInstrumentOf`), keeping a tuning the tuner
already has for that instrument (`tuningFor`, so drop D stays drop D); settings that match no answer, a fresh
installation's among them, show nothing picked rather than a guess.

### One screen, one pager, one footer

`CampfireDestination.Onboarding(isOpenedFromSettings)` is one screen, drawn edge to edge on a `ScreenSurface` like the
editor: a `HorizontalPager` of the pages that takes no swipes, with no bar above it, and `OnboardingBottomBar` under
all of them (Back, the dots, and Next, which is Start playing on a first run's last page and Done on one opened from
Settings). The way out is in the footer of the first page alone, in Back's place, which that page has no use for: Skip
on a first run, Close when opened from Settings, a tonal pill crossfading with Back as the first page is left; past it
the way out is back through the pages, or on through them, every answer being written already. The page is
`Navigator.onboardingStep`, as the settings tab is `settingsTab`: `navigateBack` steps back through the pages before it
leaves the screen (`isOnboardingBackToPreviousStep`), which covers the desktop's Escape, and a `NavigationBackHandler`
of the screen's own claims the system's gesture and the browser's Back while there is a page to go back to, the pager
following a predictive back with the finger inside one scroll, as the settings tabs do. The step is saved with the back
stack (`SavedStateStore`), so a process ended behind the onboarding comes back on the page it was left on, every answer
kept. On the web the screen is one history entry at the address of the screen under it (`BrowserRoutes`), so a reload
opens the app rather than a question.

`FirstRunController.openOnboardingOnFirstRun` opens it as the demo library is settled, while the launch screen is still
up, so the app is uncovered on it; it is skipped where a dialog is up or an import is waiting (`canShowOnboarding`). A
consent answered while it is on top does not open Settings over it; the Dropbox page says how it went. That holds
where the app did not live through the consent page too: Android's restored onboarding is back on that page, and the
web, whose answer is a new start up on the songs, puts the onboarding back on it (`SyncConsentOrigin`, over the
settings screen's General tab where that was what opened it) rather than opening Settings.

### Layout

A page is centered while it fits and scrolls with faded edges while it does not, never wider than
`ONBOARDING_CONTENT_WIDTH`; the mark is left out under `ONBOARDING_MARK_MIN_HEIGHT`. Back and the way out share one
slot of the footer, so the dots and Next never move.
