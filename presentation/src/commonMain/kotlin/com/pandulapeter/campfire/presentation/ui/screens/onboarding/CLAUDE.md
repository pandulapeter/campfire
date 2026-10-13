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

The onboarding: a few questions about the newcomer rather than about the settings, one a page, each shown on what it
changes; a first run opens it, and Settings → General ("Set up Campfire again") opens it again.

### The pages

`OnboardingStep`, in this order, each an `OnboardingPage` — a mark, a question, a line saying why, and the answers:

- **Welcome**: the theme and its color, the settings screen's own `UiModeChoice` and `ThemeColorChoice`, under a few
  lines of a song (`OnboardingSongPreview`) that show the colors as they are picked.
- **How will you use Campfire?**: three roles (`OnboardingRole`: I play an instrument, I sing, I lead a band or a
  group), each switching the chords, the setlists, the metronome, and the tuner on or off at once, with the four
  switches under them to change one by one. The role is not stored and starts unpicked; the switches are the setting.
- **What do you play?**: only with the chords or the tuner on, its line naming which of them the answer is for. Guitar,
  Ukulele, Piano or keyboard, and Something else; Bass, Banjo, Mandolin, and Violin too with the tuner on
  (`onboardingInstruments`), since with it off they would only be Something else by another name. With the chords on,
  the chord diagrams switch is under them, a G drawn beside it for the instrument picked.
- **How do you write chords?**: only with the chords on; the five notations as cards, the song preview rewritten in each.
- **What matters at a glance?**: only with the chords or the metronome on; the key and the tempo switches
  (`isKeyShown`, `isTempoShown`, each offered with its feature) under a song card drawn by the song list's own
  `SongListItem`.
- **Your songs, everywhere** (`SyncStep`): only where the build can connect Dropbox; the sync section's three stages.
- **You're all set!**: the features left on (or lyrics only) and Dropbox where it is connected, then where to start.

`onboardingSteps` is worked out from the preferences on every recomposition, so a role that switches the chords and the
tuner off takes their pages and dots away at once; `nextOnboardingStep` and `previousOnboardingStep` count by the steps'
order, so a page an answer took out of the list still leads on and back.

### Every answer is written as it is given

There is no draft: each tap goes through the setter the settings screen uses, and each page shows what the app is set to
now, which is why every way out only closes the screen, why the colors picked take hold of the onboarding itself, and why
going through it again from Settings needs no state of its own. `OnboardingInstrument` is not a preference either:
picking one writes the chord diagrams' instrument and the tuner's tuning, and the page reads its answer back out of the
two (`onboardingInstrumentOf`), keeping a tuning the tuner already has for that instrument (`tuningFor`, so drop D stays
drop D); settings that match no answer, a fresh installation's among them, show nothing picked rather than a guess.

### One screen, one pager, one footer

`CampfireDestination.Onboarding(isOpenedFromSettings)` is one screen, drawn edge to edge on a `ScreenSurface` like the
editor: Skip at the top end on a first run (gone on the last page) or Close at the top start when opened from Settings,
a `HorizontalPager` of the pages that takes no swipes, and `OnboardingBottomBar` under all of them (Back, the dots, and
Next, which is Start playing on a first run's last page and Done on one opened from Settings). The page is
`Navigator.onboardingStep`, as the settings tab is `settingsTab`: `navigateBack` steps back through the pages before it
leaves the screen (`isOnboardingBackToPreviousStep`), which covers the desktop's Escape, and a `NavigationBackHandler`
of the screen's own claims the system's gesture and the browser's Back while there is a page to go back to, the pager
following a predictive back with the finger inside one scroll, as the settings tabs do. The step lasts for the session:
a process ended behind the onboarding comes back on its first page with every answer kept. On the web the screen is one
history entry at the address of the screen under it (`BrowserRoutes`), so a reload opens the app rather than a question.

`FirstRunController.openOnboardingOnFirstRun` opens it as the demo library is settled, while the launch screen is still
up, so the app is uncovered on it; it is skipped where a dialog is up or an import is waiting (`canShowOnboarding`). A
consent answered while it is on top does not open Settings over it; the Dropbox page says how it went.

### Layout

A page is centered while it fits and scrolls with faded edges while it does not, never wider than
`ONBOARDING_CONTENT_WIDTH`; the mark is left out under `ONBOARDING_MARK_MIN_HEIGHT` and the top bar is shorter under
`SHORT_WINDOW_HEIGHT`. Back fades rather than leaving the footer on the first page, so nothing in it moves.
