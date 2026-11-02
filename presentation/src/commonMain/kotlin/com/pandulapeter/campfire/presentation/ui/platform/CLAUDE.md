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

## Platform facts

- `rememberDatePickerState` uses the system locale, so the setlist date dialog builds its own `DatePickerState` on
  `calendarLocale`, saves the unconfirmed day itself (the state is not saveable), and draws its own title and headline.
  Typed entry is off: its strings are Material's, in the system language.
- `isDesktopPlatform`: a long press on a song row opens nothing; also decides the interface scale.
- `canAskForDonations` is false on Apple platforms: guideline 3.1.1 allows only in-app purchase.
- The desktop actuals of these and `dynamicColorSchemePair` read `PlatformImpersonation` on every access:
  `tools/screenshots` sets it to draw other platforms; the desktop app never does.
- `requestLibraryPersistence()` is asked once, when the view model is created (asked by the settings screen, its row
  popped in mid transition). On the web it is `navigator.storage.persist()`, which stops eviction of the only copy of
  the library; ask exactly once, since some browsers prompt the user.

## Background work

- `SyncNotifier` tells Android / iOS a run is going so the process is not stopped or suspended. `CampfireApp` resolves
  the strings (in-app language) and hands them over once per run or when the words change; the shells keep the count
  moving themselves. `MetronomeNotifier` is provided the same way, and told a click ended only once it was shown.
- The run started by leaving the app (`AppExitController.onAppPaused`) is handed over from the ON_PAUSE callback itself,
  not through state: turning the screen off pauses and stops with no frame between, and shells can only be told while in
  front.
- `SyncNotificationScheduler` (both phone shells, tested): progress throttled to one post per interval; the keep-alive
  released only after a 2 s grace with no run, so a chained run can start in it.
- `BeatHaptics.kt`: web vibration only where `navigator.vibrate` can vibrate; on Android felt while composed, not only
  resumed. Every heard click is felt, at three strengths (`BeatTap`).

## Files, text, and the clipboard

- `ShareInboxManifest` is the iOS share extension's `share.json` (`app/ios`), decoded here so the test runs on the
  desktop: unknown fields ignored, a newer `version` left in the inbox, anything else null. Keep it in step with the
  extension's Swift.
- `saveFile` returns false for a dismissed dialog and throws for a failed write.
- `shareText` exists only where `canShare`. `canShareFile` asks per kind of file, since a browser shares only its own
  list (Chrome: PDF, PNG, `.txt`; no zip or `.cho`); the web downloads a share the browser refuses (a press more than a
  few seconds old, as after a long PDF), a closed sheet excepted.
- A `CreateDocument` that cannot be filled is deleted (it is created empty). Unreadable handed-over documents are passed
  on empty so the import reports them as skipped.
- **Every picker must answer on every way its screen can go away** (an iOS sheet swiped off included):
  `ExportController.launchFileTransfer` runs one transfer at a time and ignores others, so one that never answers blocks
  import and export for good.
- The clipboard is read only on a tap, never to decide whether to show the entry (the web's read prompts). A refusal is
  `ClipboardText.Refused`; the web's points at Ctrl / Cmd + V, whose `paste` event needs no permission. Only Android 13+
  confirms a copy itself (`isCopyConfirmedBySystem`). iOS alone can say a web address seems to be there without reading
  it (`ClipboardLinkReader.kt`), the one question asked without a tap (the first run's link offer).
- `CompactKeyboardEffect` (Android only): while typing in a short window, hides the status bar of the window it is in
  (the dialog's own or the activity's), swipeable back, restoring what it found afterwards. A landscape keyboard leaves
  the smallest phone ~150dp, a third of which the status bar would take.

## The process log (`ProcessLog.kt`)

- Every shell calls `installProcessLog` before anything else of the app's; the writer rotates at line starts, keeping
  the current file and the one before. Diagnostics stay plain `println`s.
- Actions are recorded where they take effect, never per button: `logBreadcrumbs` for the start, screens and dialogs
  (class names, kept through R8 by `app/android/proguard-rules.pro`), changed setting names (`changedPropertyNames`,
  values never), the export format, the metronome, the tuner, imports, and the song filter;
  `MessageSink.launchLibraryChange`'s `action` for every library write; exports, share links, searches opened, and sync
  at their own sources, counts only (the connection's state, why a run starts and retries, each pass's plan, the synced
  settings and shared copies steps, the change feed `Sync feed:`, the shared links list `Sharing:`). Kinds, counts, and
  names; never typed text or a song's content.
- Android and desktop: `System.out` / `System.err` mirrored into `campfire.log` (`noBackupFilesDir` on Android), flushed
  per finished line, so the lines before an uncaught exception are in the file; the previous uncaught exception handler
  still runs.
- iOS: stdout and stderr dup'ed onto a **non-blocking** pipe read by its own thread (a stalled reader drops output,
  never blocks a `println`), into the caches directory (never backed up); an uncaught exception is written by the thread
  it ends. Not installed by the share extension.
- Web: the console methods and uncaught errors, into `localStorage` (the 256 KB bound fits its few MB of UTF-16), stored
  a second after a burst and on `pagehide`. Tabs share it; the last to store wins.
- Sent after `SendProcessLogDialog`, whose text is the privacy warning, under a line naming version and build: mailed to
  `DEVELOPER_EMAIL` where `canEmailFiles` (Android's send with a `mailto:` selector, iOS's Mail sheet where Mail has an
  account), else shared or saved, the dialog then naming the address with a Copy address button (a `mailto:` carries no
  attachment on desktop or the web). The JVM's `withoutUserName` replaces the home directory with `~` and the login name
  in other paths. Song names stay: they make a report readable, and a hash of a known song's name is no secret.
- **Crashes**: an uncaught exception leaves `campfire.crash` beside the log (JVM, iOS); the next launch asks once
  (`FirstRunController.offerCrashReport`, waiting like What's new), and either answer forgets it. Never on the web,
  whose uncaught errors are mostly not the app's and do not end it.
