# Make the "For all of them" conflict choice show what its rows say, and set every row it shows

**Kind:** bug (UX)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReviewSections.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReviewItems.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReviewSectionHeader.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ConflictResolutionDropdown.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (the forwarding method, if it has its own signature),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReviewSectionsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportChoices.kt` (KDoc only)

## Problem

The Names already taken group's header shows the default for rows without a choice of their own, not what its rows will
do (`ImportReviewItems.kt`, at 72d48deb7):

```kotlin
val resolution by remember { derivedStateOf { choices().conflictResolution } }
ImportReviewSectionHeader(
    …
    resolution = resolution.takeIf { isConflicts },
    onResolutionSelected = { onConflictResolutionSelected(null, it) },
)
```

and choosing from it changes only that default (`ImportController.setImportConflictResolution`):

```kotlin
if (key == null) {
    choices.copy(conflictResolution = resolution)
} else {
    choices.copy(conflictResolutions = choices.conflictResolutions + (key to resolution))
}
```

So with one taken name set to **Replace** on its row, the header still reads **Keep both** although nothing in the group
will be kept (live run: `84-after-replace.png`). Worse, choosing Keep both from the header then does nothing visible:
the row keeps its own Replace, and the header already said Keep both. The tri-state tick box beside it, by contrast, shows
the state of the rows it heads and acts on every row it shows (`groupBoxState`, `setImportEntriesChosen`).

## Fix

Make the header's choice work like the header's box: it reads what the shown rows resolve to, and it sets them.

1. `ImportReviewSections.kt`, next to `groupBoxState`, two pure functions:

   ```kotlin
   /** What every row of [rows] with a box resolves to, or null where they differ (shown as Mixed). */
   internal fun groupConflictResolution(rows: List<ImportReviewRow>, choices: ImportChoices): ImportConflictResolution? =
       rows.mapNotNull { it.key }.map(choices::resolutionOf).distinct().singleOrNull() ?: choices.conflictResolution.takeIf { rows.none { it.key != null } }

   /**
    * [resolution] for every row of [keys], the rows the header shows. Unfiltered that is all of them, so it becomes
    * the default and the rows' own choices go; narrowed by a search, only the matching rows are set, by hand.
    */
   internal fun ImportChoices.withGroupResolution(keys: Collection<ImportEntryKey>, isFiltered: Boolean, resolution: ImportConflictResolution) =
       if (isFiltered) copy(conflictResolutions = conflictResolutions + keys.associateWith { resolution })
       else copy(conflictResolution = resolution, conflictResolutions = conflictResolutions - keys.toSet())
   ```

2. `ImportController`: split the null-key branch out into
   `fun setImportGroupConflictResolution(keys: Collection<ImportEntryKey>, isFiltered: Boolean, resolution: ImportConflictResolution) = updateChoices { it.withGroupResolution(keys, isFiltered, resolution) }`,
   make `setImportConflictResolution` take a non-null key, and update both KDocs (the old one says "which leaves the rows
   set by hand as they are"). Forward it through `CampfireViewModel` as the other import setters are.
3. `ImportReviewItems.reviewSections`: the header's `resolution` becomes
   `remember(section.rows) { derivedStateOf { groupConflictResolution(section.rows, choices()) } }`, and its
   `onResolutionSelected` calls the new setter with `keys` and `isFiltered`. Thread the new callback through
   `ImportReportScreen` in place of passing `null` as a key.
4. `ImportReviewSectionHeader` / `ConflictResolutionDropdown`: accept a nullable `resolution`; the header needs a separate
   flag for "this is the conflicts group" (today `resolution != null` means that). A null resolution shows
   `Res.string.import_conflicts_mixed` on the button and no check mark on either menu item.
5. Strings, after `import_conflicts_replace_description` in both files:
   `<string name="import_conflicts_mixed">Mixed</string>` and `<string name="import_conflicts_mixed">Vegyes</string>`.
6. Docs: in the import screen's `CLAUDE.md`, replace "and the group's header the choice for all of them, which leaves the
   rows set by hand alone" with "and the group's header the choice for all of them, which reads what the rows it shows
   come to (Mixed where they differ) and sets each of them, the search's matches only while it narrows them, as its box
   does". In `ImportChoices`' KDoc, keep the rule (the model still lets a row outlive a change of the default) but say
   that the review's header sets the rows it shows as well.

Considered: keeping "leaves the rows set by hand alone" and only changing what the header displays. Then the header
would read Replace (the only row says so) and picking Keep both from it would visibly do nothing, which is the bug again.

## Tests

In `ImportReviewSectionsTest`:

- `the group's choice is what every row comes to`: two conflict rows, no hand-set choices → `KEEP_BOTH`; one row set to
  `REPLACE` by hand and the other left → null; both set to `REPLACE` → `REPLACE`; a single row set to `REPLACE` → `REPLACE`.
- `choosing for the whole group drops the rows' own choices`: `withGroupResolution(allKeys, isFiltered = false, KEEP_BOTH)`
  on choices with a hand-set `REPLACE` → every key resolves to `KEEP_BOTH`, `conflictResolutions` empty.
- `choosing for a narrowed group sets only the matching rows`: `isFiltered = true` with one of two keys → that key
  resolves to the new value, the other to the old default.

The existing `ImportChoicesTest` cases stay as they are: the model's rule is unchanged.

## Manual check

Import an archive with one song whose name is taken. On the review, set the row to **Replace**: the "For all of them"
button reads Replace too. Pick **Keep both** from the header: the row follows. With two taken names, set one to Replace:
the header reads **Mixed**; pick Replace from it and both rows read Replace. Type a search that matches one of them, pick
Keep both from the header, clear the search: only the matching row changed, and the header reads Mixed.
