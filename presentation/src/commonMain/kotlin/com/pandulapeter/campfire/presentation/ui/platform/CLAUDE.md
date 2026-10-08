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

### `ui/platform/LanguageNames.kt`

`ui/platform/LanguageNames.kt` — `languageDisplayName(code, inLocaleCode)`, four actuals over `java.util.Locale`
(Android and desktop, one copy each since they are separate source sets), `NSLocale` and `Intl.DisplayNames`. Every
platform carries the whole of CLDR for its own formatting, so asking it is both free and more complete than any list
Campfire could keep up to date — a library may well hold a song in a language the app itself will never be translated
into. Only the web actual can be missing at runtime, and it answers with null, which is the same thing an unknown code
is.

### `ui/platform/NumericImeOptions.kt`

`ui/platform/NumericImeOptions.kt` — `numericPlatformImeOptions`, the platform IME options of the year and duration
fields: the numbers page of the full keyboard on iOS (`UIKeyboardTypeNumbersAndPunctuation`), since the iPhone number
pad `KeyboardType.Number` maps to has no return key and Next and Done could not be pressed; null on the other three,
whose number keyboards have their action key.

### `ui/platform/CalendarLocale.kt`

`ui/platform/CalendarLocale.kt` — `calendarLocale(languageCode)`, the locale the setlist dialog's date picker is built
with (`java.util.Locale` on Android and the desktop, `NSLocale`, Compose's `Locale` on the web), since
`rememberDatePickerState` takes the system's and the language is chosen in the app. The dialog builds the
`DatePickerState` itself on that locale, carries the day picked but not yet confirmed through a rotation in a saveable
of its own (the state is not saveable), and draws the title and the headline itself, since Material's headline formats
the day in the system's locale whatever the state's is. What Material still draws in the system's language is its own
strings, which the app cannot reach — the accessibility labels of the month and year buttons; typed entry is turned off
(`showModeToggle = false`) because its field's label, pattern and errors are such strings too — and on iOS and the web
the first day of the week follows the system's region rather than the app's language.

### `ui/platform/Platform.kt`

`ui/platform/Platform.kt` — `expect` declarations with one actual per platform: `isDesktopPlatform` (a long press on a
song row opens nothing at all, the overflow button that opens the same menu being one click away already; also what the
interface scale is decided by), `isLaunchScreenWholeStartup` (true in the desktop application only, the one platform
with no startup screen of its own over the app's), `isStartupScreenHeldUntilAppReady` (true on Android and the web,
whose startup screens stay over the app until `onAppReady` so that the launch screen is taken away without its fade;
false on iOS, whose storyboard the system removes at the first frame), `appIconSurface` (which icon follows the theme
color here, which the settings screen names the "Colored … icon" switch after and describes with its catch: the launcher
on Android, the home screen on iOS, the Dock, the taskbar, the window on Linux, the browser tab) and
`isLibraryEditableOutsideApp` (the desktop folder and the iOS Files app, which decide whether resuming rescans; Settings
names neither, since where the library lives is the platform's convention and nothing the user could change).

`platformStore` is the store of the platform the app is running on — every platform has exactly one official way to get
the app, and a build made by hand is still a build of its platform — which the About section's rating row opens (see the
settings screen below), and `canAskForDonations` derives from it: false on an Apple platform, where guideline 3.1.1 lets
nothing but an in-app purchase lead to paying the developer, so the "Buy me a coffee" link exists everywhere else. The
desktop actuals of these, of the interface and monospace fonts and of `dynamicColorSchemePair` read
`PlatformImpersonation` (`desktopMain/ui/platform/PlatformImpersonation.kt`) on every access: the store screenshot tool
(`tools/screenshots`) sets it to draw the desktop build as Android, iOS, macOS or Windows, and the desktop app never
does, so it is always itself.

### `ui/platform/LibraryPersistence.kt`

`ui/platform/LibraryPersistence.kt` — `requestLibraryPersistence()`, asked once by `CampfireViewModel` as it is created
and kept as its `libraryPersistence` state, which is what the settings screen reads: asked for by the screen as it
opened, the answer arrived a frame after the screen did and the row it decides appeared in the middle of the tab
transition. Three platforms answer `GUARANTEED` without doing anything, since their library is in a file system of their
own; the web actual calls `navigator.storage.persist()`, which is what stops a browser short of space from evicting the
origin's storage — and with it the only copy of the user's library. It is asked that once and no more, because the
browsers that decide by asking the user must only ask once. A refusal is shown in the Library section, since a library
that may be evicted is worth one line and an export. Next to it, `isAppAvailableOffline()` is `true` on the three
installed platforms and, on the web, reads the `window.campfireSavedForOffline` that `index.html` set before starting
the app (see `app/web`); the Library section's Storage row says both in one description, since the browser keeps or
evicts the saved app and the library together.

### `ui/platform/BackgroundSync.kt`

`ui/platform/BackgroundSync.kt` — `SyncNotifier`, provided by each shell through `LocalSyncNotifier` the way
`FilePicker` is. A sync run already outlives the screen that started it, but on a phone that is not enough: Android
stops a process nobody can see and iOS suspends one, so each shell has to tell its platform that something is going on.
`CampfireApp` drives it from the sync state and resolves the strings, so the notification is in the language chosen *in
the app*, handing it over once per run (and again should the words change) rather than once per file, since the shells
keep the count moving on their own; a notification built from Android resources would follow the system's instead. The
run that leaving the app starts (`CampfireViewModel.onAppPaused`, which returns its progress) is handed over from the
ON_PAUSE callback itself rather than through the state: a phone whose screen is turned off pauses and stops the app with
no frame in between, and the shells can only be told while the app is in front. What a shell does with it is its own
business — the Android activity keeps it until the app is being left, since a run in front needs no service (see
`app/android`). Desktop and the web provide nothing, because neither needs anything.

### `ui/platform/SyncNotificationScheduler.kt`

`ui/platform/SyncNotificationScheduler.kt` — the two timings both phone shells keep around their sync notification, one
tested class each constructs for itself: a running count posted at most once per interval from one delayed job that
reads the latest counts when it fires (the step from preparing to counting posted at once), and whatever keeps the
process alive given back only after a grace of two seconds with no run going, for a run chained behind the last one to
start in. When a run counts as chained, and whether there is anything to give back, the shells decide (see `app/android`
and `app/ios`).

### `ui/platform/FilePicker.kt`

`ui/platform/FilePicker.kt` — the interface every shell provides through `LocalFilePicker`: `pickFiles`, `saveFile`, and
`shareFile` with `canShare` saying whether sharing is a different thing from saving on this platform (it is on Android
and iOS, where the export screen has Share next to Save; on desktop and the web it has Save alone). `saveFile` answers
false for a dismissed dialog and throws for a file that could not be written, which is what the view model reports. The
Android one is a singleton the launchers of every composition are attached to, so a result survives the Activity being
recreated under the system picker. The launchers are attached to the singleton only while a composition holds them, and
taken at the moment of launching rather than when a pick or an export began: an Activity recreated in between has
unregistered the old ones. A result that arrives after the *process* was recreated finds nobody waiting and is still
acted on — picked files join `filesToImport` in `CampfireAndroidApp`, and an export is filled from the copy `saveFile`
leaves in `cacheDir/pending_export` while the picker is up.

A document that cannot be filled is deleted again (`DocumentsContract.deleteDocument`), since `CreateDocument` creates
it empty. `toImportedFiles`, which reads every document the system hands over on Android, hands one that cannot be read
over empty, so that the import reports it as skipped instead of leaving it out; the desktop's `readAsImportedFiles` does
the same for a file dropped, picked or opened with the app. The view model runs one pick, export or share at a time
(`CampfireViewModel.launchFileTransfer`) and ignores another asked for meanwhile, which is why every picker has to
answer on every way its screen can go away, a sheet swiped off on iOS included: one that never answered would keep the
app from importing or exporting anything again. A song is exported through `CreateDocument` as
`application/octet-stream` on Android, since a storage provider appends `.txt` to a `.cho` saved as text; the desktop
dialog has no file type, so a name typed without its extension gets the offered one back, unless a file already has that
name.

### `ui/platform/SharedTextImport.kt`

`ui/platform/SharedTextImport.kt` — `sharedTextsToImportedFiles(texts, subject)`, what a text shared to the Android app
becomes before it joins the import: one `.txt` `ImportedFile` per text, named after the sender's subject (numbered when
one subject covers several texts) or the text's first line that is not a directive, a section or a comment, cut to 80
characters and `untitled` where nothing is left; a text of nothing but links is passed on empty, so the import reports
it as skipped. Pure and in `commonMain` so that it is tested; the channel it is queued on stays in `:app:android`.

