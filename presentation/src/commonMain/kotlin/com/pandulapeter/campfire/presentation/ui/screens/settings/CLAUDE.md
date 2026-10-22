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

Six tabs (General, Preferences, Library, Sharing, Features, About) in a `HorizontalPager` under a tab row (capped at
560dp) while that fits, else a `SettingsCategoryPane` beside the selected page; the layouts crossfade across the
breakpoint, as do the wide layout's pages. A build without sync leaves Sharing out.

## Tab contents (rules not obvious from the code)

- **General**: Performance (the Read only switch) leads, since General is the tab Settings opens on, and only while
  the songs are on (it fades out with them). Setup ("Set up Campfire again", the onboarding) follows.
- **Preferences**: how chords are written (they are in the Chords and Scales tabs too), then a Display section about
  songs alone, which leaves with the Songs switch. No instrument row; the instrument is picked where it is used
  (`ui/instrument`). Accidentals stay enabled under a numbering notation (they still spell its key). Examples are
  written in the picked notation, never in the language.
- **Library**: Sync, Import and export, then On this device last, since its rows can't be undone. Deleting the library
  goes through `DeleteLibraryDialog`, which wants `DELETE` typed in every language (into a capitalizing field).
- **Sharing** (`SharingSections`): there while disconnected too. Stages (`sharingStageOf`) crossfade, except the list
  arriving over "Nothing shared yet". Connect keeps `SyncConsentOrigin.Sharing`. Settling into view (not being composed
  by the pager) reads the account's list once per visit; the mirror shows meanwhile and offline, saying so once a read
  fails (`isStaleSharingList`). A row being stopped (every row, for Stop sharing everything) fades, its menu crossfading
  into a progress indicator until it shrinks in place (`sharedLinkRowsOf`), or, where the stop fails, back with a
  snackbar saying why. Stop sharing everything is alone in the last section.
- **Features**: Playing switches first, in the navigation's tab order (songs, setlists, metronome, tuner, chords, chord
  diagrams, scales, scale suggestions), then an Online group (song details lookup, cover art) so the two that make
  requests are found together. Rules in `ui/CLAUDE.md`.
- `BackgroundWarmthSlider` and `LanguageChoice` are shared with the onboarding's first page.

## Layout invariants

- No app bar; tabs at the top, everything start-aligned. **Tabs stay flat**, no divider: pages fade into them
  (`fadingTopEdge`); the tab row has no fade at its ends.
- `targetPage` lights the tab name (a press answers at once); **the indicator follows the pages**
  (`pagerIndicatorSpan`), so swipe, press, and predictive back move it identically.
- **Each page is one column**, capped at `THEME_COLOR_CHOICE_WIDTH`, at every width: two columns made tabs look unlike
  each other and change with window width. `SettingsPage` (`ui/settingsPage/`) allows two side-by-side sections (380dp
  each), plus a spanning `header`.
- Every section is titled, has no background, and no tab uses a card.
- A `verticalScroll`, not a lazy list: **every row is first composed in its current state**; rows that come and go
  (demo offer, Stop syncing, web storage) use `AnimatedSettingsRow`, which keeps the last value while leaving. Controls
  that don't fit a row's end are a `SettingsSubsection`.
- A pending sync deletion question puts a dot on the Library tab (and pane entry) instead of opening it; read through a
  derived state so per-file progress doesn't recompose the screen.

## Navigation and scroll

- `Navigator.settingsTab` (a state, since the web address names it) and `settingsScrollPositions` last the session,
  so Settings reopens on the tab and scroll it was left at; only a press of its own item scrolls it to the top.
- Back from any other tab goes to General first (`isSettingsBackToGeneral`): via `navigateBack` for Escape and a
  screen-owned `NavigationBackHandler` for gestures/browser Back, so predictive back previews General (one pager scroll
  for the whole gesture; the wide layout uses a `SeekableTransitionState` at the gesture's fraction).
- Pressing the open Settings item scrolls to top, then goes to General. Top level items and the open tab scroll to top
  through `Navigator.scrollToTopRequests`, collected with **`collectLatest`**: a touch interrupting the scroll
  throws `CancellationException`, which would end a plain `collect` and every later press.

## `SyncSettings.kt`

- Driven entirely by `SyncState`. **No action takes another's place as its state changes** (a double tap's second half
  would hit the opposite action): "Sync now" stays and is disabled during a run, "Stop syncing" appears under it,
  a connection failure's reason sits under Connect. A run's progress takes the status line under the account instead of
  a row of its own (the bar is `SyncProgressBar`'s, along the top of the window).
- `ConnectionFailed` is drawn as the invitation with the reason; pressing again goes to `Connecting`, which clears it.
  `Connecting` is left through Cancel whether or not the attempt still runs (on the web it never does).
- Empty `viewModel.syncController.syncProviders` (no key in the build) says so instead of offering a button that can't work.
- A finished run shows how long ago (`rememberElapsed`, recomputed when the words would change and at least each
  minute), never a count of what moved.

## The About section

### The app says nothing about the other builds but where to find them

- "Every version of Campfire" links to https://campfire-songbook.com/#download, which can change without a release.
- A null `Distribution.listingUrl` (`ui/platform/Platform.kt`) means not on that store yet (publishing fills it in).
- `platformStore` is the store of the platform the app **runs** on (a hand-built Mac build is still a Mac build). It
  decides the one "Rate Campfire" row (absent on Linux, the web, and where the listing is null) and
  `canAskForDonations` (never on Apple platforms, guideline 3.1.1).
- The row says *rate*, never *install*: a second copy would have its own library.
- **campfire-songbook.com is the website** (downloads, support page, privacy policy) and is linked first; **GitHub is
  the source code and issue tracker**, after it. Nothing else is linked but the author's site and the donation page.
- Every About row has a description and a leading icon, none a trailing mark.
- The log row (Help, last) is named for what it does here (`processLogActionTitle`: email, share, or save) and opens
  the `SendProcessLog` confirmation, which carries the privacy warning.
