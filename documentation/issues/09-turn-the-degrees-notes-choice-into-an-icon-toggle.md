# Turn the scale sheet's Degrees / Notes choice into one icon toggle, so the title has most of the header's width on a phone

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all (worst on phones)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/ScaleSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/sheet/SheetHeader.kt` and
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/sheet/CampfireBottomSheet.kt` (option B only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/sheet/CLAUDE.md` (option B only),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/AppTestNavigation.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/ScaleSheetTitleTest.kt` (new)

**Challenged:** amended — the cell is matched without `hasClickAction()` (the cell's `clearAndSetSemantics` wipes the `OnClick` its `Surface` adds, so that matcher finds nothing at HEAD or after the fix); the UI test now scrolls the Scales grid to its search field before typing (at HEAD the field sits ~568 dp down, under the 80 dp navigation bar, so the result cell it reveals would be under the bar and the tap would land on the bar); the `openScales()` helper is shared with plan 10 only (plan 11 does not use it), and whichever of 09 and 10 lands first adds it.

**Decision:** taken 2026-10-19 — the user chose C, one icon toggle in the header, against the recommendation (A). B stays as the second step, since a 48 dp toggle still leaves the longest titles short of room.

## Problem

The scale sheet puts its Degrees / Notes choice in the header, beside the close button. In `ScaleSheet.kt` (HEAD):

```kotlin
CampfireBottomSheet(
    title = scaleTitle(scale, rootName, notation),
    subtitle = shownScaleNoteNames(scale, rootName, notation).joinToString(" "),
    // A keyboard carries no labels, so only a neck has the choice: the subtitle names a keyboard's notes.
    actions = if (geometry is ScaleDiagramGeometry.Fretboard) {
        { ScaleLabelsChoice(labels = state.labels, onLabelsSelected = { state.labels = it }) }
    } else { null },
```

```kotlin
private fun ScaleLabelsChoice(...) = SegmentedChoice(
    modifier = Modifier.padding(end = 12.dp).width(IntrinsicSize.Max),
    ...
    shouldApplyPadding = false,
    isInline = true,
    onSelected = onLabelsSelected,
)
```

`SheetHeader` (`dialogs/sheet/SheetHeader.kt`) is a `Row` with this layout: a 48 dp `IconButton`, a `Column(Modifier.weight(1f))` with the title (`titleMedium`, `maxLines = 1`, ellipsized) and the subtitle (`bodyMedium`, `maxLines = 1`), and then the actions. The actions are set in 16 sp (`actionTypography`). The row is padded `start = 4.dp, end = 4.dp` when there are actions and `end = 16.dp` when there are none.

The inline `SegmentedChoice` is about 150 dp wide at 16 sp: "Degrees" is ~74 dp and "Notes" ~58 dp with `INLINE_CONTENT_PADDING`'s 6 dp on each side, plus the 12 dp end padding that commit 59acf3e03 ("Improve segmented control alignment", a two-line change to `ScaleLabelsChoice`'s modifier) added. On a 360 dp phone, the title's column gets roughly 360 − 4 − 48 − 150 − 4 ≈ 154–158 dp, which is about 17 characters of `titleMedium`. The header then cuts these titles short:

- "C Phrygian dominant", "D♭ Bebop harmonic minor", and "C Dominant diminished (half–whole)";
- the chromatic scale's 12-note subtitle ("C D♭ D E♭ E F G♭ G A♭ A B♭ B"), and every Latin-notation subtitle longer than about 20 characters.

At a font scale of 1.5–2.0, the segmented row alone takes most of the width, and the title is reduced to an ellipsis. Nothing else in the sheet names the scale. The diagram's content description does, but only a screen reader hears it.

The root `CLAUDE.md` reserves the header for finishing actions ("Save, Create, Done, Delete, sorting, and Add link sit in the sheet header"). A view toggle that only changes the diagram's labels is not a finishing action. It is a set-once control, and the pinned-height rule says those scroll with the content.

Note on short windows: `CampfireBottomSheet` scrolls the header with the content only when `isCompactKeyboard = windowHeight < SHORT_WINDOW_HEIGHT && isKeyboardVisible`. The scale sheet has no text field, so its header stays pinned in every window, short ones included. Moving the choice into the content puts it in the sheet's own scrolling `Column`, which already scrolls in every window. Nothing special is needed for the 480 dp short window.

## Fix

The user's choice is **C: one icon toggle in the header** in place of the segmented row; **B (two-line titles for the
scale sheet)** follows as a second step in the same plan, since the toggle still takes 48 dp from the title (360 − 4 −
48 − 48 − 16 ≈ 244 dp, about 27 characters of `titleMedium` at font scale 1, short of "C Dominant diminished
(half–whole)" at about 34 and of every Latin chromatic subtitle).

Steps for C:

1. Two 24 dp vector drawables with the licence header in `composeResources/drawable/`: `ic_scale_degrees` (the digits
   1 2 3 drawn as a glyph) and `ic_scale_notes` (the letters C D E), in the style of the existing tool icons
   (`ic_scales.xml`, `ic_chords.xml`), each loaded with `painterResource`.
2. In `ScaleSheet`, replace `ScaleLabelsChoice` in the header's `actions` with an `IconButton` that flips `state.labels`
   between `ScaleLabels.DEGREES` and `ScaleLabels.NOTES`, shown only for a `ScaleDiagramGeometry.Fretboard` (the keyboard
   carries no labels, the existing comment moves onto this condition). The button shows the icon of the mode it switches
   **to**, crossfaded (`Crossfade` on `state.labels`, the user-caused change animated), and carries
   `contentDescription` from two new strings in both locales, `scales_labels_show_notes` ("Show note names" /
   "Hangnevek mutatása") and `scales_labels_show_degrees` ("Show degrees" / "Fokok mutatása"), plus
   `semantics { stateDescription = <the current mode's label, the existing scales_labels_degrees / scales_labels_notes> }`
   so a screen reader hears what is shown now and what the tap does.
3. Delete `ScaleLabelsChoice` and the `SegmentedChoice` import, and the `IntrinsicSize` / `width` imports it needed;
   `scales_labels_degrees` and `scales_labels_notes` stay (the state description uses them).
4. `ScaleSheetDiagram`'s KDoc ("which crossfade into each other as the header's choice changes") stays true.
5. In `dialogs/scales/CLAUDE.md`, change "picked by a segmented choice in the header; the keyboard carries none, and its
   header has no choice" to "flipped by one icon toggle in the header, showing the mode it switches to; the keyboard
   carries none, and its header has no toggle".

Steps for B:

- Add `titleMaxLines: Int = 1` to `CampfireBottomSheet` and `SheetHeader`, documented as what a sheet without a text
  field may raise, since no keyboard budget counts its header; `ScaleSheet` passes `2`. Every other sheet keeps one line.
- Add one line to `dialogs/sheet/CLAUDE.md`: a sheet with no text input may wrap its title to two lines.

## Tests

Add a UI test, `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/scales/ScaleSheetTitleTest.kt`, in the `presentation.ui.dialogs.scales` package.

First, add a shared helper to `AppTestNavigation.kt`, which plan 10 reuses (whichever of the two lands first adds it; the other uses it as it is):

```kotlin
/** Opens the Scales tab of a phone, behind the Tools item of its navigation bar. */
internal fun AppTestScope.openScales() {
    tap(hasText(string(Res.string.tools)) and hasClickAction())
    tap(hasText(string(Res.string.scales)) and hasClickAction())
}
```

The family row's "Scales" title is not clickable, so `hasClickAction()` picks the tab.

Test `` `a phone's scale sheet names a long scale in full` ``, with `runAppTest(window = TestWindow.PHONE)`:

1. Call `openScales()`.
2. Bring the search field to the top of the grid, then type the scale's name into it:
   `onNode(hasScrollToIndexAction() and hasAnyDescendant(hasSetTextAction())).performScrollToIndex(PINNED_SEARCH_INDEX)`
   (`presentation.ui.components.PINNED_SEARCH_INDEX`, internal but visible to this module's tests as `EDGE_FADE_SIZE`
   is to `PedalCoverageTest`), `waitForIdle()`, then
   `onNode(hasSetTextAction()).performTextInput(string(Res.string.scale_dominant_diminished))` and `waitForIdle()`.
   - Without the scroll the test depends on plan 10: at HEAD the controls are ~490 dp tall on a 360 × 640 phone, so the
     field starts ~568 dp down, under the navigation bar (which starts at 560 dp); the one result cell would be composed
     below it, and `tap` clicks at a node's centre, which would land on the bar or on nothing.
3. Tap the cell by its description, which is `scales_diagram` ("%1$s: %2$s", the title then the notes). Use
   `val title = string(Res.string.scales_sheet_title, "C", string(Res.string.scale_dominant_diminished))` and
   `tap(hasContentDescription(string(Res.string.scales_diagram, title, ""), substring = true))`.
   - **No `hasClickAction()`**: `ScaleLibraryCell` sets `clearAndSetSemantics { contentDescription = … }` on the
     modifier it hands `Surface(onClick = …)`, and Material's `Surface` chains its `clickable` after that modifier.
     Compose collapses a node's semantics tail to head and a clearing node resets what the later ones set, so the cell has
     no `OnClick` in its semantics, and the matcher would find nothing. `tap` clicks by touch at the node's centre, which
     works. (Reported separately as a finding; if that is fixed first, the matcher may take `hasClickAction()` back.)
   - The `"title: "` prefix rather than the bare title, so the matcher cannot also take a longer name starting with it.
4. Find the title node: `onNode(hasText(title) and isHeading())`. Read its layout as `OnboardingFooterTest` does, through `config[SemanticsActions.GetTextLayoutResult]`.
5. Assert `!layout.isLineEllipsized(layout.lineCount - 1)`.
6. Assert the choice is in the sheet: `onNodeWithText(string(Res.string.scales_labels_notes)).assertExists()`.

If B is taken, add `` `a large text size still names the scale in full` ``. It is the same test with `appearance = TestAppearance(textScale = 2f)` and the same assertion; the title may now take two lines.
