# Decide whether Prettify is enabled only while the editor's menu is open, not on every keystroke

**Kind:** performance (typing)  ·  **Severity:** medium (for long files)  ·  **Platforms:** all
**Lane:** U  ·  **Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActions.kt`,
`presentation/CLAUDE.md`

## Problem

The editor checks whether Prettify would change anything after every keystroke, even though only the menu ever uses
the answer. The check prettifies the whole song on the main thread. At 491c4254a
(`SongEditorScreen.kt:334`):

```kotlin
// Compare with the same formatted draft the action applies, so the option follows typing, undo and revert.
val prettifiedText = remember(textFieldState, viewModel) { derivedStateOf { viewModel.prettifyText(text.value) } }
```

and it is read in the composition of the app bar's `actions` lambda (`SongEditorScreen.kt:509-513`):

```kotlin
canPrettify = !isSaving && text.value.isNotBlank() && prettifiedText.value != text.value,
onPrettify = {
    val prettified = prettifiedText.value
    if (prettified != text.value) textFieldState.replaceWithPrettification(prettified)
},
```

`derivedStateOf` computes only when something reads it. The `actions` scope reads it on every composition, and both
`text.value` reads (here and in `isNotBlank()`) invalidate that scope on every edit. So each keystroke does four
things:

- runs `ChordProPrettifier.prettify` (through `PrettifyChordProUseCase`) over the whole text on the main thread;
- compares two strings as long as the song;
- allocates about twice the text;
- recomposes the whole `actions` scope: undo, redo, save and `EditorMenu` with its ~10 `ActionsMenuItem`s, each
  built with a `stringResource` and a `painterResource`.

The result is only shown in the dropdown. Prettify is `isAlwaysInMenu = true` (`SongEditorScreen.kt:959-965`), and
`ActionsMenu` (`SongActions.kt:108-200`) never gives such an item a button (`expandableItems` filters out
`isAlwaysInMenu`). So `isEnabled` is only read by the `DropdownMenuItem`, which is composed only while the menu is
open.

Measured warm on the desktop JVM: prettify takes 23 µs for 1.9 KB, 119 µs for 23 KB and 0.75 ms for 150 KB. A
low-end device is 8–15× slower, so a long songbook file costs 1–11 ms per keystroke before the recomposition. The
check was introduced in fc140baf4.

## Fix

Let a menu item work out whether it is enabled when the menu draws it:

1. In `SongActions.kt`, add a parameter to `ActionsMenuItem`:
   `val isEnabledInMenu: (() -> Boolean)? = null`. Its KDoc: it is read only while the menu is open and overrides
   `isEnabled` there. It is meant for an `isAlwaysInMenu` item whose answer is expensive. In `ActionsMenu`'s dropdown,
   use `enabled = item.isEnabledInMenu?.invoke() ?: item.isEnabled`. Leave the button path alone: it keeps reading
   `isEnabled`, and an item that sets `isEnabledInMenu` should also be `isAlwaysInMenu` (say so in the KDoc). The
   lambda reads snapshot state inside the dropdown's composition. That subscribes only the open menu, and the open
   popup holds the focus, so nobody is typing while it is subscribed.
2. In `SongEditorScreen.kt`, keep the `prettifiedText` derived state. It stays lazy and cached as long as nothing
   reads it outside the menu. Replace `canPrettify: Boolean` on `EditorMenu` with
   `canPrettify: () -> Boolean`, passed as
   `{ !isSaving && text.value.isNotBlank() && prettifiedText.value != text.value }`. Read `isSaving` through
   `rememberUpdatedState`, or keep it as a captured value since the lambda is rebuilt when it changes. Set
   `isEnabledInMenu = canPrettify` on the Prettify item and drop its `isEnabled`. `onPrettify` stays as it is: it
   reads `prettifiedText.value` when tapped, which prettifies once if the menu's read did not already.
3. Check that nothing else in the `actions` lambda still reads `text.value`. After this change, the scope recomposes
   for undo/redo availability, `hasUnsavedChanges` and `isSaving` only.

Opening the menu now prettifies once, the first time it is drawn after an edit. That is about 1–10 ms on a low-end
device for a 150 KB file, and well under 1 ms for an ordinary song, while the dropdown's enter animation starts. That
is acceptable for a one-off.

The alternative was a `snapshotFlow { text.value }.debounce(PREVIEW_DELAY_MILLIS).mapLatest { withContext(Default) {
prettify } }` like the preview's. It still prettifies the whole text after every pause in typing, it needs a
"not known yet" state for an item that is right there in the menu, and it costs a coroutine pipeline. The lazy read
is simpler and does no work at all while typing. Recommended: the lazy read.

Update the editor paragraph of `presentation/CLAUDE.md` that names Prettify. Add one clause saying that whether
Prettify changes anything is only decided while the menu is open (`ActionsMenuItem.isEnabledInMenu`).

## Tests

None. The change is in composition wiring, and nothing new is pure logic. The prettifier itself is already covered
by `ChordProPrettifierTest`.

## Manual check

1. On a low-end Android phone or the desktop build on a slow machine, open a long song in the editor, such as a
   pasted songbook of a few thousand lines.
2. Type quickly. Typing should feel as responsive as in a short song. With a profiler or Compose recomposition counts,
   the app bar's actions should no longer recompose per keystroke.
3. Open the overflow menu. Prettify is enabled exactly when the text is not already prettified: type a stray double
   space into a directive, open the menu, and it is enabled. Tap it, reopen the menu, and it is disabled. Undo, and it
   is enabled again.
4. While a save is in progress, Prettify is disabled.
