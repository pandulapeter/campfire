<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/shortcuts

The launcher shortcuts: Android's dynamic shortcuts and iOS's Home Screen quick actions, a long press on the app icon.

- `LauncherShortcut.offered` decides the list and its order: New song and Paste a song (songs on, read only mode off,
  as their buttons), then the main screens switched on in the navigation's order, never the home screen (the icon opens
  it) nor Settings. Systems show the first four or so, so the screens fill what is left.
- Dynamic on both platforms, never static: the set follows the switches and read only mode, the labels the in-app
  language. `LauncherShortcutsEffect` hands the shells the list once the preferences are read and on every change; the
  shells publish it (`app/android`'s `LauncherShortcutPublisher`, `app/ios`'s `IosQuickActions.kt`). Desktop and web
  have none.
- Each opens its `link`, an app link (`navigation/CLAUDE.md`). Android's icons are the app's own glyphs as adaptive
  icons in `:app:android` (`ic_shortcut_*`, a launcher takes no `ImageVector`); iOS's are SF Symbols.
