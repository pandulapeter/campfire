<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:model

Pure Kotlin domain models, no dependencies; the layer-crossing currency (stored documents and bytes never leak past
their own module). The one platform difference is `normalizedToNfc` (`domain/Normalization.kt`, expect/actual).
`DataState<T>` (`Idle` with its data; `Loading` / `Failure` with the last data, if any) wraps everything out of a
repository.

## Identity and names (`domain/LibraryFiles.kt`)

- **A song's or setlist's identity is its file name**, extension included, not a generated id.
- The extension lists (`SONG_EXTENSIONS`, `IMPORTABLE_EXTENSIONS`, `LIBRARY_BACKUP_EXTENSIONS`) **must agree with the
  import rules, the storage layer, and the three OS registrations** (Android manifest, iOS plist, desktop Gradle file).
  Importable-only types are never claimed system wide; legacy `.doc` is listed only to report it unreadable.
- `isSongFileName` / `isSetlistFileName` (and `LibraryFileKind.matches`) are the one answer to which files are the
  library, asked by the scan, the local sync listing, and the remote listing: right extension and not hidden
  (`._song.cho` is macOS's AppleDouble companion).
- `normalizedName` (rules in the root CLAUDE.md) **must stay idempotent**: an exported name is normalized again on its
  way back in. NFC comes first, before folding and lowercasing (it is what composes non-Latin marks). Letters outside
  the BMP are kept as surrogate pairs and never cut; symbols, emoji, and planes 14–16 become separators. A Latin letter
  `Accents.kt`'s `withoutAccent` table does not know becomes a separator, so extend the table; it lives here because two
  modules share it.
- `songBaseName` is the one song naming rule (artist, dash, title, each normalized; the title alone without an
  artist): the local source, the PDF export, and the demo library test use it.
- `identityKey` / `isSameLibraryName` are the one rule for whether two names are one file (NFC, then
  `uppercaseChar().lowercaseChar()` per code point, matching the JVM's `equals(ignoreCase = true)`); asked by storage
  renames, sync matching, and the import (`LibraryNameIdentityJvmTest` holds it against the JVM).
- `withoutCollisionSuffix` reads both the app's `_2` names and sync's ` (2)` conflict copies.

## Text (`domain/LibraryText.kt`)

`decodeLibraryText` is how every library file and every incoming file becomes text, so a file reads the same however it
arrived: UTF-8; mostly-UTF-8 files have stray bytes read through a code page; otherwise Windows-1250 only where the
bytes can only be Hungarian or Polish and no Western-only letter appears, else Windows-1252. Czech, Slovak, and Romanian
stay on 1252 on purpose (their bytes overlap Western ones; guessing would misread correct files). A leading BOM is
dropped (a mid-file one is `ChordProSplitter`'s).

## `UserPreferences`

- Enum `id`s are persisted; never change them. `areChordsEnabled` is stored as the inverse `isLyricsOnlyModeEnabled`.
- **Per-song maps keyed by file name** (`transpositions`, `tempos`, `capos`, `beatLevels`, `foldedSections`) must be
  listed in `withSongRenamed` and `withoutSongOverrides` and nowhere else (`UserPreferencesSongMapsTest`).
- What travels where: `chordVoicings`, `tagColors`, and the per-song transpositions, tempos, capos, and `beatLevels`
  sync through `preferences.json` but are never exported. `tagColors` are never pruned when a tag's last song goes.
  Local only: `instrument`/`tunings`, `foldedSections` (one reader's choice), `tunerSettings`, `seenWhatsNewVersions`,
  `isOnboardingPending` (default false so old installations are never sent through onboarding), and
  `demoLibraryContentHashes` (sync uses it to prefer the cloud's copy of an untouched demo file). Song filters are never
  stored (`SongFilter` in `:domain:api`).
- `instrument` ids are the tuner's standard tuning ids and, for the five with shapes, `:chordpro`'s; `ofTuningId`
  matches by longest prefix; the twelve-string guitar's id names no tuning. `tunings` has no entry for standard, and
  unknown ids (here and in `chordVoicings`) are kept as they are.
- `MetronomeSettings` stores the metronome's own ids, so this module depends on nothing; volume zero is the mute.
- `MIN_FONT_SCALE` bounds the whole song details screen, not only the lyrics.

## Songs and setlists

- `Song` is metadata only, read at scan time: its first `{tempo}`, `{time}`, and readable `{capo}` are there so a
  metronome paged to the next setlist song has them before its text loads. `canUpdateFileName` is computed once per scan
  because every row asks.
- **`Setlist.unknownFields`** (and each entry's) keep members this version does not know, as opaque JSON, so a later
  version's field survives this one's save. Per-entry transposition, tempo, and capo, `isArchived`, and `description`
  travel with the file.
- Tags, languages, cover, and links live in the song text as directives (see `:chordpro`); `Tag` / `SongLanguage` are
  counted views built per scan. `SongLanguage.UNKNOWN` (`und`, which `:chordpro` reads as no language, so no song
  carries it) groups songs that declare none. Language display names are the platform's (`:presentation`).

## Import

`ImportBudget` is the one class every platform reads incoming files through: a file the import would not look inside
is never kept (an unknown type is read only to check for a zip signature), and an oversized one is reported rather than
loaded. Limits are in `ImportLimits` (sized for an Android heap). `ImportPlan` is computed before anything is written;
`ImportChoices` answers it (unticked `ImportEntryKey`s, Keep both or Replace for all and per row; changing the for-all
choice leaves per-row ones alone). `ExtractedDocument` is transient; no original file is stored with a song.

## Other

- `AuthorizationCompletionPage` and `SystemBrowser` live here, not in `:data:source:remote:api`, so nothing above the
  repositories sees the remote contracts to connect an account.
- Cover search and song details lookup types (`CoverArtQuery`, `SongDetailsQuery`, …): only addresses are ever stored,
  never images, and nothing of a lookup is kept.
- `SyncState.Connected` carries the last run's outcome so a failure stays on screen until replaced. A run with
  `SyncSummary.failed` files or `havePreferencesFailed` is not `isComplete`.
