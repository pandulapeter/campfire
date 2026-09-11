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

Pure Kotlin domain models with no dependencies. Every other module depends on this (usually transitively via an `api` module).

- `DataState<T>` — `Idle` / `Loading` / `Failure`, each carrying nullable cached `data`. The universal wrapper for anything flowing out of a repository.
- `domain/` — `Song` (the metadata of one `.cho` file, without its text, tags included), `SongContent` (that text), `Setlist` (with per-entry transposition, so it travels with the setlist), `Tag` (one label with the number of songs carrying it, which is all the filter controls need), `SongLanguage` (the same for a language), `UserPreferences` (with `SortingMode`, `UiMode`, `ThemeColor`, `Language`, `Accidentals` and `TagMatchMode` enums whose `id` values are persisted, the display-only `ChordSpelling`, `isPerformanceModeEnabled` — read only mode for the whole app, see `:presentation` — plus the library-wide transpositions, the selected tags and the selected languages), `ImportedFile` / `ExportedFile` / `ImportResult` for the import and export paths, and `ImportPlan` (what an import would do to each name, worked out before anything is written) with the `ImportConflictResolution` the user answers it with.
- `domain/LibraryFiles.kt` — the extension vocabulary: `.cho` is what Campfire writes, `SONG_EXTENSIONS` is the whole ChordPro family it reads and registers with each operating system, and `IMPORTABLE_EXTENSIONS` adds the types (zip, plain text, JSON) it will read when handed one but never claims system wide. The import rules, the storage layer and the three OS registrations (Android manifest, iOS plist, desktop Gradle file) all have to agree with this list. `ARTIST_TITLE_SEPARATOR` is there for the same reason: the `" - "` in a song's file name is structure rather than the user's text, so both the naming of a new song and the naming of an exported one read it from here.

These types are the layer-crossing currency: stored documents and file bytes are mapped to/from them and never leak past their own module.

Tags have no store of their own: they live in the songs' own text as ChordPro directives (see `:chordpro`), so the set of them is whatever the library happens to carry, and a tagged song takes its tags with it when it is exported or synced. `Tag` is the counted view of that set, built per library scan rather than kept anywhere.

`SongLanguage` is the same thing for the languages a song is sung in (`{meta: language en}`, read into `Song.languages` as lowercase ISO codes — two letters wherever the standard has them, three for the languages ISO 639-1 left out), with one addition: the code `SongLanguage.UNKNOWN` — `und`, which `:chordpro` reads as *no* language, so no song can ever carry it — stands for the songs that declare none, which is how the filter offers them as a group. What a code is *called* is not here: that is whatever the platform says in the language the app is set to, see `:presentation`.

Song and setlist identity is the **file name**, extension included — not a generated id. Two songs with the same title are two files with different names, and a rename in the library folder is a different song as far as the app is concerned.

`LibraryFile` / `LibraryFileKind` describe the library as sync sees it — a name in one of the two folders, plus what
the file system could tell about it. `SyncState` and the types around it (`SyncProviderId`, `SyncAccount`,
`SyncOutcome`, `SyncSummary`, `SyncFailureReason`) are everything the UI needs to know about sync; `SyncState.Connected`
carries the outcome of the last run rather than a message of its own, so a failure stays on screen until something
replaces it instead of flashing past in a snackbar.
