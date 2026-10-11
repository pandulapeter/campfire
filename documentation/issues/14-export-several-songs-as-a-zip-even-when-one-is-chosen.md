# Make the export screen's Zip of several songs save what its preview shows when only one song is chosen or readable

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** option (b), recommended: `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/ExportSongsUseCase.kt`, `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSongsUseCaseImpl.kt`, `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSongsUseCaseImplTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`, `domain/implementation/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`. Option (a) instead: `presentation/.../ui/screens/export/FilesPreview.kt`, `PrintOptions.kt`, `ExportScreen.kt`, `ExportController.kt`, `presentation/.../ui/screens/export/CLAUDE.md`, `presentation/.../ui/print/CLAUDE.md`.

**Decision for the user:** option (a) or (b) below; (b) is recommended.

## Problem

The selection mode exports two or more ticked songs as `PrintSource.Kind.SONGS` (`CampfireViewModel.kt` L1150–1151:
one ticked song opens as `DialogType.Export(song = …)`, several as `DialogType.Export(songs = …)`). Whether the screen
treats the source as several songs is fixed by that kind, not by how many are chosen now
(`ui/print/PrintDocument.kt` L40: `val hasSeveralSongs get() = kind != Kind.SONG`), so with the Files format:

- the format's label is Zip (`PrintOptions.kt` ~L319–323: `kind != PrintSource.Kind.SONG -> Res.string.print_format_zip`),
  its line is `print_format_zip_songs_description`;
- the preview is the zip's file list (`FilesPreview.kt` L77–79:
  `source.hasSeveralSongs && source.songs.isNotEmpty() && selected.isEmpty() -> FilesPreviewContent.NO_SONGS` /
  `source.hasSeveralSongs -> FilesPreviewContent.ZIP`), a missing song dimmed as "not included";
- the saved message is `Message.SongsExported`.

But `ExportController.exportFiles` (L278) hands the chosen songs to `ExportSongsUseCase`, which returns a bare `.cho`
named by the song's header whenever exactly one of them reads (`ExportSongsUseCaseImpl.kt` L30–40):

```kotlin
return when (contents.size) {
    0 -> null
    // One song leaves as itself, named by its own header: a zip around a single text file would only be
    // something to unpack again.
    1 -> contents.first().let { ExportedFile(name = songRepository.songExportFileName(it), mimeType = ExportedFile.TEXT_MIME_TYPE, ...) }
    else -> ExportedFile(name = ARCHIVE_NAME, mimeType = ExportedFile.ZIP_MIME_TYPE, ...)
}
```

Scenario: tick three songs, Export, Files: the screen shows "Zip" and a list of three files. Untick two (or one of
the two chosen files cannot be read): the screen still shows Zip and a one-file list, but the file picker is offered a
`.cho` under the song's own name, and the snackbar then says the songs were exported. What is saved is not what the screen said.

## Fix

Two ways to make the screen and the file agree:

**(a) The screen follows the use case.** Where the source is `Kind.SONGS` and exactly one chosen song reads, the screen
switches to describing a single song: the format label reads ChordPro, the line is
`print_format_chordpro_song_description`, the preview is that song's text (`FilesPreviewContent.TEXT` of the chosen
`PrintSong.text`), and the saved message is `SongExported`. This needs a "how many chosen songs are readable" value
computed in `ExportScreen` and passed to `FilesPreview` and the format option, `FilesPreview` taking the text of the
chosen song rather than `source.songs.firstOrNull()`, and the label, line, and preview cross-fading as ticks change.
It keeps the use case's rule that one song never leaves zipped, but makes the format control relabel itself under
the user's finger as they tick and untick — the option they picked turns into another one — and touches four files of
the export screen.

**(b) Several songs always leave as a zip (recommended).** Give the use case a parameter the caller sets by what was
asked for, not by how many songs survived:

```kotlin
interface ExportSongsUseCase {
    /**
     * A single song leaves as the `.cho` file it already is and several as a zip, unless [isArchive] asks for a zip
     * whatever the count: the songs ticked in the selection mode, whose export screen promised one. Null when none of
     * them can be read.
     */
    suspend operator fun invoke(fileNames: List<String>, isArchive: Boolean): ExportedFile?
}
```

In the implementation, the `1 ->` branch becomes `1 -> if (!isArchive) … else zip` (simplest: compute the zip in a
local function and use it for `else` and for `isArchive`). `ExportController.exportFiles` passes `isArchive = true` for
`dialog.songs.isNotEmpty()` and `false` for the one-song branch. No default value on the parameter, so every caller
says which it means. Everything the screen already shows (Zip, the file list, "Songs exported", and the PDF side's
`campfire_songs.pdf`) then holds without a UI change, and the user gets the format they picked. The cost is a zip
holding one `.cho`, which imports exactly as the bare file would.

Recommend (b): it is a two-file change with a unit test, it keeps the user's explicit Zip choice stable while they tick,
and the screen is already written for it.

Docs for (b): `domain/implementation/CLAUDE.md`, the `ExportLibraryUseCaseImpl` / `ExportSongsUseCaseImpl` /
`ExportSetlistUseCaseImpl` bullet, first sentence: "a single song is the `.cho` file as it is on disk, everything else is
a zip" becomes "…a single song exported on its own is the `.cho` file as it is on disk, everything else — the selection's
several songs included, however many of them are chosen — is a zip". `ui/screens/export/CLAUDE.md`, the paragraph
starting "`DialogType.Export.songs`…": after "zipped as `campfire_songs.zip` by `ExportSongsUseCase`", add "even when
only one of them is left chosen". For (a), the same two paragraphs of `ui/screens/export/CLAUDE.md` and the format
paragraph of `ui/print/CLAUDE.md` ("and **Zip** too for several songs…") say that one chosen song is ChordPro instead.

## Tests

For (b), in `ExportSongsUseCaseImplTest`: `songs asked for as an archive are zipped even when one is left` —
`useCase(emptyMap(), "a.cho" to "{title: A}\n").invoke(listOf("a.cho", "gone.cho"), isArchive = true)` is named
`campfire_songs.zip` with `archive.packed.keys == setOf("a.cho")`. The existing tests pass `isArchive = false`
(the single-song ones) and `true` or `false` (the several-songs one, which zips either way). For (a), the readable-count
helper would be a pure function over `PrintSource` and the selection, testable in `:presentation`'s commonTest; the
preview and label switching are UI and are not.

## Manual check

Tick three songs in the Songs screen's selection mode, Export, Files, untick two, Save: the saved file is
`campfire_songs.zip` holding the one `.cho` (with (b)), and importing it adds that song. Repeat with one of two chosen
songs deleted from the library folder behind the app's back.
