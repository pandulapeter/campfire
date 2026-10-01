# Name a song's PDF by its header, artist included

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintFileName.kt` and `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintFileNameTest.kt` (MPL header from a sibling)
**Challenged:** amended — the name is derived from the song's header (artist and title), not from its library file name, which is what every other export does and which a legacy or numbered file name (`Hallelujah.cho`, `x_2.cho`) would otherwise leak into the PDF; the setlist's suffix depends on the sheet's setting, so the helper takes the settings.

## Problem

```kotlin
ExportedFile(LibraryFiles.normalizedName(title) + ".pdf", "application/pdf", …)
```

with `title = source.title`, the song's title alone: two songs called "Hallelujah" both export as `hallelujah.pdf`
and the second save offers to overwrite the first, while every other export names a song `artist-title`
(`ExportFileNames.kt`: "the one its own header gives it … whatever its library file happens to be called, numbered
sibling or name from before its title changed").

## Fix

A pure helper `pdfFileName(source: PrintSource, settings: PrintSettings): String` in `ui/print`, called by the sheet at
export time:

- a song: the same rule the library and the exports name a song by (`songFileName` in the local source:
  `normalizedName(title)`, preceded by `normalizedName(artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR` when
  the artist is not blank), from the `PrintSong`'s `artist` and `title` (`Song.title` already carries the subtitle,
  and a missing song falls back to its file name as the title), plus `.pdf`. `songFileName` is `internal` to the data
  module, so the helper spells the three lines out from `:data:model`'s public `LibraryFiles`; do not read
  `PrintSong.fileName`, which is the library file's name;
- a setlist: `LibraryFiles.normalizedName(title)` as today, with `"-running_order"` appended **after** normalizing
  when `settings.setlistMode` is `RUNNING_ORDER`, so the two exports of one setlist do not collide either.

`exportPdf` takes the finished name instead of the title. The name is computed from the sheet's present settings, so
it follows a mode change the debounced preferences (plan 22) have not caught up with.

## Tests

`PrintFileNameTest`: artist and title → `tukorfurogep-arviz.pdf` (from the header values, with a deliberately
different library file name in the `PrintSong`); a blank artist → the title alone; a setlist in each mode.

## Manual check

Export two same-titled songs by different artists into one folder; export a song whose file is called `Foo Bar (2).cho`
and whose header says otherwise: the PDF follows the header.
