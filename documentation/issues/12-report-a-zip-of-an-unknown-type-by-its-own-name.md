# Report a file of an unknown type that is a zip but holds nothing importable under its own name, not its entries'

**Kind:** bug · **Severity:** low-medium · **Platforms:** all
**Challenged:** amended — an entry only counts as something the import reads where it was actually read (bytes, or `isTooLarge`), not by its extension alone, so an unread `dataFile.txt` from plan 11 or an empty `x.txt` does not keep the inner names; the known limitation (a `LICENSE.txt` inside a `.jar`) and the ordering with plan 13 were added.
**Files:**
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`
- `domain/implementation/CLAUDE.md` (the `PrepareImportUseCaseImpl` bullet)
- `CLAUDE.md` (root, the "Other apps' libraries are archives under names of their own" bullet)

## Problem

Since the SongbookPro import, a file whose name the import does not know is read (`ImportBudget.readUnknown`,
`ImportLimits.kt:84-91`) and kept where its bytes start like a zip, and `PrepareImportUseCaseImpl` unpacks it
(`PrepareImportUseCaseImpl.kt:94-100` at 1c52e5347):

```kotlin
val isArchive = LibraryFiles.isArchiveFileName(file.name) ||
    (!LibraryFiles.isImportableFileName(file.name) && LibraryFiles.isZipArchive(file.bytes))
when {
    !isArchive -> sort(file)
    …
    else -> try {
        archiveRepository.unpack(archive = file.bytes, maxSize = remaining).forEach(::sort)
```

A great many everyday formats are zip archives under a name of their own: `.odt`, `.ods`, `.xlsx`, `.pptx`, `.epub`,
`.pages`/`.numbers`/`.key`, `.mscz` (MuseScore), `.gp` (Guitar Pro 7+), `.jar`, `.apk`. Picked or dropped by mistake,
or alongside songs, each is now unpacked and every entry inside it is reported by its own name: `ArchiveLocalSourceImpl`
hands back what it did not read as `ImportedFile.unread(name)` (`ArchiveLocalSourceImpl.kt:95-97`), and `sort` puts
everything whose extension is not a song, document or `.json` into `skippedFileNames`
(`PrepareImportUseCaseImpl.kt:76`), shown under "Unsupported or invalid files" (`import_status_skipped`). An `.odt`
becomes `mimetype`, `content.xml`, `styles.xml`, `meta.xml`, `settings.xml`, `manifest.xml`, `thumbnail.png` in the
report — names the user never picked — while the file they did pick is not mentioned. An unknown zip with no entries
at all is not reported at all. Before the signature check, the same file was one line: `sheet.odt`, unsupported.

## Fix

Only for an archive recognised by its bytes alone (`!LibraryFiles.isArchiveFileName(file.name)`; a `.zip` or a
SongbookPro backup picked by its name keeps today's behaviour):

- **(recommended)** Unpack it into a list first. If none of the entries is something the import reads — not hidden,
  a song-family or `.txt` file, a `.pdf`/`.docx`/`.doc`, or a `*.setlist.json` (`LibraryFiles.SETLIST_EXTENSION`; a bare
  `.json` inside some other app's container is its configuration, not a setlist), **and** actually read
  (`bytes.isNotEmpty() || isTooLarge`: `ImportedFile.unread` is the only way an entry arrives empty-handed without
  being too large, and an empty file holds nothing to import either) — add `file.name` once to `skippedFileNames` and
  drop the entries. The "actually read" half matters for plan 11: an unreadable SongbookPro backup under an unknown
  name comes back as one unread `dataFile.txt`, which by extension alone would count and be reported by its inner
  name. Otherwise sort the entries as today. A SongbookPro backup under an unknown
  name still works, since its entries are already translated into `.cho` and `.setlist.json` files by then, and a
  `.pages` file still yields the `preview.pdf` it carries, which is a fair reading of a chord sheet written in Pages.
- Alternative: keep a signature-detected archive only where it turned out to be a SongbookPro library. Simpler to
  state, but it means the data layer has to say which archives it translated, and it throws away a renamed `.zip`
  of songs, which the signature check was also written for ("the songs in them are worth looking for").

Known limitation, accepted: a container that happens to carry a text file (`META-INF/LICENSE.txt` in a `.jar` or
an `.apk`) still counts as holding something importable and is reported entry by entry, with that text imported as a
song — as today. Nobody picks a `.jar` for their songs, and narrowing `.txt` further would lose the renamed zip of
plain-text songs the signature check was written for.

An unknown zip with no entries at all (or only hidden ones) is now reported once by its name, where today it is not
reported at all. Either way the report screen, not the happy-path snackbar, is what the user sees, since
`skippedFileNames` is not empty.

Plan 13 threads an origin through the same `files.forEachIndexed` loop and `sort`; land this plan first and let 13
build on it.

Add a short comment at the decision saying why: everyday document formats are zips too, and their parts are not what
anybody picked.

Docs: in `domain/implementation/CLAUDE.md`, after "or, where its name is nothing the import knows, by its bytes
(`isZipArchive`)", add "— reported under its own name, as unsupported, where nothing inside it is something an import
reads". In the root `CLAUDE.md` bullet "Other apps' libraries are archives under names of their own", after "which is
then unpacked like a `.zip`", add "(or, where it holds nothing an import reads — an OpenDocument, an e-book — reported
as the one unsupported file it was)".

## Tests

In `ImportFilesUseCaseImplTest`, next to `a file of an unknown type is unpacked by its content, and a document is never`:
a fake `ArchiveRepository` returning `ImportedFile.unread("mimetype")`, `ImportedFile("content.xml", …)`,
`ImportedFile.unread("thumbnail.png")` for `ImportedFile("sheet.odt", zip)` (where `zip = byteArrayOf(0x50, 0x4B, 3, 4, 0)`);
assert `plan.skippedFileNames == listOf("sheet.odt")` and `plan.songs.isEmpty()`. Also an unknown file whose archive
yields only `ImportedFile.unread("dataFile.txt")` is reported as itself, and one whose archive yields nothing is
reported as itself. Also: the same fake for
`ImportedFile("songs.zip", zip)` still reports the three inner names (a picked `.zip` is unchanged), and an unknown
file whose archive yields `a.cho` still plans `a.cho` (the existing test covers this).

## Manual check

On the desktop, drag an `.odt` or `.xlsx` and one `.cho` into the window together: the report lists the `.cho` as
imported and the spreadsheet once, by its own name, under unsupported files.
