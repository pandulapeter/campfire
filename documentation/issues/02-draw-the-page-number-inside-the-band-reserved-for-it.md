# Draw the page number inside the band reserved for it, not in the margin

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`

## Problem

Content stops 18 pt above the bottom margin to make room for the number (`val bottom = height - margin - if
(options.showPageNumbers) 18f else 0f`), but the number is drawn with its **top** at the margin line:

```kotlin
PrintText("${index + 1} / ${nonempty.size}", margin, height - margin, 9)
```

`PrintText.y` is the top of the text (`drawText(topLeft = …)`), so the band stays empty and the number sits inside the
margin the user chose — at the 10 mm minimum its baseline is about 5.5 mm from the paper's edge, where many printers
no longer print. It is also left-aligned, which reads as stray text under the first column.

## Fix

Place it in the band: `y = height - margin - 11f` (a 9 pt line is about 11 pt tall, leaving 7 pt above it), and centre
it: `finishPrintDocument` takes the `measure` function and uses `x = (width - measure(text, 9, false)) / 2`.

## Tests

`PrintLayoutTest`: for margins 10 and 25, the page number's `y + 11 <= height - margin` and `y >= bottom`; no other text
on any page of a long song has `y + size * 1.45 > bottom`.

## Manual check

Export a song with 10 mm margins and print it; the page number is inside the printed area, centred.
