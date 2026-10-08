<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/settings

The settings screen.

### `ui/screens/settings/`

`ui/screens/settings/` — the settings screen is **five tabs**, one `SettingsTab` per page of a `HorizontalPager` while
the available width fits the 560dp tab row and, above that, the entries of a 180dp `SettingsCategoryPane` at the start
of the screen with the selected page next to it (`NavigationDrawerItem`s, the dot of a pending sync answer as their
badge; the choice is written straight to `settingsTab`, and the pager, which keeps a state of its own in
`SettingsTabPager`, opens on it when a window narrows again): General (theme, color, whether the app icon takes that
color, the sepia slider, language), Features (the switches that take whole parts of the app away, see the root
`CLAUDE.md`: read only mode, chords, chord diagrams — disabled with the chords off —, metronome, setlists and cover
art), Songs (how a song is read - section numbers, the chord spelling — the notation as a list of five radio rows,
Standard, German, Latin, Nashville numbers and Roman numerals, each with an example under it, since five names do not
fit a row of segments on a phone, and the accidentals, which stay enabled under a numbering since they still spell its
key — and the instrument the chord diagrams are drawn for, disabled with the chords switched off, and the instrument
with the diagrams too), Library (the sync section on an `ElevatedCard`, the one card in the app, then what the library
holds and what can be done with it — its song and setlist counts and the cover cache are each a row that deletes what it
counts, the cache behind an ordinary confirmation and the library behind `DeleteLibraryDialog`, which wants `DELETE`
typed in every language, into a field that capitalizes what is typed) and About (the app, its links and where its other
builds are listed).

The screen has no app bar: the tabs are at the top of the screen, next to the navigation rail, and everything on it
starts at the start edge like the lists of the other screens rather than being centered. **The tabs stay flat** and have
no divider under them: a page scrolled under them fades out into them (`fadingTopEdge`). The tabs themselves are capped
at 560dp and start at the start edge, and the tab being headed for (`targetPage`) is the one marked, so the indicator
sets off the moment a tab is pressed. A sync run waiting to hear whether its deletions are meant puts a dot on the
Library tab rather than opening it, since the question may be a tab away; that is read through a derived state so a run
reporting every file does not recompose the whole screen. Every page is composed with the screen
(`beyondViewportPageCount` covers them all), so a tab is never first composed as it is swiped to.

The tab left open and each tab's own scroll position are `CampfireViewModel.settingsTab` and `settingsScrollPositions`,
next to the other screens' scroll positions and for the same reason — the tab is a state, written whenever the pages
settle, since the web build's address names it; the tab lasts for the session, while the scroll of every tab is reset
whenever Settings is selected from another top level screen (the one screen that does not keep where it was left). Back
from any other tab goes to General before it leaves the screen (`isSettingsBackToGeneral`, answered in `navigateBack`
for the desktop's Escape and by a `NavigationBackHandler` of the screen's own for the gestures and the browser's Back,
so a predictive back does not preview the songs it is not going to, and previews General instead: the pager scrolls
towards it with the finger, inside one scroll that lasts the gesture so the tabs it passes are never settled on, and the
wide layout's crossfade is a `SeekableTransitionState` held at the gesture's fraction; letting go animates on from
there, and a cancel back).

Pressing the Settings item while it is open goes to General, scrolled to the top; pressing the tab that is open scrolls
it to the top, as does pressing the Songs or Setlists item on its screen (`CampfireViewModel.scrollToTopRequests`, which
the three top level screens collect). The two layouts crossfade when a window is resized across the line between them,
and so do the pages of the wide one as a category is picked (a pager slides only where a finger drags it). Each page
(`SettingsPage`, `SettingsPage.kt`) is a `SettingsSection` or two: side by side where the settled width has room for
both at 380dp each, stacked otherwise, each column capped at `THEME_COLOR_CHOICE_WIDTH` (every color disc in one row)
and starting at the start edge, the room next to the category pane being what the columns are counted in. Sections have
no background and no title of their own, so their rows keep the keylines of the app bar and of every list, and the tab
names them; the Library tab, the one with two, sets the first apart by putting it on a card.

