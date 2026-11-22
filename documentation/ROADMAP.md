<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Roadmap

What's planned for Campfire, grouped by area.

## In progress

- **A song from any page**: share, paste, or (optionally) a browser extension button turns any website's song into a
  clean pre-filled editor, a small model of our own cropping the page's clutter on the device, no code per site; the same
  model then reads PDFs (websites printed to PDF, misread song details).
  Plan: [song-from-any-page.md](plans/song-from-any-page.md)
- **Home screen widgets** on Android and iOS: random picks (a few songs, a tap opening one, and a shuffle button, drawn
  on its own rather than mirroring the songs list's picks), and a setlist (the next gig or a pinned one, a tap opening
  its first song in setlist mode). Plan: [home-screen-widgets.md](plans/home-screen-widgets.md)
- **App shortcuts** on Android and iOS: a long press on the app icon offers New song, Paste a song, and the main
  screens that are switched on. Plan: [app-shortcuts.md](plans/app-shortcuts.md)
- **A more readable sync folder**: consider organizing it in a more user-readable way (subfolders for artists), as an
  option

## Short-term

- Add Slovak, Ukrainian and Dutch translations
- Add an option to pin chord diagrams / scales. In once-column mode, with enough horizontal space, chord diagrams could be displayed on the side.
- Add ability to make notes / markups on music
- **External monitor support**: an audience screen (a projector, a TV, or a monitor) showing the song in big text,
  with the app as the controller. Plan: [audience-screen.md](plans/audience-screen.md)
- **The sync engine as a library**: sync, its providers, and the storage they need moved into
  app-agnostic `:libs:sync:*` modules, so later apps (a diary, a notes app) could share it and a new provider would be
  one library module. Parked until a second app is being built.
  Plan: [sync-library-extraction.md](plans/sync-library-extraction.md)
- **Google Drive sync**, as a second provider beside Dropbox. The groundwork landed for v5.1.0; spikes S1–S5 are next.
  Plan: [google-drive-sync.md](plans/google-drive-sync.md)

## Long-term

- **Multi-cursor support in the editor**: multiple carets and column selection.
  Plan: [multi-caret-editor.md](plans/multi-caret-editor.md)
- **Importing sheet music from MusicXML** (`.musicxml` and `.mxl`, which every notation app exports) as a song with
  its notation and, where the score has lyrics, a chord sheet of its verses; PDF sheet music stays out (it needs OMR).
  Plan: [musicxml-import.md](plans/musicxml-import.md)
