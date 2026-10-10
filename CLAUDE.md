<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Campfire

Kotlin Multiplatform app (Android + iOS + JVM desktop + wasmJs web) for viewing and editing song lyrics and chords,
with a metronome.
Compose UI is shared between all platforms. The app owns a library folder of plain
[ChordPro](https://www.chordpro.org) files on every platform, which the user fills by writing songs in the built-in
editor or by importing ChordPro, plain text, PDF and Word documents, zip archives, and other apps' library backups. **The only things that ever reach the network are sync, and the cover
images and the cover search the user asks for.** Sync is off until the user connects a cloud folder of their own in
Settings, and still involves no server of Campfire's own — see the Sync section below. A cover is fetched from the
address a song's own file names, once, and kept on the device; the search asks MusicBrainz and the iTunes Search API,
and only from the sheet the user opens for it; the "Cover art" switch in Settings → Features turns both off — see Cover art below. (The web build also asks its own deployment, at every launch, which build is current, which is the page being
loaded rather than a request about the user — see Web below. The Android build also asks Play whether a newer version of itself exists, but that
question is answered over IPC by the Play Store app; Campfire's own process makes no request — see Updates below.
On Android and iOS the system's own device backup also carries the library and the settings — to the user's Google or
iCloud backup, or straight to their next phone — but that is the operating system copying the app's files on the
user's backup settings; Campfire's process makes no request for it, and the sync credentials, the sync index, the editor
draft and the cover copies are not part of it on either (`app/android/src/main/res/xml`; on iOS a device-bound Keychain
item and files marked as excluded from backup).)

## Architecture

Strict `api` / `implementation` module split at every layer. Only `:app:*` modules see implementations; everything else
depends on `api` modules and gets wiring via Koin.

```
app:android / app:desktop / app:ios / app:web   entry points, platform chrome, "open with" and share intents (app:ios
                                             also holds the Xcode project, app:web the index.html)
  app:baselineprofile                        the Baseline Profile generator for app:android, run by hand on an emulator
  app:di                                     the Koin application: the one place every module is named, and the
                                             function the four entry points start Koin with
  presentation                               CampfireViewModel, Navigation 3 back stack, Material 3 theme + every screen
                                             (Songs, Setlists, Metronome, Tuner, Settings, SongDetails, SongEditor), string resources; the
                                             platform shells (system bars, file pickers, drag and drop, URL opening,
                                             desktop key handling) are its platform source sets
  domain:api / :implementation               use cases (single-method interfaces)
    data:repository:api / :implementation
    data:sync:implementation                 sync: the engine, the planner, the index, the synced preferences and the
                                             SyncRepository implementation, over the repositories' API and both sources
      data:source:local:api  -> :implementation   files on Android/desktop/iOS, OPFS on web (see Web below)
        data:formats                         the pure-Kotlin zip reader/writer and PDF/Word text extractors; depends on
                                             :data:model and :chordpro, used by :data:source:local:implementation
      data:source:remote:api -> :implementation   the sync contracts and the Dropbox provider, the cover download
                                                  and the MusicBrainz and iTunes searches; the only module in the project that
                                                  makes a network call (see Sync and Cover art below)
        data:model                           domain models, shared by everything
  metronome:api / :implementation            the click: the Metronome contract, its patterns and tap tempo, and the
                                             engine with one audio output per platform (see Metronome below). Depends
                                             on nothing of the app's; used by :presentation, :app:android and :app:ios
  tuner:api / :implementation                the tuner: the Tuner contract, the note arithmetic and the instrument
                                             presets, and the pitch detector, the tracker, the tones and one microphone
                                             input and one tone output per platform (see Tuner below). Depends on
                                             nothing of the app's; used by :presentation
  tools:screenshots                          the store screenshots, rendered offscreen from the desktop build drawn as
                                             each platform (see tools/screenshots); nothing depends on it
  chordpro                                   dependency-free ChordPro model, parser, serializer, transposer, tab
                                             wrapper, tag editor, highlighter, positioned chord-sheet converter, and
                                             what a chord name means and how it is played. Depends on nothing; used by
                                             :data:source:local:implementation (metadata for the song list),
                                             :domain:api and :presentation
```

Data flow: `FileStorage` (one flat directory per kind of file) -> `LocalSource` (files in, models out) -> `Repository`
(emits `DataState<T>`, reads once and caches) -> use cases (`GetScreenDataUseCase` combines the song and setlist
repositories into one `ScreenData` flow) -> `CampfireViewModel` (a lifecycle `ViewModel` exposing `StateFlow`s and the
Navigation 3 back stack) -> screens (`collectAsStateWithLifecycle`).

The library layout, inside the app-private data directory of each platform:

```
library/songs/*.cho                  one song per file; the file name is the song's identity
library/setlists/*.setlist.json      one setlist per file, exported together with the songs
preferences/preferences.json         everything in UserPreferences; outside library/, so it is never exported
preferences/preferences.json.bad     the last preferences document that did not decode, kept before it is saved over
preferences/sync-credentials.json    the connected account's tokens, and an unfinished authorization, on desktop and
                                     the web; the Keystore (an encrypted sync-credentials.bin) and the Keychain on
                                     Android and iOS
preferences/sync-index.json          what the last successful sync run saw, `preferences.json` included
preferences/sync-credentials-forget-pending   a previous installation's credentials a first launch could not forget yet
preferences/editor-draft.json        the editor's unsaved text as the app last left the front, so that the system
                                     ending it in the background does not end the text too; gone once it is saved or
                                     discarded
covers/<sha256 of the address>      the copies of the cover images the songs name; outside library/, so never exported
                                     or synced, and deleted once no song names them
instance.lock / instance.endpoint    desktop only: what keeps a second process off the library (see app/desktop)
campfire.log / campfire.log.1        desktop only: everything the process printed, bounded (see app/desktop)
```

On Android and iOS `library/` and `preferences/preferences.json` are in the system backup and the transfer to a new
device; the sync credentials, `sync-index.json`, `editor-draft.json` and `covers/` are not, so a restored installation starts
disconnected, its first sync run compares by content and its covers are downloaded again. Android does it with an allow-list of paths in
`:app:android`, iOS with a Keychain item bound to the device and `FileStorage.keepOutOfDeviceBackup` on the index, the
draft and every cover. A reinstall starts disconnected too: a
launch that finds no preferences document forgets any credentials it finds, since the iOS Keychain outlives an
uninstall and nothing else does.

## Printing

Every song and setlist has one export entry, which opens the export screen: a PDF for printing, or the library's own
files (ChordPro, a zip for a setlist) for other Campfire users. Only what is printed goes into a PDF, and the Features
tab reaches the export screen. The detail is in `:presentation`'s `ui/screens/export/CLAUDE.md` (the screen) and
`ui/print/CLAUDE.md` (the format choice, the options and the PDF pipeline).

## Conventions

- Library modules apply the convention plugins from `gradle/build-logic` (`campfire-library`, or
  `campfire-compose-library` when they contain Compose). These configure the Android, `desktop` (JVM), `iosArm64`,
  `iosSimulatorArm64` and `wasmJs` (browser) targets and derive the Android namespace from the Gradle path. Sources live
  in `src/commonMain/kotlin`; platform code goes in `androidMain` / `desktopMain` / `iosMain` / `wasmJsMain` via
  `expect`/`actual`, and JVM code both Android and the desktop need in `jvmSharedMain`, a group the convention plugin
  adds to the default hierarchy template.
- Shared code must stay JVM-free: no `java.*`, `KoinJavaComponent`, or JVM-only libraries. Use `kotlin.uuid.Uuid`,
  `androidx.compose.ui.text.intl.Locale`, `KoinPlatform.getKoin()`, and `import kotlinx.coroutines.IO` for
  `Dispatchers.IO`.
- UI strings live in `presentation/src/commonMain/composeResources/values[-hu]/strings.xml`. Read them with
  `com.pandulapeter.campfire.presentation.localization.stringResource(Res.string.x)` (generated by the
  `com.hyperether.localization` plugin, switchable at runtime via `currentLanguage`), never with the
  `org.jetbrains.compose.resources` variant, which ignores the in-app language. Add every new string to both files;
  formatted strings must always be called with their arguments. A sentence that takes text somebody else wrote — a
  title, a tag, a header value, a file or account name — is read with `textResource(Res.string.x, text)`
  (`:presentation`'s `components/TextResource.kt`) instead: the plugin's formatter scans its own output a second
  time, and the `% s` in `100% sure` is a format specifier to it (`pluralTextResource` for a `<plurals>` that
  carries such text). A counted sentence whose singular reads differently
  is a `<plurals>` with `one` and `other` items, read with `pluralStringResource`, rather than a second key.
- **Formatting is checked, not only described**: the root `.editorconfig` holds the IDE settings (150 columns, trailing
  commas, no wildcard imports) and the ktlint rules the code follows, which `campfire-style` (Spotless) applies to every
  module — `./gradlew spotlessApply` fixes them, `spotlessCheck` runs in CI with the tests — and
  `.github/scripts/check_license_headers.py` requires the MPL-2.0 header in every source file, script, workflow, XML
  resource and `CLAUDE.md`. The rest of the style (comments, KDoc, the trailing comma exceptions ktlint cannot express)
  is the `code-style` skill's.
- The UI is Material 3 Expressive (`org.jetbrains.compose.material3:material3`, versioned separately from Compose
  Multiplatform in `jetbrains-compose-material3`); don't add `androidx.compose.material` (M2) back.
- **Nearly every change the user can see is animated**: something that appears, disappears, moves, resizes, changes
  color or swaps for something else gets there with a transition (a fade, a size or bounds animation, a crossfade, a
  morph) rather than in one frame. Only a change the user caused is narrated this way, though — data arriving, or a
  state that starts on a wrong initial value, is fixed at its source so the first frame is already right, not
  animated over.
- **Content scrolled under a bar fades out into it rather than the bar lifting**: no tonal elevation, shadow or
  divider appears when something is scrolled under an app bar, tabs or a pinned header — the content fades into
  nothing over a short gradient instead (`fadingTopEdge` / `fadingVerticalEdges` in `:presentation`'s
  `components/EdgeFade.kt`, and `ListTopFade` for the list screens' cards), and the bars stay flat in the background
  color. A new scrolling container gets the same treatment, not Material's scrolled-under elevation.
- **Every modal with text inputs is a bottom sheet**, and the date picker is one too, holding only a calendar (no
  typed entry, whose strings Material draws in the system's language). Forms use `TextFieldBottomSheet` over `CampfireBottomSheet`; Save, Create, Done, Delete,
  sorting and Add link actions sit in the header, whose close button cancels the draft. Ctrl / Cmd + S presses the
  header's Save, Create or Done (never a Delete), as it saves the editor and the export screen and answers the
  editor's unsaved changes question with Save. Keep each form's existing first-field focus behavior. Both **Choose songs** and **Choose setlists** open with search unfocused;
  tapping their search field brings up the keyboard. New song offers subtitle, artist, album, composer, lyricist,
  year and duration alongside the required title; each optional label uses the same parenthesized marker as setlist
  description and link name. The date picker's Material container uses the shared sheet color, matching its header
  and the other sheets in both themes.
- **A sheet or a dialog holds as little still as it can, and the rest scrolls**: on a small phone with the keyboard
  up (360 × 640 dp leaves about 330 dp above it) every pinned row — a header, tabs, a field, a row of chips, a bar of
  buttons under the content — is taken from the one scrolling part, and a few of them leave it no room at all. What is
  only set once (filters, query fields that a search button runs, a credit line) goes into the scrolling content as
  its first or last item; what finishes the sheet (Save, Remove) goes into its header (`CampfireBottomSheet`'s
  `actions`); only the header and what is typed into throughout (a search field) or switches the whole content (tabs)
  stay pinned. Count the pinned height against that screen before adding anything that does not scroll.
- **A short window gives the keyboard everything it can** (`SHORT_WINDOW_HEIGHT`, 480dp: a phone on its side, or the
  smallest one with the keyboard up). There a sheet's header and pinned controls scroll away with its content, above
  the keyboard; forms with text inputs use the same sheet layout, with their actions in the header; the editor's
  Shortcuts collapse once when the keyboard appears, with their chevron kept available to reopen them while typing (in
  the title row where a landscape keyboard leaves no room for the control row);
  its title row is 48dp and its lines closer together; both assignment sheets open without the keyboard; and the
  song details app bar hides as the song is scrolled down and comes back as it is scrolled up. On Android, typing in a short window also takes the status bar away until the keyboard goes
  (`CompactKeyboardEffect`, swiped back as a transient bar) — the only way a landscape keyboard leaves room for a
  field, a title row and three lines. A phone held upright gives song cards two title lines and narrower padding, and
  a setlist's description starts at two lines in a short window, opening on a tap. Song lists keep their 360dp
  minimum column (a setlist's 416dp, since its cards say more on a line), so a phone on its side stays one column:
  two would each be narrower than the portrait one.
- `:app:android` and `:app:baselineprofile` are plain Android modules, `:app:desktop` a plain JVM one, `:app:ios`
  Kotlin/Native-only and `:app:web` Kotlin/Wasm-only; every other module (`:presentation` and `:chordpro` included) is
  a multiplatform library.
- **Koin is wired with Koin Annotations through the Koin compiler plugin** (`io.insert-koin.compiler.plugin`, applied
  through `campfire-koin` by every module that declares a definition). A class declares itself: `@Single` on repositories, local sources,
  the platform storage and the authenticators, `@Factory` on use cases, `@KoinViewModel` on `CampfireViewModel`.
  Each module's top-level `Module.kt` holds one `@Module @ComponentScan object XxxModule`, empty where the classes
  annotate themselves and holding a `@Single` function where a definition is built rather than constructed (the
  HTTP client, the list of sync providers). Platform definitions are ordinary annotated classes in the platform
  source sets (`AndroidFileStorage`, `IosSyncAuthenticator`, …), found by the same scan, so there is no
  `expect`/`actual` factory between a platform and its Koin definition. `:app:di` names the eight module objects in
  the one `@KoinApplication`, and `startCampfireDependencyGraph()` is what the four entry points start Koin with;
  the plugin checks the whole graph there at compile time, so a definition asking for something nobody declares
  fails the build. A dependency only a platform shell provides — the Android `Context` — is marked `@Provided`,
  which tells that check not to look for it. **Never inject a `List<T>`**: the plugin resolves a list parameter as
  `getAll<T>()`, every definition bound to `T`, and not as a definition whose type is the list, so it compiles, passes
  that check and arrives empty. A list that is itself a definition is wrapped in a type of its own (`SyncProviders`).
  **Never give a defaulted parameter to the constructor of a `@Single` / `@Factory` / `@KoinViewModel` class, or to
  a `@Single` module function**: the plugin runs with `skipDefaultValues = true`, so it compiles, passes `:app:di`'s
  check and silently uses the default.
  `:chordpro` has none of this: it is a set of stateless objects, reached
  through use cases.
- Implementation classes are `internal` and named `<Interface>Impl`. Use cases are `operator fun invoke`.
- `:chordpro` and `:metronome:api` build in explicit API mode (`explicitApi()`), so a new public declaration is written
  `public`, with its type, on purpose; what nothing outside the module calls is `internal`.
- Repositories extend `BaseLocalDataRepository`, which holds the cached `DataState` and the read-once logic.
- Layer boundaries are crossed via mappers (`mapper/` packages), never by leaking document/entity types.
- The file name is a song's (and a setlist's) identity. Nothing is ever overwritten implicitly: a new or imported file
  that collides gets a `_2`, `_3`… suffix (`FileNames.kt`).
- **Every name the app writes is normalized** — lowercase words joined with underscores, Latin letters without their
  accents and letters of every other script kept as they are (`катюша.cho`), capped at 120 UTF-8 bytes per half
  (`LibraryFiles.normalizedName`), a song's `artist` and `title` folded one at a time so the dash between them
  survives as structure: `tukorfurogep-arviz.cho`, `summer_set_2026.setlist.json`, colliding as `_2`. Three of the
  folding rules are there so that the same song written down by two people arrives at one name: an apostrophe is
  dropped rather than folded to a separator (`dont_cry`), `&` and `+` are spelled out (`rock_and_roll`), and a credit
  is filed under `ft` however it was abbreviated. Before any of that the name is brought to Unicode NFC
  (`normalizedToNfc`, an expect/actual in `:data:model`), since macOS and iOS hand out names decomposed and every other
  platform composed, and a non-Latin letter keeps its marks — so the two forms would be two songs; sync's name matching
  and the import's family lookup compose too, and every place that asks whether two names are one file asks
  `LibraryFiles.identityKey` / `isSameLibraryName` (NFC, then a per-character case fold, the same on every platform). The rule is idempotent, which it has to be, since a name that left the
  app is normalized again on its way back in. Nothing is migrated, and a name that differs from the normalized one
  only by case or by Unicode form is taken as that name — it is the same file to APFS, NTFS and the sync service, and
  a move nothing else can see is one other devices never follow — so a capitalised or decomposed file keeps its
  spelling until **Update file name** (or, for a setlist, a new title) moves it for a reason that is part of the name.
- Only pure logic is tested: `commonTest` unit tests in `:data:model` (the library name identity rule, the file name
  predicates and the tag normalization), `:chordpro` (including chord-sheet conversion, and chord names, shapes and definitions, every shape of the tables checked against the chord it is filed under), `:domain:implementation` (`ImportPlanner` and conversion import plumbing),
  `:data:formats` (zip, bounded PDF/Word readers), `:data:source:local:implementation` (the JVM file storage, with independent-producer document goldens in `desktopTest`), `:data:source:remote:*` (hashing, encoders,
  the OAuth authorization URL, the cover search's queries, its `User-Agent` and its pace, the cover download),
  `:data:repository:implementation` (the caches and the cover cache), `:data:sync:implementation` (`SyncPlanner`, which
  decides what happens to every file in a sync run, the engine and the synced preferences), `:metronome:*` (the sequencer, the synthesizer, the mixer, the engine's state machine, tap tempo and time signatures), `:tuner:*` (the note arithmetic and the presets, the FFT, the pitch detector against synthesized strings and any recording dropped into its `desktopTest` resources, the tracker, the tones, the engine's state machine, the sample ring and the FFT) and
  `:presentation` (the pure helpers behind its screens: the search index and ranking, the song picker's filter chips, the fast scroller's section
  index, the setlist slots, stepper labels, section grid and the cutting of sections into columns, row snapping and section measurements of the details screen, the editor's token cache, where a song's tempo comes from, what a click plays for and the tempo it is moving to, which chords a song plays and which
  shape each is drawn with, the tuner's note names, the step its reading is announced at and which notice its page shows, the diagrams' geometry and what the editor's Chord shape button writes, the setlist reorder merge and
  search and the list placeholders; and, over small fakes of their use cases, the song text writes, the metadata sheets'
  edits, the playing overrides and the song filters), run on
  the desktop target with
  `./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :tuner:api:desktopTest :tuner:implementation:desktopTest :presentation:desktopTest`.
  The build logic's packaging helpers (the `.msix` version and publisher id, the launcher configuration and `.deb`
  rewrites, the web build manifest) are tested in `gradle/build-logic` with `./gradlew -p gradle :build-logic:test`.
  The web build's JavaScript — its storage worker, its service worker's routing and the page's decisions about the
  build it keeps — has Node tests of its own (see `app/web`), and the parser of a
  release's description, which is Python, a `unittest` next to it in `.github/scripts`.
  `.github/workflows/tests.yml` runs all three — every module's `desktopTest`, the Node tests and the Python one, with
  `spotlessCheck` and the license header check beside them — and compiles what `desktopTest` does not: the Android
  app (every library's `androidMain`), the desktop and web apps, the Baseline Profile generator and the screenshot tool
  on Linux, and the iOS framework (every `iosMain`) on a macOS runner — on every pull request, every
  night on the default branch, and from `publish-all.yml` before it starts a single store build, so a failing test
  or a platform that does not compile stops every store's release rather than one of them.
  The UI itself is untested by code: `:app:baselineprofile` drives it, but only to record a profile, asserts nothing
  and is never run by CI; a release is checked by hand, on a Mac, before it is published.

## Product rules

What the app does, one line each; the full text is in the file named (`:presentation`'s `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`).

- A setlist shows every song it names, whatever the Songs screen is filtered to; archiving, the description, the date
  and the countdown are fields of its file: `:presentation`'s `ui/screens/setlists/CLAUDE.md`.
- Both list screens are searched from a button rather than an ever-present field, and a setlist that answers a search is
  shown whole: `:presentation`'s `ui/screens/CLAUDE.md`.
- Tags, languages, the cover and links are part of the song file (ChordPro directives), so they travel with it through
  an export, an import or a sync run: `:presentation`'s `ui/CLAUDE.md`.
- How a song is played (transposition, capo, tempo, time signature) is the first section of the song itself; the time
  signature alone is written into the file, the other three are overridden per setlist or per device: `:presentation`'s
  `ui/CLAUDE.md`.
- The tuner asks for the microphone only from the button on its page, listens only while that page is on screen, and
  keeps nothing it hears: `:presentation`'s `ui/tuner/CLAUDE.md`.
- Features are switched on and off as a whole in Settings → Features; a switch only hides, never writes or deletes:
  `:presentation`'s `ui/CLAUDE.md`.
- Haptics tell the hand only what the eye cannot easily follow (the fast scroller above all), through the platform's
  own feedback and its setting: `:presentation`'s `ui/CLAUDE.md`.
- Every chord of a song is shown fingered at its top; the shape is the song's own `{define}`, then the player's
  library-wide choice, then the app's first: `:presentation`'s `ui/chords/CLAUDE.md`.
- The app is shipped with two demo songs and one setlist, planted once through the ordinary import on a fresh
  installation only: `:presentation`'s `ui/firstRun/CLAUDE.md`.
- What's new introduces each version once, after startup import questions have finished: `:presentation`'s
  `ui/firstRun/CLAUDE.md`.
- The app icon follows the theme color wherever the platform allows, and the app's own colors are the promotional purple
  and orange: `:presentation`'s `ui/theme/CLAUDE.md`.
- The app says nothing about the other builds but where to find them: the website's download section, and one "Rate
  Campfire" row for the store of the platform it runs on: `:presentation`'s `ui/screens/settings/CLAUDE.md`.
- Documents are converted locally to ChordPro, never rendered, uploaded or kept: `data/formats/CLAUDE.md`.
- Other apps' libraries are read as archives under names of their own (SongbookPro's translated into Campfire's own
  files): `data/source/local/implementation/CLAUDE.md`.
- Every file is in the standard chord notation; German, Latin, Nashville and Roman notations are only ways of showing
  (and, the first two, of typing) chords: `chordpro/CLAUDE.md`.
- An import decides before it writes: what is already there is disregarded, what collides is numbered, and replacing a
  library file takes an answer and a confirmation: `domain/implementation/CLAUDE.md`.
- A song is named by its own header, wherever it came from: a file name is reproducible from its header alone:
  `data/source/local/implementation/CLAUDE.md`.
- A file is only ever renamed by the app when the user asks for it, or when nothing is lost by it:
  `domain/implementation/CLAUDE.md`.
- A rename reaches sync as a deletion and a new file, and an edit beats a deletion:
  `data/sync/implementation/CLAUDE.md`.

## Build

- Dependency versions in `gradle/libs.versions.toml` (including `android-compileSdk` / `android-minSdk`). The
  version and the build number are `campfire.versionName` and `campfire.buildNumber`, one of each for every platform:
  the build number is Android's version code, the Mac build's `CFBundleVersion` and the iOS one's, the last written
  into the built
  `Info.plist` by a build phase of the Xcode project, which sets no version of its own, reading `gradle.properties`
  and then `local.properties` the way Gradle does.
- `gradle.properties` turns on the local build cache and parallel project execution (`org.gradle.caching`,
  `org.gradle.parallel`). Parallel lets a Kotlin/Native release link run beside other compilations in one daemon,
  which is why `publish-ios.yml` raises the daemon's heap, and the shared Kotlin daemon ran out of memory with one
  compilation per core, which is why `org.gradle.workers.max` is 4; lower it rather than turning parallel off. CI caches Gradle with `gradle/actions/setup-gradle`, the
  cache written by runs on the default branch and read by every other.
- **The configuration cache is on** (`org.gradle.configuration-cache`): a build whose scripts and inputs have not
  changed (`local.properties` included, which `settings.gradle.kts` reads) skips configuring the twenty projects. A
  task action may capture only locals, providers and file collections, never a script-level `val` or function, which
  the cache cannot store — copy the value into a local outside the action, as `app/desktop/build.gradle.kts` does. No
  plugin the build applies reports a problem with it today; one that does gets
  `notCompatibleWithConfigurationCache("<why>")` on its tasks rather than the cache turned off or set to warn.
- **Everything configurable is a `campfire.*` Gradle property**, declared with a default in `gradle.properties` and
  read with `project.property("campfire.x")`: the app version and the build number, the
  Android release signing values, the Dropbox app key, the Mac App Store
  signing, the Microsoft Store package identity, and whether the web distribution is precompressed. `property`
  rather than `findProperty`, so a typo fails the build instead of writing the string "null" into an APK. Inside a `tasks.registering { }` block it has to be `project.property(...)`, or the
  lookup goes to the task.
- **`local.properties` overrides any of them, and is never committed.** `settings.gradle.kts` loads it and writes each
  entry onto every project before it is configured, so no build file knows the mechanism exists — they all just read
  a property. That is the whole secret story: nothing private is in the repository, and a fresh clone still builds
  every variant, because the checked-in defaults point at the debug keystore committed next to them and at an empty
  sync key. A release built that way is installable but not publishable, and Settings says sync is not configured.
  To sign for real, or to build with sync, add the keys to `local.properties`:

  ```properties
  campfire.android.keyAlias=...
  campfire.android.keyPassword=...
  campfire.android.keystoreFile=release.keystore   # relative to app/android, or an absolute path
  campfire.android.keystorePassword=...
  campfire.dropbox.appKey=...
  ```
- The `campfire-library` convention plugin sets each module's `archivesName` from its Gradle path, because a klib
  carries the name of the artifact it is built into and half the modules here are called `api` or `implementation`.
- `./gradlew :app:android:assembleDebug` — Android APK; from Android Studio, the shared "Android" run configuration,
  never "Default Activity" (see `app/android`)
- `./gradlew :app:android:generateBaselineProfile` — records the Android app's Baseline Profile and startup profile on
  the connected emulator into `app/android/src/main/generated/baselineProfiles`, which is committed; a release build,
  CI's included, only packages those files and needs no device (see `app/baselineprofile`). Regenerate it when the
  startup path or the main screens change noticeably; the prepare-release skill also records it before every release,
  even one with empty notes. A stale profile is only less useful, never wrong.
- **`.run/` holds the four shared run configurations** — Android, Desktop, Web and iOS — and the IDE writes them itself,
  which is why they carry no license header: one would be gone on the next save. Desktop and Web are the Gradle tasks
  below. iOS is the Kotlin Multiplatform plugin's own kind, which an IDE without it (any on Windows or Linux) lists as
  one it cannot run and otherwise leaves alone; it finds the Xcode project through `.idea/xcode.xml`, the one file of
  `.idea` that is checked in, and makes a shared Xcode scheme of the configuration on every Mac, which is ignored (see
  `app/ios`).
- `./gradlew :app:desktop:run` — desktop app; `:app:desktop:packageDistributionForCurrentOS` for installers
- `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` — compile/link check of the iOS framework; run the app from
  Xcode (`app/ios/iosApp/iosApp.xcodeproj`) or with
  `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`,
  then `xcrun simctl install/launch`.
- `./gradlew :tools:screenshots:run` — the store screenshots for every platform, into the gitignored
  `tools/screenshots/renders`, from a library copied into the gitignored `tools/screenshots/library`; the
  `store-screenshots` skill runs it, frames the images in Screenshot Bro and exports the store images to the Desktop
  and the README's banners to `documentation/screenshots`
- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — web app on a dev server; `:app:web:wasmJsBrowserDistribution` writes
  the deployable site to `app/web/build/dist/wasmJs/productionExecutable`.
- Publishing, the store workflows and how CI writes `local.properties` from its secrets: see `.github/CLAUDE.md`.

## Sync

Off until the user connects a cloud folder in Settings, and built so that Dropbox is the first provider rather than
the only possible one. The invariants a change anywhere else could break: there is no server of Campfire's own; a run
belongs to the app, not to the screen that started it; content decides what changed, never a clock; an edit always
beats a deletion; a run that would delete most of the library on either side stops and asks. The whole of it — the
provider contract, the planner, the guard, scheduling, the index, conflicts and the synced preferences — is in
`data/sync/implementation/CLAUDE.md` (and the contracts in `data/source/remote/api/CLAUDE.md`).

## Metronome

A third tab and a panel of controls inside the song details screen's app bar, playing on with the screen locked.
Nothing about it reaches the network. Timing is by sample count, never by a timer; a click never outlives the screen it
was started on; a tempo override lives where a transposition does (a setlist's entry or `UserPreferences.tempos`). The
detail is in `metronome/implementation/CLAUDE.md` (timing, playback as media), and in `:presentation`'s
`ui/metronome/CLAUDE.md` (the panel, the tab, tempo changes) and `ui/playing/CLAUDE.md` (where a tempo lives).

## Tuner

A fourth tab, and a sheet over the song details screen opened from its menu, hearing one note at a time through the
microphone and playing reference tones. Nothing it hears is kept or reaches the network. The microphone is asked for
only by the button on its page, never at launch or by opening the tab (on the desktop, which cannot tell whether it is
allowed, that first tap is remembered and opening the tab listens from then on); it is listened to only while that page is on
screen and the app is in front, so the system's recording indicator is lit exactly then; and nothing of it outlives
its screen. The Tuner switch in Settings → Features takes the tab, the sheet's menu entry and every way the app could ask
for the microphone. The detail is in `tuner/implementation/CLAUDE.md` (the detector, the tracker, the platforms) and
`:presentation`'s `ui/tuner/CLAUDE.md` (the page, the permission, the sheet).

## Cover art

A song names its cover in its own file (`{meta: cover …}`, see `:presentation`'s `ui/CLAUDE.md`), and the app keeps a copy of every one it
has shown. Every request is in `:data:source:remote`, the search only ever runs from the sheet the user opens for it,
and the "Cover art" switch in Settings → Features turns all of it off. The detail is in
`data/source/remote/implementation/CLAUDE.md` (the download, the search, the switch) and
`data/repository/implementation/CLAUDE.md` (the offline copies and their deletion).

## Updates

Play's in-app updates, and only on Android; the Play release's `updatePriority` is the entire policy, chosen per release
rather than in the code, and iOS, the desktop and the web have no equivalent. The detail — the thresholds, the
blocking screen, what it waits for — is in `:presentation`'s `ui/update/CLAUDE.md`.

## Web

The web build keeps the library in the browser's Origin Private File System, one tab per origin owns it, every screen
has an address whose history is the app's back stack, and the page keeps a copy of the app so it opens without a
connection. The detail is in `app/web/CLAUDE.md` (storage, the lock, the launch and the cache) and
`presentation/src/wasmJsMain/CLAUDE.md` (addresses and history).
