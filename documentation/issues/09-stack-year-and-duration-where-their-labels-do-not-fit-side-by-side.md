# Stack Year and Duration where their labels do not fit side by side, and keep every metadata label on one line

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all (phones in portrait)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongMetadataDialog.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt

**Challenged:** amended — lay the two fields out with one custom Layout instead of a BoxWithConstraints if/else, so crossing the threshold (rotation, a desktop/web resize) does not dispose the focused field and drop the keyboard.

## Problem
Both song metadata forms put Year and Duration in one row of two half-width fields — `NewSongDialog` (`Dialogs.kt`
~1226-1229 at 800ebde0b) and `SongMetadataDialog` (`SongMetadataDialog.kt` ~98-101):

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    field(Modifier.weight(1f), Field.YEAR)
    field(Modifier.weight(1f), Field.DURATION)
}
```

On a 360dp phone each half is about 152dp, and the label `"Duration (optional)"` (`optional_field_label` +
`song_editor_insert_duration`; Hungarian `"Hossz (nem kötelező)"`) wraps to two lines (live screenshots
`13_scrolled_max.png`, `14_duration.png`). The Duration field becomes taller than Year next to it, and once focused or
filled the floating label sits over the outline as two lines and breaks the notch. `SongMetadataField`'s label is a
plain `Text` with no line limit.

## Fix
1. In `SongMetadataField` (`SongMetadataDialog.kt`), give the label one line:
   `Text(text = …, maxLines = 1, overflow = TextOverflow.Ellipsis)`. That alone keeps the outline intact at any width
   and font scale.
2. So that the "(optional)" marker is not ellipsized on phones, share one helper between the two forms that lays the
   two short fields side by side only where there is room, the way `SetlistDateRow` does with `MIN_DATE_ROW_WIDTH`:
   ```kotlin
   /**
    * The year and the duration, side by side where both labels fit on one line in either language, and one under the
    * other on a phone, where half a sheet cuts "Duration (optional)" in two.
    */
   @Composable
   internal fun SongMetadataShortFields(field: @Composable (Modifier, Field) -> Unit) = BoxWithConstraints {
       if (maxWidth >= MIN_SHORT_FIELDS_ROW_WIDTH) {
           Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
               field(Modifier.weight(1f), Field.YEAR)
               field(Modifier.weight(1f), Field.DURATION)
           }
       } else {
           Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
               field(Modifier.fillMaxWidth(), Field.YEAR)
               field(Modifier.fillMaxWidth(), Field.DURATION)
           }
       }
   }
   ```
   **Do not branch with `if` inside `BoxWithConstraints` as sketched above**: the Row and the Column are different
   composition groups, so a width change across the threshold (rotating with the caret in Year, resizing the desktop
   window or the browser) disposes both fields and recreates them, which drops focus and the keyboard mid-typing (the
   cover art sheet's comment about moving a focused field between layouts is the same trap). Keep both fields in one
   place in the composition and decide in measure instead:
   ```kotlin
   @Composable
   internal fun SongMetadataShortFields(field: @Composable (Modifier, Field) -> Unit) = Layout(
       content = {
           field(Modifier.fillMaxWidth(), Field.YEAR)
           field(Modifier.fillMaxWidth(), Field.DURATION)
       },
   ) { measurables, constraints ->
       val gap = 8.dp.roundToPx()
       val sideBySide = constraints.maxWidth >= MIN_SHORT_FIELDS_ROW_WIDTH.roundToPx()
       val width = if (sideBySide) (constraints.maxWidth - gap) / 2 else constraints.maxWidth
       val (year, duration) = measurables.map { it.measure(Constraints.fixedWidth(width)) }
       val height = if (sideBySide) maxOf(year.height, duration.height) else year.height + gap + duration.height
       layout(constraints.maxWidth, height) {
           year.placeRelative(0, 0)
           if (sideBySide) duration.placeRelative(width + gap, 0) else duration.placeRelative(0, year.height + gap)
       }
   }
   ```
   (the sheet's column always gives a bounded width). Focus order (Next) follows composition order, which this keeps.
   with `private val MIN_SHORT_FIELDS_ROW_WIDTH = 480.dp` and a KDoc saying what it is (each half must hold the longer
   of the two languages' labels plus the clear button). Start at 480dp and check both languages at 16sp: lower it if
   both labels fit narrower, raise it if not. Use it in both forms in place of the `Row`. Keep the comment in
   `SongMetadataDialog` about why the two share a row (the year's room next to the clear button) on the helper.
   The focus order (Next from Lyricist → Year → Duration) is unchanged, since the fields stay in the same order.

## Tests
None: layout only.

## Manual check
360×640dp emulator, English and Hungarian: New song and Edit song details show Year and Duration as two full-width
fields, each label on one line, outlines intact when focused and filled. On a tablet or a desktop window wider than the
threshold they share a row as before. At the largest system font scale, labels end with "…" instead of wrapping.
