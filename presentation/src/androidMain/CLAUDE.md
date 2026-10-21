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
- **Install referrer** (`platform/InstallReferrer.kt`, Play's Install Referrer library): read once, on a first launch
  only (`FirstRunController.importInstallReferrerLink`), off the main thread, and given up on after ten seconds. Only
  `campfire_link=<payload>` that `ShareLinks` reads is taken (`installReferrerLink`); Play's own `utm_source` and
  campaigns are ignored, as is a device or install without Play. No flag is kept: the first run's preferences write
  already ends first launches.
- `update/AppUpdate.android.kt` and `platform/SyncNotificationPermission.kt` live here rather than in `:app:android`
  because a composable can get the activity, a result launcher, and the lifecycle itself.
- **Notification permission** is asked the moment sync becomes connected: a foreground service started without it keeps
  the run alive but silently drops its notification, so the run can't be followed or stopped from outside. The answer is
  ignored (a refusal costs only the notification); asked once per launch while connected and missing, and Android stops
  showing it after a second refusal. The metronome's media session notification is exempt.
- **File picker**: a singleton the launchers of every composition attach to, so a result survives Activity recreation
  under the picker. Launchers are taken at launch time, not when the pick / export began (a recreated Activity has
  unregistered the old ones). A result after *process* recreation is still acted on: picks join `filesToImport` in
  `CampfireAndroidApp`, exports are filled from `cacheDir/pending_export`. A song is exported as
  `application/octet-stream`: storage providers append `.txt` to a `.cho` saved as text.
- **Drops** (`ui/LibraryDropTarget.kt`): from another app in split screen, a desktop window, or the taskbar. URIs are
  read under `requestDragAndDropPermissions`, asked while the drop is answered and released after the read; text items
  are a pasted song. A drag's description can't tell a text file from a text, so a `text/plain`-only drag starts only
  where `isNewSongPasteAvailable`.
