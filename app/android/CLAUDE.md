<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:android

Android application entry point. The only Android module that knows about implementation modules.

- `CampfireAndroidApplication` — starts Koin with `dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule + domainModule + presentationModule`. Add new Koin modules here.
- `CampfireActivity` — single `AppCompatActivity`, edge-to-edge, hosts `CampfireAndroidApp` and opens links via Custom Tabs (colored to match the theme the composable reports). System bar appearance is handled inside `CampfireAndroidApp`. It also receives the files the system hands over: `ACTION_VIEW` ("open with") and `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (shared to Campfire), read off the main thread and passed to the UI through a `Channel`. The activity is `singleTask`, so a second file opened while Campfire is running arrives at `onNewIntent` rather than at a new instance.

`AndroidManifest.xml` registers Campfire **only** for the ChordPro extensions (`.cho`, `.chopro`, `.chordpro`, `.crd`, `.chord`, `.pro`), with a wildcard MIME type so that the `pathPattern`s are what actually decide — a `content://` URI has no extension in the eyes of the intent resolver unless a type is declared. Zip and plain text are deliberately not registered: the app reads one when it is handed over, but an app that claims them system wide answers for every archive and note on the device. The share filter is narrowed to `text/plain` for the same reason. Keep this list in step with `LibraryFiles.SONG_EXTENSIONS`.

It also registers the `campfire://oauth` scheme and holds the app's only `INTERNET` permission, both for sync: the consent page opens in the user's own browser (never a WebView — a page asking for a password has to be somewhere the address bar is visible) and the redirect comes back as an `ACTION_VIEW` intent, which `CampfireActivity.handle` tells apart from a file being opened and forwards to `onSyncRedirectReceived`. Nothing tells an app that the user closed a browser it launched, so the authenticator also watches for the app coming back to the foreground without a redirect and treats that as a cancellation.

`sync/CampfireSyncService` is a foreground service that keeps the process alive for as long as a sync run lasts and shows the run as a notification with a Stop action. It does **no syncing of its own** — the run lives in `SyncRepository`, a singleton that outlives every screen — it only tells Android that something worth keeping alive is going on. Every string arrives in the intent rather than from `res/`, so the notification follows the language chosen inside the app rather than the system's. The activity starts and stops it through the `SyncNotifier` it hands to `CampfireAndroidApp`, which is also why the service can live here, where the manifest is.

A `FileProvider` (authority `${applicationId}.files`, paths in `res/xml/file_paths.xml`) lets a shared song leave the app's private storage as a content URI.

Build types: `debug` (`.debug` suffix, `internal.keystore`) and `release` (R8 + resource shrinking). The `internal` signing config is literal on purpose — it is the standard Android debug keystore, committed next to the build file, and there is nothing about it worth hiding. The `release` config reads `campfire.android.*` Gradle properties, which `gradle.properties` defaults to that same debug keystore so a fresh clone can build a release variant and get something installable; a real key goes in `local.properties`, which is never committed and overrides them (see the Build section of the root `CLAUDE.md`). Contains the app's `AndroidManifest.xml`, launcher icon, and themes and colors (`values/` + `values-night/`) — the only Android XML resources in the project; everything the Compose UI draws lives in `:presentation`'s `composeResources`.
