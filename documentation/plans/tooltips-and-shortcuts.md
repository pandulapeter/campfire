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
its tooltip or menu entry shows the keys where there is a hardware keyboard. The table has a platform column
(`ShortcutPlatforms`): a shortcut matches and shows its hint only where it works. App-wide ones go through
`appShortcutOf`, heard at the app's root on every platform (`ui/AppKeyboardShortcuts.kt`); the web's shell answers the
keys its browser acts on first (`LocalKeysLeftToShell`), blocking the browser only where the app used the key. The
shortcuts Ctrl+N (not on the web), the editor's undo and redo, its search's F3 and Enter, Select all, a setlist's
Ctrl+S, transposing, T, Ctrl+1 … 6, and Ctrl+, are in, and the navigation items' tooltips name their keys. Apple's keys
are written as `isAppleKeyboard` (`ui/platform`) decides; `multi-caret-editor.md`'s `isMacKeymap` asks nearly the same,
so whichever lands second reuses the first. What follows is what remains.

## Done

The shortcuts sheet (`ui/dialogs/shortcuts`, Ctrl / Cmd + slash or `?`, Settings → About where there is a keyboard),
Android's Meta + / list, and iOS's Command-hold overlay (`app/ios`) are in, all filled from the table's scope column
(`ui/components/CLAUDE.md`). The song's arrows and Page Up / Page Down, the print preview's keys, and Escape are table
entries.

## Order of work

1. A way to run the UI tests impersonating the desktop (they impersonate Android, which has no keyboard), and a test
   that hovering an icon button shows its label and its keys. Update the CLAUDE.md files of the packages touched.
