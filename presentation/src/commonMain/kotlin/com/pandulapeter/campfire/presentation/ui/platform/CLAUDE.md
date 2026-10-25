<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/platform

The `expect` declarations and the interfaces the platform shells provide.

### `CalendarLocale.kt`

- `rememberDatePickerState` uses the system locale, so the setlist date dialog builds its own `DatePickerState` on
  `calendarLocale`, saves the unconfirmed day itself (the state is not saveable), and draws its own title and headline.
- Typed entry is off (`showModeToggle = false`): its strings are Material's, in the system language.

### `Platform.kt`

- `isDesktopPlatform`: a long press on a song row opens nothing; also decides the interface scale.
- `canAskForDonations` is false on Apple platforms: guideline 3.1.1 allows only in-app purchase.
- The desktop actuals of these and `dynamicColorSchemePair` read `PlatformImpersonation` (desktopMain) on
  every access: `tools/screenshots` sets it to draw other platforms; the desktop app never does.

### `LibraryPersistence.kt`

- `requestLibraryPersistence()` is asked once, by `PreferencesController` when the view model is created (asked by the
  settings screen, its row popped in mid transition). On the web it is `navigator.storage.persist()`, which stops
  eviction of the only copy of the library; ask exactly once, since some browsers prompt the user.
- `isAppAvailableOffline()` reads `window.campfireSavedForOffline` from `index.html` (see `app/web`).

### `BackgroundSync.kt`

- `SyncNotifier` tells Android / iOS a run is going so the process is not stopped or suspended. `CampfireApp`
  resolves the strings (in-app language, not the system's) and hands them over once per run or when the words change;
  the shells keep the count moving themselves.
- The run started by leaving the app (`AppExitController.onAppPaused`) is handed over from the ON_PAUSE callback
  itself, not through state: turning the screen off pauses and stops with no frame in between, and shells can only be
  told while in front.

### `SyncNotificationScheduler.kt`

- Shared, tested timing for both phone shells: progress throttled to one post per interval, and the keep-alive released
  only after a 2 s grace with no run, so a chained run can start in it.

### Text from outside (`SharedText*.kt`, `ShareInboxManifest.kt`)

- `ShareInboxManifest` is the `share.json` the iOS share extension writes (`app/ios/CLAUDE.md`), decoded here so the
  test runs on the desktop: unknown fields ignored, a newer `version` left in the inbox, anything else null. The
  extension's Swift writes it; keep the two in step.

### `SyncConsentOrigin.kt`

- The `expect`s keep where Connect was tapped across the web's page load (JSON, since the export's origin carries
  its item), see `ui/sync/CLAUDE.md`.

### `FilePicker.kt`

- `saveFile` returns false for a dismissed dialog and throws for a failed write (the view model reports it).
- `shareText` (a shared link) exists only where `canShare`: Android's `ACTION_SEND`, iOS's share sheet with an `NSURL`.
- A `CreateDocument` that cannot be filled is deleted (it is created empty). Unreadable handed-over documents
  (`toImportedFiles`, desktop `readAsImportedFiles`) are passed on empty so the import reports them as skipped.
- **Every picker must answer on every way its screen can go away** (an iOS sheet swiped off included):
  `ExportController.launchFileTransfer` runs one transfer at a time and ignores others, so one that never answers
  blocks import and export for good.

### `ProcessLog.kt`

- Every shell calls `installProcessLog` before anything else of the app's; `ProcessLogWriter` (common, tested) stamps
  lines and rotates at line starts, keeping the current file and the one before. Diagnostics stay plain `println`s;
  actions are recorded where they take effect, never per button: `logBreadcrumbs` (`ui/state`) for the start, screens
  and dialogs (class names, kept through R8 by `app/android/proguard-rules.pro`), changed setting names
  (`changedPropertyNames`, from `toString`, nested ones dotted, values never), the export format, the metronome, the tuner, imports, and the song filter;
  `MessageSink.launchLibraryChange`'s `action` for every library write; exports, share links, searches opened, and
  sync at its own sources, counts only: the connection's state, why a run starts and retries, each pass's plan, the
  synced settings and shared copies steps, the change feed (`Sync feed:`), and the shared links list (`Sharing:`), with
  the Share screen's link state. Kinds, counts, and names; never typed text or a song's content.
- Android and desktop (`jvmSharedMain`): `System.out` / `System.err` mirrored into `campfire.log`, in
  `noBackupFilesDir` on Android, the data directory on desktop, flushed once per finished line, so the lines before an
  uncaught exception are in the file; the previous uncaught exception handler still runs.
- iOS: stdout and stderr dup'ed onto a **non-blocking** pipe read by its own thread (a stalled reader drops output,
  never blocks a `println`), into the caches directory (never backed up); an uncaught exception is written by the
  thread it ends. Not installed by the share extension.
- Web: the console methods and uncaught errors, into `localStorage` (UTF-16 in a few MB for the site, which the 256 KB bound fits), stored
  a second after a burst and on `pagehide`. Tabs share it; the last to store wins.
- Sent by `ExportController.sendProcessLog`, under a line naming the version and build, after `SendProcessLogDialog`
  (`ui/dialogs`), whose text is the privacy warning: attached to a mail to `DEVELOPER_EMAIL` where `canEmailFiles`
  (Android's send with a `mailto:` selector, iOS's Mail compose sheet where Mail has an account), else shared or saved,
  the dialog then naming the address, with a Copy address button (a `mailto:` carries no attachment on desktop or the
  web). The JVM's
  `withoutUserName` replaces the home directory with `~` and the login name in other paths. Song names stay: they are
  what makes a report readable, and a hash of a known song's name is no secret.
- **Crashes**: an uncaught exception leaves `campfire.crash` beside the log (JVM, iOS); the next launch asks once
  (`FirstRunController.offerCrashReport`, waiting like What's new), and either answer forgets it
  (`forgetPendingCrashReport`). Never on the web, whose uncaught errors are mostly not the app's and do not end it.

### `ClipboardText.kt`

- Read only on a tap, never to decide whether to show the entry (the web's read prompts). A refusal is
  `ClipboardText.Refused`; the web's points at Ctrl / Cmd + V, whose `paste` event needs no permission.
- Written through `Clipboard.setClipEntry` with `plainTextClipEntry` (`Clipboard.kt`), each platform building the entry
  its own way; only Android 13+ confirms a copy itself (`isCopyConfirmedBySystem`).
- `ClipboardLinkReader.kt` is iOS's alone: it can say a web address seems to be there without reading it, which is
  the one question asked without a tap (the first run's link offer).

### `CompactKeyboardEffect.kt`

- Android only: while typing in a short window, hides the status bar of the window it is in (the dialog's own or the
  activity's), swipeable back as a transient bar, and restores what it found afterwards. A landscape keyboard leaves the
  smallest phone ~150dp, a third of which the status bar would take.

### The platform's side

- `MetronomeNotifier` (`BackgroundMetronome.kt`) is provided like `SyncNotifier`, and only told a click ended once it
  was shown.
- `BeatHaptics.kt`: web vibration only where `navigator.vibrate` can vibrate; on Android felt while composed, not only
  resumed (`areBeatHapticsFeltInBackground`). Every heard click is felt, at three strengths (`BeatTap`): accent, beat,
  and a lighter subdivision.
