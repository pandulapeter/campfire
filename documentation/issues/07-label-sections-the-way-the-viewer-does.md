# Label sections the way the viewer does: no heading for an unnamed verse

**Kind:** output quality  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`, `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt` (its `PrintLabels(…)` call only)
**Challenged:** amended — neither test passes the chorus and bridge labels today (both call `PrintLabels` with five arguments), so removing the defaults also edits lane B's `PrintRendererTest` by one call; the sheet's `PrintLabels(…)` call is shared with plan 34.

**Decision D4** — taken 2026-10-01: the recommended option, as written below.

## Problem

```kotlin
val sectionLabel = block.label ?: when (val type = block.type) {
    SectionType.Verse -> labels.verse
```

Every `{start_of_verse}` without a label prints a bold "Verse" (five identical headings on a five-verse song), where
the viewer shows none (`SectionType.Verse -> null` in `SongLyrics.kt`). A verse the file numbers ("Verse 1") keeps its
label either way.

## Fix

`SectionType.Verse -> null`; remove `PrintLabels.verse`, the `print_verse` string in both languages and its argument in
`PrintExportSheet`. Also remove the English defaults of `PrintLabels.chorus`/`bridge`, so no hard-coded English can reach
a page, and pass them in both tests: `PrintLayoutTest.labels` and the one `PrintLabels("Key", "Capo", "Tempo", "Time",
"Missing")` call in `PrintRendererTest` (lane B's file, merged first — touch that line only). Plan 34 later swaps the
sheet's `print_chorus`/`print_bridge` for `song_details_section_*`; keep the argument order so its edit is a rename.

## Tests

`PrintLayoutTest`: an unlabelled verse emits no heading; a labelled one keeps it. Update tests relying on the defaults.

## Manual check

Export a song with plain `{sov}` sections: no "Verse" headings; the blank gap still separates the verses.