It is a `verticalScroll` and not a lazy list: a lazy list has no notion of a section holding several rows, a tab is a
few dozen rows, and **every row is first composed in the state it is in** — what comes and goes afterwards (the demo
offer, a sync run's progress, the web's storage row) expands and shrinks inside its section through
`AnimatedSettingsRow`, which keeps the last value to draw while it leaves, where an item added to a lazy list a frame
late was animated in. A control that does not fit at the end of a row (the segmented choices, the color discs, the
language list) is a `SettingsSubsection`, set in a list item's own headline and supporting styles since it is a peer of
the switch next to it.

**The About tab is one section** (`AboutSection`, untitled, since the tab names it) and the same rows on every platform:
"Every version of Campfire" (the download section of campfire-songbook.com, which is the whole of what the app says
about the other builds), Help and support (the website's support page, which gives an email address and the GitHub
issues), the rating row, the privacy policy, GitHub (the source code and the issue tracker, after the website's rows on
purpose), the author and the version and, last and where `canAskForDonations`, the coffee. Every row has a description.
The rating row opens `platformStore`'s `listingUrl` — the store of the platform the app runs on rather than the one it
came from — and is absent on Linux, in the web build and wherever that listing does not exist yet; its wording says
*rate*, since a store page also offers to install a second copy of the app. **campfire-songbook.com is the app's
website** and GitHub its source code and issue tracker; the author row is the link to the author's own site.

### `ui/screens/settings/SyncSettings.kt`

`ui/screens/settings/SyncSettings.kt` — the rows of the sync section (the first section of the Library tab), driven
entirely by `SyncState`: an invitation to connect, or the account with what the last run did and the two things one can
do about it. No action ever takes the place of another as the state it starts changes: "Sync now" stays where it is and
is disabled during a run, "Stop syncing" is part of the progress row under it, and the reason a connection failed is
under the Connect row rather than above it — a double tap otherwise landed its second half on the opposite action.

`SyncState.ConnectionFailed` is drawn as the invitation to connect with the reason under it, so a refused consent, a
redirect that did not match or a failed code exchange says what happened instead of putting the button back as if it had
done nothing; pressing it again moves on to `Connecting`, which clears the message, and `Connecting` is left through its
Cancel row whether or not the attempt that got there is still running — on the web it never is. A build with no sync
credentials has an empty `viewModel.syncProviders` and says so, rather than offering a button that cannot work. A
finished run is reported as a success with how long ago it happened ("15 minutes ago", "Yesterday", `elapsed` in
`components/RelativeTime.kt`), worked out again for as long as the screen is open at the very moment the words would
change and at least once a minute (`rememberElapsed`), so it is never behind the clock, never as a count of what moved.

## The About section

### The app says nothing about the other builds but where to find them

**The app says nothing about the other builds but where to find them.** Settings → About is one section on every
platform, and the row that names no platform — "Every version of Campfire" — leads to the download section of the app's
website, https://campfire-songbook.com/#download (the `campfire-website` repository, which the web build is deployed
into as well), which is a page that can be kept up to date without a release and the one place a store has nothing to
say about. `Distribution` (in `:presentation`'s `ui/platform/Platform.kt`) is now just the four app stores and their
listing URLs, a null `listingUrl` marking one the app is not on yet; publishing is filling it in.

Every platform has exactly one official way to get the app, so no build is told where it is handed out: `platformStore`
is the store of the platform the app is **running** on — a Mac build made by hand is a Mac build like the one the Mac
App Store hands out — and it decides both the one "Rate Campfire" row, absent on Linux, on the web and wherever that
listing does not exist yet, and whether the app may ask for money at all (`canAskForDonations`: never on an Apple
platform, guideline 3.1.1). The row says *rate* and never *install*: a store page carries an install button, and a
second copy of the app would come with a library of its own. **campfire-songbook.com is the project's website**, and
what the app and the README point people at first: the downloads, the support page (which answers the common questions
and gives both an email address and the GitHub issues as the way to report a problem — Settings' "Help and support"
row), and the privacy policy. **GitHub is the source code and the issue tracker**, and its row comes after those; the
About section links nothing else but the author's own site and the donation page.
