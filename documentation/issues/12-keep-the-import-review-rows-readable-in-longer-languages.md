# Put the import review's Keep both / Replace choice under the row's text, and keep its counts and title whole

**Kind:** bug (layout, translations)  ·  **Severity:** medium  ·  **Platforms:** all (phones above all)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/review/ImportReviewRowItem.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/review/ImportReviewSectionHeader.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/checklist/CheckboxListItem.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`,
`presentation/src/commonMain/composeResources/values/strings.xml`, `presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/composeResources/values-ro/strings.xml`, `presentation/src/commonMain/composeResources/values-de/strings.xml`,
`presentation/src/commonMain/composeResources/values-es/strings.xml`, `presentation/src/commonMain/composeResources/values-fr/strings.xml`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReviewTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TextFit.kt` (from plan 10)

**Challenged:** amended — Select all keeps its box on its label's line with the choice on a line under both, a
`CheckboxListItem` with a control but no description still draws its supporting slot, the KDocs that place the
choice "at its end" are rewritten with the move, and the summary check shares the rows test's twelve runs.

**Runs after plan 10** in lane L (it uses plan 10's `TextFit.kt`).

**Decision (user):** where the choice stands. Recommended: always under the row's text (option A), which costs a
taken-name row the button's height on a wide window and gives its title the whole width on a phone.

## Problem

Live run, import review of a zip holding a new, an identical, and a colliding song, 360 dp
(`sheets/import-review-100.png`, `import-review-130.png`):

1. **The taken names' rows squeeze their text into a narrow column.** A song row (`ImportReviewSongRow` in
   `ImportReviewRowItem.kt`) is a `ListItem` whose trailing content is the choice and the chevron:

   ```kotlin
   trailingContent = {
       Row(verticalAlignment = Alignment.CenterVertically) {
           conflictChoice?.invoke()
           PreviewChevron()
       }
   },
   ```

   The choice is an `OutlinedButton` with the resolution's name (`ConflictResolutionDropdown`): English "Keep both"
   is ~110 dp with its padding and icon, Romanian "Păstrează ambele" and Spanish "Conservar ambos" ~160 dp, Hungarian
   "Maradjon mindkettő" ~175 dp. The title keeps what is left: four lines in English ("Traditional / American - /
   House of the / Rising Sun"), five in Romanian with ~73 dp ("Traditional / American / - House / of the / Rising
   Sun"). At 1.3 the column is a word wide.
2. **Select all** (`SelectAllRow` in `ImportReviewSectionHeader.kt`, the same choice for the whole group at its end):
   the label is a `weight(1f)` `Text` beside the button, so Romanian "Selectează tot" and Spanish "Seleccionar todo"
   wrap at 1.0 (109 dp left) and break mid-word at 1.3 ("Select/ează", "Selecci/onar"), German "Alle aus/wählen" too.
3. **A count alone on a line**: the summary card's lines wrap at their last space, which is the one before the number:
   German `import_review_summary_counts` "Zu importierende Songs: 2 · Setlists: / 0" at 1.0, English
   `import_review_name_taken_counts` "Names taken: Replace 0 · Keep both / 1" at 1.0 (`import-review-100.png`).
4. **Title cut at 1.3**: Spanish `import_review_title` "Revisar la importación" (≈299 dp at 1.3 `titleLarge` in a
   296 dp title) reads "Revisar la importaci…"; Hungarian "Importálás áttekintése" (≈299 dp) the same.

## Fix

### 1. The choice under the text (option A, recommended)

- `ImportReviewSongRow`: the trailing content is only `PreviewChevron()`. The supporting content becomes a `Column`:
  the description (`CrossfadedText`, as now) and under it `conflictChoice`, at the text's start, no padding added
  (memory: list screens are not to grow spacious; the button brings its own 40 dp height and 48 dp touch target).
- `CheckboxListItem` (the non-preview rows: setlists, a song with no preview): add a `supportingControl:
  (@Composable () -> Unit)? = null` to the `ToggleableState` overload, drawn under the description in the same way,
  documented like `trailingContent` ("takes its own taps rather than ticking the box"); `ImportReviewRowItem` passes the
  choice there instead of as `trailingContent`. The supporting slot is non-null where either the description or the
  control is (a taken name always has its "will be numbered / will replace" description today, but the component must
  not drop a control that comes without one). Check the other callers of `CheckboxListItem` are unaffected (the
  parameter defaults to null).
- `SelectAllRow`: the box and the label keep their own line, as now (the box beside its label, both centered in the
  48 dp line); the choice goes on a line under that one, starting where the label starts (`CHECKBOX_ZONE_WIDTH`-style
  indent: the row's 16 dp, the box, and the label's 16 dp), rather than at the row's end. The label gets the line's
  whole width, so it stays one line (`maxLines = 1` is not needed). The whole row, both lines, keeps its
  `triStateToggleable`; the button takes its own taps as now. Centering the box on a label-and-button column instead
  would leave it level with neither and out of line with the rows' boxes.
- The box's touch zone (`CHECKBOX_ZONE_WIDTH`, the row's whole height) is unchanged.
- KDocs that place the choice at the row's end change with it: `ImportReviewRowItem`'s ("a name already taken carries
  its own choice at its end" → "under its text"), `ImportReviewSongRow`'s ("the chevron at its end, after the name's
  choice where it is taken" → the chevron alone at its end), `ImportReviewSectionHeader`'s ("it also ends in the choice
  for all of them, where the rows have theirs" → "it also has the choice for all of them under its label, where the
  rows have theirs under their text"), and `SelectAllRow`'s ("The box, the text, and the end of the row at the places
  of a `CheckboxListItem`'s").
- `ui/screens/importReport/CLAUDE.md`, Review bullet: "a **Select all** row laid out as the rows are (and in the taken
  names' group ending in the Keep both / Replace choice for all)" → "(and in the taken names' group with the Keep both /
  Replace choice for all under its label, as each taken name's row has its own under its text, so a title keeps the
  row's width)".

**Option B:** keep the choice beside the text where the text column keeps at least ~160 dp, and under it below that.
These rows are items of a lazy list, where `animatePlacement` must not be used (`PlacementAnimation.kt`'s KDoc), so a
resize across the threshold could only crossfade the row; more code for a look only wide windows keep.

### 2. Counts that never stand alone (strings, every language)

In `import_review_summary_counts`, `import_review_name_taken_counts`, and `import_review_left_out_count`, make the
space right before each `%N$d` a no-break space (U+00A0), so a number always stays with its word, in `values`,
`values-hu`, `values-ro`, `values-de`, `values-es`, and `values-fr`. French already writes U+00A0 as the raw character
before its colons (107 in `values-fr/strings.xml`); write it the same way, raw, and keep French's own no-break spaces.
English: `Songs to import:␣%1$d · Setlists:␣%2$d`, `Names taken: Replace␣%1$d · Keep both␣%2$d`,
`Left out by you:␣%1$d` (␣ = U+00A0). Insert with a script (`perl -CSD -pi -e 's/ (%\d\$d)/\x{A0}$1/g'` limited to these
three keys) and check with `grep -c $'\xc2\xa0'`, since the character is invisible in a diff.

### 3. Titles (strings)

| File | Key | Now | New | Width at 1.3 |
|---|---|---|---|---|
| es | `import_review_title` | Revisar la importación | Revisar importación | ≈268 of 296 |
| hu | `import_review_title` | Importálás áttekintése | Import áttekintése | ≈245 of 296 |

(Hungarian is the user's own language; a better short title is welcome.)

## Tests

Extend `ImportReviewTest` (it already has the archive and `startImport`), with plan 10's `TextFit.kt`:

- `` `a taken name's title gets the row's width, with its choice under it, in every language` ``: for each of
  `APP_LANGUAGES` at text scale 1.0 and 1.3, `runAppTest(pickedFiles = listOf(archive), appearance = …)`,
  `startImport()`. The colliding song's headline is the only review text containing `RISING_SUN_TITLE`
  (`hasText(RISING_SUN_TITLE, substring = true)`, `useUnmergedTree = true`; the identical song is Home on the Range).
  Its choice is the lower of the two `import_conflicts_keep_both` texts (the other is Select all's). Assert the
  choice's top is at or below the headline's bottom, and the headline's layout `!breaksAWord`.
- In the same runs: the `select_all` label of the taken names' group is whole and on one line (`lineCount == 1`).
- In the same runs (twelve app starts in all, not twenty-four): for the `import_review_summary_counts` and
  `import_review_name_taken_counts` lines, no line of the layout (`getLineStart`..`getLineEnd`) is only digits and
  spaces (no-break ones included), so that no count stands alone on a line of the review's summary.
- The title: in Spanish and Hungarian at 1.3, the `import_review_title` text is whole.

All fail on HEAD for the languages named in the problem.
