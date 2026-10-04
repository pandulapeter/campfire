# Narrow the inline segmented buttons' side padding so the editor's "Preview" segment is not cut to "Previ…" on a 360dp phone

**Kind:** layout  ·  **Severity:** low  ·  **Platforms:** all (seen on Android at 360dp)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SegmentedChoice.kt, presentation/CLAUDE.md

**Challenged:** sound — `isInline = true` has the editor as its only caller; the `SegmentedButton` overload used takes `contentPadding` in material3 1.12.0-alpha03.

## Problem

Live run, scratchpad live/15_created.png (360×640dp): the editor's control row — the transposition stepper, the Edit / Preview segments and the Shortcuts chevron — leaves the segmented row about 158dp, so each segment is ~79dp and the label reads "Previ…". In Hungarian the labels are longer still (`Szöveg`, `Előnézet`).

The segments are `SegmentedChoice(isInline = true, shouldApplyPadding = false)` (SongEditorScreen.kt:505–517). `SingleChoiceSegmentedButtonRow` gives every segment an equal weight, and Material's `SegmentedButton` pads its content by `SegmentedButtonDefaults.ContentPadding`, 12dp at each side (material3 1.12.0-alpha03, SegmentedButton.kt:611), so the label gets `segment − 24dp` ≈ 55dp. The inline form already drops the check mark ("the mark's 26dp is most of what a label has there on a phone", SegmentedChoice.kt:29–31), but not the padding. Material 1.12's `SingleChoiceSegmentedButtonRowScope.SegmentedButton` takes a `contentPadding` parameter, so this can be changed without a custom component.

## Fix

In `SegmentedChoice`, pass a narrower horizontal content padding where the choice is inline:

```kotlin
SegmentedButton(
    …
    contentPadding = if (isInline) INLINE_CONTENT_PADDING else SegmentedButtonDefaults.ContentPadding,
    …
)

/**
 * Inline, the segments share a row with other controls on a phone, and Material's 12dp at each side of the label is
 * what cut the editor's "Preview" short at 360dp. The vertical padding stays Material's, so the row keeps its height.
 */
private val INLINE_CONTENT_PADDING = PaddingValues(
    start = 6.dp,
    top = SegmentedButtonDefaults.ContentPadding.calculateTopPadding(),
    end = 6.dp,
    bottom = SegmentedButtonDefaults.ContentPadding.calculateBottomPadding(),
)
```

This gives each label 12dp more (~67dp at 360dp). Update the `isInline` KDoc to mention the narrower padding. `SegmentedChoice(isInline = true)` is used only by the editor's row (verify with a grep; if other inline uses exist, check them at 360dp too). Add a clause to the presentation/CLAUDE.md sentence that describes the editor's Edit/Preview/Split choice or `SegmentedChoice` (grep "check mark") saying the inline segments also pad their labels less.

**Drop condition / Decision:** before committing, check both languages at 360dp (English "Preview", Hungarian "Előnézet"). If either is still ellipsized with 6dp, do not go lower than 4dp; if it still does not fit, stop and report rather than shipping a different design — the remaining options are product choices (icon-only segments in `WindowSize.COMPACT`, mirroring how the Shortcuts toggle already drops its label there via `isLabeled = windowSize != WindowSize.COMPACT`; or a shorter Hungarian label such as `Nézet`).

## Tests

None: text measurement against Material's layout; this is checked on a device.

## Manual check

On the 360×640dp emulator, open any song in the editor: the segments read "Edit" and "Preview" in full; switch the app to Hungarian in Settings and check "Szöveg" and "Előnézet". On a tablet / desktop window the row (with Split) looks as before apart from slightly tighter labels.
