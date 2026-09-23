# 25 — A single song and a setlist are exported under their file names instead of their headers

**Severity:** documented behaviour not implemented (all platforms) · **Area:** `:domain:implementation` (`ExportSongsUseCaseImpl.kt`, `ExportSetlistUseCaseImpl.kt`, `ExportFileNames.kt`)

**Decided by the user on 2026-09-23 (decision D-B):** export names follow the header — a single song is exported
under the name its header gives it (the import rule, `SongRepository.importFileName`), a setlist under its title.
The code changes; the documentation that already says so stays as it is.

**Read, not run.** This was found by reading the export use cases at HEAD (2065e47f); it has not been reproduced in a
running build. The "Verification" section below is how to confirm it, and confirming it is the first step of the
work.

## What the user sees

Exporting one song hands out a file named after the song's **library file name**, run through the normalizer —
not after the song's header. The two differ wherever they have drifted apart, which is exactly the case the app
already knows about and offers "Update file name" for (`Song.canUpdateFileName`):

- a song whose title was changed in the editor without taking "Update file name": `old_title.cho` is exported as
  `old_title.cho`, not as the title it now has;
- a numbered sibling: the second arrangement kept next to the first, `foo_2.cho`, is exported as `foo_2.cho`;
- a file named by hand (`My Song (live).cho`) is exported as `my_song_live.cho` whatever its header says.

A setlist likewise leaves under its file name (`summer_2.zip` for the second setlist titled "Summer", or the name of a
file an older rule named), not under its title.

The root `CLAUDE.md` says the opposite: "A song is named by its own header, wherever it came from … That holds for a
song written in the editor, one that arrives through an import (`SongLocalSource.importFileName`) and one handed out by
an export (`ExportFileNames.kt`) alike", and "a file name is reproducible from its header alone". `domain/implementation/CLAUDE.md`
says an export is named "the song's or setlist's own title otherwise".

## Cause

`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSongsUseCaseImpl.kt:25-36`:

```kotlin
    override suspend operator fun invoke(fileNames: List<String>): ExportedFile? {
        val contents = fileNames.mapNotNull { songContentRepository.loadSongContent(it) }
        return when (contents.size) {
            0 -> null
            // One song leaves as itself: a zip around a single text file would only be something to unpack again.
            1 -> contents.first().let {
                ExportedFile(
                    name = it.fileName.toSongExportFileName(),
```

`toSongExportFileName` (`ExportFileNames.kt:30-40` in the same package) normalizes the *file name*, half by half.

`ExportSetlistUseCaseImpl.kt:45-49`:

```kotlin
        return ExportedFile(
            name = exportFileName(
                base = setlistFileName.removeSuffix(LibraryFiles.SETLIST_EXTENSION),
                extension = LibraryFiles.ARCHIVE_EXTENSION,
            ),
```

— the setlist's file name, although the `Setlist` itself is in hand two lines earlier (`:34`).

## The change

Invoke the **`code-style`** skill before the first edit.

### A song: the import's own rule

