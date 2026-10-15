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

### `LanguageNames.kt`

- Language names come from the platform's CLDR, never a shipped list (a library may hold any language). The web actual
  may be missing at runtime and answers null, like an unknown code.

### `NumericImeOptions.kt`

- iOS uses `UIKeyboardTypeNumbersAndPunctuation`: the number pad `KeyboardType.Number` maps to has no return key.

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

### `SharedTextImport.kt`, `SharedTextName.kt`, `SharedTextPayload.kt`, `ShareInboxManifest.kt`

- Pure helpers for text that arrives from outside (paste, drop, both phones' shares), tested in `commonTest`.
- A name made from what arrived (`cleanedForFileName`) is `LibraryFiles.normalizedName`, the rule every library name
  follows; null where the text has no letter or digit, so the next fallback applies rather than the rule's own. A
  text of nothing but links is passed on empty, reported as skipped.
- The iOS extension's `ShareInboxWriter.fileName` cannot call it, so the copy it writes is only lowercase, underscored,
  free of separators, control, invisible, and bidirectional characters and leading dots, and at most 200 bytes.
- `ShareInboxManifest` is the `share.json` the iOS share extension writes (`app/ios/CLAUDE.md`), decoded here so the
  test runs on the desktop: unknown fields ignored, a newer `version` left in the inbox, anything else null. The
  extension's Swift writes it; keep the two in step.

### `SyncConsentOrigin.kt`

- The `expect`s keep where Connect was tapped across a consent page, see `ui/sync/CLAUDE.md`.

### `FilePicker.kt`

- `saveFile` returns false for a dismissed dialog and throws for a failed write (the view model reports it).
- A `CreateDocument` that cannot be filled is deleted (it is created empty). Unreadable handed-over documents
  (`toImportedFiles`, desktop `readAsImportedFiles`) are passed on empty so the import reports them as skipped.
- **Every picker must answer on every way its screen can go away** (an iOS sheet swiped off included):
  `ExportController.launchFileTransfer` runs one transfer at a time and ignores others, so one that never answers
  blocks import and export for good.

### `ClipboardText.kt`

- Read only on a tap, never to decide whether to show the entry (the web's read prompts). Android asks the clip's
  description first (no toast) and takes no URI-only clip; iOS asks `hasStrings` first, and text that comes back nil is
  `Refused` (Allow Paste); the web's `readText()` refusal points at Ctrl / Cmd + V, whose `paste` event needs no
  permission. The desktop's `awtClipboardText` also serves its Ctrl / Cmd + V.

### `CompactKeyboardEffect.kt`

- Android only: while typing in a short window, hides the status bar of the window it is in (the dialog's own or the
  activity's), swipeable back as a transient bar, and restores what it found afterwards. A landscape keyboard leaves the
  smallest phone ~150dp, a third of which the status bar would take.

### The platform's side

- `MetronomeNotifier` (`BackgroundMetronome.kt`) is provided like `SyncNotifier`, and only told a click ended once it
  was shown.
- `BeatHaptics.kt`: web vibration only where `navigator.vibrate` can actually vibrate (Chrome on Android; never Safari
  or desktop browsers, which define it with nothing to vibrate); null on desktop. Driven on `beatHapticsDispatcher`
  (off the main thread on Android, so composition does not delay a beat) while resumed, and on Android while composed at
  all (`areBeatHapticsFeltInBackground`: the foreground service lets it vibrate).
