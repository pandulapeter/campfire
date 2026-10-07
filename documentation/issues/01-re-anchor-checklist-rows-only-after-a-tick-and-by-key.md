# Re-anchor the checklist's rows only when a tick grew the selected group, and find the anchor again by its key

**Challenged:** amended — the helper also returns null when the anchor's index would not change (a key added to the group that the list does not show, e.g. a sync putting the song into a setlist the search hides, would otherwise issue a no-op `requestScrollToItem` from last frame's layout info, possibly mid-drag), with a test; the arithmetic and every other case were confirmed by a throwaway probe test; a sync that changes the rows without a tick is deliberately left to the lazy list's own keyed anchoring.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChecklistOrder.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChecklistOrderTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt` (KDoc of `PickerList`
only, if its wording changes), `presentation/CLAUDE.md` (only if it ends up naming the rule)

## Problem

`KeepChecklistRowsInPlace` (`ui/components/ChecklistOrder.kt:162-175` at 1b26dfb94) takes any change of the leading row
count with unchanged `heldKeys` for a tick:

```kotlin
SideEffect {
    val delta = layout.leadingRowCount - previous.layout.leadingRowCount
    if (delta != 0 && layout.heldKeys == previous.layout.heldKeys) {
        // The layout info is still the last measured one, from before the group grew.
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { (it.key as? String)?.startsWith(ROW_KEY_PREFIX) == true }
            ?.let { anchor -> listState.requestScrollToItem(index = anchor.index + delta, scrollOffset = -anchor.offset) }
    }
    previous.layout = layout
}
```

and `ChecklistLayout`'s KDoc says "[heldKeys] changing is a refresh". It is not always: a refresh (a new query, sorting
or filter chip) reseeds the order with `ChecklistOrder(checkedKeys)` (`rememberChecklistOrder`), so when nothing was
ticked or unticked since the previous refresh the reseeded `heldKeys` is the same set. The narrowed or widened `items`
change how many held rows match, so `leadingRowCount` changes (`layout` counts `selectedGroup(items, key).size` and
the divider). A probe confirmed it: `ChecklistOrder(setOf("b", "d")).layout(listOf("a", "b", "c", "d"))` and the same
order's `layout(listOf("a", "b"))` have equal `heldKeys` and leading counts differing by one.

Every call site runs it right after `ScrollToStartWhenChanged`, which calls `listState.requestScrollToItem(0)` on the
refresh; `requestScrollToItem` only stores the position, so the later request from `KeepChecklistRowsInPlace` wins
(its own KDoc says it must be called last for exactly that reason). Call sites in `ui/dialogs/Dialogs.kt`: `PickerList`
(Choose songs, Choose setlists), the Manage tags list (`KeepChecklistRowsInPlace(listState, tagLayout)`) and the Manage
languages list (`KeepChecklistRowsInPlace(listState, languageLayout)`).

Result, with at least one row checked and nothing ticked since the sheet opened or the last refresh: typing into the
search, clearing it, changing the sorting or tapping a filter chip in Choose songs leaves the list away from its first
row — scrolled down by the number of selected rows that came back into the results when a query is cleared at the top
of the list, and mid-list (or at its end) when the list had been scrolled into the rest. In Choose songs a typed
non-empty query is then overridden by `revealRowsKey`'s `scrollToItem(1)`, but clearing the query and the chips are not.

Secondary (same code): `anchor.index + delta` assumes nothing changed among the rows outside the group. When a row is
added that is checked at the same time — Choose setlists' **New setlist**, which `createSetlistWithSong` creates with
the song in it, so it enters both the selected group and the rest of the rows at its sort position — the anchor lands
one row off wherever the new row sorts above it. A sync that changes the checked setlists together with the list does
the same.

## Fix

Make the decision a pure function of the two layouts, and resolve the anchor by key:

1. Give `ChecklistLayout` what the decision needs:
   ```kotlin
   internal data class ChecklistLayout(
       val heldKeys: Set<String>,
       val addedKeys: List<String>,
       val leadingRowCount: Int,
       val remainingKeys: List<String>,
   )
   ```
   filled in `ChecklistOrder.layout` (`addedKeys = addedKeys`, `remainingKeys = remainingRows(items, key).map(key)`;
   compute `remainingRows` once there and reuse it for the divider check).
