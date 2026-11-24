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

- `LauncherShortcut.offered` decides the list, in the owner's order: Paste a song, Metronome, Tuner, Setlists, and New
  song, each only where it is offered (the two song actions with the songs on and read only mode off, as their buttons;
  a screen while switched on and not the home screen, which the icon opens). Nothing else is one: not Chords, Scales,
  or Settings. iOS and most launchers show four, so New song is the one cut there.
- Dynamic on both platforms, never static: the set follows the switches and read only mode, the labels the in-app
  language. `LauncherShortcutsEffect` hands the shells the list once the preferences are read and on every change; the
  shells publish it (`app/android`'s `LauncherShortcutPublisher`, `app/ios`'s `IosQuickActions.kt`). Desktop and web
  have none.
- Each opens its `link`, an app link (`navigation/CLAUDE.md`). Android's icons are the app's own glyphs as adaptive
  icons in `:app:android` (`ic_shortcut_*`, a launcher takes no `ImageVector`); iOS's are SF Symbols.
