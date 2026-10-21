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

Implements `:domain:api` over `:data:repository:api`. A failure a use case notes but does not throw goes to the injected
`Logger` (`:data:model`). Use cases are grouped by feature, the same packages in `:domain:api`; `importing`, `library`,
`export`, and `songs` have a `CLAUDE.md` of their own.

## Import

`PrepareImportUseCaseImpl` + `ImportPlanner` work out what would happen; `ImportFilesUseCaseImpl` carries it out.

### Review (product rule)

- **Every import the user starts is reviewed before it writes** on its own screen (`CampfireDestination.ImportReport`,
  `:presentation`'s `ui/screens/importReport/`): every entry listed and ticked; names taken by something *different*
  each get keep-both or replace, plus one choice for all (`ImportChoices`); unticking leaves an entry out. A single
  song file opened with, shared to, or dropped on the app is written and opened directly unless its name is taken.
- **Replacing is the only overwrite in the app**: a choice on that screen plus a confirmation, and only
  `ImportConflictResolution.REPLACE` overwrites. `ImportFilesUseCaseImpl` re-checks the rule at the write.
- A song already in the library under its name, a numbered sibling (`x_2.cho`), or the exact name it arrived under is
  disregarded, compared after `ChordProPrettifier`, notation normalization, and one spelling per directive
  (`{t:X}` = `{title: X}`). Both sides are prettified, so files imported before formatting was automatic still match.
- Two different files of one batch wanting one name are never a question: the second is numbered. A library file the
  batch brings back unchanged is never offered for replacement (found in a first pass over the whole batch, since the
  unchanged copy may come after the song wanting its name); a different one wanting that name is numbered.

## Renames and references

- **A file is only renamed by the app when the user asks, or when nothing is lost.** A setlist's file follows its
  title (`EditSetlistUseCase`, which also writes the description), since nothing points at a setlist by name. A
  song's does not: its name may title it, setlists point at it, and the user may have chosen it. **Update file name**
  is offered where name and metadata drift apart beyond case or Unicode form (`Song.canUpdateFileName`). Old names
  stay until one of those happens.

## Others with logic

- `SetChordProTagUseCaseImpl` is the one `:chordpro` wrapper left, for the NFC fold; `:presentation` calls the rest
  directly and owns the preference translation (`presentation/mapper/ChordProMappers.kt`).
- `NormalizeTextUseCaseImpl` uses `:data:model`'s `withoutAccent` table (shared with file names) and drops combining
  marks. `NormalizeSearchTextUseCaseImpl` also strips spaces, punctuation, and symbols; it would break sorting
  (`a b` after `ab c`).
- Connecting (`ConnectSyncProviderUseCaseImpl`), and restoring at startup when credentials come back connected
  (`RestoreSyncUseCaseImpl`), start a sync run at once (not debounced) and do not wait for it.
- `CreateShareLinkUseCaseImpl` reads the account's list first and answers an existing share of the item, then builds
  the copy through the export use cases, undated (the same files make the same bytes on every device), and refuses one
  over `ImportLimits.MAX_IMPORT_SIZE`, which the recipient's import would refuse. The races are the repository's.
