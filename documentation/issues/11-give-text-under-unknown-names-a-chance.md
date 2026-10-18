# Read a text file under an unknown name as a chord sheet when it has the structure of one

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** new `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetStructure.kt` (+
a new `ChordSheetStructureTest.kt` beside `ChordSheetConverterTest.kt`; one bullet appended to the **end** of
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`),
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/importing/ImportLimits.kt` (`ImportBudget`),
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/library/LibraryFiles.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/importing/ImportedFile.kt`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`,
`data/source/local/implementation/src/desktopTest/.../source/ArchiveLocalSourceTest.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/importing/PrepareImportUseCaseImpl.kt`,
`domain/implementation/src/commonTest/.../importing/PrepareImportUseCaseImplTest.kt`,
`domain/implementation/src/commonTest/.../importing/ImportFilesUseCaseImplTest.kt`,
`domain/implementation/src/commonMain/.../importing/CLAUDE.md`, `data/model/CLAUDE.md` (the Import paragraph),
`presentation/src/desktopTest/.../ui/platform/ReadAsImportedFilesTest.kt`

**Challenged:** amended — `hasSongStructure` is tightened (probed: `- A`, `(a)`, `a`, `Do`, and `La` are all chord
rows to `isChordLine`, and `[C](url)` is a bracketed chord name, so the draft's rule let a Markdown README or a licence
through); it uses `:chordpro`'s internal helpers, which are not public API (same module, so allowed); `sort`'s size
check and `planSongs`' `.txt` branch and fallback title are named (as drafted, every flagged file was reported
oversized, since `maxSizeOf` is 0 for an unknown name); `ImportedFile.equals`/`hashCode` must carry the flag; the
folder-walk peek of plan 10 learns text.

Run after plan 10: it extends the `Sniff` policy. Lane C edits `ChordSheetConverter.kt`; this plan must not touch
that file, which is why `hasSongStructure` is a new file built on the converter's public surface.

## Problem

Inside an archive, `.onsong`, `.md`, `.html`, `.rtf`, `.xml`, `.text`, `.txt.bak`, and extensionless files (OpenSong
keeps extensionless XML songs in `Songs/`; a text file named `Mr. Brightside` reads as extension ` brightside`) are
`NOT_WANTED` and never read. Picked loose, `ImportBudget.readUnknown` keeps only zips, and `PrepareImportUseCaseImpl`'s
`sort` drops every other extension:

```kotlin
extension !in SONG_EXTENSIONS && extension !in DOCUMENT_EXTENSIONS && extension != SETLIST_EXTENSION -> triage.skippedFileNames += file.name
```

So a user who picks a chord sheet saved as `riverlight.onsong` or `riverlight` is told it is unsupported, although
the converter would have read it. Nothing is silent (the names are reported as skipped), but "a fair chance for
every file" does not hold.

Two guards must stay: `README.md`, `LICENSE`, an EPUB's `.xhtml` chapters and an ODT's `content.xml` must never
become prose songs ("no recognised structure means escaped prose" is right for a `.txt` the user chose, wrong for a
file nobody asked about), and the bytes-only archive gate (`entries.any { it.isImportable() }`) must keep reporting a
document container as one file.

## Fix

