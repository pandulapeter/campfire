# Make a tick's regrouping and card lookup avoid indexing and keying every song

**Challenged:** sound

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChecklistOrder.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongSelectionGroups.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/ChecklistOrderTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongItemIndexTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongSelectionGroupsTest.kt`

## Problem

Every tick in the songs screen's selection mode, and every tick in the Choose songs sheet, does a few whole-library
passes on the main thread that allocate per song where only the ticked songs matter (code at ab97d3105):

- `ChecklistOrder.selectedGroup` builds a map of every row to find the few rows added since the last refresh:

  ```kotlin
  fun <T> selectedGroup(items: List<T>, key: (T) -> String): List<T> {
      val byKey = items.associateBy(key)
      return items.filter { key(it) in heldKeys } + addedKeys.mapNotNull { key -> byKey[key]?.takeIf { key !in heldKeys } }
  }
  ```

  The songs screen calls it on a flattened copy of the whole list per tick
  (`SongSelectionGroups.kt`: `val selected = order.selectedGroup(flatMap { it.songs }) { it.fileName }`).
- In the sheets, `ChecklistOrder.layout()` calls `remainingRows`, `hasHeading` (which calls `selectedGroup` when nothing
  is checked) and `selectedGroup` again, and `checklistItems` calls `selectedGroup`, `remainingRows`, and `hasHeading`
  once more — two to three `selectedGroup` passes per tick, each with its whole-list map.
- `itemIndexOf` (`SongList.kt`), which `KeepCardsInPlaceUnderTick` calls on every tick to hold the first card in sight,
  builds the item key string of every song it scans:

  ```kotlin
  val songIndex = group.songs.indexOfFirst { group.itemKey(it) == key }
  ```

  where `SongGroup.itemKey` is `"copy_song_${song.fileName}"` or `"song_${song.fileName}"`. A card held deep in a
  library scans thousands of songs, each one a new string. `SongList`'s search scroll anchor
  (`groups.indexOfFirst { group -> group.songs.any { group.itemKey(it) == card.key } }`) does the same.

At 4,800 songs on the desktop JVM, the regrouping measured 0.35–1 ms and the card lookup 0.25–0.8 ms per tick, several
times that on a low-end phone, and all of it garbage for the collector.

## Fix

1. **`selectedGroup`**: one pass that keeps only the rows it needs, rather than a map of all of them:

   ```kotlin
   fun <T> selectedGroup(items: List<T>, key: (T) -> String): List<T> {
       val added = if (addedKeys.isEmpty()) emptySet() else addedKeys.toHashSet()
       val held = ArrayList<T>()
       val addedRows = HashMap<String, T>()
       for (item in items) {
           val itemKey = key(item)
           if (itemKey in heldKeys) held += item else if (itemKey in added) addedRows[itemKey] = item
       }
       return held + addedKeys.mapNotNull { addedRows[it] }
   }
   ```

   This keeps the old answers exactly: held rows in the list's order, then the added ones in `addedKeys`' order, a key
   in both left to the held part, and, for two rows with one key, the last one (as `associateBy` keeps the last).
2. **`layout()` and `checklistItems`**: work out `selectedGroup` and `remainingRows` once each and derive the heading
   from them (`checkedKeys.isNotEmpty() || group.isNotEmpty()`), rather than calling `hasHeading`, which runs
   `selectedGroup` again. Keep `hasHeading` as the public answer for other callers, implemented the same way.
3. **`itemIndexOf`**: compare without building strings. A key is a `String` with one of two known prefixes; strip it
   once, then match by file name and by whether the group holds the song as a copy:

   ```kotlin
   val text = key as? String ?: return null
   val isCopy = text.startsWith(SONG_COPY_ITEM_KEY_PREFIX)
   val fileName = when {
       isCopy -> text.removePrefix(SONG_COPY_ITEM_KEY_PREFIX)
       text.startsWith(SONG_ITEM_KEY_PREFIX) -> text.removePrefix(SONG_ITEM_KEY_PREFIX)
       else -> return null
   }
   ... val songIndex = group.songs.indexOfFirst { it.fileName == fileName && (it.fileName in group.copiedFileNames) == isCopy }
   ```

   (`"copy_song_"` does not start with `"song_"`, so the two prefixes cannot be confused; move both constants next to
   each other, or give `SongGroup` a `holds(key)` function that does this, and use it for the search anchor's
   `group.songs.any { group.itemKey(it) == card.key }` too.) A key of another kind (a header's, the divider's) answers
   null as today, since no song's key equals it.

Behaviour is unchanged: the same groups, the same rows in the same order, and the same item indexes.

## Tests

Pure logic, all pinned already; add cases for what the rewrite could get wrong:

- `ChecklistOrderTest`: a row whose key is in both `heldKeys` and `addedKeys` appears once, in the held part; two rows
  with one key added since the refresh yield the last one; `layout()` counts the heading, the group, and the divider the
  same as before when nothing is checked but the group still holds an unchecked row.
- `SongItemIndexTest`: a copy's key and its song's key in the same list resolve to their own items (the existing case),
  a copy key whose file name is not copied in any group is nowhere, a header key (`"header_..."`) and the divider's key
  are nowhere, and a non-`String` key is nowhere.
- `SongSelectionGroupsTest`: the existing cases keep the regrouping pinned; add one where a song is added after the
  refresh and the list holds it twice in two groups (an artist sorting never does, but the function must not care).

## Manual check

On a phone with a library of a few thousand songs, start the selection mode, scroll far down, and tick cards quickly:
the card under the finger stays exactly where it is, the copy joins the end of the Selection section, and the ticks
keep up with the taps. In a setlist's Choose songs sheet, tick rows quickly far down the list: the rows stay in place,
the heading counts what is ticked, and the divider stays under the group.
