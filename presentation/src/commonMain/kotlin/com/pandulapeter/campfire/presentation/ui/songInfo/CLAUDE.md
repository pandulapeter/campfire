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

The About the song body, shared by its sheet and the editor's preview card.

- **The one place a song's description is edited**: each group is a tile opening its own sheet. Key, capo, tempo, and
  time stay on the page; the sheet only reads what the file declares and opens Song defaults. No menu lists the sheet's
  editors one by one.
- Read live from the song's text (over the editor, the draft as typed, no overrides) and closed with the song.
- Group titles are always plural, so a title does not change wording under the finger adding the first item.
- Chips in a tile are labels; the tile takes the tap. An empty group's call to action is exactly `tagPillHeight` tall,
  so the tile keeps its height as its first chip arrives.
- **No tile is shorter than the links tile with one link**. Link buttons report their height without the touch target
  slack, or their tile's centering would be off. The editor's compact card shows links as chips.
- **Attachments** follow the links (`SongInfoGroupKind.ATTACHMENTS`): one full-width outlined row each, as tall as a link
  button (the same slack taken out), its type's icon (`AttachmentKind`), its name (else the type's), and its size; a
  press opens it (`SongAttachmentEditing.open`, read only mode too). A file this device lacks (sync not done, a song
  copied in by hand) is the same row disabled, "Not on this device" in place of the size; presence and size are
  `LibraryFileSizes.fileSizes`, the draft shelf's included. The compact card shows them as chips. The group is left
  out where `SongInfoEditing.onEditAttachments` is null (the Features switch).
- The Attachments sheet (`SongAttachmentsDialog`) is the links' sheet's twin: Add file in the header
  (`SongAttachmentEditing.pick`, kept on the draft shelf, arriving through `picked` for the sheet of its target), a name
  field and Remove per row, keyed by file, Save writing once (`SaveSongAttachmentsUseCase` for a file, the draft's text
  over the editor), the close button cancelling; attachments synced in while it was open are kept.
- Song details: the duration's digits fill from the right like a timer (`428` is `4:28`, since a number pad has no
  colon), at most `99:59:59`; a duration it cannot read is left alone on Save. Opens unfocused. **Writes only the fields
  changed**, so a field synced in meanwhile survives. A blank field removes the directive. A new title or artist is not
  a rename.
- Links: a draft written once on Save. Rows are keyed by an id of the dialog's own, so a moved row slides rather than
  two rows trading contents. Duplicate or unusable addresses and a named row without one block Save; empty rows are
  dropped; links synced in while open but never offered are kept.
