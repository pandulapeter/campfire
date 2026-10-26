# Shrink the import preview's card to one line and its buttons in a short window, with the page's choices behind a menu

**Kind:** UI · **Severity:** medium · **Platforms:** phones (any window under 480 dp tall)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ImportPreviewBar.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ImportPreviewChoicesMenu.kt` (new, if `ImportPreviewBar.kt` would pass its size limit),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ImportPreviewTest.kt`

**Challenged:** amended — the line takes up to two lines rather than one (a one-line taken-name or shared-update
sentence beside Import as a copy + Replace keeps ~290 dp on a phone on its side and was cut mid-sentence, contradicting
"nothing is lost"; two lines of `titleSmall` fit inside the 48 dp row the buttons and the choices button set, so the
card is no taller); the choices button lives inside the per-page crossfade so its dropdown is bound to the page it was
opened on; the line's own text change (a tick, Keep both ↔ Replace) crossfades, as every user-caused change does; the
test confirms Replace's dialog (`import_replace_title`, as `ImportReviewTest` does) and opens the row's preview the way
`ImportReviewTest` reaches the review.

**Decision (taken by the user, 2026-10-26): the compact card.** **Lane P, after plan 06.** Lane P owns both
`strings.xml` files (plan 09 is in it too).

## Problem

The preview's card (`ImportPreviewBar`) floats over the bottom of the pages and the pages leave its height free under
their last line (pedal rule, `previewBarClearance` in `SongDetailsScreen`). Over the review it holds, top to bottom: the
title row (`MessageTitle`: icon + "Preview: not in your library yet"), the page's message line, the Include checkbox row
(48 dp touch height), for a taken name the Keep both / Replace `SingleChoiceSegmentedButtonRow` in a `FlowRow` beside or
under it, and the buttons (`ImportPreviewButtons`) beside the message where it keeps `MIN_MESSAGE_WIDTH` (140 dp), else
under it; plus 12 dp vertical padding each side and `PREVIEW_BAR_MARGIN` outside.

```kotlin
is ImportPreviewPageChoice.NameTaken -> {
    MessageLine(stringResource(if (choice.isChosen) Res.string.import_preview_name_taken else Res.string.import_preview_left_out))
    FlowRow( ... ) {
        IncludeToggle(modifier = placement, isChosen = choice.isChosen, onChosenChange = { onChosenChange(page.key, it) })
        ResolutionChoice(modifier = placement, resolution = choice.resolution, onResolutionSelected = { onResolutionSelected(page.key, it) })
    }
}
```

The review measured about **130 dp** on a phone on its side (640 × 360 dp, under `SHORT_WINDOW_HEIGHT`, 480 dp) — about
half of what the app bar leaves for the song — and about **175 dp** on a 360 dp wide portrait phone with a taken name
(the buttons go under the message and the choices wrap). On its side, the song is read through a slot a few lines tall.

## Fix

Below `SHORT_WINDOW_HEIGHT` (read as `SongDetailsScreen` does:
`LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT`, inside `ImportPreviewBar`), the card is
**compact**: one row — the import icon, one line of text, a choices button where the page has choices, and the buttons
(`ImportPreviewButtons`, unchanged) at its end. Taller windows keep today's card exactly.

- **The line** (`maxLines = 2`, ellipsized, `titleSmall` as the full card's title: two lines are 40 dp, inside the
  48 dp the choices button and the 40 dp the buttons already make the row, so the card is no taller for them) says what
  the full card's message line would: for a single-song preview the name-taken / shared-update line
  where there is one, else the title; over the review, per `importPreviewPageChoiceOf`: `AlreadyInLibrary` →
  `import_preview_already_there`; `New` chosen → `import_preview_title`, left out → `import_preview_left_out`;
  `NameTaken` left out → `import_preview_left_out`, chosen → the new `import_preview_name_taken_choice` with the
  resolution's label (`import_conflicts_keep_both` / `import_conflicts_replace`), e.g. "Name taken: Keep both". The full
  sentence stays available to screen readers: keep the `liveRegion = Polite` semantics on the line.
