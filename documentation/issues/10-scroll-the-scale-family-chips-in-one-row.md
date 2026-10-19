# Put the Scales tab's family chips in one scrolling row, so a phone shows scales on the tab's first screen

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all (phones held upright, short windows)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScaleLibraryControls.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScaleFamilyRow.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScaleLibraryState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScalesScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/controls/ChoiceChipRow.kt` (KDoc; option A2 also changes its body),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScalesFirstScreenTest.kt` (new)

**Challenged:** amended — "a chip is only picked by tapping it" is false for the promo footage (`Promo.kt`'s `FAMILY_CHANGES` sets `familyIndex` to 5 and 6, Pentatonic and Blues and hexatonic, which a one-line row on the promo's iPhone frame leaves off screen); added a step for that and for the website shot, both release-time and in files another agent has uncommitted edits in. The cell matcher is fixed (the bare title with `substring = true` matched three Common cells, and `hasClickAction()` matches none, see plan 09). The test no longer assumes plan 09 landed first: it adds `openScales()` itself if absent.

**Decision:** awaiting the user. The recommendation is A1, with A2 as the step that gives a whole row of cells.

## Problem

`ScaleLibraryControls` stacks three settings blocks above the pinned search field and the heading. The family block is a wrapping `ChoiceChipRow`, a `FlowRow` of `SelectableChip`s, under a `SettingsSubsection` titled "Scales":

```kotlin
SettingsSubsection(title = stringResource(Res.string.scales)) {
    ChoiceChipRow(
        labels = listOf(stringResource(Res.string.scale_family_common)) + ScaleFamily.entries.map { stringResource(it.label) },
        selectedIndex = selectedFamilyIndex,
        onSelected = onFamilySelected,
    )
}
```

Heights from the code:

- A `SettingsSubsection` is 12 dp of padding, a `bodyLarge` title of 24 dp, `SUBSECTION_CONTROL_GAP` of 12 dp, the control, and another 12 dp.
- A chip is `FilterChipDefaults.Height` (32 dp) tall, 16 dp of padding plus its `labelLarge` label wide, with `CHIP_GAP` (8 dp) between chips and between lines.
- The grid's items are 8 dp apart (`CELL_GAP`).
- A cell is 8 + 24 (`titleMedium`) + 8 + 52 (`NECK_HEIGHT`) + 8 = 100 dp.

The chip widths below estimate `labelLarge` at about 7.3 dp per character, so confirm the totals with the test's bounds. At 360 dp, the chips get 328 dp per line:

| Block | Height |
|---|---|
| Instrument (title + 40 dp `PickerChip`s) | 100 dp |
| Key (12 chips, two lines) | 12 + 24 + 12 + 72 + 12 = 132 dp |
| Family: 10 chips on five lines ("Common"/"Major modes", "Harmonic minor modes", "Melodic minor modes"/"Other harmonic", "Pentatonic"/"Blues and hexatonic", "Symmetric"/"Bebop"/"World") | 12 + 24 + 12 + 192 + 12 = 252 dp |
| Search field (56 dp + 8 dp) | 64 dp |
| Heading | 32 dp |
| Gaps (3 × 8 dp) | 24 dp |
| **Total before the first cell** | **≈ 604 dp** |

Room under the tools' tab row (`TOOLS_TAB_ROW_HEIGHT`, 72 dp) and above an 80 dp navigation bar:

- In the UI test's 360 × 640 window (no system bars, `MIN_TOP_EDGE` of 4 dp): 640 − 4 − 72 − 80 = 484 dp.
- On a device with a status bar and a gesture bar: about 440–470 dp.

So a phone held upright opens the tab on controls only, and nothing says the tab has scales until it is scrolled. The Chords tab (`ChordLibraryControls`) has the same instrument and key rows but no family row, so it is not affected.

## Fix

Space before the first cell, in the test window (484 dp), on a device (~460 dp), and in a 480 dp tall rail window (no tool tabs, ~476 dp). Visible cell height is in brackets; a cell is 100 dp.

| Option | Controls + search + heading | 360 × 640 test | Device | 480 dp window |
|---|---|---|---|---|
| Today | 604 | none | none | none (two family lines at 560 dp wide) |
| **A1: family as one scrolling line, its "Scales" title dropped** | 408 | 76 | ~52 | 68 |
| A: A1 keeping the title | 444 | 40 | ~16 | 32 |
| A2: A1 + the key row scrolling too | 368 | 116 (a whole row) | ~92 | 108 |
| B: a `PickerChip` for the family opening a sheet | as A or A1 | as A or A1 | as A or A1 | as A or A1 |
| C: key and family side by side on wide windows only | 604 | none | none | none |

What each option means:

- **A1 (recommended).** The heading under the search already names the family ("Major modes in C", `scales_heading`), so the "Scales" title over the chips repeats it. Cells show their names and the top half of their necks.
- **A2.** The key row is shared with the Chords tab, which would then scroll its keys too, and the 12 keys need ~640 dp, so half are hidden. It needs care: a key picked on the other tab must be visible on this tab's first frame. Take it only if a whole row of cells on the first screen is worth that.
- **B.** Same height as A or A1, but every family change becomes a sheet round trip. Rejected.
- **C.** Does nothing for phones. Rejected.

None of these adds spacing. Per the "list screens are not made more spacious" ruling, the fix makes the controls smaller and adds no room around them.

Steps for A1:

