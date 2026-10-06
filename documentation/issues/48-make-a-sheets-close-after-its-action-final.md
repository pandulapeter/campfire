# Make the close that follows a sheet's action final, so a second tap during the slide cannot keep the sheet open

**Kind:** bug (ux)  ·  **Severity:** low  ·  **Platforms:** all (reproduced on desktop with a double click; a second tap does the same on a phone)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/CLAUDE.md
**Challenged:** amended — the fallback for a final hide cut short no longer dismisses the sheet in one frame whatever cut it: the scrim's tap and back / Escape are not turned off by `sheetGesturesEnabled` (material3 1.12.0-alpha03 `ModalBottomSheet.kt:152-163`: the scrim's `onClick` depends only on `properties`, and back goes through `settleToDismiss`), and each starts a `sheetState.hide()` of Material's own that cancels this one; dismissing at once there would make the sheet vanish mid-slide where today Material's own hide finishes it smoothly. The fallback now waits a frame, lets a hide of Material's own run to its end, and otherwise hides again from where the sheet is (retrying once a frame while a press still holds it), dismissing when the sheet is gone or the scope is. Every `CampfireBottomSheet` caller was checked: only post-action closes reach `close` (no sheet keeps itself up to show a failed write, the sort menus, Add link, the cover search's Remove and the song info sheet's buttons never call it, and no picker row closes its sheet).

## Problem

Every sheet is a `CampfireBottomSheet` (`Dialogs.kt:2065`), and every way it closes itself goes through one `close`
(`Dialogs.kt:2122-2135` at 8ee010b36):

```kotlin
var isCloseRequested by remember { mutableStateOf(false) }
val isClosing = remember(sheetState) {
    { isCloseRequested || (sheetState.targetValue == SheetValue.Hidden && sheetState.currentValue != SheetValue.Hidden) }
}
val close = {
    if (!isCloseRequested) {
        isCloseRequested = true
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
            // A finger that took hold of the sheet keeps it, and its actions with it.
            if (cause == null) onDismiss() else isCloseRequested = false
        }
    }
    Unit
}
```

That one `close` is handed to three kinds of caller: the header's own close button (`SheetHeader(onClose = close)`),
the header actions (`actions = { close -> … }`, which `TextFieldBottomSheet` passes on as `confirmButton(close)`), and
the content (`BottomSheetContentScope(close = close)`). Taking the hide back when a finger grabs the sheet is right for
the close button — the draft is still there and Save still works — but not for a close that follows an action that has
already written. Material's sheet uses `Modifier.anchoredDraggable` (material3 1.12.0-alpha03 `BottomSheet.kt:329`),
which starts a drag immediately on any press while its state is animating, so a second press anywhere on the sheet
during the hide cancels `sheetState.hide()`; the sheet settles back to Expanded on release, `onDismiss` is never called,
`isCloseRequested` goes back to false and `visibleDialog` stays set.

Live, on the New song sheet (`NewSongDialog`, `Dialogs.kt:1222-1275`): type a title, double-click **Create** with no gap.
The first click runs `confirmOnce { onCreate(…); close() }` — the song is created and the editor pushed; the second press
lands on the sliding sheet and cancels the hide. The sheet stays over the new song's editor, and its Create is dead,
because `rememberSingleConfirmation` (`:929-937`) has already let its one confirmation through. Only the X or Escape gets
rid of it. Reproduced 3 of 3 with a zero-gap double click, never with clicks ≥ 120 ms apart.

Every sheet whose header action (or content) writes and then calls `close()` has the same hole:

| Sheet | Where (8ee010b36) | After the reverted close |
|---|---|---|
| New song | `Dialogs.kt:1231-1240`, `:1274` | stuck over the editor, Create dead (`confirmOnce`) |
| New setlist / Edit setlist / Duplicate setlist (`SetlistDetailsDialog`) | `:993`, `:1047-1055` | stuck open, Create/Save dead (`confirmOnce`) |
| Delete library | `:832-837`, `:890` | stuck open over an emptied library, Delete dead (`confirmOnce`) |
| Setlist date (the calendar sheet inside the setlist sheet) | `:1172-1182` | stuck open, Save live again |
| Manage tags | `:1439-1444` | stuck open over the saved tags |
| Languages | `:1581-1586` | same |
| Song details (metadata) | `SongMetadataDialog.kt:108-117` | same |
| Manage links | `SongLinksDialog.kt:151-156` | same |
| Song defaults | `SongPlayingDialog.kt:197-201` | same |
| Cover art search, Save | `CoverArtSearchSheet.kt:167-187` | same |
| Welcome (small window), Get started / Open settings | `Dialogs.kt:614-629` | stuck open (nothing written, but it was asked to go) |

