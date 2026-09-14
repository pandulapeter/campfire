# 03 · A setlist naming the same song twice crashes the Setlists screen and the song pager

**Severity:** high (crash on every launch until the file is fixed by hand) · **Area:** `:data:source:local:implementation`, `:presentation`

## Symptom

A `*.setlist.json` whose `songs` array lists one file twice (hand edit, a foreign archive, a folder edited on another
machine and synced down) throws `IllegalArgumentException: Key "… #*# …" was already used` as soon as the Setlists tab
is shown, and the song pager opened from that setlist fails the same way.

## Cause

- `SetlistMappers.toModel` (`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SetlistMappers.kt:15`)
  filters blank entries only; its own comment admits a hand-edited document "can name the same song twice".
- `SetlistsScreen.kt:352–354` keys song rows by `SetlistItemKey(setlistFileName, songFileName)` and `ReorderableItem`
  at :362 uses the same key; `SongDetailsScreen.kt:318` keys pager pages by `songs[it].fileName`.
- The in-app picker deduplicates (`Dialogs.kt:966`, `.distinct()`), so only files reach this.

## Fix

1. **Deduplicate where the file is read.** In `SetlistMappers.toModel`, keep the first occurrence of each file name:

   ```kotlin
   entries = songs.filter { it.file.isNotBlank() }.distinctBy { it.file }.map { … }
   ```

   Update the comment above it: the second mention of a song is dropped, first one wins, its transposition with it.
   `parseSetlist` (imports) goes through the same mapper, so imported setlists are covered too.

2. **Make the keys survive anyway** (a setlist created in memory could still repeat a song through a bug elsewhere):
   in `SetlistsScreen`, build the rows from `setlistWithSongs.rows(draggedSetlist)` as today but key them by the
   entry's **index** within the setlist plus the setlist file name (`SetlistWithSongs.Entry` already carries `index`).
   Check that the reorderable state's key and the `listItemAnimation` keys are the same string, and that
   `reorderSetlist` still receives file names in the new order (it maps by file name; with duplicates removed in
   step 1 that stays unambiguous).
   In `SongDetailsScreen`, key the pager on `"$index|${songs[index].fileName}"`.

3. **Docs.** `data/source/local/implementation/CLAUDE.md`: one sentence that a setlist file naming a song twice is
   read as naming it once.

## Verification

- Add a unit test in `data/source/local/implementation/src/commonTest` for `toModel` with a duplicated entry (mapper
  functions are `internal`, tests in the same module see them).
- Manual: put a duplicate into a setlist file in the desktop library folder, open the Setlists tab, open the setlist's
  first song. No crash, the song appears once.
