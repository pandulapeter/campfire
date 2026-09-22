# 30 · A setlist cannot be reordered with a screen reader or a keyboard

**Severity:** wrong behaviour, accessibility (all platforms. Certain for every TalkBack / VoiceOver user and for a
desktop or web user without a pointing device: the only way to change the order is a pointer drag) · **Area:**
`:presentation` (`ui/screens/setlists/SetlistsScreen.kt`, `ui/components/SongActions.kt`)

## Symptom
Open a setlist of several songs with TalkBack or VoiceOver on and try to move song 5 up to position 2. The grip is
announced as "Reorder", but activating it does nothing, and the row's menu (Edit, Setlist assignments, Update file
name, Export, Share, Delete) has nothing that moves the song. The only way round is to take songs out and put them
back in the order wanted. The same holds for somebody driving the desktop or web build from the keyboard.

## Cause
The only ways to reorder are the pointer gestures of Reorderable 3.1.0
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt:392`
`DragHandle(modifier = Modifier.draggableHandle(...))`, and `:404` / `:423` `Modifier.longPressDraggableHandle(...)`).
The library adds no semantics of its own, and nothing under `ui/` sets `customActions` (grep finds none). `DragHandle`
(`ui/components/ListItems.kt:396-406`) is a `Box` with an `Icon(contentDescription = "Reorder")`: a node a screen
reader lands on, with no action. `SetlistEntryActions` (`SetlistsScreen.kt:478-501`) offers `SongActionsButton`, or
for a missing file only "Remove from setlist".

Platform support was checked in Compose Multiplatform 1.12.0: `customActions` reaches TalkBack on Android and
VoiceOver on iOS (`ui-iosarm64` `Accessibility.ios.kt:254`, `accessibilityCustomActions`), but the desktop bridge
(`ComposeAccessible.kt:294-311`) only maps click, long click, expand, collapse and dismiss, and the web has no
screen reader bridge for it. So custom actions alone do not cover desktop and web; menu entries do, on every platform,
for pointer, keyboard and screen reader alike.

## Fix
Two entries in the row's overflow menu, **Move up** and **Move down**, and the same two as custom accessibility
actions on the row (which lets TalkBack / VoiceOver users move a row without opening the menu). Both call the existing
`CampfireViewModel.reorderSetlist` with the order shifted by one place, the same single write a finished drag does.

1. Strings, in the `<!-- Setlists screen -->` group after `setlists_reorder`:
   - `values/strings.xml`: `<string name="setlists_move_up">Move up</string>` and
     `<string name="setlists_move_down">Move down</string>`
   - `values-hu/strings.xml`: `<string name="setlists_move_up">Mozgatás feljebb</string>` and
     `<string name="setlists_move_down">Mozgatás lejjebb</string>`

2. Two drawables, `composeResources/drawable/ic_move_up.xml` and `ic_move_down.xml`, with the MPL header and the
   shape of `ic_drag_handle.xml` (24dp, viewport 24, `#FF000000`), path data (Material "arrow upward" / "arrow
   downward"):
   - up: `M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z`
   - down: `M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z`

3. `SongActions.kt`, `SongActionsButton`: add a parameter after `lockedSetlistFileName`
   ```kotlin
   leadingItems: @Composable (select: (action: () -> Unit) -> Unit) -> Unit = {},
   ```
   documented in the KDoc: `@param leadingItems Entries that belong to the row rather than to the song, put before
   the song's own: moving a row of a setlist up or down.` Call `leadingItems(select)` as the first statement inside
   the `ActionsMenu` content, before the `songFileNamesInSetlists` collection's first use (right after the
   collection line is fine).

