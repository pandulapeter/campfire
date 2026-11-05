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

- Add tooltips that mention keyboard shortcuts
- Support multi-line sticky headers (setlist titles)
- Song title font on card should have smaller line spacing, and that should be consistent between the two card display modes
- Minimum card width should be reduced so that 2 columns are displayed on an unfolded Fold 8 / iPhone Duo
- Add option to switch metronome tempo from the next beat instead of from the next bar
- The chord shape picker bottom sheet performs an unnecessary horizontal animation on Hebrew only
- Full Preferences redesign: currently accidentally turning Performance mode on results in confusion + settings don't have a centralized place
- Add Portuguese, Polish, Czech, Slovak, Ukranian and Dutch translations
- Add an option to pin chord diagrams / scales. In once-column mode, with enough horizontal space, chord diagrams could be displayed on the side.
- Find a solution for storing the library used for screenshot generation in the cloud and automate updating screenshots

## Short-term

- **Rename the new `master` branch to `main`**
- **External monitor support**: an audience screen (a projector, a TV, or a monitor) showing the song in big text,
  with the app as the controller. Plan: [audience-screen.md](plans/audience-screen.md)
- **Documentation of every keyboard shortcut** used in the app
- **Google Drive sync**, as a second provider beside Dropbox. The groundwork landed for v5.1.0; spikes S1–S5 are next.
    Plan: [google-drive-sync.md](plans/google-drive-sync.md)
- **A more readable sync folder**: consider organizing it in a more user-readable way (subfolders for artists), as an
  option

## Long-term

- **Simpler comments and annotations** on songs
- **Multi-cursor support in the editor**: multiple carets and column selection.
  Plan: [multi-caret-editor.md](plans/multi-caret-editor.md)
- **Comments in setlists**, between songs
- **Importing sheet music from MusicXML** (`.musicxml` and `.mxl`, which every notation app exports) as a song with
  its notation and, where the score has lyrics, a chord sheet of its verses; PDF sheet music stays out (it needs OMR).
  Plan: [musicxml-import.md](plans/musicxml-import.md)
- **A streaming zip writer** on all platforms
