# Set lyrics in the app's text font, keep monospace for tabs and grids, and give the page a hierarchy

**Kind:** output quality  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (constructor, `text`, `width`, `draw` — not `pdf`), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt` (the `newRenderer()` line, the preview renderer's `remember` keys and the measure lambda only), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`, `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`
**Challenged:** amended — `FontFamily.Default` is not the viewer's lyrics face on the web (that is Inter, preloaded into `MaterialTheme.typography`), so the text family is injected like the monospace one; the label's extra space is part of its row, not a row of its own; the gray test draws with `draw` instead of decoding lane B's changing image stream; the heading gap is plan 01's `headingGap`.

**Decision D1** — taken 2026-10-01: the recommended option, as written below.

## Problem

Everything on the page is one monospace face: title 2 pt larger than the lyrics, artist and metadata styled like
lyrics, section labels bold exactly like chords ("Intro" over `G C D G` is ambiguous). The viewer sets lyrics and
chords in the proportional text font and uses monospace only where columns must line up
(`// Tablature and grids are both columns of characters that have to line up` in `SongLyrics.kt`). Monospace also costs
about a third of the line: two-column A4 holds ~33 characters at 12 pt, so ordinary lyric lines wrap.

## Fix

- `PrintText` (and the private `Part`) gain a style instead of `size`/`bold`:
  `data class PrintStyle(val size: Int, val bold: Boolean = false, val italic: Boolean = false, val monospace: Boolean = false, val gray: Int = 0)`
  (`gray` 0 = black … 255 = white). `measure` becomes `(String, PrintStyle) -> Float`, and plan 12's memo keys on
  `(text, style)`.
- `PrintRenderer(measurer, monospaceFontFamily = FontFamily.Monospace, textFontFamily = FontFamily.Default)` maps the
  style: `textFontFamily` unless `monospace`, `FontStyle.Italic`, `Color(gray, gray, gray)`; both the draw cache and
  `width` key on the style. The sheet passes `MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default` —
  the face the viewer's `lyricsStyle` uses: the platform default on Android, iOS and desktop, and on the web the Inter
  family `interfaceTypography()` preloads (where `FontFamily.Default` is not what the viewer shows) — and adds it to
  the `remember` keys of the preview renderer and to `newRenderer()`. The preloaded fonts live in the same
  `LocalFontFamilyResolver` the renderer's `TextMeasurer` is built on, as the monospace ones already do.
- Layout styles (sizes are `Int`: `(fontSize * 1.6f).roundToInt()`, 13–32 over the 8–20 range; `fontSize - 1` is ≥ 7):
  title `(fontSize * 1.6f).roundToInt()` bold; artist `fontSize` regular; metadata line `fontSize - 1` gray 90; section
  label `fontSize - 1` bold gray 90, its first row `0.3 * fontSize` taller with its parts moved down by that much (part
  of the label's row, so it never ends a column alone or opens one with a gap); chords bold black; lyrics regular; tab
  lines and grid rows `monospace = true` — `wrapped()` takes the style, and the tab `characters` count keeps measuring
  `"M"` in the monospace style; page number gray 90. Overview: setlist title `(fontSize * 1.6f).roundToInt()`, entries
  bold, artists gray 90.
- Nothing else assumed a monospace face: `padLyricsToFitChords` measures pieces and the U+00A0 padding as the viewer
  does with its proportional font, chord anchors are measured prefixes, and the over-tall split groups by `y`.
- Plan 01's `headingGap` becomes `fontSize * 0.8f`.

## Tests

Update `PrintLayoutTest`'s fake measurer to take the style; assert tab and grid texts are monospace and lyric texts are
not, and the title size. `PrintRendererTest` (one new test at the end of the class): lay out a song with metadata, draw its first page with
`renderer.draw` onto an `ImageBitmap` at scale 3 (not through `pdf()`, whose encoding lane B changes), and read the
pixels: the darkest pixel in the metadata line's band is ≥ 70 (gray 90 is never drawn black), the darkest in a lyric
line's band is ≤ 30.

## Manual check

Export the demo setlist on desktop, Android, iOS and web and print one page: title, artist, labels, chords and lyrics
are told apart at a glance; the tab still lines up; on the web the lyrics use the same face the viewer does.
