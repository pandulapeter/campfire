# Give the focus back to the editor's text field, not to the screen, when a resize or rotation switches Edit and Split while the user is typing

**Challenged:** amended — the requester and the focus callback go innermost on the field (after its scroll container, which is a focus group) and read `hasFocus`; the flag is a plain holder rather than snapshot state; Shortcuts/keyboard notes added to the manual check.

**Kind:** ux  ·  **Severity:** medium  ·  **Platforms:** Android, iOS (large phones and tablets rotated), desktop and web (window resized across 840dp)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`,
`presentation/CLAUDE.md` (the `screens/songEditor/` sentence "which takes the focus itself as it opens and whenever its
panes change")

## Problem

The editor shows Split only where the window is `WindowSize.EXPANDED` (840dp wide and more), and Split is what the
choice starts at; a narrower window *shows* Edit in its place:

```kotlin
val hasRoomForSplitPanes = windowSize == WindowSize.EXPANDED
var selectedPanes by rememberSaveable(stateSaver = EditorPanes.Saver) { mutableStateOf(EditorPanes.SPLIT) }
val panes = if (selectedPanes == EditorPanes.SPLIT && !hasRoomForSplitPanes) EditorPanes.EDIT else selectedPanes
val hasSideBySidePreview = panes == EditorPanes.SPLIT
```

The field is composed in one of two different places depending on that:

```kotlin
if (hasSideBySidePreview) {
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        editor(Modifier.weight(1f).fillMaxHeight())
        …
    }
} else {
    AnimatedContent( … targetState = panes == EditorPanes.PREVIEW, … ) { showPreview ->
        if (showPreview) preview(Modifier.fillMaxSize()) else editor(Modifier.fillMaxSize())
    }
}
```

so a change of `panes` disposes the `ChordProTextField` and composes a new one. On top of that, every change of `panes`
explicitly moves the focus to the screen's outer `Column`:

```kotlin
val focusRequester = remember { FocusRequester() }
LaunchedEffect(panes) { focusRequester.requestFocus() }
…
Column(
    modifier = Modifier
        .fillMaxSize()
        .focusRequester(focusRequester)
        .focusable()
```

That is right for its purpose (Ctrl / Cmd + S needs something inside the editor focused), but it also means that while
the user is typing, anything that changes `panes` without them asking takes the caret out of the field and the
software keyboard down:

- A large phone (landscape width ≥ 840dp, e.g. a Pixel Pro / Pro XL or an iPhone Pro Max on its side) or a tablet:
  typing in portrait (Edit shown, Split remembered) and rotating to landscape switches to Split; rotating back
  switches to Edit. Android does not recreate the Activity on rotation here (`android:configChanges` in
  `app/android/src/main/AndroidManifest.xml` includes `orientation|screenSize|…`), so without this the focus and the
  keyboard would survive; with it, the keyboard closes on every rotation and the user has to tap back into the text.
- Desktop and web: resizing the window across 840dp while typing loses the caret in the same way (the next keystroke
  goes nowhere).

The text, the selection and both scroll positions are *not* lost — `TextFieldState` and the scroll states are hoisted
into the screen (`editorField.textFieldState`, `fieldScrollState`, `fieldHorizontalScrollState`) — so only the focus
needs restoring: once the field has it again, the caret is where it was.

## Fix

Recommended: remember whether the field had the focus, and give it back to the field rather than to the `Column` when
the field is still shown after the change.

1. Add a second requester and a focus flag next to `focusRequester`. The flag is a plain holder, not snapshot state:
   it only has to be read at the moment `panes` changes, and a `mutableStateOf` read there would recompose the whole
   editor screen on every focus change of the field.

   ```kotlin
   val fieldFocusRequester = remember { FocusRequester() }
   val fieldFocus = remember { object { var hasFocus = false } } // or a tiny private class
   ```

2. Attach both **innermost**, directly over `BasicTextField`'s own focus target — not through the `editor` lambda's
   `paneModifier`. `ChordProTextField` applies its `modifier` *outside* `bounceHorizontalScroll` (`horizontalScroll`),
   and a scroll container is a focus group in current Compose foundation: an `onFocusChanged` above it sees
   `ActiveParent` (so `isFocused` is `false` while the caret is in the field), and a requester above it has to rely on
   focus entering a non-focusable group. Give `ChordProTextField` a parameter, e.g.
   `fieldModifier: Modifier = Modifier`, appended last in its chain (after `.padding(EditorFieldPadding(…))`):

   ```kotlin
   ChordProTextField(
       modifier = paneModifier,
       fieldModifier = Modifier
           .focusRequester(fieldFocusRequester)
           .onFocusChanged { fieldFocus.hasFocus = it.hasFocus },
       …
   ```

   Read `hasFocus` rather than `isFocused` either way, so the flag stays right if anything focus-related is ever put
   between the two.

3. Capture the flag in the composition in which `panes` changes, *before* the old field is disposed (disposal, and any
   `onFocusChanged(false)` it triggers, happen while that composition is applied), and choose the target there:

   ```kotlin
   val wasFieldFocused = remember(panes) { fieldFocus.hasFocus }
   LaunchedEffect(panes) {
       if (wasFieldFocused && panes != EditorPanes.PREVIEW) fieldFocusRequester.requestFocus() else focusRequester.requestFocus()
   }
   ```

   `LaunchedEffect` runs after the apply, by which time the new field (the `Row`'s, or the `AnimatedContent`'s first
   content, which `AnimatedContent` composes in its first composition) is attached, so the requester has a target.
   Edit ↔ Split is an `if`/`else`, so the old and the new field are never composed at once and the requester is never
   attached to two nodes; Edit → Preview keeps the outgoing field during the fade, but that change goes to the
   `Column` anyway. Verify the capture order on a device; if the flag has already been reset by then, fall back to
   setting it `false` only from the places that knowingly move the focus away (the preview's `clickable`s, the
   Preview segment).

   A field that gets the focus programmatically shows the software keyboard on Android and iOS, which is the point.
   The old field's text input session ends as it is disposed, so the keyboard may dip and come back in the same
   moment; that is acceptable, a lost caret is not.

4. Update the comment above `LaunchedEffect(panes)` and the `presentation/CLAUDE.md` clause ("which takes the focus
   itself as it opens and whenever its panes change, since nothing in it is focused before the text is clicked or while
   only the preview shows") to say that a field that was being typed in keeps the focus across the change.

Alternative considered: `movableContentOf` for the editor so the same field node moves between the `Row` and the
`AnimatedContent`. It does not help on its own: `LaunchedEffect(panes)` would still move the focus to the `Column`,
and moving a layout node between parents detaches it, which clears its focus anyway. Not recommended.

Keep the current behavior where it is right: Edit → Preview (field gone) and Preview → Edit (field was not focused)
still focus the `Column`, and so does opening the editor.

## Tests

None: focus and composition behavior, with no pure logic to extract. Manual check only.

## Manual check

1. Android, a phone whose landscape width is at least 840dp (e.g. the Pixel_10_Pro_XL AVD at its native size; check
   with `adb shell wm size`/density that landscape is ≥ 840dp) with auto-rotate on: open a song's editor in portrait
   (Edit is shown), tap into the middle of a line, type a letter. Rotate to landscape: Split appears, the keyboard stays
   (or comes straight back) and the next letter typed goes in right after the first. Rotate back: the same.
2. iOS, an iPhone Pro Max simulator: the same.
3. Desktop: open the editor in a window narrower than 840dp, click into the text, then widen the window past 840dp and
   type: the letters go into the text at the caret. Ctrl / Cmd + S still saves.
4. Regression: with the field focused, tap Preview and then Edit; then press Ctrl / Cmd + S — it saves (the `Column`
   still has the focus when the field was not shown).
5. Regression: open the editor and, without touching the text, rotate / resize across 840dp: no keyboard comes up and
   nothing is focused but the `Column` (Ctrl / Cmd + S still saves).
6. Shortcuts: on the phone of check 1, reopen the Shortcuts while typing, then rotate. If the keyboard dipped during the
   swap, `LaunchedEffect(isKeyboardVisible)` folds them again as it comes back — the same as tapping back into the
   text does today, so not a regression; just make sure they do not flicker open and shut.