`SongRepository.importFileName(fallbackTitle, text)` (`data/repository/api/.../SongRepository.kt`, "the name an
imported song wants") is the rule the root `CLAUDE.md` names for both directions, and it is what the import will call
on the exported file when it comes back. Using it here is what makes the round trip exact: an exported song is
imported under the name it was exported under. The fallback title is the file name without its extension, which is
what `PrepareImportUseCaseImpl.kt:110` passes for a file holding one song (`file.name.substringBeforeLast('.')`), so a
song that declares no `{title}` is named the same way in both directions.

The one difference: `importFileName` always answers with `.cho` (`songFileName`'s default,
`data/source/local/implementation/.../FileNames.kt:28`), while the library reads the whole ChordPro family and a
`.crd` file is a `.crd` file. Keep the stored extension on the way out — as `toSongExportFileName` does today and its
KDoc explains — since renaming a file's extension is claiming its content is written differently than it is.

Replace the body of `ExportFileNames.kt`'s song function (`:18-40`) with:

```kotlin
/**
 * The export name of a song: the one its own header gives it, by the rule the import names an incoming song by
 * ([SongRepository.importFileName]), so that a song leaves under the name it would come back under - whatever its
 * library file happens to be called, numbered sibling or name from before its title changed. The file name stands in
 * as the title only where the song declares none, as it does for the import. The extension is the stored one, since
 * the library reads the whole ChordPro family and handing a file out under another one would be a claim about how it
 * is written.
 */
internal fun SongRepository.songExportFileName(content: SongContent): String {
    val extension = LibraryFiles.SONG_EXTENSIONS.firstOrNull { content.fileName.endsWith(it, ignoreCase = true) } ?: LibraryFiles.SONG_EXTENSION
    return importFileName(fallbackTitle = content.fileName.substringBeforeLast('.'), text = content.text)
        .removeSuffix(LibraryFiles.SONG_EXTENSION) + extension
}
```

(`importFileName` is not suspending and reads nothing — `SongLocalSource.importFileName` parses the metadata of the
text it is given.) `toSongExportFileName` and its split-on-separator logic go: with the name derived from the header,
there is no hand-written separator to preserve — `songFileName` normalizes the artist and the title one at a time and
joins them with `LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR` itself (`FileNames.kt:28-32`), which is what the
split was reconstructing. `exportFileName(base, extension)` stays; the setlist uses it.

`ExportSongsUseCaseImpl.kt` gets the `SongRepository` (Koin resolves it; `ExportLibraryUseCaseImpl` already takes one):

```kotlin
@Factory
class ExportSongsUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository,
) : ExportSongsUseCase {

    override suspend operator fun invoke(fileNames: List<String>): ExportedFile? {
        ...
            // One song leaves as itself, named by its own header: a zip around a single text file would only be
            // something to unpack again.
            1 -> contents.first().let {
                ExportedFile(
                    name = songRepository.songExportFileName(it),
```

The multi-song zip keeps its fixed name (`campfire_songs.zip`) and its entries keep their library names — a setlist
may travel next to them and points at them by file name (`domain/implementation/CLAUDE.md`, "what is *inside* an
archive keeps its library names").

### A setlist: its title

`ExportSetlistUseCaseImpl.kt:45-49`:

```kotlin
        return ExportedFile(
            // Named after its title, the one thing about a setlist the user named: the file name only follows it, and
            // is numbered where two setlists share one.
            name = exportFileName(base = setlist.title, extension = LibraryFiles.ARCHIVE_EXTENSION),
```

`LibraryFiles.normalizedName` never answers an empty string (it falls back to `untitled`, `LibraryFiles.kt:145`), so
a setlist with a blank title (a hand-written file) still gets a name. The document inside the zip keeps its library
file name, as today (`:39`).

### Interplay with plan 37

Plan 37 (lane D, "export file extension") makes the desktop save dialog keep the extension and the Android save
document not append `.txt`. It consumes `ExportedFile.name` as given; the two plans touch different files and compose.

## Tests

New `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSongsUseCaseImplTest.kt`
with a fake `SongContentRepository` (map of file name → text), a fake `SongRepository` whose `importFileName`
records its arguments and answers from a small table, and the `FakeArchiveRepository` shape of
`ExportLibraryUseCaseImplTest`:

1. `a single song is named by its header` — the file `old_title.cho` whose fake `importFileName` answers
   `new_title.cho`: the export is named `new_title.cho`, and `importFileName` was called with
   `fallbackTitle = "old_title"` and the file's text.
2. `the stored extension is kept` — `song.crd`, `importFileName` answers `song.cho`: exported as `song.crd`.
3. `several songs keep their library names inside the archive` — two files: the name is `campfire_songs.zip` and the
   archive entries are the two library names, `importFileName` never called.

Since the fake can only prove the wiring, add one test of the real rule on the JVM, in
`data/source/local/implementation/src/desktopTest/.../source/` next to `RenameTest.kt` (or in a new
`ExportNameTest.kt` there): `SongLocalSourceImpl(fileStorage).importFileName("foo_2", "{title: Foo}\n{artist: Bar}\n")`
is `bar-foo.cho`, and for `"{title: Foo}\n{subtitle: Live}\n"` it is `foo_live.cho` — pinning that a numbered sibling
and a subtitle are named by the header.

New `ExportSetlistUseCaseImplTest.kt` in the same package:

4. `a setlist is named by its title` — a setlist with `fileName = "summer_2.setlist.json"`, `title = "Summer set"`:
   exported as `summer_set.zip`, and the archive holds `summer_2.setlist.json` (the document under its library name).
5. `a blank title is still a name` — `title = ""`: exported as `untitled.zip`.

Run `./gradlew :domain:implementation:desktopTest :data:source:local:implementation:desktopTest`, then the root unit
test command.

## Verification

1. Create a song in the app with title "Foo", artist "Bar" (file `bar-foo.cho`). Edit its title to "Foo (acoustic)"
   and save, but do **not** take "Update file name".
2. Export it (song details → overflow → Export).
   - **Before:** the save dialog proposes `bar-foo.cho`.
   - **After:** it proposes `bar-foo_acoustic.cho`.
3. Import that exported file into a second, empty profile / device: it lands as `bar-foo_acoustic.cho` (unchanged
   behaviour, now matching the export).
4. Create two setlists both titled "Summer" (the second is stored as `summer_2.setlist.json`) and export the second:
   before, `summer_2.zip`; after, `summer.zip`.
5. Export a song stored as `.crd` (put one into the desktop library by hand): the name keeps `.crd`.

## Docs

Per D-B the documentation is right and stays. The only text that describes the code being removed:

- `ExportFileNames.kt`'s own KDoc on `toSongExportFileName` goes with the function; the new function's KDoc is given
  above.
- `domain/implementation/CLAUDE.md`, the `ExportFileNames.kt` bullet (line 89 onwards: "`LibraryFiles.normalizedName`
  over the whole name, or over each half of a song's `artist - title` separately so that the dash between them
  survives. Which separator it splits on is what makes the rule idempotent …"): replace with "`ExportFileNames.kt` —
  what a file is called on the way out: a single song the name its own header gives it, by the import's rule
  (`SongRepository.importFileName`, the file name standing in as the title only where the song declares none) with
  its stored extension, so that it comes back under the name it left under; a setlist its title through
  `LibraryFiles.normalizedName`." The earlier sentence of the export bullet (line ~73, "the song's or setlist's own
  title otherwise") is now accurate as it stands.

## Files touched

- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ExportFileNames.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSongsUseCaseImpl.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSetlistUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSongsUseCaseImplTest.kt` (new)
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportSetlistUseCaseImplTest.kt` (new)
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ExportNameTest.kt` (new, or a test added to an existing file there)
- `domain/implementation/CLAUDE.md`

## Depends on

Nothing. Composes with **37** (lane D), which changes how the platforms save the name this plan produces.