(The sheets with no header action — filters, the pickers, the song info sheet — only close through the X, a swipe or
the scrim, and keep today's behaviour.)

The project rule is that monkey-tapping is fixed with state guards, never with time debounces; the guard here is
`confirmOnce`, which stops the second write but not the reverted close.

## Fix

One change in `CampfireBottomSheet`, which covers every sheet above without touching any of them: split the close
into a **cancel** (the header's X, revertible as today) and a **close** (everything else — the header actions and the
content — final once started).

1. Hoist the close state above the `ModalBottomSheet(...)` call (it is read by one of its parameters below), and add
   the finality flag next to it. Keep the existing comment block (the paragraph starting "Hiding the sheet by hand does
   not count as dismissing it") with it, amended as below.

   ```kotlin
   var isCloseRequested by remember { mutableStateOf(false) }
   // Set by a close that follows something the sheet has already done - a Save, a Create, a Delete. Such a hide is
   // never taken back: the sheet's gestures are off for the slide, and a hide cut short anyway is finished from where it is, since
   // a sheet left up after its action would offer an action that has already happened (a dead Create over the editor).
   var isCloseFinal by remember { mutableStateOf(false) }
   val isClosing = remember(sheetState) {
       { isCloseRequested || (sheetState.targetValue == SheetValue.Hidden && sheetState.currentValue != SheetValue.Hidden) }
   }
   val requestClose = { isFinal: Boolean ->
       if (!isCloseRequested) {
           isCloseRequested = true
           isCloseFinal = isFinal
           coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
               when {
                   cause == null -> onDismiss()
                   // A finger that took hold of the sheet keeps it, and its actions with it.
                   !isCloseFinal -> isCloseRequested = false
                   else -> coroutineScope.launch {
                       // Cut short after its action: by a hide of Material's own (the scrim, back, Escape), which is
                       // let run to its end, or by a press that took hold of the sheet before the recomposition that
                       // turned its gestures off, from whose hold it is hidden again. A frame first, by which either
                       // has started.
                       while (sheetState.isVisible) {
                           withFrameNanos { }
                           if (sheetState.isAnimationRunning && sheetState.targetValue == SheetValue.Hidden) continue
                           try {
                               sheetState.hide()
                           } catch (exception: CancellationException) {
                               // Refused while a press still holds the sheet: tried again on the next frame.
                               currentCoroutineContext().ensureActive()
                           }
                       }
                   }.invokeOnCompletion { onDismiss() }
               }
           }
       }
   }
   val cancel = { requestClose(false) }
   val close = { requestClose(true) }
   ```
   The inner `invokeOnCompletion` dismisses both when the sheet is gone and when the scope is cancelled (the sheet left
   composition: another dialog replaced it, or an Activity was recreated mid-slide — in which case the dialog is
   cleared rather than coming back with its one confirmation spent). After a hide of Material's own, `onDismiss` is
   called twice — once by Material's `onDismissRequest`, once here — which every caller tolerates: `dismissSheet(dialog)`
   does nothing once its dialog is gone, and the date sheet's `isPickerVisible = false` is idempotent. Do not call
   `sheetState.hide()` again while Material's own hide runs: it would cancel that one, and back's `settleToDismiss`
   calls `onDismissRequest` on its cancellation, dismissing in one frame. Imports: `withFrameNanos`
   (`androidx.compose.runtime`), `currentCoroutineContext` / `ensureActive` (`kotlinx.coroutines`); all common.

   `isCloseFinal` is never reset: a final close is the sheet's last act, and the composable leaves with its dialog.
   A cancelled hide of a final close can also be the scope's own cancellation (the sheet left composition because
   another dialog replaced it); calling `onDismiss` then is safe by the parameter's own contract — it must be
   `dismissSheet(itsOwnDialog)`, which does nothing once another dialog has taken the sheet's place (the nested date
   sheet's `onDismiss` only clears its own `isPickerVisible`).

2. `ModalBottomSheet(...)`: pass `sheetGesturesEnabled = !isCloseFinal`. With the drag off, the second press of a double
   tap no longer takes hold of the sliding sheet, so it keeps sliding (the change stays animated); the `isCloseFinal`
   branch in `invokeOnCompletion` is only the fallback for a press handled before the recomposition that turns the
   gestures off, in which case the sheet slides on from where the press left it. `sheetGesturesEnabled` turns off the
   drag and the content's nested scroll into the sheet (`BottomSheet.kt:280-290`, `:332`), not the scrim's tap or back,
   which is why the fallback has to let a hide of Material's own finish. The keyboard's back handler, the web history
   entry, `LocalIsSheetClosing` (still true for the whole slide, since `isCloseRequested` is never reset for a final
   close) and the short-window scroll of the header with the content are untouched by the flag.

3. Wire the two. `SheetHeader` today hands its own `onClose` to the actions (`MaterialTheme(typography =
   actionTypography) { actions(onClose) }`, `Dialogs.kt:2266`), so give it a second parameter:
   ```kotlin
   private fun SheetHeader(
       title: String,
       subtitle: String,
       actions: (@Composable RowScope.(close: () -> Unit) -> Unit)?,
       onClose: () -> Unit,       // the X: revertible
       onActionDone: () -> Unit,  // handed to the actions: final
   )
   ```
   with `IconButton(onClick = onClose)` unchanged and `actions(onActionDone)` at the end. `CampfireBottomSheet` calls
   `SheetHeader(title, subtitle, actions, onClose = cancel, onActionDone = close)` and
   `BottomSheetContentScope(columnScope = this, close = close, …)`. No sheet changes: every action and content caller
   in the table already calls `close()` only after its write (or, for the welcome sheet, as its way out).

4. KDoc: in `CampfireBottomSheet`'s `@param actions`, add that the close handed to them is final — a hide that follows an
   action is not taken back by a finger landing on the sliding sheet; in `@param content`, the same for
   `BottomSheetContentScope.close`. In `rememberSingleConfirmation`'s KDoc, add one sentence: the sheet's close after
   a confirmation cannot be taken back (`CampfireBottomSheet`), so the one confirmation it lets through is never left
   on a sheet that stays up.

5. `presentation/CLAUDE.md`, the `ui/dialogs/Dialogs.kt` entry, the sentence "It does so only for a hide that ran to its
   end, …": say that the close button's hide counts only when it ran to its end, while a close that follows a header
   action or the content's own button is final — the sheet's drag is off for that slide and a hide cut short is
   finished from where the sheet is, after any hide of Material's own (the scrim, back) has run — so a second tap of a double tap never leaves an answered sheet up.

**Where this sits relative to lane D's other `Dialogs.kt` plans:** 31 edits `SongPicker` (`:1735-1833`), 33 edits the
EditSetlist call site (`:301-321`), 35 edits `DeleteLibraryDialog`'s `enabled` and its Done branch (`:823-900`). This
plan edits only `CampfireBottomSheet` (`:2034-2195`), `SheetHeader`'s wiring if step 3 needs it (`:2224-2275`) and the
KDoc of `rememberSingleConfirmation` (`:921-928`); none of them overlap, so it can go before or after them.

## Tests

None: the change is Compose state inside `CampfireBottomSheet`, with no pure logic to extract.

## Manual check

Desktop: Songs → New song → type a title → double-click Create as fast as possible, several times over new titles: the
song is created once and the sheet always slides away, leaving the editor. Same for New setlist (Create), Edit setlist
(Save), Manage tags (Save) and Settings → Library → delete (type DELETE, double-click Delete). Then open New song, tap
the X and immediately press and hold the sliding sheet: it still stays up (the X is revertible), and Create still works.
On a phone: the same double tap on Create, and a swipe down started during a Save's slide does not bring the sheet back.
Desktop: Save in Manage tags and immediately press Escape, and (separately) click the scrim right after Save: the sheet
still slides away, never vanishing in one frame, and the next sheet opens normally (no second dismissal closes it).
