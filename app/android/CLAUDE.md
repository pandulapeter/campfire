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

### `CampfireAndroidApplication`

`CampfireAndroidApplication` — starts Koin through `:app:di`'s `startCampfireDependencyGraph`, handing it the
`androidContext` the file storage, the authenticator and the metronome's audio output are built from. The modules are
named in `:app:di`, not here.

### `CampfireMainActivity`

`CampfireMainActivity` — single `ComponentActivity` (no AppCompat or Material Components: nothing but Compose draws in
it), edge-to-edge, hosts `CampfireAndroidApp` and opens links via Custom Tabs (`UrlOpening.kt`, colored to match the
theme the composable reports) — except a `play.google.com` address, which is handed to the Play Store app by package and
only falls back to a Custom Tab where there is no Play. A link nothing can open (no browser at all) is reported back to
the UI, which says so in the app's language. System bar appearance is handled inside `CampfireAndroidApp`. It also
receives the files the system hands over: `ACTION_VIEW` ("open with") and `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (shared
to Campfire — files and text alike), read off the main thread and passed to the UI through a top level `Channel`
(`AndroidFileImport.kt`) — both the channel and the scope the files are read in belong to the process, so a recreated
activity in the middle of a read does not lose the import.

A file that cannot be read is passed on empty, so the import reports it as skipped rather than the app coming to the
front and saying nothing. **An intent is handled once**: a recreated activity (`savedInstanceState != null`, which is a
language or dark mode change as much as the system bringing the app back after killing it) and one reopened from Recents
(`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) are handed an intent that was acted on already, and skip it. The activity is
`singleTask`, so a second file opened while Campfire is running arrives at `onNewIntent` rather than at a new instance —
or, when there is no instance at that moment, once the system has recreated one. **Most configuration changes are
handled in place** (`android:configChanges`: orientation, the screen size and layout, density, font scale, keyboard and
navigation), since Compose follows them on its own and recreating would rebuild the whole app on every rotation, fold
and step of a window resize.

The locale and `uiMode` are left out on purpose: the in-app language's System option reads the JVM default locale, which
is not composition state, and the window background and splash colors come from the `values-night` theme, which only a
new window reads — so a system language or dark mode change still recreates the activity, and everything written for
recreation above still runs for those and for a reclaim. It declares `adjustResize`, because an unspecified soft input
mode is taken as `adjustPan` for a Compose window, and the pan would come on top of the app's own keyboard padding
(`WindowInsets.ime`); on an edge-to-edge window `adjustResize` resizes nothing and only passes the insets through.

`AndroidManifest.xml` has a narrow Open with filter for the ChordPro extensions (`.cho`, `.chopro`, `.chordpro`, `.crd`,
`.chord`, `.pro`), with a wildcard MIME type so that the path filters are what actually decide — a `content://` URI has
no extension in the eyes of the intent resolver unless a type is declared. Android 12 and later match the extension with
`pathSuffix`, in lower and upper case. Before that there is only `pathPattern`, whose `.*` stops at the first occurrence
of the next character and never backtracks, so each extension has one pattern per number of dots in the path, up to four
(`Hey.Jude.cho` in `Songs 2.0/` is three), and matching there is lower case only. `pathAdvancedPattern` is no
substitute: it never backtracks either, so `.*\\.cho` in it matches nothing. A URI with no file name in its path (the
Downloads provider's `msf:` ids, most attachments) cannot be matched by any of them. A second Open with filter accepts
`text/plain` and `application/octet-stream` content URIs without a path constraint, so those opaque document IDs can
reach Campfire.

It also offers Campfire for unrelated files under those MIME types; the import skips unsupported extensions by display
name. There is no `application/zip` Open with filter, while plain text files can now be offered through the MIME
fallback and imported; a provider reporting a zip as `application/octet-stream` may offer Campfire for it too. The share
filter accepts `text/plain`, `application/octet-stream`, `application/zip`, `application/x-zip-compressed`,
`application/pdf` and `application/vnd.openxmlformats-officedocument.wordprocessingml.document`: the first is also the
type of shared *text*, so Campfire is offered for a selection or a note; the second is often used for `.cho` files with
no registered MIME type, the two zip types let archives enter the existing zip import, and PDF/Word enter the local
document conversion without adding Open with associations. These MIME types can also offer Campfire for unrelated files,
which the import skips by display name.

Shared text carries `EXTRA_TEXT` instead of a stream, and `importSharedTexts` (`AndroidFileImport.kt`) queues the
`ImportedFile` that `:presentation`'s `sharedTextsToImportedFiles` (`ui/platform/SharedTextImport.kt`, where it is
tested) makes of it, named after `EXTRA_SUBJECT` — or the text's first plain line — ending in `.txt`, which then goes
through chord-sheet conversion and the ordinary import like any text file: named by its own header, disregarded when the
library already has it, asked about when the name is taken. A stream wins over a text in the same intent, since that
text is a caption; a text that is nothing but links is passed on empty and reported as skipped, because a link is not a
song and the app does not fetch one. Only `content://` is registered for Open with: the app holds no storage permission,
so a `file://` URI into shared storage could not be read. Keep the Open with extension list in step with
`LibraryFiles.SONG_EXTENSIONS`.

