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

- **Fixes for pictures, attachments, and their sync — first, before anything else ships**: large attachments no
  longer run an Android phone out of memory on Save and export, messages show over open sheets, the import report
  matches its review, a file lost to an older version's edit is restored from the cloud folder's history, and every
  smaller finding of the stability check of 2026-11-17.
  Plan: [attachments-release-fixes.md](plans/attachments-release-fixes.md)
- **Streaming archive import — critical for the next release**: archives of any size imported without holding them
  in memory, safety and reliability first, checked by hand on every platform before it ships. Starts once the fixes
  above have landed (they carry out its first storage steps).
  Plan: [streaming-archive-import.md](plans/streaming-archive-import.md)
- Hebrew import issues (https://www.tab4u.com/, https://negina.co.il/ )
- Add Slovak, Ukrainian and Dutch translations
- Add an option to pin chord diagrams / scales. In once-column mode, with enough horizontal space, chord diagrams could be displayed on the side.

## Short-term

- **Simpler comments and annotations** on songs, add ability to make notes / markups on music
- **Comments in setlists**, between songs
- **External monitor support**: an audience screen (a projector, a TV, or a monitor) showing the song in big text,
  with the app as the controller. Plan: [audience-screen.md](plans/audience-screen.md)
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
