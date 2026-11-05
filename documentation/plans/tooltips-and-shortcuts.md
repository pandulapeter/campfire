<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keyboard shortcuts: what is left

The tooltips, the shortcut table, and the hints are in (`ui/components/CLAUDE.md`, the `code-style` skill): every
shortcut is a `KeyShortcut` (`ui/components/shortcuts`), matched through it, and the control it presses names it, so
its tooltip or menu entry shows the keys where there is a hardware keyboard. App-wide ones go through `appShortcutOf`,
heard at the app's root on every platform (`ui/AppKeyboardShortcuts.kt`); the web's shell answers the keys its browser
acts on first (`LocalKeysLeftToShell`). What follows is what remains.

## Decided

- **A platform column joins the table** with the first shortcut a platform cannot have (Ctrl+N on the web): a hint
  appears only where its shortcut works.
- **Apple's keys**: `isAppleKeyboard` (`ui/platform`) decides how keys are written; `multi-caret-editor.md`'s
  `isMacKeymap` asks nearly the same, so whichever lands second reuses the first.
- **On iOS**, the shortcuts are also listed for the system's Command-hold overlay (`UIKeyCommand`).
- **Navigation items get tooltips with the new shortcuts**: an item that shows its label gets one naming only its
  `Ctrl+1` … `Ctrl+6` key (on desktop and web, where those work).

### A sheet that lists every shortcut

- **Keyboard shortcuts sheet**: opened with `Ctrl+/` (and `?` outside text fields), and from Settings → About when the
  device has a hardware keyboard or is a desktop or web build. It lists the shortcuts that work on the current
  platform, grouped by screen, and covers what no button shows: arrows and Page Up / Page Down, Space and M, Esc,
  Home / End in the print preview, and Ctrl + scroll.
- **Android's system list (Meta+/)** is filled from the same table (`onProvideKeyboardShortcuts`).
- The print preview's keys and the song's arrows join the table with the sheet, which is what lists them.

### New shortcuts

| Keys                       | Where                         | Does                                      |
|----------------------------|-------------------------------|-------------------------------------------|
| Ctrl+N                     | Songs, Setlists               | New song / New setlist                    |
| Ctrl+Z, Ctrl+Shift+Z / Y   | Song editor                   | The editor's own Undo / Redo history      |
| Enter / Shift+Enter, F3 / Shift+F3 | Editor search         | Find next / previous                      |
| Ctrl+A                     | Songs, selection mode         | Select all                                |
| Ctrl+S                     | Setlist                       | Share or export, as song details has it   |
| Ctrl+Shift+↑ / ↓           | Song details                  | Transpose up / down                       |
| T                          | Metronome tab, song details   | Tap tempo (same rules as M)               |
| Ctrl+1 … Ctrl+6            | Main screens (desktop, web)   | Switch to the n-th visible main screen    |
| Ctrl+,                     | Anywhere (desktop, web)       | Settings                                  |
| Ctrl+/                     | Anywhere                      | The keyboard shortcuts sheet              |

Nothing that deletes gets a shortcut. On the web, each new shortcut that the browser also uses (Ctrl+1 … 6,
Ctrl+,, Ctrl+A, Ctrl+Y, and F3) has its default blocked only while the app handles that key, the way
`EditShortcutEffect` does today. Chrome lets a page block all of these, but never Ctrl+N, which always opens a new
window before the page sees it (tested in Chrome 157 on macOS with real key presses, November 2026). So the web build
leaves out Ctrl+N and hides its hint; New stays one click away in the list's header.

## Order of work

1. The new shortcuts, each a table entry with its hint on the control it presses, and the navigation items' tooltips.
2. The shortcuts sheet, Android's system list, and iOS's `UIKeyCommand` list.
3. A way to run the UI tests impersonating the desktop (they impersonate Android, which has no keyboard), and a test
   that hovering an icon button shows its label and its keys. Update the CLAUDE.md files of the packages touched.