It also registers the `campfire://oauth` scheme and holds every permission the app asks for — `INTERNET`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_NOTIFICATIONS` for sync, and
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `VIBRATE` (a normal permission, no prompt; the beat in the hand, in the
background too, which the metronome's foreground service is what allows) for the metronome. The consent page opens in
the user's own browser (never a WebView — a page asking for a password has to be somewhere the address bar is visible)
and the redirect comes back as an `ACTION_VIEW` intent, which `CampfireMainActivity.handle` tells apart from a file
being opened and forwards to `onSyncRedirectReceived`. `handle(intent)` runs before `setContent`, so that a redirect
which started the process — the previous one killed while the browser was in front — is waiting before the view model
asks for it. Nothing tells an app that the user closed a browser it launched, so the authenticator also watches for the
app coming back to the foreground without a redirect and treats that as a cancellation.

`sync/CampfireSyncService` is a foreground service that keeps the process alive for as long as a sync run lasts and
shows the run as a notification with a Stop action. It does **no syncing of its own** — the run lives in
`SyncRepository`, a singleton that outlives every screen — it only tells Android that something worth keeping alive is
going on. Every string arrives in the intent rather than from `res/`, so the notification follows the language chosen
inside the app rather than the system's. The activity starts and stops it through the `SyncNotifier` it hands to
`CampfireAndroidApp`, which is also why the service can live here, where the manifest is. The service only runs **while
the app is not in front**: in front the activity keeps the process alive anyway, and a foreground service with an action
shows its notification at once, which for the run every edit starts would be a notification after nearly every change.

So the activity keeps the notification the composition hands it (`sync/SyncServiceStarter`, which it tells about its
pause and resume) and starts the service in `onPause` (not for a rotation), or at once when a run is handed over after
that — the one leaving the app starts arrives from the composition's own ON_PAUSE, which is told before `onPause` — and
takes it down again in `onResume`, where the settings screen shows a run that is still going. Once leaving it starts the
service again when the words change and whenever the service is not running (`CampfireSyncService.isRunning`); the
counts are the service's own business, rendered from the sync state at most twice a second (`SyncNotificationScheduler`,
in `:presentation`'s `ui/platform`, where the rate and the grace below are tested), since Android sheds notification
updates past five a second.

The service also watches `GetSyncStateUseCase` itself and stops once no run is going: the activity that started it is
the very thing that may be gone by the time the run ends, and the notification must not outlive the run. It waits two
seconds before it does (the scheduler's `scheduleRelease`), cancelled by a run that starts meanwhile: a change made
during a run is carried by a run chained right behind it, which starts within milliseconds — usually with the app in the
background, where the service could not be started again — and the notification then starts over from "preparing". It
only ever stops itself after `startForeground` has been called — stopping earlier would leave the system waiting for the
foreground call `startForegroundService` promised it, which it treats as a crash. It answers `onTimeout` — Android 15's
six-hour budget for data sync services — by stopping the run and itself.

`metronome/CampfireMetronomeService` is the metronome's foreground service, `mediaPlayback` type: it keeps the process
alive while a click plays and is its face to the system — a framework `MediaSession` (play/stop only, no Media3: minSdk
28 needs no compat library), which the lock screen, the headset's button and Bluetooth controls talk to, and its
`Notification.MediaStyle` notification with Stop on a low-importance channel of its own (a media session's notification
needs no notification permission). It plays nothing itself — the engine is the `Metronome` singleton, whose audio output
owns the audio focus and the noisy receiver — and it follows the engine's `playback` for stopping, since the activity
may be gone by then; the session's pause and stop, the headset's button and the notification's Stop all mean
`metronome.stop()`. Like the sync service, it only ever stops itself after `startForeground`: it starts following the
engine in `onStartCommand`, once in the foreground, so a click that ended before then is stopped there.

The activity starts it through the `MetronomeNotifier` it hands to `CampfireAndroidApp` the moment a click starts, which
is always a tap in front, with a plain `startService` for new words once it runs (`isRunning`; both in
`CampfireMetronomeService.start`). Swiping Campfire away is a request for silence, answered in `onTaskRemoved`, which
stops the engine and the service; `stopWithTask` is therefore false, since a service stopped with its task never gets
that callback and the engine would play on. A click that cannot sound is stopped a few seconds after the app goes out of
sight (`:presentation`), which stops the service with it, so `mediaPlayback` never runs for silence — unless the beats
are vibrated, which is a click the user still gets in a pocket. The Play Console needs the `mediaPlayback` foreground
service type declared (a form and a short video, once).

**The launcher icon follows the theme color.** The activity is not a launcher entry itself: the manifest's
`activity-alias`es are, and `AppIconSwitcher` keeps exactly one enabled — `CampfireActivity…<Color>`, one per theme
color with the adaptive icon `mipmap-anydpi/ic_launcher_<color>.xml` on the seed of that palette (`campfire_<color>`;
the app's own is `ic_launcher`, the purple to orange gradient drawn as bitmaps — `mipmap-*/ic_launcher_background` and
`_foreground` — rather than on a color, and the Play listing's `appIcon.png` is the same icon), and one for System whose
background is the wallpaper's `system_accent1_600` (`values-v31`). It switches as soon as `CampfireAndroidApp` reports
the preference, except for the first switch, which waits for `onStop`. The switch runs on a thread of its own, one
switch at a time, so the first one of a process (which asks the package manager about every entry) costs the main thread
nothing. The alias named `.CampfireActivity` is the entry of an installation that never switched and carries the name
the activity had before, since a home screen icon and a restored launcher layout point at a component by name.

It is the only one enabled in the manifest, and it is disabled explicitly at the first switch and never enabled again;
every other alias, the app's own `CampfireActivityCampfire` included, only moves between enabled and its manifest
default of disabled. That split is load bearing: Android closes the tasks started from a component it is told is
*disabled*, and leaves alone those of one set back to a disabled default, so the user's place in the app is lost at the
first switch and never after, which is why only that one waits for the user to leave, and not for a screen locked over a
playing click, which it would silence (measured on Pixel Launcher, API 37, which also moves a home screen icon over to
the new entry rather than removing it — a launcher that does not is the risk this whole mechanism carries). A switch a
device policy refuses (a managed or work profile, some OEM launchers) is logged and leaves the icon as it was, not
recorded as applied, so the next color change or the next stop tries again.

The same split is why Android Studio has to be told which activity to start: its "Default Activity" is the first
launcher entry of the manifest, `.CampfireActivity`, which a device that has switched colors once has disabled, and
starting it then fails with "Activity class … does not exist". The shared run configuration `.run/Android.run.xml`
starts `CampfireMainActivity` itself, which no switch ever disables; a task started that way is the one the launcher
icon brings back too, since the activity is `singleTask`. That only works because the activity declares no intent filter
of its own: since Android 15 an explicit intent from another app — the shell Studio starts it from included — has to
match one of the target's filters, and Studio's carries MAIN and LAUNCHER, which no filter of the activity may have
without making it a second launcher entry. A component with no filters is exempt, so the open, share and sign-in filters
are on `.CampfireIntentActivity`, an alias that is always enabled, is no launcher entry and is not one of
`AppIconSwitcher`'s. From the command line, `adb shell monkey -p com.pandulapeter.campfire.debug -c
android.intent.category.LAUNCHER 1` asks the device which entry is enabled.

A `FileProvider` (authority `${applicationId}.files`, paths in `res/xml/file_paths.xml`) lets a shared song leave the
app's private storage as a content URI.

`data_extraction_rules.xml` (API 31+) and `full_backup_content.xml` (API 28–30) are the system backup rules. They must
say the same thing: each is an allow-list for `library/` and `preferences/preferences.json`, so everything else under
the files directory stays behind: the encrypted sync credentials, the sync index and the forget-pending note, the editor
draft and the `covers/` copies. There are no `<exclude>`s because lint rejects one outside an included path. The 25 MB
backup quota is all or nothing; the debug build is a package of its own, with its own backup set.

Build types: `debug` (`.debug` suffix, `internal.keystore`) and `release` (R8 + resource shrinking), plus
`nonMinifiedRelease` and `benchmarkRelease`, which the `androidx.baselineprofile` plugin adds for `:app:baselineprofile`
to record on and which copy the `release` signing config. The release build packages the Baseline Profile and the
startup profile committed in `src/main/generated/baselineProfiles` (`baseline-prof.txt` and `startup-prof.txt`, the
latter also laying out the DEX so that the classes a start up needs sit together) and never generates them itself
(`automaticGenerationDuringBuild = false`), so it needs no device; `profileinstaller` is a dependency of its own rather
than a transitive one, and it is what writes the profile for a build that did not come from Play. The `internal` signing
config is literal on purpose — it is the standard Android debug keystore, committed next to the build file, and there is
nothing about it worth hiding.

The `release` config reads `campfire.android.*` Gradle properties, which `gradle.properties` defaults to that same debug
keystore so a fresh clone can build a release variant and get something installable; a real key goes in
`local.properties`, which is never committed and overrides them (see the Build section of the root `CLAUDE.md`).
Contains the app's `AndroidManifest.xml`, launcher icons, and themes and colors (`values/` + `values-night/`; the theme
is the platform's own `Theme.Material`, with the window background pinned to the Material 3 one —
`campfire_window_background`, `#FEF7FF` and `#141218` — since it is what the starting window, the splash screen behind
the icon and the frame before the preferences are read are painted in, and the platform's is a gray; the themes name a
splash screen icon background color only so that Android 12+ draws the whole adaptive icon — the yellow and the gray one
included — rather than guessing that its background should be dropped) — the only Android XML resources in the project;
everything the Compose UI draws lives in `:presentation`'s `composeResources`.
