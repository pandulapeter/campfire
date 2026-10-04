# Give up a setlist's dragged order when the write that would have saved it fails or is refused, and when the setlist is archived

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/CLAUDE.md

**Challenged:** amended — the stale-drag guard compares by identity (`===`), since `DraggedSetlist` is a data class; added an ordering note for the KDoc and `presentation/CLAUDE.md` bullet plan 24 also edits.

## Problem

The setlists screen draws a drag's order from `draggedSetlist` until the library agrees with it
(`SetlistsScreen.kt:379-386` at 800ebde0b):

```kotlin
LaunchedEffect(setlistsWithSongs, draggedSetlist) {
    draggedSetlist?.let { dragged ->
        val songFileNames = setlistsWithSongs.firstOrNull { it.setlist.fileName == dragged.setlistFileName }?.entries?.map { it.songFileName }
        if (songFileNames == dragged.songFileNames || songFileNames?.toSet() != dragged.songFileNames.toSet()) {
            draggedSetlist = null
        }
    }
}
```

It is only let go of when the library matches the drag, or holds a different set of songs (which also covers the
setlist disappearing: `null != set`). Two outcomes of `CampfireViewModel.reorderSetlist` (`:3009-3021`) match neither:

- **The write throws.** `launchLibraryChange` (`:3324-3333`) catches it and shows `Message.OperationFailed`; nothing
  is written, so nothing re-emits and the effect never runs again.
- **The write is refused.** `updateEditableSetlist` (`:3024-3032`) returns the setlist unchanged when it is archived —
  which a sync run can do between the drag starting and the finger lifting. The archived setlist arrives with the same
  songs in the old order.

Either way the rows keep showing an order that was never saved until the screen leaves composition, numbered by slots
that are not the file's (and, for the archived case, in a read-only setlist whose reorder mode the
`reorderingSetlistFileName` effect at `:329-335` has already ended). `isRearranging = draggedSetlist != null` also
stays on for every row's placement animation.

## Fix

1. In the effect, also let go once the setlist is archived — an archived setlist is never reordered, so whatever was
   dragged will not be written:

   ```kotlin
   val setlist = setlistsWithSongs.firstOrNull { it.setlist.fileName == dragged.setlistFileName }
   val songFileNames = setlist?.entries?.map { it.songFileName }
   if (setlist?.setlist?.isArchived == true || songFileNames == dragged.songFileNames || songFileNames?.toSet() != dragged.songFileNames.toSet()) {
   ```

   Update the comment above the effect to say so.

2. Let the screen hear about a write that did not happen. Recommended: give `reorderSetlist` an `onNotWritten: () -> Unit
   = {}` parameter, called when `updateEditableSetlist` returned null or an archived setlist, or threw:

   ```kotlin
   fun reorderSetlist(setlistFileName: String, songFileNames: List<String>, onNotWritten: () -> Unit = {}) = launchLibraryChange {
       var isWritten = false
       try {
           isWritten = updateEditableSetlist(setlistFileName) { setlist -> /* unchanged */ }?.isArchived == false
       } finally {
           // The screen holds the dragged order until the library agrees with it, which a write that never happened
           // would leave it waiting for.
           if (!isWritten) onNotWritten()
       }
   }
   ```

   and in `onDragStopped` pass `onNotWritten = { if (draggedSetlist === dragged) draggedSetlist = null }` (capture
   `dragged` as the value handed to the call), so a later drag that started meanwhile is not cleared. Compare by
   identity: `DraggedSetlist` is a data class, and a second drag of the same setlist that happens to end on the same
   order is a different drag whose own write is still in flight. Every move builds a new instance, so identity tells
   drags apart. The Move up / Move down callers keep the default. Document the parameter in the KDoc as a new
   paragraph after the existing two. Plan 24 rewrites the second paragraph of the same KDoc and, in the same
   `presentation/CLAUDE.md` bullet as step 3, the "dealing the visible songs back…" sentence; both are in lane B, so
   whichever lands second keeps the other's text. A callback that runs after the screen left composition only writes
   a `remember`ed state nobody reads any more, which is harmless.

   Rejected: clearing on the returned `Job`'s completion regardless of outcome — the `setlists` state reaches the screen
   a moment after the write returns, so the rows would flash their old order for a frame, which is exactly what
   `draggedSetlist` exists to prevent.

3. In `presentation/CLAUDE.md`, the "A drag is answered in the frame it is reported…" bullet: after "gives it up only
   once the library agrees", add "— or once the write fails, is refused or the setlist is archived".

## Tests

None: the state lives in the composable and the view model's write path, neither of which is a pure helper.

## Manual check

On a device signed into sync with a second device: start Reorder songs on a setlist, and on the other device archive
that setlist and sync. Drag a row on the first device after its sync has landed (or just before, finger still down).
On lift the rows return to the file's order instead of keeping the dragged one. For the failure path, make the
library's setlists folder read-only on the desktop build (`chmod a-w …/library/setlists`), drag a row: the
"could not be written" snackbar appears and the rows go back to the saved order.
