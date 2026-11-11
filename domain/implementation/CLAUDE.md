<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :domain:implementation

A failure a use case notes but does not throw goes to the injected `Logger`. `importing`, `library`, `export`, and
`songs` have a `CLAUDE.md` of their own.

## Import review (product rule)

- **Every import the user starts is reviewed before it writes** (`CampfireDestination.ImportReport`): every entry
  listed and ticked; names taken by something *different* each get keep-both or replace, plus one choice for all
  (`ImportChoices`); unticking leaves an entry out. A batch that is one song is previewed instead, on the song details
  screen, and written only by its Import.
- `ImportChoices.tags` go into every song written (new, numbered, or replacing) in the text it is written with, so a
  file is written once; identical songs, which are never written, are left untagged.
- **Replacing is the only overwrite in the app**: a choice on that screen plus a confirmation, and only
  `ImportConflictResolution.REPLACE` overwrites. `ImportFilesUseCaseImpl` re-checks the rule at the write.
- A song already in the library under its name, a numbered sibling (`x_2.cho`), or the exact name it arrived under is
  disregarded, compared after `ChordProPrettifier`, notation normalization, and one spelling per directive. Both sides
  are prettified, so files imported before formatting was automatic still match.
- Two different files of one batch wanting one name are never a question: the second is numbered. A library file the
  batch brings back unchanged is never offered for replacement (found in a first pass over the whole batch, since the
  unchanged copy may come after the song wanting its name); a different one wanting that name is numbered.

## Renames and references

- **A file is only renamed by the app when the user asks, or when nothing is lost.** A setlist's file follows its
  title, since nothing points at a setlist by name. A song's does not: its name may title it, setlists point at it, and
  the user may have chosen it. **Update file name** is offered where name and metadata drift apart beyond case or
  Unicode form (`Song.canUpdateFileName`).
- **Only the use case that renamed or deleted a file can tell the two apart** (sync sees a deletion plus a new file), so
  it tells `SharedLinksRepository` once the file has moved or gone, not cancellably: a song's rename, a setlist's new
  title, either deletion, and Delete library (before its run starts). A shared item's link then follows it or stops.

## Others with logic

- `SetChordProTagUseCaseImpl` is the one `:libs:chordpro` wrapper left, for the NFC fold (tag matching ignores case);
  `:presentation` calls the rest directly.
- `NormalizeTextUseCaseImpl` uses `:data:model`'s `withoutAccent` table (shared with file names) and drops combining
  marks; the marks and word-final letter forms of other scripts come from `ScriptFolding`, which file names never use.
  `NormalizeSearchTextUseCaseImpl` also strips spaces, punctuation, and symbols, so it must never be used for sorting
  (`a b` would sort after `ab c`).
- Connecting and restoring at startup start a sync run at once (not debounced) and do not wait for it.
- `CreateShareLinkUseCaseImpl` answers an existing share of the item (never one whose copy is gone,
  `ShareState.COPY_MISSING`, which the new share replaces), builds the copy undated (the same files make the same bytes
  on every device), and refuses one the recipient's import would refuse (`ExportedFile.isTooLargeToImport`: unpacking to over
  `ImportLimits.MAX_IMPORT_SIZE`, or larger itself).
