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

- The system bar icon colors follow the app's theme (`enableEdgeToEdge`), which may differ from the system's. The
  `urlOpener` returns whether anything opened the link (`Message.LinkNotOpened` otherwise).
- **Install referrer**: read once, on a first launch only, off the main thread, given up on after ten seconds. Only a
  `campfire_link=<payload>` that `ShareLinks` reads is taken; Play's own `utm_source` and campaigns are ignored, as is a
  device without Play. No flag is kept: the first run's preferences write already ends first launches.
- `SystemKeyboardShortcutsEffect` hands the activity the shortcuts for Meta + / as `KeyboardShortcutGroup`s, in the
  app's language, whenever they or the features they depend on change.
- `update/AppUpdate.android.kt` and `platform/SyncNotificationPermission.kt` live here rather than in `:app:android`
  because a composable can get the activity, a result launcher, and the lifecycle itself.
- **Notification permission** is asked the moment sync becomes connected: a foreground service started without it keeps
  the run alive but silently drops its notification, so the run can't be followed or stopped. The answer is ignored (a
  refusal costs only the notification); asked once per launch while connected and missing, and Android stops showing it
  after a second refusal. The metronome's media session notification is exempt.
- **File picker**: a singleton the launchers of every composition attach to, so a result survives Activity recreation
  under the picker. Launchers are taken at launch time, not when the pick began (a recreated Activity has unregistered
  the old ones). A result after *process* recreation is still acted on: picks join `filesToImport`, exports are filled
  from `cacheDir/pending_export`, which is the export's own file moved there (a rename, no copy), as a share's is moved
  into `cacheDir/shared`. A song is exported as `application/octet-stream`: storage providers append `.txt` to a
  `.cho` saved as text.
- **Pictures picked for a view model that is gone** (process death, or any Activity destroyed outside a configuration
  change, which clears it: "Don't keep activities" on every trip to the picker) have launchers of their own and never
  join the import: `OrphanedPicturesEffect` puts them into the editor where it tops the restored back stack (its Image
  button is the picture picker there), or into the missing picture whose Add image picked them, which the view model's
  saved state names while its picker is up (`PendingMissingImagePick`), if the song on top still names it; else lets them
  go with a log line.
- **An attachment picked so** is read at once (its grant ends with the Activity) and, once the saved sheet's
  restoration has settled (`SavedDialogRestorer.isSettled`), `OrphanedAttachmentsEffect` adds it to the reopened
  Attachments sheet, else to the editor on top; anywhere else, and in read only mode, it is let go with a message asking
  for it again.
- **Drops** (from another app in split screen, a desktop window, or the taskbar): URIs are read under
  `requestDragAndDropPermissions`, asked while the drop is answered and released after the read; text items are a pasted
  song. A drag's description can't tell a text file from a text, so a `text/plain`-only drag starts only where
  `isNewSongDropAvailable`.
- **Archives while they are staged** (`ImportedFiles.android.kt`): each URI is opened once through
  `acquireUnstableContentProviderClient(uri).openFile`, never `openInputStream`, whose stable reference takes Campfire
  down with a dying provider; its head decides, and an archive's copy starts at once from that open stream, which reads
  on after the grant (the activity's, or a drop's permissions) is gone. A copy shorter than `OpenableColumns.SIZE` is
  unreadable (a dead provider's pipe reads as a clean end).
