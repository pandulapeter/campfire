# Ignore a sheet's header actions once it has started closing, so Close then Save cannot write a cancelled draft

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/TextFieldBottomSheet.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CoverArtSearchSheet.kt,
presentation/CLAUDE.md

**Challenged:** amended — the keyboard Done paths that write (New song, Delete library) also drop the event once the sheet is closing, since ✕ leaves the keyboard up and Done during the slide wrote the cancelled draft (or deleted the library) the header guard was meant to stop.

## Problem
`CampfireBottomSheet`'s close (`Dialogs.kt` ~2081 at 800ebde0b) only starts the hide animation:

```kotlin
val close = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause -> if (cause == null) onDismiss() }; Unit }
```

For the ~300ms the sheet slides away its header stays fully live. The edit forms' Save buttons have no guard of their
own — `SongMetadataDialog` (`SongMetadataDialog.kt` ~105-118), `SongLinksDialog` (~152-158), `SongTagsDialog`
(`Dialogs.kt` ~1400), `SongLanguagesDialog` (~1542), the cover art sheet's Save and Remove (`CoverArtSearchSheet.kt`
`CoverArtSearchActions`), and the date picker once plan 04 makes it slide away — only `NewSongDialog`,
`SetlistDetailsDialog` and `DeleteLibraryDialog` use `rememberSingleConfirmation`. So:
- tapping the header's ✕ and then Save within the slide (or a swipe-down/scrim tap, which Material hides the same way)
  writes the draft the user just cancelled — Save calls `close()` again, the second `hide()` cancels the first, and the
  sheet still leaves;
- a quick double tap on Save writes the file twice (two rewrites, two sync schedules).

## Fix
One central guard in `CampfireBottomSheet`, rather than `rememberSingleConfirmation` sprinkled over every form:

1. In `CampfireBottomSheet`, keep whether the sheet is on its way out:
   ```kotlin
   // Set as the tap is handled, so the second tap of the same frame already reads it; Material's own hide (a swipe, the
   // scrim) shows up as the sheet's target becoming Hidden while it is still on screen.
   var isCloseRequested by remember { mutableStateOf(false) }
   val isClosing = isCloseRequested || (sheetState.targetValue == SheetValue.Hidden && sheetState.currentValue != SheetValue.Hidden)
   val close = {
       if (!isCloseRequested) {
           isCloseRequested = true
           coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
               if (cause == null) onDismiss() else isCloseRequested = false // a finger took hold: the sheet stays
           }
       }
       Unit
   }
   ```
   Read `isClosing` through a lambda or `derivedStateOf` so the header does not recompose on every frame of the slide.
2. Hand it to the header actions through a private `compositionLocalOf { { false } }` (e.g. `LocalIsSheetClosing`,
   documented with a KDoc saying why) provided around `actions(onClose)` in `SheetHeader` (and around `content`, for
   the actions a sheet draws in its body).
3. `BottomSheetConfirmButton` (`TextFieldBottomSheet.kt`) drops the click while closing:
   `onClick = { if (!LocalIsSheetClosing.current()) onClick() }` (capture the lambda in composition). Keep its look
   unchanged — the button is sliding away, and a colour change in its last frames would be an un-narrated flicker.
4. `CoverArtSearchActions`' Remove `IconButton` gets the same guard (or is routed through a small shared helper next to
   `BottomSheetConfirmButton`).
4b. The keyboard's Done key is the other way a form writes, and tapping ✕ does not take focus or the keyboard away,
   so ✕ then Done within the slide still writes the cancelled draft — in `NewSongDialog` (Done on Duration creates the
   song) and `DeleteLibraryDialog` (Done on the typed
   `DELETE` deletes the library). `rememberSingleConfirmation` does not catch it: it only stops a *second*
   confirmation. In both, read the same local inside the content lambda (where it is provided) and drop
   the Done there: e.g. in `NewSongDialog`'s `text = { … }`, `val isClosing = LocalIsSheetClosing.current` and
   `onDone = { if (!isClosing()) create(closeSheet) }`; the same for `DeleteLibraryDialog`'s `onDone` (`Dialogs.kt` ~852). (`SetlistDetailsDialog` has no
   writing Done: its title goes Next and its description has no Done key.) Keep
   `rememberSingleConfirmation` for the double event it was made for.
5. Update the `CampfireBottomSheet` KDoc (`@param actions`: they do nothing once the sheet has started closing) and
   the `rememberSingleConfirmation` KDoc (it remains for a keyboard Done and a button tap landing on the same frame;
   a Done after the sheet started closing is dropped by step 4b).
   In `presentation/CLAUDE.md`'s `ui/dialogs/` paragraph, after "The close button hides the sheet (`sheetState.hide()`)
   and then clears `visibleDialog` itself", add one sentence: from the moment a sheet starts closing — its close
   button, a swipe or the scrim — its header actions do nothing, so a Save tapped during the slide never writes a
   cancelled draft.

## Tests
None: the guard lives in Compose state around `SheetState`; there is no pure function to test.

## Manual check
On Android: open Edit song details, change the title, tap ✕ and immediately Save — the file keeps its old title. Do the
same with a swipe down then Save, and in Manage links, Manage tags, Languages and the cover art sheet. Double-tap Save
in Edit song details — the sync indicator / file is written once. Start a swipe down, let go before the threshold —
the sheet settles back and Save works again. New song with a title typed and the caret in Duration: ✕ then the keyboard's Done at once —
no song is created.
