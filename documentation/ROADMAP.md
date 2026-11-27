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
- The Random picks widget's shuffle button doesn't work
- Update and improve widget descriptions
- The metronome bottom sheet could include a slider
- **Debug builds apart from release builds** on every platform (their own identity, data, Dropbox sign-in, and deep
  links, on physical devices too), marked by the app icon's logo drawn with a dashed line in every theme color.
  Plan: [debug-builds.md](plans/debug-builds.md)
- Allow changing menu item order


## Short-term

- Add Slovak, Ukrainian and Dutch translations
- Add ability to make notes / markups on music
- **External monitor support**: an audience screen (a projector, a TV, or a monitor) showing the song in big text,
  with the app as the controller. Plan: [audience-screen.md](plans/audience-screen.md)
- **Google Drive sync**, as a second provider beside Dropbox. The groundwork landed for v5.1.0; spikes S1–S5 are next.
  Plan: [google-drive-sync.md](plans/google-drive-sync.md)

## Long-term

- **Importing sheet music from MusicXML** (`.musicxml` and `.mxl`, which every notation app exports) as a song with
  its notation and, where the score has lyrics, a chord sheet of its verses; PDF sheet music stays out (it needs OMR).
  Plan: [musicxml-import.md](plans/musicxml-import.md)
- **Multi-cursor support in the editor**: multiple carets and column selection.
  Plan: [multi-caret-editor.md](plans/multi-caret-editor.md)
- **The sync engine as a library**: sync, its providers, and the storage they need moved into
  app-agnostic `:libs:sync:*` modules, so later apps (a diary, a notes app) could share it and a new provider would be
  one library module. Parked until a second app is being built.
  Plan: [sync-library-extraction.md](plans/sync-library-extraction.md)
