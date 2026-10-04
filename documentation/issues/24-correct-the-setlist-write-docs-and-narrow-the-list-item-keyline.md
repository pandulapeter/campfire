# Name the view model's archived-setlist guard correctly, drop the stale "filters hide entries" explanation of reorderSetlist, and make LIST_ITEM_KEYLINE internal

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/CLAUDE.md, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt

**Challenged:** sound

## Problem

Three small inaccuracies at 800ebde0b:

1. **The archived guard is named wrong.** `presentation/CLAUDE.md`, the "An archived setlist is read only" bullet,
   ends "…and the view model refuses those writes for it (`editSetlist`, `updateSetlist`)." `updateSetlist` is the
   use case every setlist write goes through and must accept an archived setlist — `setSetlistArchived`
   (`CampfireViewModel.kt:2972-2975`) unarchives through it. The guard is `editSetlist`'s own check (`:2947`,
   `if (setlists.value.firstOrNull { it.fileName == setlistFileName }?.isArchived != false) return@launchLibraryChange`)
   and `updateEditableSetlist` (`:3024-3032`), which song assignments (`setSetlistSongs`), removal
   (`removeSongFromSetlist`), reordering (`reorderSetlist`) and the other entry writes (`:2225`, `:2911`) go through,
   reading the archived state inside the serialized update.

2. **The reorder is explained by filters that no longer hide anything.** The `reorderSetlist` KDoc
   (`CampfireViewModel.kt:3000-3008`) says:

   > [songFileNames] is what the screen was showing, which is not necessarily the whole setlist. The entries the
   > filters hide cannot be dragged and must not be moved by a drag that could not see them, so the visible songs
   > are dealt back into the slots visible songs already occupied and everything else stays exactly where it is.

   and `presentation/CLAUDE.md`'s "A drag is answered in the frame it is reported…" bullet says the order is written
   "dealing the visible songs back into the slots visible songs occupied so that entries the filters hide keep their
   places". But a setlist shows every song it names whatever the filters (root `CLAUDE.md`, "A setlist shows every
   song it names"; `setlistsWithSongs` reads `ScreenData.unfilteredSongs`), and a search shows a matching setlist
   whole. The dealing-back logic is still right and worth keeping; what it actually protects against today is an
   entry that reached the file after the screen's order was taken (a sync run, a song assignment landing between the
   drag and the write), which the drag never saw.

3. **`LIST_ITEM_KEYLINE` became public.** `ListItems.kt:1207-1210`:

   ```kotlin
   /**
    * The x position the text of a [ListItem] starts at, before the card's own outer inset.
    */
   val LIST_ITEM_KEYLINE = 16.dp
   ```

   It was `private` at f8634ddb6 and was opened up for `SortMenu.kt:112` and `ExportScreen.kt:1082`; every other
   cross-file constant in `:presentation` is `internal`, and nothing outside the module uses it.

## Fix

1. In `presentation/CLAUDE.md` replace "(`editSetlist`, `updateSetlist`)" with "(`editSetlist`, and
   `updateEditableSetlist` for every write to its entries, which reads the archived state inside the serialized
   update; `updateSetlist` itself stays open to it, since unarchiving goes through it)".

2. Rewrite the second paragraph of the `reorderSetlist` KDoc:

   ```kotlin
    * [songFileNames] is the order the screen was showing, which a sync run or another write may have overtaken by the
    * time this one runs: an entry the drag never saw must not be moved by it, so the songs it did see are dealt back
    * into the slots they already occupied and everything else stays exactly where it is.
   ```

   and in `presentation/CLAUDE.md` replace "dealing the visible songs back into the slots visible songs occupied so
   that entries the filters hide keep their places" with "dealing the songs the drag saw back into the slots they
   occupied, so that an entry that reached the file meanwhile keeps its place".

3. `internal val LIST_ITEM_KEYLINE = 16.dp`. Both users are in `:presentation`, so nothing else changes.

## Tests

None: documentation and a visibility modifier; the build compiling is the check.

## Manual check

None; `./gradlew :presentation:compileKotlinDesktop --offline` (or any target) still compiles.
