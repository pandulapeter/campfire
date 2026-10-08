# Decide the app-wide keyboard shortcuts in one common, tested table instead of twice in the desktop and web shells

**Kind:** testability  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** desktop, web
**Challenged:** amended — the desktop's early `return false` for any other Ctrl/Cmd key-down *is* observable: it is what
keeps Ctrl/Cmd + Escape from going back or asking to close, so it must survive explicitly; Shift is ignored (not
forbidden) for Space and M on both shells, and the KDoc and tests say so.
**Files:** new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppShortcuts.kt`;
`presentation/src/desktopMain/.../ui/CampfireDesktopApp.kt` (`CampfireViewModel.handleKeyEvent`, the F / zoom / Space-M
branches; the Escape branch and `handlePreviewKeyEvent` stay); `presentation/src/wasmJsMain/.../ui/CampfireWebApp.kt`
(`SearchShortcutEffect`, `MetronomeShortcutEffect`, the key branch of `SongTextZoomEffect`); new
`commonTest/.../AppShortcutsTest.kt`; `presentation/CLAUDE.md` (the desktop and web shell paragraphs: say the chords
are decided by `appShortcutOf`)
**Depends on:** none

## Problem

The three app-wide shortcuts — Ctrl/Cmd+F (open the current search), Ctrl/Cmd + plus/minus/zero (song text size),
Space and M (metronome) — are encoded once per shell, each with its own copy of the modifier rules and the dispatch:

```kotlin
// desktopMain: CampfireViewModel.handleKeyEvent
if (keyEvent.type == KeyDown && keyEvent.key == Key.F && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) return openCurrentSearch()
if (keyEvent.type == KeyDown && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && !keyEvent.isAltPressed) {
    val steps = when (keyEvent.key) { Key.Equals, Key.Plus, Key.NumPadAdd -> 1; Key.Minus, Key.NumPadSubtract -> -1; Key.Zero, Key.NumPad0 -> null; else -> return false }
    return zoomSongText(steps)
}
if (keyEvent.type == KeyDown && (keyEvent.key == Key.Spacebar || keyEvent.key == Key.M) && !keyEvent.isCtrlPressed && !keyEvent.isMetaPressed && !keyEvent.isAltPressed) { … toggleMetronomeByKey(isSpace = …) }
```
```kotlin
// wasmJsMain
val isF = keyEvent.code == "KeyF" || keyEvent.key == "f" || keyEvent.key == "F"
if ((keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey && isF && viewModel.openCurrentSearch()) keyEvent.preventDefault()
…
val steps = when (keyEvent.key) { "+", "=" -> 1; "-", "_" -> -1; "0" -> null; else -> return@listener }
if ((keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey && viewModel.zoomSongText(steps)) keyEvent.preventDefault()
…
if (keyEvent.defaultPrevented || keyEvent.ctrlKey || keyEvent.metaKey || keyEvent.altKey || isTypingTarget(keyEvent)) return@listener
val isSpace = when (keyEvent.code) { "Space" -> true; "KeyM" -> false; else -> return@listener }
```

The comments explaining the rules ("Alt is left out because AltGr arrives as Ctrl + Alt on Windows", "Shift is not:
the plus of a US layout is Shift + equals") are duplicated too, and nothing checks the two agree; adding a shortcut
(the root `CLAUDE.md` already lists Ctrl/Cmd+S, which is per-surface and stays so) means editing both shells by hand.

## Fix

1. Add `ui/AppShortcuts.kt` (common, pure):
   ```kotlin
   /** A key as the shells can name it on every platform: a physical key on the desktop, `code`/`key` on the web. */
   internal enum class ShortcutKey { F, PLUS, MINUS, ZERO, SPACE, M }
   internal sealed interface AppShortcut {
       data object OpenSearch : AppShortcut
       data class ZoomSongText(val steps: Int?) : AppShortcut      // null resets
       data class ToggleMetronome(val isSpace: Boolean) : AppShortcut
   }
   /** The shortcut a key-down is, or null. Ctrl or Cmd, never with Alt (AltGr arrives as Ctrl + Alt on Windows), for F
    *  and the zoom keys, Shift allowed (the plus of a US layout is Shift + equals); Space and M with neither Ctrl, Cmd
    *  nor Alt (Shift is not looked at, on either shell, today). */
   internal fun appShortcutOf(key: ShortcutKey, isCtrlOrMeta: Boolean, isAlt: Boolean): AppShortcut?
   /** Carries [shortcut] out; true if it did something, which is when the shells consume the key. */
   internal fun CampfireViewModel.perform(shortcut: AppShortcut): Boolean = when (shortcut) {
       AppShortcut.OpenSearch -> openCurrentSearch()
       is AppShortcut.ZoomSongText -> zoomSongText(shortcut.steps)
       is AppShortcut.ToggleMetronome -> toggleMetronomeByKey(shortcut.isSpace)
   }
   ```
2. Desktop: map `Key` → `ShortcutKey` (`F`; `Equals`/`Plus`/`NumPadAdd` → `PLUS`; `Minus`/`NumPadSubtract` → `MINUS`;
   `Zero`/`NumPad0` → `ZERO`; `Spacebar` → `SPACE`; `M`), then
   `appShortcutOf(...)?.let { if (it is ToggleMetronome && isMetronomeKeyRepeat) false else perform(it) }`, keeping the
   key-repeat rule and the order of checks (F, zoom, Space/M, then Escape). Today's zoom branch returns `false` for
   **any** other key-down with Ctrl or Cmd and without Alt, before the Escape branch — so this handler never acts on Ctrl+Escape or
   Cmd+Escape (no `navigateBack`, no exit question from here). That is observable and must stay: after the `appShortcutOf` dispatch,
   keep `if (keyEvent.type == KeyDown && (isCtrlPressed || isMetaPressed) && !isAltPressed) return false` ahead of the
   Escape branch. Likewise a Ctrl+F whose `openCurrentSearch()` returns false returns false at once, as today.
3. Web: each listener keeps its phase, its `preventDefault` and its own guards (`defaultPrevented`, `isTypingTarget`,
   `repeat` for the metronome; capture phase for search and zoom), and maps the event to a `ShortcutKey` the way it
   does today — search by `code == "KeyF"` or `key` `f`/`F`; zoom by `key` (`+`/`=` → `PLUS`, `-`/`_` → `MINUS`, `0` →
   `ZERO`, because the browser's own zoom goes by `key` and the Hungarian plus is Shift+3); metronome by `code`
   (`Space`, `KeyM`) — then calls `appShortcutOf` and acts only on the kind that listener handles (`perform` returns
   whether to `preventDefault`). The Ctrl+wheel and touchpad-pinch handling in `SongTextZoomEffect` is not a key
   shortcut and stays as it is.
   One commit.

## Tests

`AppShortcutsTest`: F with Ctrl → `OpenSearch`, with Cmd → `OpenSearch`, with Ctrl+Alt → null, without modifier →
null; `PLUS`/`MINUS`/`ZERO` with Ctrl → `ZoomSongText(1/-1/null)`, with Alt → null, without Ctrl → null; `SPACE`/`M`
without modifiers → `ToggleMetronome(true/false)`, with Ctrl, Cmd or Alt → null (Shift is not a parameter, so Shift+Space
is a toggle, as on both shells today).

## Manual check

Desktop: Ctrl+Escape and Cmd+Escape on a pushed screen and on the songs screen behave exactly as before the change
(this handler does not consume them: no navigation and no exit question from here).

Desktop app and web build: Ctrl/Cmd+F on Songs, Setlists and the import screen (search opens or takes the caret; in
the browser, on Settings, the browser's find bar opens); Ctrl/Cmd + plus, Shift+equals, minus, zero on a song (text
size steps and resets; on the web the page does not zoom); Space on the Metronome tab and M on a song start and stop the
click, a held key toggles once, Space typed into a search field types a space.
