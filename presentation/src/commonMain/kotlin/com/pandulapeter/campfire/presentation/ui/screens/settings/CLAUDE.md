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

Six tabs under a tab row (capped at 560dp) while that fits, else a category pane beside the selected page; the layouts
crossfade across the breakpoint, as do the wide layout's pages. A build without sync leaves Sharing out.

## Tab contents

- **General**: Performance (the Read only switch) leads, since General is the tab Settings opens on, and only while the
  songs are on (it fades out with them). Appearance ends with the Tooltips switch.
- **Preferences**: how a song is read, where Features is what the app has. Lists first, the one section found nowhere
  else; the chord-writing rows are disabled only while nothing names a chord (the Chords and Scales tabs do too). Lists
  and Song page leave with the Songs switch. No instrument row: it is picked where it is used (`ui/instrument`).
  Accidentals stay enabled under a numbering (they still spell its key). Examples are written in the picked notation,
  never in the language.
- **Library**: On this device last, since its rows can't be undone. Deleting the library wants `DELETE` typed in every
  language (into a capitalizing field).
- **Sharing**: there while disconnected too. Stages crossfade, except the list arriving over "Nothing shared yet".
  Settling into view (not being composed by the pager) reads the account's list once per visit; the mirror shows
  meanwhile and offline, saying so once a read fails. A row being stopped (every row, for Stop sharing everything)
  fades, its menu crossfading into a progress indicator until it shrinks in place, or back with a snackbar saying why
  where the stop fails. Stop sharing everything is alone in the last section.
- **About**: Help starts with Keyboard shortcuts on a desktop or web build, elsewhere only while a keyboard is attached.
- **Features**: one switch per tab in the navigation's order, then an Online group, so the two that make requests are
  found together (`ui/CLAUDE.md`).

## Layout invariants

- No app bar; everything start-aligned. **Tabs stay flat**, no divider: pages fade into them; the tab row has no fade at
  its ends.
- The pressed tab's name lights at once; **the indicator follows the pages**, so swipe, press, and predictive back move
  it identically.
- **Each page is one column**, capped at `THEME_COLOR_CHOICE_WIDTH`, at every width: two columns made tabs look unlike
  each other and change with window width. Sections may sit side by side (`ui/settingsPage/`).
- Every section is titled, has no background, and no tab uses a card.
- A `verticalScroll`, not a lazy list: **every row is first composed in its current state**; rows that come and go use
  `AnimatedSettingsRow`, which keeps the last value while leaving. Controls that don't fit a row's end are a
  `SettingsSubsection`.
- A pending sync deletion question puts a dot on the Library tab instead of opening it, read through a derived state so
  per-file progress doesn't recompose the screen.

## Navigation and scroll

- The tab and each page's scroll last the session (the tab is a state, since the web address names it), so Settings
  reopens where it was left; the tab row slides to the open tab on first layout. Only a press of its own item scrolls to
  the top, then goes to General.
- Back from any other tab goes to General first: `navigateBack` for Escape, a screen-owned `NavigationBackHandler` for
  gestures and browser Back, so predictive back previews General (one pager scroll for the whole gesture; the wide
  layout seeks a `SeekableTransitionState`).
- Scroll-to-top requests are collected with **`collectLatest`**: a touch interrupting the scroll throws
  `CancellationException`, which would end a plain `collect` and every later press.

## Sync

- **No action takes another's place as its state changes** (a double tap's second half would hit the opposite action):
  "Sync now" stays, disabled during a run, "Stop syncing" appears under it, a connection failure's reason sits under
  Connect. A run's progress takes the status line under the account, not a row of its own.
- A failed connection is drawn as the invitation with the reason. Connecting is left through Cancel whether or not the
  attempt still runs (on the web it never does).
- A build with no key says so instead of offering a button that can't work.
- A finished run shows how long ago (recomputed when the words would change), never a count of what moved.

## About

- The app says nothing about the other builds but where to find them: "Website" first opens campfire-songbook.com
  itself; "Every version of Campfire" links to its `#download`, which can change without a release. **The website** is
  downloads, support, and privacy policy; **GitHub** the source and issue tracker, after it.
- A null `Distribution.listingUrl` means not on that store yet. The store is the one of the platform the app **runs**
  on (a hand-built Mac build is still a Mac build); it decides the one "Rate Campfire" row (absent on Linux, the web,
  and with no listing) and donations (never on Apple platforms, guideline 3.1.1). The row says *rate*, never *install*:
  a second copy would have its own library.
- "Share Campfire" sends the website's address, never a listing, only where the platform shares (elsewhere it would
  repeat the download row). Never rewarded (Apple 3.2.2, Play's incentivized installs).
- Every About row has a description and a leading icon, none a trailing mark.
- The log row (Help, last) is named for what it does here (email, share, or save) and opens the confirmation that
  carries the privacy warning.
