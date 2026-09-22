# 11 · Double-tapping a menu entry runs it twice: two downloads, two share sheets, two save dialogs, two file pickers

**Severity:** wrong behaviour (all platforms; likely for anyone who double-taps or double-clicks out of habit — the
window is the whole of the menu's exit animation, a few hundred milliseconds with the expressive motion scheme) ·
**Area:** `:presentation` (`components/OverflowMenu.kt` and every menu built on it: `SongActions.kt`,
`SetlistActions.kt`, `NewItemMenu.kt`, `SetlistsScreen.kt`'s missing-entry menu, `SongEditorScreen.kt`'s revert menu)

## Symptom
- Web: open a song row's ⋮ menu and double-click "Export". Two identical downloads (`song.cho`, `song (1).cho`).
  The same with a setlist header's "Export".
- Android: ⋮ → double-tap "Share": two share sheets stacked on top of each other; back out of one and the other is
  still there. ⋮ on a song → double-tap "Export": the second save request resumes the first as dismissed and launches
  the system picker a second time.
- Songs or setlists screen, "New" (+) → double-tap "Import files": two file pickers requested (on Android the second
  takes over the first; on the desktop a second `FileDialog` can open over the first when the clicks are quick).
- Desktop: the second click of a quick double-click on "Export" lands before the modal `FileDialog` blocks the window
  and a second save dialog follows the first.

## Cause
Every menu entry closes the menu and then acts, e.g.
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActions.kt:160-166`:

```kotlin
ActionsMenuItem(
    title = stringResource(Res.string.export),
    icon = painterResource(Res.drawable.ic_export),
    onClick = {
        dismiss()
        viewModel.exportSong(filePicker, song.fileName)
    },
)
```

`dismiss` is `OverflowMenuState.dismiss` (`OverflowMenu.kt:53`, `:73-75`), which only sets `isExpanded = false`. The
menu does not leave with that: Material's `DropdownMenu` keeps its popup composed for as long as its exit transition
runs (`material3` 1.12.0-alpha03, `SkikoMenu.skiko.kt:173-176` and the Android actual alike):

```kotlin
val expandedState = remember { MutableTransitionState(false) }
expandedState.targetState = expanded
if (expandedState.currentState || expandedState.targetState) {
```

and nothing disables the items meanwhile, so a second tap on the fading item runs its `onClick` again. The app's
theme is `MaterialExpressiveTheme` with `MotionScheme.expressive()`, whose FastSpatial/FastEffects springs keep that
transition running well past the interval of a double tap. What runs twice is harmless for most entries (showing the
same dialog twice is idempotent, `openEditor` checks the top of the stack, a second rename finds no file and returns
null, archiving captures the value it toggles from), but not for the ones that hand over to the platform:
`exportSong`, `shareSong`, `exportSetlist` and `importFiles(filePicker)` each launch a new coroutine per call
(`CampfireViewModel.kt:1125`, `:1310-1320`) and nothing between the menu and the picker notices a second request.

## Fix
Make an entry fire once per opening of its menu, at the one place every menu goes through. In `OverflowMenu.kt`:

1. Add to `OverflowMenuState`:

   ```kotlin
   /**
    * Closes the menu and runs [action] - once per opening. The menu does not leave with the tap that chose an entry
    * but with the end of its exit animation, and until then every entry in it can still be tapped: a double tap on
    * "Export" downloaded the file twice. The state is written as the tap is handled, so the second tap of the same
    * gesture already reads it.
    */
   fun select(action: () -> Unit) {
       if (!isExpanded) return
       isExpanded = false
       action()
   }
   ```

2. Change `OverflowMenu`'s `content` parameter from `@Composable (dismiss: () -> Unit) -> Unit` to
   `@Composable (select: (action: () -> Unit) -> Unit) -> Unit`, pass `state::select`, and update its KDoc (`@param
   content The entries of the menu, handed the way to choose one: every entry acts through it, which closes the menu
   before the action and ignores a second choice made while the menu is on its way out.`).

3. `ActionsMenu` (`SongActions.kt:56-75`) passes the same type through; update its KDoc the same way.

4. Rewrite every entry from `onClick = { dismiss(); action() }` to `onClick = { select { action() } }`:
   - `SongActions.kt` — six entries (edit, setlist assignments, update file name, export, share, delete);
   - `SetlistActions.kt` — six entries (edit, song assignments, duplicate, archive/unarchive, export, delete);
   - `NewItemMenu.kt` — create and import;
   - `SetlistsScreen.kt:483-492` — the missing entry's "Remove from setlist";
   - `SongEditorScreen.kt:652-660` — the revert entry.
   The comments above the entries ("Each entry closes the menu before it acts, so that it is gone by the time the
   dialog or the picker it opens is on the screen.") become "Each entry acts through `select`, which closes the menu
   before it acts - so that it is gone by the time the dialog or the picker it opens is on the screen - and only once."

Nothing else changes: the long press that opens a song row's menu opens the same state, and a menu opened again after
it has closed can be chosen from again.

Do **not**:
- debounce by time, or disable the entries with a flag cleared by an effect: the state that already says the menu
  is closed is the guard;
- rely on 10's single-flight guard in the view model (`launchFileTransfer`) instead of this: that guard lasts only
  as long as the transfer, and the transfers a menu starts mostly end at once - Android's `shareFile` returns as soon
  as the chooser is started, and the web's `saveFile` as soon as the download is handed to the browser - so the second
  tap of a double tap finds it released and runs again. The two plans are complementary and touch different files
  (10: `CampfireViewModel.kt`, `IosFilePicker.kt`; this one: the menus): 10 covers requests that do not come from a
  menu (the Settings rows, the empty states) and the long-running ones (an export whose archive takes seconds to
  build), this one covers the menu's exit animation for every entry. Do not remove either in favour of the other.

## Tests
None (UI is untested).

## Verify
1. Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): double-click "Export" in a song's menu and in a setlist's
   menu, as fast as possible, ten times each. Before: two downloads most of the time. After: one download every time.
2. Android: double-tap "Share" (one share sheet), double-tap "Export" (one system save screen, and the file written
   once it is confirmed), "New" → double-tap "Import files" (one picker; the import still happens).
3. Desktop: double-click "Export" → one save dialog.
4. Every entry of every menu still works on a single tap, including long-press → menu on a song row (Android/iOS),
   the setlist header menu, the missing-entry menu (delete a song file from outside the app on desktop), and the
   editor's revert (confirmation dialog appears).
5. Desktop: open a menu, press Escape: it closes and the back stack is untouched (`isAnyOverflowMenuOpen` still
   counts it).
6. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Docs
`presentation/CLAUDE.md`, in the `ui/components/` bullet, the `OverflowMenu` parenthesis "(the dropdown behind every
overflow button - the song and setlist menus and the editor's revert - which is also what counts the open ones, since
a menu keeps that inside the composition and the desktop Escape handler is outside it)" gains: "; its entries act
through `OverflowMenuState.select`, which closes the menu and runs the entry once per opening, because a `DropdownMenu`
stays on screen and tappable through its exit animation and a double tap on Export used to export twice".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/OverflowMenu.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActions.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SetlistActions.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/NewItemMenu.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing; coexists with 10 (see above), which may land before or after. `SetlistsScreen.kt` is also touched by 35 and
08, and `SongEditorScreen.kt` by 20, 30 and 31 (only the menu entries' lambdas change here); schedule
edits to those files one after another.
