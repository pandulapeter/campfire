<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — iosMain

The iOS shell, called by `:app:ios`.

- `iosMain/ui/CampfireIosApp.kt` — `CampfireIosApp(urlOpener, filePicker, filesToImport)` hosts `CampfireApp`. Takes the
  URL opener and the file picker as parameters so the module stays free of UIKit. `onUiModeChanged` hands the theme
  preference out to the app module, which sets the window's interface style from it so that the status bar, the system
  sheets and the keyboard match the app; it carries the preference rather than the resolved dark flag, because on iOS
  the system's mode is read from the trait collection that override changes, and "System default" has to leave it free
  to follow the phone. `onAppIconChanged` hands out the color the home screen icon should be in, once it has held still
  for a second while the app is resumed and again at every resume, since iOS answers each switch with an alert and
  refuses one while the app is not active (see `app/ios`). Back navigation (including the swipe back gesture) is handled
  by Navigation 3 in `CampfireApp`.
