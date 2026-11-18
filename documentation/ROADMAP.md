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

- **Streaming archive import — critical for the next release**: archives of any size imported without holding them
  in memory, safety and reliability first, checked by hand on every platform before it ships. It is built and on main, unreleased;
  left are the release check by hand on every platform and removing the old in-memory path.
  Plan: [streaming-archive-import.md](plans/streaming-archive-import.md)
- Hebrew import issues (https://www.tab4u.com/, https://negina.co.il/ )
- Add Slovak, Ukrainian and Dutch translations
- Add an option to pin chord diagrams / scales. In once-column mode, with enough horizontal space, chord diagrams could be displayed on the side.

## Short-term

- **Simpler comments and annotations** on songs, add ability to make notes / markups on music
- **Comments in setlists**, between songs
- **External monitor support**: an audience screen (a projector, a TV, or a monitor) showing the song in big text,
  with the app as the controller. Plan: [audience-screen.md](plans/audience-screen.md)
- **Sync's knowledge of songs behind seams**: the engine's keep, delete, and conflict rules depend on a few narrow
  interfaces instead of songs and setlists, a check keeps it so, and sign-in and transport are separated from Dropbox.
  Safer deletions, and the groundwork Google Drive builds on, so it lands before Drive's provider work.
  Plan: [sync-seams.md](plans/sync-seams.md)
- **Google Drive sync**, as a second provider beside Dropbox. The groundwork landed for v5.1.0; spikes S1–S5 are next.
  Plan: [google-drive-sync.md](plans/google-drive-sync.md)
- **A more readable sync folder**: consider organizing it in a more user-readable way (subfolders for artists), as an
  option

## Long-term

- **Multi-cursor support in the editor**: multiple carets and column selection.
  Plan: [multi-caret-editor.md](plans/multi-caret-editor.md)
- **Importing sheet music from MusicXML** (`.musicxml` and `.mxl`, which every notation app exports) as a song with
  its notation and, where the score has lyrics, a chord sheet of its verses; PDF sheet music stays out (it needs OMR).
  Plan: [musicxml-import.md](plans/musicxml-import.md)
- **Low priority: the sync engine as a library**: sync, its providers, and the storage they need moved into
  app-agnostic `:libs:sync:*` modules, so later apps (a diary, a notes app) could share it and a new provider would be
  one library module. Parked until a second app is being built; needs the sync seams first.
  Plan: [sync-library-extraction.md](plans/sync-library-extraction.md)
