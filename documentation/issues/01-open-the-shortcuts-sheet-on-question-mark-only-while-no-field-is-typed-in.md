<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# Open the shortcuts sheet on `?` only while no text field is being typed in

**Challenged:** amended — option A dropped: on the desktop and the web every mouse click focuses the clickable it
lands on (Foundation's `isRequestFocusOnClickEnabled()` is `true` there), so "only while the root itself is focused"
would lose `?` after nearly any click (a rail item, a chip, sort), on the web too, where nothing is broken today. The
fix now watches the platform text input session instead (`InterceptPlatformTextInput`), which every editable field
opens while it is focused, on every platform, with no per-field marking.

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** desktop (certain), iOS/iPadOS with a hardware keyboard (likely)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/menu/OverlayState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppShortcuts.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/AppKeyboardShortcutsTest.kt`

## Problem

`?` (Shift + slash) is an app-wide shortcut heard at the root after everything under it:

```kotlin
SHORTCUTS_QUESTION_MARK(KeyChord.plain(Key.Slash, "?", shift = KeyChord.Shift.IN_LABEL), ShortcutScope.APP),
```

`AppKeyboardShortcuts.kt`'s root `onKeyEvent` maps every unconsumed `KeyDown` through `appShortcutOf(...)`, and
`showKeyboardShortcutsByKey()` checks only `isUncovered`. The docs (`AppShortcuts.kt`: "`?` outside a field",
`wasmJsMain/CLAUDE.md`: "being no shortcut in a field") assume a focused text field consumes the key-down. On the
desktop it does not (verified in Foundation 1.12's `TextFieldKeyEventHandler.onKeyEvent`): text is typed only from an
event with `isTypedEvent`, which on the desktop is an AWT `KEY_TYPED` (Compose `KeyEventType.Unknown`); the
`KEY_PRESSED` key-down of Shift + Slash maps to no `keyMapping` command, returns `false`, and bubbles to the root,
which opens the sheet, while the following `KEY_TYPED` still types `?` into the field. iOS's `isTypedEvent` is `false`
for every hardware key (text arrives through UIKit), so it likely behaves the same. Android types on the key-down
(consumed), and on the web a focused field is a hidden `<input>` whose keys never reach the canvas.

Scenario (desktop): type "How many roads must a man walk down?" into the editor, or `?` into the Songs search (Ctrl+F):
the Keyboard shortcuts sheet slides up mid-typing and takes the focus. Nothing on a screen is a dialog, so
`isUncovered` holds. M and T have the same leak in principle but act only on Metronome and song details, whose only
field is a stepper, guarded by `OverlayState.isValueBeingTyped`; `?` acts everywhere and is the one left unguarded.

## Fix

Options considered:

- (A, rejected) Act on `?` only while the root's own focus target is focused (`focusState.isFocused`). Clicks focus
  their clickable on the desktop and the web (`Clickable.kt`'s `requestFocusWhenInMouseInputMode`, enabled by
  `RequestFocusOnClick.desktop.kt` / `.web.kt`; only iOS and Android leave the focus alone), and a focused rail item,
  chip, or row keeps it, so `?` would die after the first click; on song details any clicked control would do the
  same to its node. A regression on the web, which works today.
- (B) A focus counter in `OverlayState` set by a modifier on every field. Today that is four components reachable while
  uncovered (`SearchField`, `SearchFieldSlot`, `FilterSearchField`, `ChordProTextField`; the stepper is already
  counted), but every future field must remember it.
- (C) On the desktop only, act on the unconsumed `KEY_TYPED` `?`. Platform-specific, leaves iOS.
- **(D, take this) Count open text input sessions.** Every editable `BasicTextField` (both the `TextFieldState` and the
  legacy value-based ones, so every Material field) opens a platform text input session through
  `PlatformTextInputModifierNode.establishTextInputSession` as it gains focus and closes it as it loses it
  (`TextFieldDecoratorModifier`'s focus callback; the app sets no `showKeyboardOnFocus = false`, and the one
  `readOnly` field is in a dialog). `InterceptPlatformTextInput` (public, `androidx.compose.ui.platform`, common code)
  sees every such session below it, before the platform's own service, so it also works in the offscreen tests.

  1. In `OverlayState`, a plain (non-state) counter with `onTextInputStarted()` / `onTextInputEnded()` and
     `isTextBeingTyped` (only key handlers read it).
  2. In `CampfireApp`, wrap the content of the root `Box` (the one carrying `appKeyboardShortcuts`) in
     `InterceptPlatformTextInput(interceptor)`, the interceptor `remember`ed (a new instance each composition tears an
     open session down and restarts it): `overlayState.onTextInputStarted(); try { nextHandler.startInputMethod(request) }
     finally { overlayState.onTextInputEnded() }` (`startInputMethod` returns `Nothing`, so the `finally` runs on
     cancellation, which is how a session ends).
  3. `showKeyboardShortcutsByKey()` stays as is (Ctrl / Cmd + slash must open the sheet from a field, the web's shell
     included); only the root's `AppShortcut.ShowKeyboardShortcuts(KeyShortcut.SHORTCUTS_QUESTION_MARK)` returns
     `false` (unconsumed) while `isTextBeingTyped`. Simplest in `perform`.

  Where `?` then works: anywhere uncovered with no field focused, whatever else holds the focus: the root on every main
  screen, Settings, Metronome and Chords and Scales (not while their BPM or search field is focused), song details
  (its node's preview handler leaves Shift + slash unconsumed, so the root still hears it; not while a stepper is
  typed into), a clicked or Tab-focused button. Not in the editor's text. The web is unchanged (its fields never reach
  the canvas; the shell leaves `?` to the root). No change to `SongKeyboardShortcuts.kt`.

Update the root shortcut paragraph in `ui/CLAUDE.md` (`?` is left alone while a field is being typed in: an open text
input session, counted by `InterceptPlatformTextInput` in `CampfireApp`) and the `?` sentence in `components/CLAUDE.md`;
the `AppShortcuts.kt` KDoc ("outside a field") then holds.

## Tests

In `AppKeyboardShortcutsTest` (UI test, desktop impersonated, `runDesktopTest`): open the Songs search with Ctrl+F,
wait for idle, press Shift + slash with `performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Slash) } }` (a bare
key-down, as AWT sends before its typed event), and assert no Keyboard shortcuts sheet; add `?` with nothing focused
opening the sheet, and `?` after a `performClick` on a focusable control of the songs screen (a sort or filter chip)
opening it, which is the case option A would have broken. Keep the Ctrl + slash case. See the field case fail without
the fix first (lane report only).
