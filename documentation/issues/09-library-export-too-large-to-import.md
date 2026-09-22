# 09 · A library of several thousand songs exports to an archive the app's own import refuses

**Severity:** wrong behaviour — silent loss of the user's only way back (all platforms; needs a library of roughly
6,000–14,000 songs, depending on their size: rare, but it hits exactly the users with the most to lose, and nothing
says so until the day the archive is needed) · **Area:** `:domain:implementation` (`ExportLibraryUseCaseImpl`),
`:data:model` (`ImportLimits`), `:presentation` (`CampfireViewModel.save`)

**Decided (by the user, 2026-09-22): C′** (export it anyway, then explain). The question was what the app does with a library whose archive is over the import limit —
A: make the import take it (raise the caps for archives while keeping the per-file cap, which needs a streaming import
so that the archive, what it unpacks to and its text are no longer all in memory at once);
B: split the export into several archives that each import;
C: refuse to export above the limit, with a message;
C′: export it anyway and say, after the save, that Campfire cannot import this archive in one go and how to bring it
back;
D: leave it and document it.
**Recommendation: C′ now**, A later if libraries that size turn up (see Fix).

## Symptom
1. Have a large library: at the demo songs' size (1.6 KB) about 14,000 songs, at 3 KB (a plain chord sheet) about
   8,000, at 4 KB (a song with a tab or two) about 6,000.
2. Settings → Export library. The archive is written without a word, it is a perfectly good zip, and every unzip tool
   opens it.
3. Import it — on another device, or on this one after a reinstall. The picker hands the file over, it is refused
   before it is read, and the user gets the import result message counting nothing (`Res.string.import_result` with
   four zeros), followed by **"One file is too large and cannot be imported."** (`Res.plurals.import_oversized`). Nothing is imported, not even
   the part that would have fit.

## Cause
- `ZipWriter` stores (no compression, `zip/ZipWriter.kt:12-15`), so the archive is the library's bytes plus
  30 + 46 + 2 × the entry name per file (`songs/<file name>`: about 180 bytes for a typical 45-character name).
- `ExportLibraryUseCaseImpl.invoke` (`domain/implementation/…/useCases/ExportLibraryUseCaseImpl.kt:32-49`) packs
  every song and setlist with no limit and returns the archive.
- The import caps the archive **as it was picked** at `ImportLimits.MAX_IMPORT_SIZE`, 24 MiB
  (`data/model/…/domain/ImportLimits.kt:30`): `ImportBudget.read` returns `ImportedFile.unread(name, isTooLarge = true)`
  when the size is known to be over it (`:62`, and `:64` for a file whose size the platform could not say) and `PrepareImportUseCaseImpl.plan` does the same for an archive that
  arrived some other way (`file.bytes.size > ImportLimits.MAX_IMPORT_SIZE -> oversizedFileNames += file.name`, `:73`).
  Everything one import unpacks to is capped at the same 24 MiB, so compressing the export would not help.
- `CampfireViewModel.applyImportPlan` then sends `Message.ImportFinished` and `Message.ImportOversized`
  (`presentation/…/ui/CampfireViewModel.kt:1293-1297`).

The limit is right where it is: `ImportLimits`' KDoc works out that an import holds up to four times it (picked
archive, what it unpacked to, and its text at two bytes a character), which is what has to fit next to the app in an
Android heap of about 192 MB. Its own KDoc calls 24 MiB "more than twice a library of three thousand songs", i.e. it
was sized for 4 KB songs and a library of 3,000; nothing ties the export to that.

The export's own memory is not the problem at this size: `ZipWriter` sizes its buffer up front
(`ByteArrayBuilder(entries.sumOf { … })`), so the peak is the texts as byte arrays, the buffer and the `build()` copy —
about three times the library, 72 MB at the limit, transient — and Android writes it to its cache file and streams
from there (`FilePicker.android.kt:210`). It grows linearly past the limit, and so is only worth watching if option A
lifts the cap. `ZipWriter` refuses 65,535 entries or more (`ExportFailed`), which the size limit reaches first for any
song over 380 bytes.

## Fix
Options, as the user's call:

- **A — the import takes it.** Raise the archive caps (the picked archive and the total it unpacks to) while keeping
  `MAX_TEXT_FILE_SIZE` per file. Only safe with a streaming import: read the central directory from the platform's
  file handle, unpack and plan entry by entry, and hold names and comparable hashes rather than every text for
  `ImportPlanner`. Touches all four `FilePicker`s (they read whole files into `ImportedFile.bytes`), `ImportBudget`,
  `ArchiveLocalSource`, `PrepareImportUseCase` and `ImportPlanner`. Large; the right answer if big libraries are a
  real audience.
- **B — split the export** into `campfire_library_1.zip` … each under the limit, setlists in the first. Every
  platform's save flow is one file per call (Android `CreateDocument`, one browser download, one desktop dialog), so it
  is N dialogs or N downloads, and a later import of only some parts leaves setlists naming missing songs.
- **C — refuse** above the limit with a message. Honest, but it takes away the one full copy the user can make, which
  unzipped by hand into the library folder (the export mirrors its layout for that reason) or into a synced folder is
  still a complete backup.
- **C′ — export and say so (recommended).** The archive is still written; after the save the user is told that
  Campfire cannot import it in one go and what to do instead. Small, keeps the backup, and makes the limit visible
  the day it is made rather than the day it is needed:
  1. `CampfireViewModel.save` (`:1337-1345`): after `filePicker.saveFile(it)` returned true, if
     `it.mimeType == ExportedFile.ZIP_MIME_TYPE && it.bytes.size > ImportLimits.MAX_IMPORT_SIZE`, send
     `Message.ExportTooLargeToImport` (a new `data object` next to `ExportFailed`, rendered where `ExportFailed` is in
     `CampfireApp.kt:509`). Generic rather than library-only, so a setlist export of that size says it too.
  2. New string `export_too_large_to_import`, both files:
     en: "This archive is larger than the 24 MB Campfire can import at once. It is a complete copy of your library;
     to bring it back, unzip it and import the songs a few thousand at a time."
     hu: "Ez az archívum nagyobb annál a 24 MB-nál, amennyit a Campfire egyszerre importálni tud. A könyvtárad teljes
     másolata; a visszatöltéshez csomagold ki, és importáld a dalokat néhány ezres adagokban."
     The number is written into the text rather than formatted from the constant, since `MAX_IMPORT_SIZE` is not a
     round megabyte count to a reader (`24L shl 20`); if the constant changes, the strings change with it — say so in
     `ImportLimits`' KDoc.
  3. Update `ImportLimits.MAX_IMPORT_SIZE`'s KDoc: "…more than twice a library of three thousand songs of 4 KB. A
     library exports to an archive of its own size plus about 180 bytes a file, so one of about six thousand such
     songs exports to an archive this refuses, and the export says so."
- **D — leave it**, and add the sentence of C′ step 3 to the KDoc and to the README's export section.

Do **not** start deflating the export as a fix: the import's cap on what an import unpacks to is the same 24 MiB.

## Tests
None for C′ (the view model is untested; the check is one comparison). For A, `ImportPlanner` and the zip reader
would need tests of their own and are a plan of their own.

## Verify
1. Desktop (`./gradlew :app:desktop:run`) with a library over the limit: fill `library/songs/` with 9,000 copies of a
   3 KB song under distinct names
   (`for i in $(seq 1 9000); do sed "s/{title: .*}/{title: Song $i}/" song.cho > "song_$i.cho"; done` in the library
   folder), start the app, Settings → Export library: the archive is saved (about 29 MB) and the new message appears.
2. Import that archive: "One file is too large…" as before (C′ does not change the import).
3. Delete down to 5,000 songs, export again: no message; importing the archive into an empty library brings back all
   5,000.
4. Export a single setlist and a single song: no message.

## Docs
`data/source/local/implementation/CLAUDE.md` nothing; root `CLAUDE.md` nothing. The KDoc of `ImportLimits` as in step 3.
If A is chosen instead, the root `CLAUDE.md` Web section and `ImportLimits`' KDoc both change and the plan is written
anew.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportLimits.kt`

## Depends on
The user's decision. 47 (zip reader) is independent of it.
