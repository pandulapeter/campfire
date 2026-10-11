# Count the Choose setlists sheet's boxes against a hashed selection, once per pass

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SelectionSetlistsSheet.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistSongToggle.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistSongToggleTest.kt`

## Problem

The selection mode's Choose setlists sheet works out every setlist's box on every recomposition of the sheet — every
keystroke in its search field, every tap on a box, and every `setlists` or `userPreferences` emission — and it does so
against the selection as a `List<String>` (`SelectionSetlistsSheet.kt` at ab97d3105):

```kotlin
val selection = dialog.songFileNames
...
fun stateOf(setlist: Setlist) = selectionBoxState(
    selection = selection,
    held = heldOf(heldWhenTapped, setlist, selection),
    added = added[setlist.fileName].orEmpty(),
)
val checkedSetlistKeys = setlists.filter { stateOf(it) != ToggleableState.Off }.mapTo(mutableSetOf()) { it.fileName }
```

and every visible row calls `stateOf(setlist)` and `heldOf(heldWhenTapped, setlist, selection)` again. Both helpers
(`SetlistSongToggle.kt`) are linear in the selection per element:

```kotlin
internal fun heldOf(recorded: Map<String, Set<String>>, setlist: Setlist, selection: Collection<String>): Set<String> =
    recorded[setlist.fileName] ?: setlist.entries.mapNotNullTo(hashSetOf()) { entry -> entry.songFileName.takeIf { it in selection } }

internal fun selectionBoxState(selection: Collection<String>, held: Set<String>, added: Set<String>): ToggleableState {
    val count = selection.count { it in held || it in added }
    ...
}
```

`it in selection` on a `List` is a linear scan, so `heldOf` costs entries × selection per setlist, and
`selectionBoxState` walks the whole selection per setlist. With 5,000 songs selected (Select all on a large library)
and 200 setlists of 20 songs, one pass measured about 23 ms on the desktop JVM — more than a frame on every keystroke
of the sheet's search, several times that on a low-end phone — against 0.13 ms with a hashed selection.

## Fix

1. In `SelectionSetlistsSheet`, hash the selection once: `val selectionSet = remember(selection) { selection.toHashSet() }`
   (the dialog's list is fixed for the sheet's life). Keep `selection` (the list) wherever its order matters:
   `addSongsToSetlist`, `createSetlistWithSongs`, and the `added` record, which is
   `selection.filterNot { it in heldHere }.toSet()` and stays as it is.
2. Change the parameters of `heldOf`, `withHeldRecorded`, and `selectionBoxState` from `Collection<String>` to
   `Set<String>`, so that a list can no longer be passed by accident, and pass `selectionSet` at every call, the
   remembered `pickableSetlists` filter and the row's `withHeldRecorded` included.
3. Count the box over the small sets rather than over the selection:

   ```kotlin
   internal fun selectionBoxState(selection: Set<String>, held: Set<String>, added: Set<String>): ToggleableState {
       val count = held.count { it in selection } + added.count { it in selection && it !in held }
       ...
   ```

   Do **not** shorten this to `(held + added).size`: `held` and `added` are subsets of the selection as the sheet builds
   them (`heldOf` filters by it, `added` is `selection.filterNot { ... }`), but the existing test passes a `held`
   holding a name outside the selection (`held = setOf("a", "b", "x")` is expected to be `On` for a selection of two),
   and the function should keep answering that. The count equals the old one because the selection has no duplicates:
   it is `CampfireViewModel.selectionInListOrder()`, built from a `Set`. Compare `count` against `selection.size` (the
   set's), which is the list's size for the same reason.
4. Work every setlist's box out once per change rather than once per call site: a remembered map the filter and the
   rows both read,

   ```kotlin
   val boxStates = remember(setlists, selectionSet, heldWhenTapped, added) {
       setlists.associate { it.fileName to stateOf(it) }
   }
   val checkedSetlistKeys = remember(boxStates) { boxStates.filterValues { it != ToggleableState.Off }.keys }
   ```

   and the row reads `boxStates[setlist.fileName] ?: stateOf(setlist)`. Keep `checkedSetlistKeys` a `Set<String>` as
   `rememberChecklistOrder` expects. The row's `isEnabled` (`held.size < selection.size`) and its `onClick` keep
   calling `heldOf` for that one setlist, which is cheap once hashed.

Everything the sheet shows and writes stays the same: the same boxes, the same enabled states, the same writes in the
same order.

## Tests

Extend `SetlistSongToggleTest` (pure logic): keep its existing cases, switched to a `Set` selection, so the answers are
pinned unchanged, including the `held` that holds a name outside the selection. Add a case where `held` and `added`
overlap (a name in both counts once: `held = setOf("a")`, `added = setOf("a")` over a selection of `a` and `b` is
`Indeterminate`), and a case where `added` holds a name outside the selection (not counted).

## Manual check

On a phone with a large library (a few thousand songs) and many setlists: on the Songs screen, start the selection
mode, Select all, then open Choose setlists. Type quickly in its search field and tap several boxes: the field keeps up
with typing, the boxes tick at once, and every box shows the same state (ticked, empty, or half ticked) as before the
change, including after taking songs back out with a second tap.
