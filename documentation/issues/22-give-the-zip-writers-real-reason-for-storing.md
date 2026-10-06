# Give the zip writer's real reason for storing its entries uncompressed

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** data/source/local/implementation/CLAUDE.md,
data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipWriter.kt,
data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt
**Challenged:** amended — both stated reasons hold at 8ee010b36 (no deflater in the module or its dependencies `:data:source:local:api`, `:chordpro`, `:data:model`; the export's check at `CampfireViewModel.kt:3409` compares the archive's bytes with `MAX_IMPORT_SIZE`); added a third stale statement of the same fact, `ArchiveLocalSourceImpl`'s KDoc "inflating and deflating in memory".

## Problem

Two places give a reason for `ZipWriter` writing only STORED entries, and the reason is wrong:

`data/source/local/implementation/CLAUDE.md:197` (8ee010b36)
```
  `ZipWriter` (STORED only — song text compresses badly enough not to be worth it), with every entry dated by its
```
`ZipWriter.kt:12-15`
```kotlin
/**
 * Writes zip archives with one STORED (uncompressed) entry per input. The files the app exports are tiny ChordPro and
 * JSON documents, so leaving out DEFLATE compression costs nothing and keeps the writer trivial.
 */
```

Measured in the live run: a library export of 2 952 songs and 41 setlists is 5.04 MB stored for 4.60 MB of text, while
a deflated archive of a library like it (Python's `zipfile`) was 2.23 MB for about 4.5 MB of text — ChordPro text
compresses about 2×, and a whole-library export is not "tiny". So the CLAUDE.md sentence is false and the KDoc's
"costs nothing" is false for the library export (it holds for a single song or a setlist zip).

The real reasons, from the code:
- **No encoder in this module.** `:data:source:local:implementation` has an inflater (`zip/Inflater.kt`, the read side)
  and no deflater; the only DEFLATE encoder in the project is `PrintDeflater` in `:presentation`
  (`ui/print/PrintDeflater.kt`), a layer above, which this module cannot depend on. Storing keeps the writer a page of
  header bytes.
- **A stored archive's size is what an import unpacks.** The import caps what one selection unpacks to at
  `ImportLimits.MAX_IMPORT_SIZE` (24 MiB, `ZipReader`'s `maxTotalSize`), and the export warns that a file is too large to
  import by its *archive* size (`CampfireViewModel.kt:3409`: `file.bytes.size > ImportLimits.MAX_IMPORT_SIZE`) — which
  `ImportLimits`' KDoc spells out ("A library exports to an archive of its own size plus about 180 bytes a file"). That
  one check is right only because the entries are stored.

## Fix

Documentation only (recommended).

1. `data/source/local/implementation/CLAUDE.md:197`: replace "(STORED only — song text compresses badly enough not to be
   worth it)" with "(STORED only — the module has no DEFLATE encoder, and a stored archive's size is what an import
   unpacks, which is what lets the export warn by the archive's size that it is past `ImportLimits.MAX_IMPORT_SIZE`;
   song text would compress about 2×)".
2. `ZipWriter.kt` KDoc: replace the second sentence with: "Storing keeps the writer to the headers - this module has no
   DEFLATE encoder, only the reader's `Inflater` - and keeps an archive's size equal to what an import unpacks it to
   (plus its headers), which is what the export's too-large-to-import warning compares with
   `ImportLimits.MAX_IMPORT_SIZE`. ChordPro text would deflate to about half its size, so a whole library's export is
   about twice the file it could be."
3. `ArchiveLocalSourceImpl.kt:31-34` KDoc: "it is all inflating and deflating in memory" → "it is all inflating and
   packing in memory" (packing being the stored write; nothing here deflates).

Option, not recommended: deflate the entries. It would halve a library export, but it is not a small change: the
encoder would have to move from `:presentation` down to a module both can use (or be duplicated here, with its tests),
`ZipWriter` would need the deflated size and method 8 in both headers, `ZipRoundTripTest` would need DEFLATE cases, and
the export's too-large check at `CampfireViewModel.kt:3409` would have to compare the *uncompressed* total with
`MAX_IMPORT_SIZE` — otherwise an archive under 24 MB that unpacks past it would be exported without a warning and then
refused by the import. Nothing the user cannot do is gained (the import's limit is on unpacked text either way), so
leave it for a request that wants smaller backups.

## Tests

None: comments and documentation only.

## Manual check

None.
