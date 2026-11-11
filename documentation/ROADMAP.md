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

- Tooltips should respect insets
- Add an option to pin chord diagrams / scales. In once-column mode, with enough horizontal space, chord diagrams could be displayed on the side.

## Short-term

- **A streaming zip writer** on all platforms: exports (archives and PDFs) written to a file instead of held in memory,
  needed before songs' images go into exports and shared links. Plan:
  [streaming-zip-writer.md](plans/streaming-zip-writer.md)
- **Images in songs**: ChordPro's `{image}` directive, pictures kept in the library and named by their content, synced,
  exported, shared, and printed with the song; a lone image file imports as a song (a chart that only exists on
  paper or as a PDF, still in the setlist). Plan: [song-images.md](plans/song-images.md)
- Add Portuguese, Polish, Czech, Slovak, Ukrainian and Dutch translations
- **Rename the new `master` branch to `main`**
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
- Find a solution for storing the library used for screenshot generation and the PDF goldens in the cloud and automate updating screenshots
