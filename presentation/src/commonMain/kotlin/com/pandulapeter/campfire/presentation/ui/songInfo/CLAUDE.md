<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/songInfo

The About the song body (`SongInfoBody`), shared by the About the song sheet and the editor's preview card, and
its editors.


- **The one place a song's description is edited**: each group is a tile opening its own sheet; the cover opens the
  cover search. Key, capo, tempo, and time stay on the page; the sheet only reads what the file declares and opens Song
  defaults. No menu lists the sheet's editors one by one; Edit song file is the details menu's first own entry.
- `DialogType.SongInfo`, read live from the song's text and closed with the song; opened only by the title (below). The
  sheet and the preview's card share `SongInfoBody`; `songPlayingOverrides` is shared with the Song defaults sheet.
- Group titles are always plural, so a title does not change wording under the finger adding the first item.
- Chips in a tile are labels; the tile takes the tap. An empty group's call to action (`SongInfoAction`) is exactly
  `tagPillHeight` tall, so the tile keeps its height as its first chip arrives.
- Links are outlined buttons (they open a page, inside a tile that opens Manage links). **No tile is shorter than the
  links tile with one link** (`songInfoTileMinHeight`). Link buttons report their height without the touch target slack
  (`withoutTouchTargetSlack`), or their tile's centering would be off. The editor's compact card (`isCompact`) shows
  links as chips.
- Over the editor (`SongEditTarget.EditorDraft`) it reads `EditorSession.editorDraft`, the key as typed, no
  overrides; its tiles edit the typed text.
- `SongMetadataDialog.kt`: the duration's digits fill from the right like a timer (`DurationDigits.kt`, `428` is `4:28`,
  since a number pad has no colon), written as `m:ss`, at most `99:59:59`; a duration it cannot read is left alone on
  Save. Opens with no field focused. **Writes only the fields changed** (`ChordProMetadataFields.set`), so a field
  synced in meanwhile survives. A blank field removes the directive. A new title or artist is not a rename.
- `SongLinksDialog.kt`: a draft written once on Save. Rows are keyed by an id of the dialog's own so a moved row slides
  rather than two rows trading contents. Links keep the file's order (tags and languages are alphabetical,
  `sortedAlphabeticallyBy`). Duplicate or unusable addresses and a named row without one block Save; empty rows are
  dropped (`songLinksToSave`); links synced in while open but never offered are kept.
