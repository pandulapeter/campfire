<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/sync

- A consent's answer is shown only onto a stack nobody has built on since start (home, Settings, or an owed onboarding
  still on page one, which is moved to its Dropbox page), where Connect was tapped (`SyncConsentOrigin`; Settings'
  Sharing tab is `Sharing`, since the tab Settings was left on lasts only the session). The export
  screen (`SyncConsentOrigin.Export`, carrying its `ShareTarget`) is reopened on Link over whatever stack the start up
  has, once the library holds the item (`SavedDialogRestorer.reopenExport`). `SyncConsentOrigins` keeps the origin in
  the web's session storage (`ui/platform`), where every consent ends in a fresh start, and in the saved state, where
  Android restores the back stack but not the export screen, a dialog. The answer's screen is shown when the redirect is found, not when the
  exchange ends, and shows `Connecting` until it does.
- `rememberSyncNotifications` only reports "nothing to show" after showing something; otherwise opening the app onto a
  background run would stop the Android service.
- `SyncLifecycleEffects`: a run on every ON_START but the first (the launch's run is the restore's), and the folder
  watched while STARTED. `SyncProgressBar` is the only sign of a run outside Settings: a trackless line along the top
  of the window, over everything `CampfireApp` draws, for every run, automatic ones included.
- On a launch with no preferences document the stored credentials are forgotten inside `startRestoring`'s coroutine,
  before `restore` reads them.
