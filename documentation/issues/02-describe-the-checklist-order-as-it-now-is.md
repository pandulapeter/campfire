# Describe the picker chips' and the multi-select plan's order the way `ChecklistOrder` now orders

**Challenged:** sound

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt` (KDoc of
`PickerFilters`), `documentation/plans/multi-select.md`

## Problem

Commit 9d2f40947 changed `ChecklistOrder` (`ui/components/ChecklistOrder.kt`): the group is the rows checked at the
last refresh (`heldKeys`) in the list's own order; a later tick joins the end of the group as a copy, in tick order,
while its row stays where it was tapped (`selectedGroup` / `remainingRows`); and a sideways chip row, which has no room
for copies, uses

```kotlin
/** A sideways row of chips has no room for copies: the held chips lead it, and a chip that is tapped stays put. */
fun <T> ordered(items: List<T>, key: (T) -> String): List<T> {
    val (leading, remaining) = items.partition { key(it) in heldKeys }
    return leading + remaining
}
```

Two texts still describe the old "newest tick first" order:

1. `PickerFilters`' KDoc in `ui/dialogs/Dialogs.kt`:
   > Selected chips lead each row, newest first, with an animated scroll to the start. Deselected chips remain in
   > that group until search or sorting changes.

   A tapped chip no longer moves at all, and `SortableChipRow` (`ui/components/Controls.kt`) only animates to the
   start when its refresh key changes: "Opening/restoring the row and selecting a chip leave its scroll position
   alone; refreshing animates to the start." The chips' refresh key is the query and the sorting
   (`chipRefreshKey = query to userPreferences?.sortingMode`, plus the group's own label sorting mode).

2. `documentation/plans/multi-select.md` (a future feature's plan):
   - §1 item 3: "a **Selection** section on top, the newest tick first, an unticked song staying there until the
     search, the sorting or a filter next changes."
   - "What is already there": "ticked and recently unticked keys share a leading group, a new tick goes before all of
     it, and a refresh key reseeds the group in the list's own order."
   - §3.1: the Selection section is built "in `ChecklistOrder.ordered`'s order". With today's `ordered` a ticked card
     would not reach the section until the next refresh, which contradicts the plan's own decision 2 ("A ticked card
     moves to the Selection section at once").

## Fix

1. Replace the paragraph of `PickerFilters`' KDoc with, in the file's voice:
   "The chips selected when the search or the sorting last changed lead each row, in its sorting order, and a divider
   separates them from the rest. A chip tapped since stays where it is — a sideways row has no room for a copy — and
   joins them at the next change, as one deselected there stays among them until then ([ChecklistOrder.ordered]). Only
   that change sends the row back to its start; each row starts with the toggle that switches its order
   ([SortableChipRow])."
2. In `multi-select.md`:
   - §1 item 3: "a **Selection** section on top — what was ticked when the search, the sorting or a filter last
     changed, in the list's order, and every later tick after it in the order it was ticked — an unticked song staying
     there until the next such change."
   - "What is already there": "the keys ticked at the last refresh lead in the list's own order, a later tick joins
     the end of the group and an untick leaves its row there until the next refresh (`selectedGroup`); the choosers
     also keep a ticked row where it was tapped (`remainingRows`), and a chip row, with no room for copies, leaves it
     there until the refresh (`ordered`)."
   - §3.1: "in `ChecklistOrder.selectedGroup`'s order, and taken out of their sections — not left in place as
     `remainingRows` leaves them, since here the card moving is the signal (decision 2)". Note that the sections then
     hold the songs whose keys are in neither `heldKeys` nor `addedKeys`, which `ChecklistOrder` has no function for
     yet; the plan's implementer adds one (or filters by `selectedGroup`'s keys) with its test.
   Leave the plan's "Written … against `3160dc997`" line as it is.

## Tests

None: documentation only.

## Manual check

None on a device. Read the new `PickerFilters` KDoc against Choose songs: with a tag chip selected, reopen the sheet
(it leads the row), tap another chip (it stays where it is, the row does not scroll), type a letter (both lead, the row
back at its start).
