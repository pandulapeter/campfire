<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — androidMain

The Android shell, called by `:app:android`.

- `ui/CampfireAndroidApp.kt` keeps the system bar icon colors in step with the app's theme (`enableEdgeToEdge`), since
  it may differ from the system's. `onAppIconChanged` reports the theme color once preferences are read
  (`AppIconSwitcher` in `app/android`). The `urlOpener` gets the dark theme flag (Custom Tabs colors) and returns
  whether anything opened the link (`Message.LinkNotOpened` otherwise).
- `update/AppUpdate.android.kt` and `platform/SyncNotificationPermission.kt` live here rather than in `:app:android`
  because a composable can get the activity, a result launcher, and the lifecycle itself.
- **Notification permission** is asked the moment sync becomes connected: a foreground service started without it keeps
  the run alive but silently drops its notification, so the run can't be followed or stopped from outside. The answer is
  ignored (a refusal costs only the notification); asked once per launch while connected and missing, and Android stops
  showing it after a second refusal. The metronome's media session notification is exempt.
