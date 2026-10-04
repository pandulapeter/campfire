# End the editor's field at the top of the keyboard while typing in a short window, rather than a navigation bar's height behind it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android (and iOS where the home indicator inset is non-zero)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt

**Challenged:** sound — `restingBottomInset` (`contentEdges` bottom) is the same value as the `bottom` of `songEditorContentPadding`, so outside compact typing the new formula is identical (`max(bars, ime) + 32dp`); tablets, desktop (no IME) and the web (IME inset 0) are unchanged.

## Problem

The editor's `ChordProTextField` pads its bottom with (SongEditorScreen.kt:720–723 and :788):

```kotlin
val restingBottomInset = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding()
…
val endPadding = if (isCompactTyping) 0.dp else restingBottomInset + 32.dp
…
override fun calculateBottomPadding() = (contentPadding.calculateBottomPadding() - restingBottomInset).coerceAtLeast(0.dp) +
        endPadding
```

`contentPadding` is `songEditorContentPadding` (CampfireApp.kt:564), a `KeyboardAwarePadding` whose bottom is `maxOf(systemBars.bottom, ime.bottom - 0)`. With the keyboard up its bottom is the IME inset, which on Android is measured from the window's bottom edge and so *includes* the navigation bar under the keyboard. The field's bottom padding is therefore `ime − navigationBar + endPadding`:

- not typing compactly: `ime − nav + nav + 32dp = ime + 32dp` — correct;
- typing in a short window (`isCompactTyping`: the keyboard leaves less than `SHORT_WINDOW_HEIGHT`, 480dp, of the window — a small phone upright, such as 360×640dp, or any phone on its side): `ime − nav` — the field's last `nav` dp (≈24dp with gesture navigation, 48dp with three buttons in portrait) lie **behind the keyboard**. `BasicTextField` keeps the caret inside its own viewport, which now extends under the keyboard, so typing on the last line of a long song can put the caret line (18sp lines in compact typing, so one to two and a half lines) out of sight.

This is unconfirmed on a device (the live run did not type at the end of a long song in compact typing); the arithmetic above is from the code.

**Drop this plan if** on the 360×640dp emulator (see memory: smallest supported screen), with three-button navigation, typing at the end of a song longer than the screen keeps the caret's line fully above the keyboard with the current code.

## Fix

The two terms that cancel out when not compact show what the padding means: the handed padding's bottom, plus 32dp of room when there is room for it. Write it that way so the compact case loses only the 32dp:

```kotlin
// The keyboard, or the system bars where it is down, plus room below the last line where the window has it: typing in
// a short window gives every line to the text, but none of the field may sit behind the keyboard.
val endPadding = if (isCompactTyping) 0.dp else 32.dp
```

and in `EditorFieldPadding`, drop the `restingBottomInset` parameter and return

```kotlin
override fun calculateBottomPadding() = contentPadding.calculateBottomPadding() + endPadding
```

Update the KDoc of `EditorFieldPadding` (remove "Only the part of the handed padding that reaches higher than the resting inset is taken off the field") and the long comment on the `.padding(EditorFieldPadding(…))` call so they say the handed padding is applied once, whole. `restingBottomInset` is then unused in `ChordProTextField`; remove it. Keep the comment block above `endPadding` about the caret-following and scroll independence, since the padding still does not depend on the scroll position.

Check the side-by-side case too: the field's pane in Split mode receives `contentPadding.only(start = true, end = !hasSideBySidePreview, bottom = true)` (SongEditorScreen.kt:546), so the same formula applies.

## Tests

None: the padding is inset arithmetic read during layout on a device; there is no pure helper worth extracting.

## Manual check

On the 360×640dp emulator with three-button navigation, open a song longer than the screen in the editor, tap at the very end of the text and type several new lines: each new line and the caret stay fully visible right above the keyboard. Rotate to landscape and repeat. Then with the keyboard down, scroll to the end: the last line still has its 32dp of room above the navigation bar, as before.
