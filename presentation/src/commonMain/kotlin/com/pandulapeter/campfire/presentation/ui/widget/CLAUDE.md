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

- **The app writes a snapshot, the widgets read only that** (`WidgetSnapshot`, JSON): every song (title, artist, link),
  every setlist (title, `yyyy-MM-dd` day and the app's own writing of it, archived, its songs the library holds, link),
  the Songs and Setlists switches, and every text already in the app's language, so a widget never reads the library,
  parses ChordPro, or starts Koin. Not a stored document: no version, unknown keys ignored, and the iOS extension
  decodes the same keys in Swift. Never backed up, never synced.
- `WidgetSnapshotEffect` builds it from a library read whole (`DataState.Idle` and `isWholeLibrary`, never a first
  read's batches) and hands it to the shells once it has held for a second, again only when it changes.
- A counted text is a `Plural` of the language's template for each count from 0 to 199 (a larger count reads as the
  one past 100 with its last two digits), so neither platform needs the plural rules.
- **Every tap is an app link** (`navigation/CLAUDE.md`), built here: a song's details, a setlist's first song in setlist
  mode, else (no songs) `setlist/{setlist}`, which brings it into sight.
- The decisions are pure and tested here (`WidgetContent.kt`), Android's widgets calling them: the draw (up to the
  count, by a seed the widget keeps, each song ranked by its file name's hash alone so the library's order never
  changes it; no 20-song threshold, the songs list's Random picks switch does not apply), the
  next gig (not archived, earliest day from today, ties in the setlists screen's order), and the relative day. iOS
  writes the same few rules in Swift.
- Songs switched off, both widgets say so; Setlists off, the setlist widget does. An empty library says to add songs, no
  upcoming setlist and a pinned one gone (deleted or renamed) say so and open the setlists screen.
