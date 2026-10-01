# Convert pixels to gray by luminance, not by the red channel

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (`pdf` only), `presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRendererTest.kt`

## Problem

```kotlin
// All print content is neutral, so a channel is its grayscale value.
gray[row * width + column] = (pixel shr 16 and 255).toByte()
```

Not all content is neutral: colour emoji in a title or lyric are drawn in colour. The live run's 🔥 nearly vanishes
(red ≈ 255 → white), 🇭🇺 gets the wrong stripes and 🎸 is faint.

## Fix

`(299 * r + 587 * g + 114 * b) / 1000`, integer arithmetic; fix the comment.

## Tests

`PrintRendererTest`: unit-test an extracted `internal fun printGray(argb: Int): Int` — pure red → 76, pure green → 149,
white → 255, black → 0.

## Manual check

Export a song with 🔥 in its title; the flame is a visible mid-gray.
