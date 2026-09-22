# 32 · The editor's Edit / Preview choice is cut off on phones, badly in Hungarian

**Severity:** minor (Android and iOS phones, narrow desktop and web windows. Certain on a 360dp phone: the selected
segment's label is cut at its end with no ellipsis, in English "Preview" and in Hungarian "Szerkesztés", the segment
the editor opens on) · **Area:** `:presentation` (`ui/components/SegmentedChoice.kt`,
`ui/screens/songEditor/SongEditorScreen.kt`)

## Symptom
On a 360dp phone the editor's second bar row holds the transpose stepper, the Edit / Preview segments and the
shortcuts toggle. Pick Preview: its label reads "Previ" and simply stops at the segment's edge. In Hungarian the
editor opens with "Szerkesztés" selected and it shows as a clipped "Szer…". The same cut, milder, hits a selected
segment in Settings whenever its label fills the segment (the Hungarian "Kereszt (#)" of the accidentals choice on a
360dp phone).

## Cause
Two things add up.

1. Material 3 1.12.0-alpha03 `SegmentedButtonContentMeasurePolicy` (`SegmentedButton.kt:414-455`) measures the label
   with the segment's whole inner width (`contentMeasurables.fastMap { it.measure(constraints) }`) and then, in a
   selected segment, places it after the check mark: `contentOffsetX = IconSize + IconSpacing + offset`, 18dp + 8dp
   with `offset = 0` when the icon is there. A label that fills the width therefore runs 26dp past the segment,
   where its shape clips it, and `TextOverflow.Ellipsis` never triggers because the text was given room for itself.
   An unselected segment has no icon (`SegmentedButtonDefaults.Icon` emits nothing when inactive) and the policy's
   `-13dp` offset plus the centring of an oversized placeable cancel out, so it is fine.
2. The editor row is tight. `SongEditorScreen.kt:372-405`: the row has 16dp + 4dp padding, the stepper takes about
   116dp and the toggle 48dp; `SegmentedChoice` (`SegmentedChoice.kt:36`) adds 16dp on either side on top; each
   segment has 12dp content padding per side. On 360dp that leaves 48dp for a label, and "Szerkesztés" alone is about
   73dp.

## Fix
1. `SegmentedChoice.kt`: offer the label only the width it will have. In the `label` slot:
   ```kotlin
   label = {
       Text(
           // Material measures the label at the whole width of the segment and then places it after the check mark
           // of a selected one, so a label as wide as the segment ran on under its shape instead of being ellipsized.
           // The check mark's room is taken off the width the label is offered.
           modifier = if (value == selected && hasCheckMark) Modifier.withoutCheckMarkWidth() else Modifier,
           text = label,
           maxLines = 1,
           overflow = TextOverflow.Ellipsis,
       )
   },
   ```
   and below the composable:
   ```kotlin
   /**
    * Measures the content with the width of a selected segment's check mark and the gap after it taken off: 18dp
    * (`SegmentedButtonDefaults.IconSize`) and 8dp, which is Material's own spacing and not public.
    */
   private fun Modifier.withoutCheckMarkWidth() = layout { measurable, constraints ->
       val reserved = (SegmentedButtonDefaults.IconSize + CHECK_MARK_SPACING).roundToPx()
       val placeable = measurable.measure(
           if (constraints.hasBoundedWidth) {
               constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth - reserved).coerceAtLeast(0))
           } else {
               constraints
           },
       )
       layout(placeable.width, placeable.height) { placeable.place(0, 0) }
   }

   private val CHECK_MARK_SPACING = 8.dp
   ```
2. Still in `SegmentedChoice`, a parameter for a choice that is one control of several in a row:
   ```kotlin
    * @param isInline True where the choice shares a row with other controls, as in the editor's bar: it keeps a
    *   narrower gap from them than from the edges of a screen, and a selected segment has no check mark - its fill
    *   already marks it, and the mark's 26dp is most of what a label has there on a phone.
   ```
   `isInline: Boolean = false,` after `isEnabled`. Apply it: `modifier.fillMaxWidth().padding(horizontal = if (isInline) 8.dp else 16.dp)`,
   and on each `SegmentedButton` pass `icon = if (isInline) ({}) else ({ SegmentedButtonDefaults.Icon(value == selected) })`
   (the second is Material's default, spelled out). Let `hasCheckMark` in step 1 be `!isInline`.
   Imports: `androidx.compose.ui.layout.layout`.
3. `SongEditorScreen.kt:385`: pass `isInline = true,` to the pane `SegmentedChoice`.
4. A shorter Hungarian label for the edit pane. It uses the shared `edit` key (also the menus' "Szerkesztés"), so add
   a key of its own in the song editor group of both files, next to `song_editor_preview`:
   - `values/strings.xml`: `<string name="song_editor_edit">Edit</string>`
   - `values-hu/strings.xml`: `<string name="song_editor_edit">Szöveg</string>` (the text as against its preview,
     which is the choice the segments make)
   and read it at `SongEditorScreen.kt:390`: `EditorPanes.EDIT to stringResource(Res.string.song_editor_edit),`,
   importing `com.pandulapeter.campfire.presentation.resources.song_editor_edit`. Remove the `resources.edit` import
   from `SongEditorScreen.kt` if nothing else there uses it (line 94; grep first).

With this, a 360dp phone gives each of the editor's segments about 56dp of label: "Edit", "Preview", "Szöveg" and
"Előnézet" fit whole; at 320dp they ellipsize visibly instead of being cut.

## Tests
None (UI).

## Verify
1. Android 360dp emulator (or a Pixel at the largest display size), editor open, English: both segments read in
   full, with Preview selected too; no check mark in this row. Hungarian: "Szöveg" / "Előnézet" in full.
2. At 320dp (largest display size on a small phone): labels end in "…", never cut mid-glyph.
3. Settings: the accidentals, theme and other segmented choices still show the check mark on the selected segment,
   and a selected label that fills its segment ends in "…" (Hungarian "Kereszt (#)" at 360dp).
4. Tablet and desktop: the editor row looks the same apart from the missing check mark and 8dp side gaps.
5. Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`.

## Docs
`presentation/CLAUDE.md`, where `SegmentedChoice` is listed among the components, add after its name: "(offering a
selected label only the width its check mark leaves, since Material measures it at the whole segment and cuts it off;
`isInline` for one control of several in a row, the editor's panes, which drops the mark and the wide side gap)".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SegmentedChoice.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`
- `presentation/CLAUDE.md`

## Depends on
Nothing.