### `ui/platform/CompactKeyboardEffect.kt`

`ui/platform/CompactKeyboardEffect.kt` — while something is typed into in a short window (the editor, an open list
search, a sheet, a typed dialog's full screen form), the Android actual hides the status bar of the window it is in (the
dialog's own, or the activity's), lets it be swiped back as a transient bar, and puts back the visibility and the
behavior it found once the keyboard goes or the content leaves: a landscape keyboard leaves the smallest phone about
150dp, of which the status bar would take a third. The other three platforms keep their own chrome, and their actuals do
nothing.

### The platform's side

**The platform's side**: `ui/platform/BackgroundMetronome.kt`'s `MetronomeNotifier`, provided like `SyncNotifier` (the
Android service, iOS's Now Playing, the web's media session in `BackgroundMetronome.wasmJs.kt`, nothing on the desktop),
told about a click from `CampfireApp` in the language chosen in the app and only told it ended once it was shown;
`BeatHaptics.kt` (Android's predefined clicks, iOS's impact generators, the web's `navigator.vibrate` pulses where the
page is on a phone or a tablet that has the Vibration API — Chrome on Android, never Safari, and never a desktop
browser, which defines it with nothing to vibrate — and null on the desktop), driven from the heard beats
while the app is resumed, and on Android while it is composed at all (`areBeatHapticsFeltInBackground`: the metronome's
foreground service lets the process vibrate). The flexible update's Restart waits for a playing click like it waits for
a sync run. `CampfireApp`'s `ON_STOP` / `ON_START` effects stop a click that cannot sound (`MetronomePattern.canSound`:
the volume at zero, or every beat muted) once the app has been out of sight for three seconds, on every platform
(`CampfireViewModel.onAppStopped`, the grace covering an Android activity recreated in front), with a snackbar waiting
for the return — but not one still felt there, with Vibrate on and a beat not muted, on Android.
