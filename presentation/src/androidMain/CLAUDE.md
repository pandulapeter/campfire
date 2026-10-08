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

### `androidMain/ui/CampfireAndroidApp.kt`

`androidMain/ui/CampfireAndroidApp.kt` — `CampfireAndroidApp(urlOpener, filesToImport)` obtains the `CampfireViewModel`
with `koinViewModel()`, keeps the system bar icon colors in sync with the selected theme via `enableEdgeToEdge(...)`
(the app theme may differ from the system theme), and hands off to the shared `CampfireApp`. `onAppIconChanged` reports
the theme color once the preferences are read, which the activity switches the launcher entry to as the user leaves
(`AppIconSwitcher` in `app/android`). The `urlOpener` receives whether the dark theme is active so Custom Tabs can
match, and returns whether anything opened the link — one nothing opens is the desktop's `Message.LinkNotOpened`
snackbar.

`update/AppUpdate.android.kt` and `platform/SyncNotificationPermission.kt` are next to it, both here rather than in
`:app:android` because a composable can ask for everything they need (the activity, an activity result launcher, the
lifecycle) by itself. The notification permission is asked for the moment sync becomes connected — the only notification
of Campfire's that needs it (a playing metronome's is a media session's, which is exempt), and the first point at which
the question means anything — because a foreground service started without it keeps the run alive but has its
notification silently dropped, leaving a sync the user can neither follow nor stop from outside the app. A refusal costs
the notification, not the sync, so the answer is ignored: it is asked once per launch while sync is connected and the
permission is missing, and after the second refusal Android stops showing the dialog on its own.
