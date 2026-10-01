# Reuse one bitmap and one set of buffers for every page of an export

**Kind:** performance  ·  **Severity:** high  ·  **Platforms:** all (iOS, web and desktop most)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (`pdf` only), `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`

## Problem

```kotlin
document.pages.forEach { page ->
    …
    val bitmap = ImageBitmap(width, height)
    …
    val pixels = IntArray(width)
    val gray = ByteArray(width * height)
    for (row in 0 until height) {
        …
        bitmap.readPixels(pixels, startY = row, width = width, height = 1)
```

Each page allocates an 18 MB native bitmap (1786 × 2526 ARGB) and a 4.5 MB array; nothing releases the bitmap, and
Skia's native memory is only freed when the tiny wrapper object happens to be collected. Measured heap growth on
desktop was 180–234 MB for 18–141 pages; on wasm linear memory never shrinks, on iOS this is jetsam territory.
`readPixels` is called 2 526 times a page.

## Fix

Allocate the `ImageBitmap`, its `Canvas`, and the pixel and gray arrays once before the loop (`draw` repaints the
whole page white first). Read in bands of 64 rows into an `IntArray(width * 64)`, yielding between bands as today.
`PrintPdfWriter.addPage` consumes `gray` before returning (it encodes into its own output), so the array is free for
the next page — state that in its KDoc.

## Tests

`PrintRendererTest`: a three-page document whose pages differ — decode each page's image stream and check page 2 does
not contain page 1's text rows (white where page 2 is empty), i.e. reuse leaks nothing between pages.

## Manual check

Export a 100+ page setlist on an iPhone and in the browser; watch memory in Xcode / the task manager stay flat.