4. `SetlistsScreen.kt`:
   - Hoist the rows: before `items(` at `:349`, `val rows = setlistWithSongs.rows(draggedSetlist)`, and pass
     `items = rows`. The order the moves work from is the one on screen, which is the dragged order while a drag's
     write is still round tripping.
   - Inside the item, after `isReorderable`, work out the two moves:
     ```kotlin
     // The drag written as two steps, for whoever cannot drag: a screen reader, a keyboard. Each is the same single
     // write a finished drag makes, and a row with nowhere to go in a direction is offered no step that way.
     val songFileNames = rows.map { it.entry.songFileName }
     val onMoveUp = songFileNames.movedOnePlace(entry.songFileName, by = -1)?.takeIf { isReorderable }?.let { order ->
         { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = order) }
     }
     val onMoveDown = songFileNames.movedOnePlace(entry.songFileName, by = 1)?.takeIf { isReorderable }?.let { order ->
         { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = order) }
     }
     val moveUpLabel = stringResource(Res.string.setlists_move_up)
     val moveDownLabel = stringResource(Res.string.setlists_move_down)
     val moveActions = Modifier.semantics(mergeDescendants = true) {
         customActions = listOfNotNull(
             onMoveUp?.let { CustomAccessibilityAction(moveUpLabel) { it(); true } },
             onMoveDown?.let { CustomAccessibilityAction(moveDownLabel) { it(); true } },
         )
     }
     ```
     (`mergeDescendants` so that a missing song's row, which has no `clickable` of its own, is still one node a screen
     reader lands on; a present row is merged by its `combinedClickable` already.)
   - Put `moveActions.then(...)` in front of both `longPressDraggableHandle` modifiers (`:404`, `:423`):
     `modifier = moveActions.longPressDraggableHandle(enabled = isReorderable, ...)`.
   - Pass `onMoveUp` / `onMoveDown` to `SetlistEntryActions(...)` and add the two parameters there
     (`onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?,`, KDoc: "Null where the row cannot move that way, or
     cannot be moved at all."). Its body renders the entries through a private helper:
     ```kotlin
     /** The two steps a row of a setlist can be moved by without a drag, for as far as it can go each way. */
     @Composable
     private fun MoveMenuItems(
         select: (action: () -> Unit) -> Unit,
         onMoveUp: (() -> Unit)?,
         onMoveDown: (() -> Unit)?,
     ) {
         onMoveUp?.let { onClick ->
             ActionsMenuItem(
                 title = stringResource(Res.string.setlists_move_up),
                 icon = painterResource(Res.drawable.ic_move_up),
                 onClick = { select(onClick) },
             )
         }
         onMoveDown?.let { onClick ->
             ActionsMenuItem(
                 title = stringResource(Res.string.setlists_move_down),
                 icon = painterResource(Res.drawable.ic_move_down),
                 onClick = { select(onClick) },
             )
         }
     }
     ```
     `Present` → `SongActionsButton(..., leadingItems = { select -> MoveMenuItems(select, onMoveUp, onMoveDown) })`;
     `Missing` → call `MoveMenuItems(select, onMoveUp, onMoveDown)` before the "Remove from setlist" entry.
   - The helper, next to `rows`:
     ```kotlin
     /** This order with [songFileName] one place further along it ([by] = 1) or back ([by] = -1), or null where it has no room to go. */
     private fun List<String>.movedOnePlace(songFileName: String, by: Int): List<String>? {
         val from = indexOf(songFileName)
         val to = from + by
         return if (from < 0 || to !in indices) null else toMutableList().apply { add(to, removeAt(from)) }
     }
     ```
   - Imports: `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.customActions`,
     `androidx.compose.ui.semantics.semantics`, and the resources `setlists_move_up`, `setlists_move_down`,
     `ic_move_up`, `ic_move_down`.

Do not put the actions on `DragHandle`: it is not there on a one song setlist or in performance mode, and a screen
reader user reads the row, not its grip. Two quick moves before the first write lands both work from the order on
screen, so the second one moves the row to the same place; nothing is lost and a third step moves it on.

## Tests
None (UI).

## Verify
1. Android with TalkBack: in a setlist of four songs, focus the second row, open the actions (three-finger tap or the
   actions menu) and pick "Move up": it becomes first, stays focused, and the numbers update. The first row offers
   only "Move down", the last only "Move up", a one song setlist neither.
2. iOS VoiceOver: swipe up/down on a row to reach the actions, same results.
3. Desktop and web, keyboard only: Tab to a row's overflow button, Enter, arrow to "Move down", Enter.
4. The same entries in the overflow menu by pointer on every platform; a missing file's row offers them above "Remove
   from setlist". In performance mode none of them appear. A drag still works and still writes once.
5. Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`,
   `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `:app:web:wasmJsBrowserDevelopmentRun` (or its compile task).

## Docs
`presentation/CLAUDE.md`, in the bullet "**A drag is answered in the frame it is reported and written once when it
ends.**", append: "A row can also be moved one place at a time, for whoever cannot drag: Move up / Move down entries
in its overflow menu and the same two as custom accessibility actions on the row, each one the same single
`reorderSetlist` write, worked out from the order on screen and offered only in a direction the row can go."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActions.kt`
- `presentation/src/commonMain/composeResources/drawable/ic_move_up.xml` (new)
- `presentation/src/commonMain/composeResources/drawable/ic_move_down.xml` (new)
- `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`
- `presentation/CLAUDE.md`

## Depends on
Nothing.