- **The choices button**: over the review only, for `New` and `NameTaken` pages (none for `AlreadyInLibrary` or a
  single-song preview, whose choice is its buttons). An `OverflowMenu` (`ui/components/menu`: every overflow button opens
  a dropdown, entries that keep it open act directly) with an `IconButton` (`ic_tune`, content description the new
  `import_preview_choices`), holding: a `DropdownMenuItem` "Import this song" (`import_preview_include`) with a leading
  `Checkbox` (toggles, menu stays open); and for `NameTaken` two `DropdownMenuItem`s Keep both / Replace with a leading
  `RadioButton` (selects, menu stays open, so the effect on the line is seen). Same callbacks as the full card
  (`onChosenChange`, `onResolutionSelected`). Put it in `ImportPreviewChoicesMenu.kt` if `ImportPreviewBar.kt` (321
  lines at the review) would pass the code-style size limit (`FILE_LINES` in `.github/scripts/check_sizes.py`).
- **Layout**: reuse `MessageBesideOrAboveButtons` with the compact message (the icon, then the per-page
  `AnimatedContent` holding the line and the choices button in a `Row`, the line `weight(1f)` — the button inside the
  per-page content, so the dropdown's callbacks are the page's own and a page change closes it with the old page), so the buttons still go under the line where the line would keep less than `MIN_MESSAGE_WIDTH` — on a
  short window that is rare (it is wide), and the rule stays one.
- **Animation** (UI rules: user-caused changes animate, a resized window moves nothing abruptly): the switch between the
  full and the compact card is a crossfade with size (`AnimatedContent` keyed on `isCompact`, inside the existing
  `animateContentSize` Surface), and the page's line keeps its per-page crossfade (`contentKey = { it?.index }`); within
  a page, the line's text changing with a tick or Keep both / Replace crossfades too (`Crossfade` / `AnimatedContent` on
  the string). The
  height the pages clear already follows the card (`onHeightChanged` → `animateDpAsState`).
- Nothing else changes: the card's colour, width cap (`PREVIEW_BAR_MAX_WIDTH`), margins, and the buttons' rules.

**Strings**, in both `values/` and `values-hu/`, next to the other `import_preview_*` keys:

```xml
<string name="import_preview_choices">Choices for this song</string>
<string name="import_preview_name_taken_choice">Name taken: %1$s</string>
```
```xml
<string name="import_preview_choices">A dal importálási beállításai</string>
<string name="import_preview_name_taken_choice">A név foglalt: %1$s</string>
```

(The user may have uncommitted What's new edits in these files at execution time; the lane starts from a clean commit
and the merge keeps both sides word by word.)

**Doc**: in `screens/songDetails/CLAUDE.md`'s import preview section, after "Marked plainly": "In a short window
(`SHORT_WINDOW_HEIGHT`) the card is one line and its buttons; over the review the page's tick and Keep both / Replace
are in a dropdown behind its choices button, and the line names them." Keep the file under its budget (code-style).

## Tests

UI test in `ImportPreviewTest` (`tools/screenshots`), with `TestWindow.PHONE_ON_ITS_SIDE`: import two files where one
name is taken (the existing `collision` plus `newSong`) so the review opens, open the colliding row's preview, assert
the compact line ("Name taken: Keep both") is shown and "Import this song" is not until the choices button
(`import_preview_choices`) is clicked; pick Replace in the dropdown and assert the line now says Replace; then the
review's Import writes the library song over (as the existing collision test checks the copy). Use the existing helpers
(`pickFiles`, `waitForText`); open the review row's preview with a row tap (`ImportReviewTest` shows reaching the review with an archive or two
picked files). Replace needs its confirmation: after the review's Import, assert and confirm the dialog
(`Res.plurals.import_replace_title`, as `ImportReviewTest`'s `a confirmed replace overwrites the colliding file and
nothing else` does). Run
`./gradlew :tools:screenshots:test` and `:presentation:desktopTest`.
