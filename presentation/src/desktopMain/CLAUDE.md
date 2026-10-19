<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — desktopMain

The desktop shell, called by `:app:desktop`.

## `ui/CampfireDesktopApp.kt`

- Dropped files are imported (a folder stands for the files in every folder inside it, to
  `ImportLimits.MAX_FOLDER_DEPTH`; hidden entries are left out, unknown names peeked at and read only as an archive or a
  text (never a photo or a recording by its name), else reported as skipped, as in a zip). A drop whose file list can't
  be read is refused, never thrown: an exception out of the target skips the end of the drag session and the window
  refuses every later drag. Dropped text is a pasted song, accepted only where `isNewSongPasteAvailable`; a file list
  wins. Read only mode refuses both (`isDropAvailable`).
- Links (`openUrl` in `ui/platform/SystemBrowser.desktop.kt`) go through `java.awt.Desktop`, falling back to `open`,
  `rundll32`, or `xdg-open` where AWT has no desktop or `browse` fails. On macOS and Windows anything but a web page
  (the microphone and sound settings pages) always uses the system command, since `browse` hands every address to the
  default browser. A link nothing opens shows `Message.LinkNotOpened`. The same opener is the Koin
  `DesktopSystemBrowser` the sync authorizer opens the consent page with.
- File picker: a name typed without extension gets the offered one back, unless a file already has that name.

## Keys (`CampfireViewModel.handleKeyEvent`, wired by `:app:desktop`)

- Which keys are shortcuts is one common, tested table for desktop and web (`ui/AppShortcuts.kt`: `appShortcutOf`, then
  `perform`); each shell only names its keys.
- **Escape**, in order: dismiss what is open on top, close the top screen's search, pop the back stack, or ask
  `DialogType.ConfirmExit` (an extra Escape is a habit, not a decision; the close button and system quit don't ask).
  - "Open on top" is `visibleDialog` plus `overlayState.isAnyMenuOpen` (`LocalOverlayState`, also given to sheets a
    screen hosts itself, like the editor's search). A window key handler runs before Compose turns Escape into a back
    event, so missing one here pops the stack behind the modal.
  - Search before the back stack (`isCurrentSearchOpen`, the Chords tab's query included, which is emptied): selecting
    a tab rebuilds the stack with the songs under it, so Escape would otherwise leave the tab with its search open.
  - `handlePreviewKeyEvent` swallows held-Escape repeats, which would close every screen and then the app.
- **Ctrl/Cmd + F** → `openCurrentSearch` (opens it, or refocuses an open one with its text selected), not under a
  dialog, sheet, or menu. It must be a window handler: nothing on a list screen is focused while its search is closed.
- **Ctrl/Cmd + plus (or equals), minus, zero** → `zoomSongText` on the song details screen (`isSongTextZoomable`).
- **Ctrl/Cmd + V** → `newSongFromClipboard` where `isNewSongPasteAvailable`; a focused field takes its paste first.