2. Add a pure helper next to it, documented in KDoc:
   ```kotlin
   /** Where the row [anchorKey], at [anchorIndex] in [previous], has to be put to stay under the finger, or null when nothing has to move. */
   internal fun ChecklistLayout.anchorIndexAfter(previous: ChecklistLayout, anchorKey: String, anchorIndex: Int): Int? {
       if (heldKeys != previous.heldKeys || addedKeys.size <= previous.addedKeys.size) return null
       val previousPosition = previous.remainingKeys.indexOf(anchorKey)
       val position = remainingKeys.indexOf(anchorKey)
       if (previousPosition == -1 || position == -1) return null
       return (anchorIndex - previous.leadingRowCount - previousPosition + leadingRowCount + position).takeIf { it != anchorIndex }
   }
   ```
   Only a tick grows `addedKeys` while `heldKeys` stays: a refresh resets `addedKeys` to empty (or changes `heldKeys`
   where something was ticked or unticked since), so it can never pass the first check, and no refresh key has to be
   threaded into the four call sites. The first tick with nothing held (heading and divider appearing) passes, as it
   must. The new index is counted from where the checklist starts (`anchorIndex - previous.leadingRowCount -
   previousPosition`, which leaves items before it, like Choose songs' filter header, out of it) plus the anchor's new
   place among the remaining rows, so a row inserted above it is accounted for. The early return keeps the
   `indexOf` scans off every recomposition but a tick's. A result equal to `anchorIndex` is null too: a key can join
   `addedKeys` without being listed (a sync putting the song into a setlist the search hides), and re-requesting the anchor's own place from the last measured layout
   info would only fight a scroll in progress. An untick needs nothing: the unticked row stays in the group until the
   next refresh, so `leadingRowCount` does not change. A sync that changes the rows without a tick is not narrated
   (CLAUDE.md: data arriving is not animated over) and is left to the lazy list's keyed first-visible-item anchoring,
   as at 1b26dfb94 for every change but a leading-count one.
3. In `KeepChecklistRowsInPlace`, replace the `delta` logic:
   ```kotlin
   listState.layoutInfo.visibleItemsInfo
       .firstOrNull { (it.key as? String)?.startsWith(ROW_KEY_PREFIX) == true }
       ?.let { anchor ->
           layout.anchorIndexAfter(previous.layout, (anchor.key as String).removePrefix(ROW_KEY_PREFIX), anchor.index)
               ?.let { listState.requestScrollToItem(index = it, scrollOffset = -anchor.offset) }
       }
   ```
   and fix `ChecklistLayout`'s KDoc ("[heldKeys] changing is a refresh" → a tick is what grows [addedKeys] with the same
   [heldKeys]; a refresh never does) and the "Call it after…" paragraph, which stays true.

Rejected alternative: passing the refresh key into `KeepChecklistRowsInPlace` and skipping a composition where it
changed. It works but needs the key at four call sites, and the languages dialog's order key (`listOf(sortingMode,
query, appLanguageCode)`) already differs from its `ScrollToStartWhenChanged` key (`sortingMode to query`); it would
also leave the index arithmetic of the secondary problem as it is.

## Tests

In `ChecklistOrderTest`, using the existing `rows = listOf("a", "b", "c", "d")`:

- A refresh with nothing ticked is not re-anchored: `ChecklistOrder(setOf("b", "d")).layout(listOf("a", "b")) { it }
  .anchorIndexAfter(ChecklistOrder(setOf("b", "d")).layout(rows) { it }, anchorKey = "a", anchorIndex = 4)` is null
  (the two layouts have equal `heldKeys` and different leading counts — the case that used to move).
- A tick keeps the anchor: before `ChecklistOrder(setOf("b")).layout(rows)` (leading 3, rest a, c, d), after
  `.withCheckedKeys(setOf("b", "c")).layout(rows)` (leading 4); anchor "c" at index 5 with one header item before the
  list → 6.
- The first tick with nothing held: `ChecklistOrder(emptySet())` → `.withCheckedKeys(setOf("c"))`, anchor "a" at 0 → 3.
- A checked row arriving above the anchor (New setlist): before `ChecklistOrder(setOf("b")).layout(rows)`, after
  `ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "aa")).layout(listOf("a", "aa", "b", "c", "d"))`; anchor "c"
  at 4 → 6 (the old arithmetic gave 5).
- Ticking a row again that is already a copy in the group moves nothing: previous
  `ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "c")).withCheckedKeys(setOf("b"))`, current that
  `.withCheckedKeys(setOf("b", "c"))` → null.
- An anchor no longer listed → null.
- A key added that the list does not show moves nothing: previous `ChecklistOrder(setOf("b")).layout(rows)`, current
  `ChecklistOrder(setOf("b")).withCheckedKeys(setOf("b", "zz")).layout(rows)`, anchor "c" at 4 → null (without the
  `takeIf` it is 4).
- Update `layoutCountsTheHeadingTheGroupAndTheDivider` only if constructing `ChecklistLayout` changed its asserts.

Run `./gradlew :presentation:desktopTest`.

## Manual check

On any platform, open **Choose songs** for a setlist that already holds three songs, tick nothing: type a letter, then
clear the field — the list is at its first row (the "Selected" heading visible) both times. Tap a tag chip on and off —
first row again. Scroll well into the rest of the songs, then change the sorting — first row. Then tick a song in the
middle of the rest with the heading on screen: the tapped row stays under the finger as its copy joins the group.
Repeat the search/clear check in **Choose setlists**, **Manage tags** and **Manage languages** on a song that already
has some. In Choose setlists, scroll so a setlist is at the top, tap **New setlist** and create one whose name sorts
above it: the row that was at the top stays there.
