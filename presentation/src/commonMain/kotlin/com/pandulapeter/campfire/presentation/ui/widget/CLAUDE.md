<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/widget

The home screen widgets' common side (Android and iOS): Random picks and Setlist. The widgets themselves are the
shells' (`app/android`'s Glance widgets, `app/ios`'s `CampfireWidgets` extension).

- **The app writes a snapshot, the widgets read only that** (`WidgetSnapshot`, JSON): every song (title, artist, link,
  cover), every setlist (title, `yyyy-MM-dd` day and the app's own writing of it, archived, its songs the library holds,
  link), the Songs and Setlists switches, the app's theme (`WidgetThemes.kt`: its fixed light or dark half, and its
  palette's few colors, sepia included, none for the system's own colors), and every text already in the app's language,
  so a widget never reads the library, parses ChordPro, or starts Koin. Not a stored document: no version, unknown keys
  ignored, and the iOS extension decodes the same keys in Swift. An update's widgets read the old app's file until the
  app is opened, so a key added later has a default (optional in Swift). Never backed up, never synced.
- `WidgetSnapshotEffect` builds it from a library read whole (`DataState.Idle` and `isWholeLibrary`, never a first
  read's batches) and writes it through the shell's `WidgetFiles` once it has held for a second, again only when it
  changes.
- **Covers** (`WidgetPublisher`, `WidgetCovers.kt`): a song's `cover` names a small JPEG thumbnail (120 px on its
  shorter side, made by `reencodePicture`) in a `covers/` folder beside the snapshot, named by the address's SHA-256,
  since an iOS extension cannot read the app's `covers/`. The bytes come through `GetCoverArtUseCase`, as the songs
  list's; none with Cover art off, and a cover that cannot be had leaves its song without one (no placeholder). The
  snapshot is written first with the thumbnails already there, then the unnamed ones are deleted and the missing ones
  made, and it is written again with them: a snapshot never names a file that is not there. The shells only write,
  list, and delete files.
- **Only for placed widgets** (`WidgetPlacement`, `coverAddressesShownBy`): a Random picks widget anywhere covers every
  song; Setlist widgets alone, the songs of every setlist (not only the ones they show now, since the next gig moves on
  without the app); none placed, nothing is fetched and every thumbnail is deleted. Asked again at every return to the
  front (`RESUMED`) and whenever the shell says the widgets changed (Android's receivers). A change that settles after
  the app left (a theme picked, then the home screen) is published at once, not at the return.
- A counted text is a `Plural` of the language's template for each count from 0 to 199 (a larger count reads as the
  one past 100 with its last two digits), so neither platform needs the plural rules.
- **Every tap is an app link** (`navigation/CLAUDE.md`), built here: a song's details, a setlist's first song in setlist
  mode, else (no songs) `setlist/{setlist}`, which brings it into sight, and each of a setlist's songs (`songLinks`) the
  setlist at that song.
- The decisions are pure and tested here (`WidgetContent.kt`), Android's widgets calling them: the draw (up to the
  count, by a seed the widget keeps, each song ranked by its file name's hash alone so the library's order never
  changes it; no 20-song threshold, the songs list's Random picks switch does not apply) from the songs the widget's
  filter leaves (`RandomPicksFilter`: the songs screen's tag and language rule, any of a group, a value the library
  lost ignored), as many as its pick count (`RANDOM_PICKS_COUNTS`, 5 until set), Shuffle only over more songs than that, the
  next gig (not archived, earliest day from today, ties in the setlists screen's order), and the relative day. iOS
  writes the same few rules in Swift.
- Songs switched off, both widgets say so; Setlists off, the setlist widget does. An empty library says to add songs, no
  upcoming setlist and a pinned one gone (deleted or renamed) say so and open the setlists screen.
