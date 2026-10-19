<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# 37th review: regression of the 35th and 36th sweeps' landings

Reviewed at `231b53885` (master), 2026-10-19. Angle: the plans executed today (the converter and other apps'
formats from the 35th; Dropbox transport, sync scheduling, covers, the update gate, and Connecting from the 36th) were
never reviewed after landing. Lean: three Opus area reviewers (imports, transport and sync, UI), the scoped docs check
on Sonnet (no false claims), a regression read of the fixes, two plan writers, two challengers.

## Headlines

- An XML or HTML file with a stray closing tag failed the whole import, or a whole zip's songs (fixed).
- A stalled Dropbox sign-in service makes every concurrent caller renew in turn: six callers take 21 minutes and 42
  token exchanges to fail instead of 5 minutes and 7 (plan 01).
- A JSON export's own title is replaced by its first lyric when that lyric sits over a chord row (plan 03).

## Fixed during the review

- `5648d6058` Keep the required update screen still while it fades out after Continue offline.
- `95b9e8318` Skip an XML or HTML file with a stray closing tag instead of failing the whole import.
- `545f98c06` Keep a brace in an OpenSong section name from cutting its heading short when importing.
- `c205c006d` Name a large batch of imported songs sharing one title without slowing down with every song.
- `3ca463921` Set the chords of an OnSong song whose sections are headed in brackets, such as [Chorus], when importing.
- `0eb806041` Put two chord sheet converter KDocs back above the functions they describe.
- `470c4efdb` Keep an annotated chord set against a word in an OnSong song as it is written when importing (found by
  the regression read of `3ca463921`).

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Share a token renewal that failed for the network with every caller that waited for it | medium | T |
| 02 | Count a Dropbox call's requested waits and its failures without an answer apart | medium | T |
| 03 | Keep a JSON song's own title, and its first lyric | medium | I |
| 04 | Set the chord rows an OpenSong song marks with a dot as chord rows | medium | I |
| 05 | Keep a title's own "Chord" when reading a page's title | low | I |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| T | Dropbox transport | 01, 02 | `dropbox/DropboxTokens.kt`, `dropbox/DropboxTransport.kt`, their tests, `data/source/remote/implementation/CLAUDE.md` |
| I | Import converters | 03, 04, 05 | `chordpro/.../convert/` (`ChordSheetConverter.kt`, `ChordSheetTitles.kt`, `CLAUDE.md`, tests), `backup/JsonSongs.kt`, `backup/OpenSongSong.kt`, their tests, `data/source/local/implementation/CLAUDE.md`, `ui/platform/SharedTextName.kt` and its test |

**Merge order:** T, then I. The lanes share no file, so the order only matters for the build: I touches
`:presentation` and runs last so the full build after it covers everything. Both may run at once (two Gradle builds).

**Shared files:** none.

## Decisions

- **03, paste half — decided 2026-10-19: keep the rule (as recommended).** A paste whose first line sits straight over a chord row reads that line as
  the title (39901f6e7; `ChordSheetConverterTest` pins `Riverlight` / `Am C` / `Hello world`). An invented lyric there
  cannot be told from a title. Recommended: keep the rule; the editor shows the guessed title, so the line is misplaced,
  not lost. Plan 03 fixes only the JSON half either way.

## Checked and found solid

- Docs: no false claim in the CLAUDE.md files under the paths changed since `4daf236d2`; the stale-name script's five
  flags are external names.
- UI: Connecting and Cancel during a consent answer; the disconnect guard; the sync status line's key; the network
  watcher's lifecycle and first value; Continue offline across rotation and process death; cover search's loaded-only
  choice and failed thumbnails; the shared page title; the web picker under `ImportBudget`; the Song Details subtitle.
- Sync: exception classification in `request`; 401 renewal sharing; the Retry-After cap; browser call timeouts;
  required response fields; the incomplete-download check on every engine; the cover bound; preferences failures; the
  retry scheduler; conflict-copy reuse; disconnect and revocation; resent requests.
- Imports: inflater prefix mode; zip walk and sniffing; entry policy; `foreignSongs` dispatch; JSON bounds; heading,
  label, capo, and key reading; annotated and starred chord rows; Genius filtering; converter performance (linear).

## Dropped after verification

- Desktop peeking at `BINARY_EXTENSIONS` files: the name check short-circuits before any read.
- Plan 06 (read known names before unknown texts in a folder or zip, so ~23 MiB of non-song text cannot crowd the
  songs out of the 24 MiB budget), dropped by the challenge: rare, it does not help extensionless OpenSong songs or
  nested archives, it adds a two-pass read in `ZipReader` and both folder walks, and the user can drop the folder again
  without the junk.
- `DropboxSyncProvider.deleteBatch`'s own retry loop: it counts only rounds of busy entries, so 02's pattern is absent.

## Measurements

- Stalled token endpoint, expired token: 1 caller 305 s / 7 exchanges; 2 callers 424 s / 14; 6 callers 1260 s / 42
  at HEAD; 304 s / 7 with plan 01.
- Three 429s then a reset: the run ends after 4 requests at HEAD; retried and done at 17 s with plan 02.