1. `ChordSheetStructure.hasSongStructure(text: String): Boolean`, public in `:chordpro` (`explicitApi`), in the new
   file. It may use the module's internal `ChordProDirectives` and `ChordProChordNames` (same module) and the
   converter's public `isChordLine`, and never edits `ChordSheetConverter.kt`. Over the first 400 lines only, it is true
   when any of these holds:
   - a known directive alone on its line (`ChordProDirectives.isKnownDirective(line.trim())`);
   - **two or more** bracketed chords written against a syllable, on at least two lines: a bracket whose content has no
     whitespace, is a chord name (standard or Latin: `ChordProChordNames.isChordName` or `latinExpanded(…) != null`,
     what the converter's private `isChordOrLatinName` asks), and is followed directly by a letter (`[G]Hello`), never
     by `(` (Markdown's `[C](https://…)`), as the converter's own `isChordPro` reads it;
   - **chord rows over lyrics:** at least two chord rows (`isChordLine`), at least one of which holds two or more
     chord tokens, and at least one of which is followed directly by a non-blank line that is not a chord row. A row
     of one token alone (`- A`, `(a)`, `Do`, `La`, a lone `A`) never counts toward the "two or more chords".
   Pin it with `ChordSheetStructureTest`: a UG-shaped sheet, a ChordPro file, and a Latin-notation sheet → true; prose,
   a README with `[C](https://…)` links and `- A` / `- B` bullets, a licence with `(a)` / `(b)` clauses, an HTML page,
   and an XML file → false.
2. Sniffing as text: `LibraryFiles.isTextLike(prefix: ByteArray)`: a UTF-16 BOM counts as text (check it first, and let
   `decodeLibraryText` decode it); otherwise no NUL byte and at most 2% C0 controls other than `\t\n\r\f`. A list
   `BINARY_EXTENSIONS` (images, audio, video, `.db`, `.sqlite`, `.sqlite3`, `.hive`, `.exe`, `.dll`, `.so`, `.apk`,
   `.jar`, fonts) is skipped by name without a peek. Plan 10's `ImportBudget.isWorthReadingUnknown(head)` becomes
   `isZipArchive(head) || isTextLike(head)`, so the desktop and web folder walks read a text file under an unknown name.
3. `ImportedFile` gains `val isPlainTextByContent: Boolean = false` (carried, not a renamed name: the report shows the
   name the file came under), **added to its hand-written `equals` and `hashCode`**. `ImportBudget.readUnknown` keeps a
   text-like unknown file within `MAX_TEXT_FILE_SIZE` flagged so (one over it is handed back unread, as a photo is).
   `ArchiveLocalSourceImpl`'s `sniffedLimitOf` answers `MAX_TEXT_FILE_SIZE` for a text-like prefix; since a `ZipEntry`
   does not say why it was read, the source flags an entry itself when `maxSizeOf(name) == 0`, it is not SongbookPro's
   document, and its bytes are not a zip. `ImportedFile` is never serialized (the iOS inbox's `share.json` holds texts
   and names, not `ImportedFile`s; every platform reader goes through `ImportBudget`), so nothing else changes.
4. `PrepareImportUseCaseImpl.sort`: before the extension check, a flagged file whose decoded text `hasSongStructure`
   is sorted as a song file; one without structure is `skippedFileNames`. Its size check uses `MAX_TEXT_FILE_SIZE`,
   not `maxSizeOf(file.name)` (0 for an unknown name, which would report every flagged file oversized). In
   `planSongs`, the `.txt` branch is `file.name.endsWith(TEXT_EXTENSION, true) || file.isPlainTextByContent`, and a
   flagged file's fallback title drops only an extension without a space after its dot (`riverlight.onsong` →
   `riverlight`, `Mr. Brightside` stays whole). A `.txt` keeps today's behaviour (a lyrics-only `.txt` is a song the
   user chose). Plans 12–14 put their own hooks before this one where a flagged text starts with `{`, `[`, or `<`.
5. The bytes-only archive gate: `isImportable()` counts a flagged entry only when it has song structure, and a `.txt`
   entry only when it has song structure or `isTooLarge` (an oversized `.txt` counts as today). A `.cho`, `.pdf`,
   `.docx`, `.setlist.json` entry counts as today. This leaves the pinned
   `a file of an unknown type that holds nothing an import reads is reported under its own name` passing (its
   `dataFile.txt` is unread and not too large, so it never counted) and does not touch exports, which are `.zip` by
   name and so bypass the gate. The cost to accept: a zip under an unknown name holding only lyrics-only `.txt` files is
   then one skipped file rather than its songs.
6. Docs: the importing package note (what an unknown name needs to be read: text-like bytes and song structure, else
   reported skipped), `data/model/CLAUDE.md`'s Import paragraph ("a file the import would not look inside is never
   kept (an unknown type is read only to check for a zip signature)" → or for text, kept flagged), and the appended
   bullet in `chordpro/convert/CLAUDE.md` naming `ChordSheetStructure` as the one answer to "is this text a chord sheet
   at all", used by the import's unknown-name rule.

Memory: a kept text is charged as a `.txt` is; a rejected entry costs the 512-byte peek; loose unknown files are
already read up to the remaining budget today, so heap use does not grow.

## Tests

- `ChordSheetStructureTest` as in step 1.
- `ArchiveLocalSourceTest`: `a chord sheet under an unknown name inside an archive is read` (`x.onsong` with chords
  over lyrics, and an extensionless `Riverlight`, both flagged); `a binary file under an unknown name is skipped without
  charge` (PNG bytes as `x.onsong`, then a song that only fits if the PNG was not charged).
- `PrepareImportUseCaseImplTest`: `a text of unknown name with song structure is planned as a converted song` (and is
  not reported oversized); `a README under an unknown name is reported skipped, not imported as prose`; `a bytes-only
  archive of a licence text is reported as one file` (an archive recognised by bytes holding `LICENSE.txt` of prose →
  the archive's own name in `skippedFileNames`); `an unknown name with a dot in it keeps its whole name as the title`.
- `ImportLimitsTest`: `an unknown loose file that is text is kept flagged, a binary one is not`.
- `ReadAsImportedFilesTest`: a folder holding an extensionless chord sheet yields it flagged.
