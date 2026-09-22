# 35 · A setlist's song numbers break onto two lines from 100 songs up, or from 10 at a large font

**Severity:** minor (all platforms, worst on Android and iOS with a large system font. Likely for somebody at
the largest font sizes: any setlist of 10+ songs; otherwise only setlists of 100+ songs at a font scale a little
above 100%) · **Area:** `:presentation` (`ui/components/ListItems.kt`)

## Symptom
At Android's 200% font size, row 10 of a setlist shows "1" over "0": the number wraps inside its slot, the row grows
a line and the title, aligned to the number's first baseline, stays up on the first. In a setlist of more than 99
songs the same happens to "100" at a font scale of about 1.1.

## Cause
`ListItemIndex` (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt:369-379`)
is `Text(modifier = modifier.width(LIST_ITEM_INDEX_KEYLINE), text = (index + 1).toString(), style = labelLarge)` with
`LIST_ITEM_INDEX_KEYLINE = 24.dp` (`:884`, "two digits and the gap they keep from the title"). The slot is in dp and
the digits are in sp, so the slot stops holding two digits as the font grows (labelLarge 14sp is about 26dp tall at
200% under Android 14's nonlinear scaling, two digits about 28dp wide), and three digits (about 23.6dp at 100%)
leave no gap at all. The text soft-wraps, so a number that does not fit breaks across lines.

## Fix
Size the keyline in `sp`, so it keeps holding two digits and their gap at every font scale, and let a number that is
wider still (three digits) push its title a little rather than wrap.

1. `:884`: change the constant and its KDoc:
   ```kotlin
   /**
    * How far after the start of a [ListItemIndex] the title of its row starts: two digits and the gap they keep from
    * the title. In sp, because it is measured against the digits and has to grow with them when the font does. A number
    * of three digits takes a little more than this and pushes its own title along, rather than every row of every
    * setlist making room for it.
    */
   private val LIST_ITEM_INDEX_KEYLINE = 24.sp
   ```
   Import `androidx.compose.ui.unit.sp` if it is not there yet.
2. `ListItemIndex`:
   ```kotlin
   ) = Text(
       modifier = modifier.widthIn(min = with(LocalDensity.current) { LIST_ITEM_INDEX_KEYLINE.toDp() }),
       text = (index + 1).toString(),
       style = MaterialTheme.typography.labelLarge,
       color = MaterialTheme.colorScheme.onSurfaceVariant,
       softWrap = false,
       maxLines = 1,
   )
   ```
   and in its KDoc replace "Laid out over a fixed width ([LIST_ITEM_INDEX_KEYLINE]) instead of around the number" with
   "Laid out over at least [LIST_ITEM_INDEX_KEYLINE] instead of around the number". Import
   `androidx.compose.ui.platform.LocalDensity` if missing (`widthIn` is already imported).
3. `listItemIndexIndent` (`:384`), which cannot read a composition local, converts inside a layout, where the
   measure scope is the density:
   ```kotlin
   private fun Modifier.listItemIndexIndent(hasIndex: Boolean) = if (hasIndex) {
       layout { measurable, constraints ->
           val indent = LIST_ITEM_INDEX_KEYLINE.toDp().roundToPx()
           val placeable = measurable.measure(constraints.offset(horizontal = -indent))
           layout(placeable.width + indent, placeable.height) { placeable.placeRelative(indent, 0) }
       }
   } else {
       this
   }
   ```
   Imports: `androidx.compose.ui.layout.layout`, `androidx.compose.ui.unit.offset`.

`Density.toDp(TextUnit)` goes through the font scale converter, so it follows Android 14's nonlinear scaling the way
the digits do. At 100% the keyline stays 24dp and nothing moves. `ListItemIndex`'s place in the headline `Row` is
unchanged, so the baseline alignment and the drag renumbering behave as before; only rows from 100 up sit a couple of
dp further in.

## Tests
None (UI).

## Verify
1. Android, system font size at maximum: a setlist of 12 songs shows "10", "11", "12" on one line each, and every
   title starts on the same keyline; the artist line under a title is indented to match.
2. At 100%: pixel-identical rows.
3. A setlist of 105 songs (import a zip, or add songs in bulk through the picker): "100"-"105" on one line, their
   titles a little further in. Dragging row 99 past row 100 does not wrap anything.
4. iOS with the largest Dynamic Type accessibility size: the same as 1.
5. Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`.

## Docs
`presentation/CLAUDE.md`, the list items bullet: replace "over a fixed width so the titles stay on one keyline while
a drag renumbers the rows it passes" with "over a width in sp (two digits and a gap, growing with the font) so the
titles stay on one keyline while a drag renumbers the rows it passes; a three digit number pushes its own title a
little rather than wrap".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 30 edits `SetlistsScreen.kt`, not this file.
