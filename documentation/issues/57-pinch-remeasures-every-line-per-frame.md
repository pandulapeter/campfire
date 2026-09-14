# 57 · Pinch-to-zoom re-measures every chord and fragment of the song on every pointer event

**Severity:** medium (jank on long songs; also on theme change) · **Area:** `:presentation` (`SongLyrics.kt`, `FontScaleGestures.kt`)

`SongLyrics.kt:147–157` derives `lyricsStyle`/`chordStyle` from `fontScale` **and** the theme colors;
`SongLineWithChords` (:995–1007) runs `textMeasurer.measure` for each chord and each fragment inside `remember`s keyed
on those styles (`rememberTextMeasurer()` caches 8 results); `SongSectionsLayout` (:669–676) re-runs the intrinsic
loop per measure. `fontScaleGestures` calls `onFontScaleChanged` per pointer event, so a 300-line song does all of
that synchronously each event. Because the keys include `color`, a theme cross-fade re-measures every line per
frame although layout does not depend on color.

## Fix

1. **Measure without color, draw with color.** Keep a `measureStyle` per role that carries size, weight, family,
   letter spacing but `color = Color.Unspecified`; key every `remember { textMeasurer.measure(...) }` on that. Apply
   the color where the text is drawn (`Text(color = …)` / `drawText(color = …)`). `TextStyle.copy(color = Unspecified)`
   once per role at the top of `SongLyrics` is enough.
2. **Coalesce the gesture to one update per frame.** In `fontScaleGestures`, instead of calling `onFontScaleChanged`
   on every event, store the latest value and emit it from a `withFrameNanos` loop (or `snapshotFlow` +
   `collectLatest`) so at most one relayout happens per frame. The ViewModel's `pendingFontScale` already debounces
   the *preference write*; this is about the layout.
3. Optionally raise `rememberTextMeasurer(cacheSize = 64)` at the `SongLyrics` level and pass it down, so lines that
   scroll back into view hit the cache.
4. Measure before/after on the desktop with a 300-line song and a pinch (trackpad Ctrl+scroll): frame times in the
   Compose Desktop `-Dcompose.debug` overlay or a simple `System.nanoTime` around the layout.
