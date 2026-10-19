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

- A consent's answer is shown only onto a stack nobody has built on since start (Songs, Settings, or an owed onboarding
  still on page one, which is moved to its Dropbox page), where Connect was tapped (`SyncConsentOrigin`). Only the web
  keeps the origin across the page load (session storage, `ui/platform`), since only there does every consent end in a
  fresh start; Android restores its back stack.
- `rememberSyncNotifications` only reports "nothing to show" after showing something; otherwise opening the app onto a
  background run would stop the Android service.
- Forgetting happens inside `startRestoring`'s coroutine, before `restore` reads the credentials.
