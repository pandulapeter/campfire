# Drop a segmented row's check marks where a label would not fit beside one

**Challenged:** amended — the `BoxWithConstraints` is a `SubcomposeLayout`, which throws on an intrinsic measurement: the plan now says so in a comment, names the one caller inside a Material container that could ask (the welcome `AlertDialog` on a window of 500dp and more) and adds it to the manual check; the measurement keys and the inline path made explicit.

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all (phone width; also the export screen's 260dp options pane)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SegmentedChoice.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/SegmentedChoiceTest.kt` (new), `presentation/CLAUDE.md`

## Problem

`SegmentedChoice` (dac1d9d59) gives a selected, non-inline segment Material's 18dp check mark plus 8dp, and takes that
room off the label (`withoutCheckMarkWidth`), so a label that does not fit is ellipsized:

```kotlin
icon = if (isInline) ({}) else ({ SegmentedButtonDefaults.Icon(value == selected) }),
contentPadding = if (isInline) INLINE_CONTENT_PADDING else SegmentedButtonDefaults.ContentPadding,
label = {
    Text(
        modifier = if (value == selected && !isInline) Modifier.withoutCheckMarkWidth() else Modifier,
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
},
```

At 360dp a three-segment row is (360 − 2·16) / 3 ≈ 109dp a segment, less 2·12dp padding and 26dp for the mark: about
59dp for a label. Settings → Songs (`SettingsScreen.kt`, the two `SegmentedChoice` calls in the Songs section) then
shows "✓ Sharp…" for Accidentals → Sharps (#) and "✓ Keybo…" for the new Instrument → Keyboard
(`android/07_instrument_keyboard.png`); Hungarian "Kereszt (#)" and "Billentyű" are cut too. The selected option is
exactly the one whose name the user most needs to read. The same arithmetic hits two-segment rows in the export
screen's 260dp side-by-side options pane ((260 − 32) / 2 − 24 − 26 ≈ 64dp: "Landscape", "Running order",
Hungarian "Álló tájolás" and "Fekvő tájolás"; not reproduced on a device, follows from the same arithmetic).

## Fix

Decide per row, once, whether the marks fit — the whole row with or without marks, so a mark never appears on one
selection and not on the next:

1. Add a pure helper in `SegmentedChoice.kt`:

   ```kotlin
   /**
    * Whether every label of a row of segments [rowWidth] wide still fits its segment with a selected segment's check
    * mark beside it. Decided for the whole row, so that picking another option never takes the mark away.
    */
   internal fun hasRoomForCheckMarks(labelWidths: List<Float>, rowWidth: Float, contentPadding: Float, checkMark: Float): Boolean {
       if (labelWidths.isEmpty()) return true
       val labelRoom = rowWidth / labelWidths.size - contentPadding - checkMark
       return labelWidths.all { it <= labelRoom }
   }
   ```
2. In `SegmentedChoice`, for the non-inline case wrap the row in `BoxWithConstraints` (after the horizontal padding the
   row applies), measure every label once with `rememberTextMeasurer()` in the label style Material uses
   (`MaterialTheme.typography.labelLarge`), `remember`ed on the labels, the style, the density and the font scale, and
   compute `showsCheckMarks = hasRoomForCheckMarks(widths, maxWidth.toPx(), (start+end content padding).toPx(), (SegmentedButtonDefaults.IconSize + CHECK_MARK_SPACING).toPx())`.
   The segment border (1dp each) is noise at this scale; leave it out or subtract `options.size` dp from the width.
3. Use `showsCheckMarks` where `!isInline` is used today: `icon = if (showsCheckMarks) ({ SegmentedButtonDefaults.Icon(…) }) else ({})`
   and `Modifier.withoutCheckMarkWidth()` only when `showsCheckMarks && value == selected`. The selected segment's fill
   still marks it, as it does for inline rows.
4. Leave `isInline` as it is (it also narrows padding and gaps). An inline row (the editor's Edit / Preview / Split)
   is not wrapped and measures nothing: it never shows marks, so the branch is decided by the constant `isInline`, not
   by anything that changes between compositions.
   The measurement is `remember(labels, style, density) { labels.map { textMeasurer.measure(it, style).size.width.toFloat() } }`,
   where `labels = options.map { it.second }` (a new list every composition, but compared by equality, so it is measured
   again only when a label changes - the language, or a dynamic label) and `density` is `LocalDensity.current`, which
   changes with the system font scale. The app's own text size preference does not reach these rows.
   `BoxWithConstraints` is a `SubcomposeLayout`, which throws if a parent asks for its intrinsic size; no caller does
   at dac1d9d59 (the only `IntrinsicSize` in the callers' files is `Controls.kt`'s section title, not around a
   `SegmentedChoice`), and the KDoc of `SegmentedChoice` should say so for the next one.
5. Update the KDoc of `SegmentedChoice` and the `SegmentedChoice` entry in `presentation/CLAUDE.md` (the components
   list, "offering a selected label only the width its check mark leaves …"): add "and a row whose labels would not
   all fit beside a check mark has none, the fill alone marking the choice, so 'Keyboard' and 'Sharps (#)' are whole at
   360dp".

Alternative: drop the mark from every row of three or more segments — simpler, but loses it on wide windows where it
fits, and does not cover the export pane's two-segment rows. The measured, per-row rule is recommended.

## Tests

`SegmentedChoiceTest` in `presentation/src/commonTest/.../ui/components/` (runs with `:presentation:desktopTest`):
- three labels of 50, 60 and 70 in a 328-wide row with padding 24 and mark 26 → room 59.3 → `false`;
- the same labels in a 600-wide row → `true`;
- a label exactly at the room → `true`;
- an empty list → `true`.

## Manual check

On the 360 × 640 dp emulator, English and Hungarian: Settings → Songs, select Sharps (#) / Kereszt (#) and Keyboard /
Billentyű — whole labels, no check marks on those two rows; Settings → General theme row (System / Light / Dark) keeps
its marks if they fit. On a desktop window, the same rows show check marks. Export screen in landscape on the phone
(260dp options): Portrait / Landscape and a setlist's Song sheets / Running order are whole. On a desktop window of
500dp and more, a first launch's welcome `AlertDialog` (its Light / Dark / System row) opens without a crash. At a
system font size of 130%, rows that lose their marks lose them whole, never on one segment only.
