<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/screens/songEditor/search

The editor's find and replace.

- Ctrl / Cmd + F and + R open and switch modes (`editorSearchShortcuts`; on the web the shell keeps the browser's find
  bar and reload from both while the editor is on top).
- `EditorSearchSheet` is the editor's own, not a `DialogType`, so it counts itself into `OverlayState`: Escape and the
  browser's Back close it before leaving the editor. Its state (`EditorSearch`) is saved with the field.
- A search over the preview switches to Edit. The current match is the first at or after the field's selection
  (`currentMatchIndex`, wrapping); reaching a match selects it, so the next typed letter searches on from it. A one-line
  selection opens as the query. Matches are case-insensitive unless Match case, never overlapping (`searchMatchStarts`).
  Replace all is one edit.
- The current match is revealed in the strip above the sheet (`EditorSearch.sheetTop` via `onTopMoved`, `revealTarget`),
  re-revealed when the strip settles at another height, debounced so the slide doesn't scroll per frame. While open, the
  field gains the covered height as room after its last line, shrunk only once the text has scrolled back above it, so
  closing slides rather than jumps.
