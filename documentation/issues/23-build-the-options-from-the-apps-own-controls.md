# Build the options from the app's own controls and spacing

**Kind:** UI  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDisplayControls.kt` (make `Stepper` `internal`, or move it to `ui/components/Stepper.kt`), both `strings.xml`
**Challenged:** amended — the bottom inset goes to the action row (a permanent row below the list cannot also have the list scroll under the bar); `Stepper` needs its own icons and labels, and margins need a snapping rule; keep `print_landscape` and add only `print_portrait`; Save moves with plan 21's crossfaded content.

## Problem

The sheet does not look like the rest of the app:

- Exclusive choices (paper, columns, setlist content, Preview/Options) are `FilterChip`s — the app's multi-select
  filter pills. Everything else uses `SegmentedChoice` (`components/SegmentedChoice.kt`).
- Text size and margins are the app's only `Slider`s; integer values elsewhere use the `Stepper` of
  `SongDisplayControls.kt` (minus / value / plus, with an optional reset).
- Section titles are bare `Text(style = titleSmall)` with no colour or spacing; `SettingsSectionTitle`
  (`components/ListItems.kt`) is the app's.
- Spinners are `CircularProgressIndicator`; the app uses `DelayedLoadingIndicator`.
- `CheckboxListItem`s sit inside a `LazyColumn` with 16 dp horizontal `contentPadding`, so checkboxes start at 32 dp
  while titles and chips start at 16 dp.
- The bottom inset is applied to the whole container (`.padding(padding)`) instead of as `contentPadding`,
  leaving a band of sheet colour above the navigation bar — `CampfireBottomSheet`'s KDoc asks for the latter.
- Save is a `TextButton` in the header's `actions` slot, which the KDoc reserves for things "such as the order of a
  list"; `CoverArtSearchSheet` puts its Save in a bottom action row.
- The hint text has no `onSurfaceVariant` colour.

## Fix

- `SegmentedChoice` for the four choices. Landscape joins Paper as a second `SegmentedChoice` "Portrait / Landscape":
  keep `print_landscape` ("Landscape" / "Fekvő tájolás") and add **one** string, `print_portrait` ("Portrait" /
  "Álló tájolás"), in both languages, so nothing is renamed. `SegmentedChoice` and `SettingsSectionTitle` pad
  themselves by 16 dp, so only the hint text (and anything else that is neither) needs the explicit 16 dp.
- `Stepper` for size (8–20) and margins (10–25 in steps of 5). It is `private` today and takes its icons and
  descriptions as parameters, so the sheet passes them: for the size `ic_text_decrease` / `ic_text_increase` with the
  existing `song_details_text_size_decrease` / `_increase`; for the margins `ic_subtract` / `ic_add` with two new
  strings (`print_margin_decrease` / `print_margin_increase`, both languages). `resetLabel = null, onReset = null`
  (as `TextTranspositionControls`), `isDefault = true`. The label goes on the left in a row built like
  `MenuStepperRow` (which already puts a label and a stepper on one line), at the sheet's 16 dp, not the menu's 12.
  The margin step **snaps**: increase goes to the next multiple of 5 above the stored value and decrease to the next
  one below, then both are clamped by `normalized()`, so a stored 12 steps to 15 / 10, not 17 / 7.
- `SettingsSectionTitle` for every section title; `DelayedLoadingIndicator` for the spinners; the hint in
  `onSurfaceVariant`; drop the list's horizontal content padding.
- **The bottom inset goes to the action row, not to the list**, exactly as `CoverArtSearchActions` does
  (`padding(contentPadding)` on the row): Save (and Share, plan 28) are a permanent row below the scrolling area, so
  the list has no bar to scroll under and giving it the inset as well would leave a double gap above the row. The list
  keeps an 8 dp content padding; the preview column keeps its 16 dp. Nothing pads the whole container.
- Move Save into that bottom action row, a filled `Button`, carrying plan 21's fixed-size crossfade for its content.
  `BottomSheetContentScope.close()` is only reachable inside the sheet's content lambda, so keep the row (and, for
  plan 27, the collector of the saved event) inside it, not in `actions`.

## Tests

None (UI).

## Manual check

Android with gesture and three-button navigation, desktop, web: the action row clears the navigation bar, controls
align on one start edge, the list does not end on a double gap, and the sheet reads like Settings. Margins stored as
12 mm step 10 / 15.