1. **New `ScaleFamilyRow.kt`** in `ui/screens/scales`, holding `internal fun ScaleFamilyRow(modifier, labels, selectedIndex, scrollState, onSelected)`. Build it as `ScaleChordsRow` builds its horizontally scrolling chord row:
   ```kotlin
   Row(
       modifier = modifier
           .fillMaxWidth()
           .fadingHorizontalEdges(scrollState)
           .horizontalScroll(scrollState)
           .padding(horizontal = 16.dp)
           .selectableGroup(),
       horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
   ) { /* one SelectableChip per label, role = Role.RadioButton, as ChoiceChipRow draws them */ }
   ```
   - It fades at an end past which there are more chips, as `ToolsTabRow` does.
   - It is not used inside `SideBySideWhenFits`, so intrinsic widths do not matter.
   - It keeps `selectableGroup()` (the `ui/CLAUDE.md` accessibility rule) and its padding inside the scroll, so the first and last chips line up with the page.
2. **Keep the row's scroll for the session**, so a family picked far to the right is on screen again on return, on the first frame and without an animation. Add `val familyScrollPosition = ScrollPosition()` to `ScaleLibraryState` and read it in `ScalesScreen` with `rememberRetainedScrollState(state.familyScrollPosition)`.
   - On a first visit the row starts at 0, where the selected "Common" is.
   - In the app a chip is only picked by tapping it, so the selected chip is always on screen. The one other writer of
     `familyIndex` is the promo footage (see step 6).
   - Create the state in `ScalesScreen`, outside the grid's items, not inside the controls item: the item is disposed
     when the controls scroll out of the grid, and a state remembered in it would come back at 0.
   - Pass the scroll state down through `ScaleLibraryControls`.
3. In `ScaleLibraryControls`, replace the family `SettingsSubsection` with:
   ```kotlin
   ScaleFamilyRow(modifier = Modifier.padding(vertical = 12.dp), ...)
   ```
   The 12 dp is the subsection's own padding, kept so the row sits as far from the key row as it does today. If the 12 dp bottom then doubles with the search field's gap, use `top = 12.dp` only. Measure this in the test.
   - Drop the import of `Res.string.scales` if nothing else in the file uses it. The string itself stays, because it is the tab's name.
4. **`ChoiceChipRow`'s KDoc** says it serves "the Scales tab's families". Drop that clause.
5. **`screens/scales/CLAUDE.md`**: change "the family row, Common first" to "the family row, Common first, one line scrolling sideways (its scroll kept in `ScaleLibraryState`), with no title, since the heading names the family".
6. **Release-time, not part of this change's commit** (both files have another agent's uncommitted edits now):
   `tools/screenshots/.../Promo.kt`'s "scales" clip switches the family programmatically (`FAMILY_CHANGES`: 5, 6, 1).
   On a one-line row those chips are off screen, so the clip would show the grid change with no chip lit. When the
   promo is next regenerated (the `promo-video` skill), scroll the family row with each change or pick families whose
   chips are on screen. `Website.kt`'s scale shot (`scrollTo(string("scale_family_common"), SCALE_FAMILIES_Y)`) still
   finds "Common", but its framing changes; the `store-screenshots` skill retakes it.

For A2, if the user takes it:

- Make `ChoiceChipRow` a `Row` + `horizontalScroll` like step 1. `SideBySideWhenFits` reads `maxIntrinsicWidth`, which a scrolling `Row` answers with its content's width; a `LazyRow` would throw there, so do not use one.
- Each tab keeps its own key row scroll (`ChordLibraryState` and `ScaleLibraryState`).
- The selected key must be on screen on the first frame. Compute the initial offset from the selected index and the measured chip widths, never from a `LaunchedEffect`, which is a frame late (the first-frame rule).
- Update `screens/chords/CLAUDE.md` to match.

## Tests

Add a UI test, `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/ScalesFirstScreenTest.kt`. It uses the `openScales()` helper of `AppTestNavigation.kt` as plan 09 writes it (tap `Res.string.tools` and then `Res.string.scales`, each `and hasClickAction()`); if plan 09 has not landed, add the helper here, with that plan's KDoc, and add `AppTestNavigation.kt` to this plan's files.

Test `` `a phone shows scales on the Scales tab's first screen` ``, with `runAppTest(window = TestWindow.PHONE)`:

1. Call `openScales()` and do not scroll.
2. The first cell of the Common family is the major scale in C. Its node is
   `hasContentDescription(string(Res.string.scales_diagram, string(Res.string.scales_sheet_title, "C", string(Res.string.scale_major)), ""), substring = true)`,
   the description's "C Major: " prefix.
   - Not the bare title with `substring = true`: "C Major" is also the start of the Common family's "C Major
     pentatonic" and "C Major blues", so `onNode` would find three nodes and throw.
   - No `hasClickAction()`: the cell's `clearAndSetSemantics`, chained before its `Surface`'s `clickable`, leaves it no
     `OnClick` (see plan 09's test), so that matcher finds nothing.
3. Assert `assertIsDisplayed()` on it.
4. Read its bounds and the bounds of the navigation bar's Songs item (`hasText(string(Res.string.songs)) and hasClickAction()`). Assert that at least the cell's name is visible: the cell's top is at least 40 dp above that item's top.
5. If A2 is taken, assert instead that the whole cell is above the item: the cell's bottom is at or above the item's top.

The test fails today: the cell is not displayed.
