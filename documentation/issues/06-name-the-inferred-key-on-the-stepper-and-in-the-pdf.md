<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Name the key a song's chords are in on the Transposition control, its menu, the read only line, and the PDF where the file names none, as the app bar does

**Kind:** bug (decided by the user 2026-10-26)  ·  **Severity:** low  ·  **Platforms:** all (song page and PDF)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/rendering/SongRenderer.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongHomeKey.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsTitle.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/firstSection/RememberSongPlayingControls.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongPages.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPage.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongLyricsModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/rendering/SongRendererTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsTitleTest.kt`

Runs after plan 01 in lane A (both touch `SongPages.kt`, `SongDetailsPage.kt`, `SongLyricsModel.kt`, and
`ExportController.kt`); re-locate the code by the quoted snippets.

## Problem

For a file with no `{key}`, the app bar names the key its chords are in (`headerKeyOf` in
`screens/songDetails/SongDetailsTitle.kt` falls back to `inferredKey`, worked out by `InferredSongKeys` as the text is
read), but the first section's Transposition control does not
(`songView/firstSection/RememberSongPlayingControls.kt:61,66` at 54829e6fd):

```kotlin
val key = if (canTranspose) viewModel.songRenderer.renderKey(song = song, transposition = playback.soundingTransposition, spelling = chordSpelling) else null
…
val shownKey = viewModel.songRenderer.renderKey(song = song, transposition = soundingTransposition(transposition, fileCapo), spelling = chordSpelling)
```

`SongRenderer.renderKey(song, …)` reads only `song.chordsKey`, so the stepper and each of its twelve menu entries name no
key, a line under a bar that names one. The read only line (the same four values as text, `withMetadataSection` reading
the rendered song's `metadata.key`) and the PDF's playing row and setlist overview (`PrintLayout` reading
`entry.song?.metadata?.key`, set from `rendered` in `ExportController.preparePrintSource`) name none either. The user
decided on 2026-10-26 that the stepper and the PDF fall back to the inferred key; the read only line is the stepper's
read only form and is included (see README, Decisions).

## Fix

One rule, used everywhere: the written key, else, for a file naming none, the inferred one moved by the sounding
transposition.

1. `SongRenderer.renderKey(song: Song, transposition: Int, spelling: …)` gains `inferredKey: String? = null` and takes
   the fallback out of `headerKeyOf`:

   ```kotlin
   fun renderKey(song: Song, transposition: Int, spelling: UserPreferences.ChordSpelling, inferredKey: String? = null) =
       renderKey(key = song.chordsKey, transpose = song.transpose, transposition = transposition, spelling = spelling)
           ?: inferredKey?.takeIf { song.key.isNullOrBlank() }?.let { renderKey(key = it, transpose = 0, transposition = transposition, spelling = spelling) }
   ```

   KDoc: the inferred key (`InferredSongKeys`) was found after the file's own `{transpose}`, so it is moved by the
   transposition alone. `headerKeyOf` calls it with `inferredKey` and keeps its KDoc's paragraph about the inferred key.
2. In `SongHomeKey.kt`, next to `inferredKeyOf`, add
   `internal fun SongRenderer.inferredKeyOfText(text: String) = inferredKeyOf(transposedSong(text = text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default))`
   and use it for `CampfireViewModel`'s `InferredSongKeys(inferKey = …)`, so that the screen and the PDF infer one way.
3. `rememberSongPlayingControls` gains `inferredKey: String?` (`@param`: the key the song's chords were found in, see
   `InferredSongKeys`, for a file that names none), passed to both `renderKey` calls and added to the `remember` keys of
   `transpositionLabels`.
4. `SongPages`: collect `viewModel.songTextStore.inferredKeys.keys` once (`collectAsStateWithLifecycle`, as
   `SongDetailsAppBar` does) and pass `inferredKeys[song.fileName]` to `rememberSongPlayingControls`. For the read only
   line, pass `SongDetailsPage` a new `inferredKey: String?` only where `isReadOnly` (the controls name the key
   otherwise): the sounding name,
   `inferredKeys[song.fileName]?.takeIf { song.key.isNullOrBlank() }?.let { renderKey(key = it, transpose = 0, transposition = playback.soundingTransposition, spelling = chordSpelling) }`.
   `SongDetailsPage` puts it into `SongLyricsInputs` (a new `inferredKey: String? = null`, so the page is built again
   when it arrives) and, in the builder, sets it on the notated song where that names no key:
   `notateSong(…).let { song -> inputs.inferredKey?.takeIf { song.metadata.key.isNullOrBlank() }?.let { song.copy(metadata = song.metadata.copy(key = it)) } ?: song }`
   (`standardSong`, which the degrees and scales read, is left alone).
5. `ExportController.preparePrintSource`: for a song whose file names no key, work out
   `withContext(Dispatchers.Default) { songRenderer.inferredKeyOfText(content.text) }` and, where `rendered` names no
   key, set `rendered.metadata.key` to
   `songRenderer.renderKey(song = song, transposition = transposition, spelling = spelling, inferredKey = inferred)`
   (already in the reader's notation, so set on `rendered`, after `notatedSong`, not before). `transposition` there is
   the sounding one (after plan 01, `playback.soundingTransposition`). Infer only where `song` is known, its `key` is
   blank, and `content` was read; everything else prints as today.
6. Docs: `screens/songDetails/CLAUDE.md`, the item "**A file with no `{key}` is named by the key its chords are in**":
   say it is named so in the bar, the Transposition control and its menu, the read only line, and the PDF.
   `print/CLAUDE.md`, the playing row item: "The sounding key (the inferred one where the file names none, as on
   screen), …".

## Tests

`SongRendererTest` (`./gradlew :presentation:desktopTest`), "a file with no key is named by the key its chords are
in": `renderKey(song with key = null, transposition = 2, spelling = Default, inferredKey = "G")` is `"A"`; with the
song's `key = "D"` it is the written key moved (`"E"`), the inferred one ignored; with `inferredKey = null` it is null.
`SongDetailsTitleTest` keeps passing unchanged (its `headerKeyOf` cases now go through the renderer). If
`inferredKeyOfText` is simple to pin, add a case that a plain `[G]…[C]…[D]…[G]` text infers `G`, matching
`inferredKeyOf` on the parsed song.
