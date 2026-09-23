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

Kotlin Multiplatform app (Android + iOS + JVM desktop + wasmJs web) for viewing and editing song lyrics and chords.
Compose UI is shared between all platforms. The app owns a library folder of plain
[ChordPro](https://www.chordpro.org) files on every platform, which the user fills by writing songs in the built-in
editor or by importing files and zip archives. **The only thing that ever reaches the network is sync**, which is off
until the user connects a cloud folder of their own in Settings, and which still involves no server of Campfire's own
— see the Sync section below. (The Android build also asks Play whether a newer version of itself exists, but that
question is answered over IPC by the Play Store app; Campfire's own process makes no request — see Updates below.
On Android and iOS the system's own device backup also carries the library and the settings — to the user's Google or
iCloud backup, or straight to their next phone — but that is the operating system copying the app's files on the
user's backup settings; Campfire's process makes no request for it, and the sync credentials and the sync index are
not part of it on either (`app/android/src/main/res/xml`; on iOS a device-bound Keychain item and a file marked as
excluded from backup).)

## Architecture

Strict `api` / `implementation` module split at every layer. Only `:app:*` modules see implementations; everything else
depends on `api` modules and gets wiring via Koin.

```
app:android / app:desktop / app:ios / app:web   entry points, platform chrome, "open with" and share intents (app:ios
                                             also holds the Xcode project, app:web the index.html)
  app:di                                     the Koin application: the one place every module is named, and the
                                             function the four entry points start Koin with
  presentation                               CampfireViewModel, Navigation 3 back stack, Material 3 theme + every screen
                                             (Songs, Setlists, Settings, SongDetails, SongEditor), string resources; the
                                             platform shells (system bars, file pickers, drag and drop, URL opening,
                                             desktop key handling) are its platform source sets
  domain:api / :implementation               use cases (single-method interfaces)
    data:repository:api / :implementation
      data:source:local:api  -> :implementation   files on Android/desktop/iOS, OPFS on web (see Web below);
                                                  also holds the pure-Kotlin zip reader/writer
      data:source:remote:api -> :implementation   the sync contracts and the Dropbox provider; the only module in
                                                  the project that makes a network call (see Sync below)
        data:model                           domain models, shared by everything
  chordpro                                   dependency-free ChordPro model, parser, serializer, transposer, tab
                                             wrapper, tag editor and highlighter. Depends on nothing; used by
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
preferences/sync-index.json          what the last successful sync run saw
preferences/sync-credentials-forget-pending   a previous installation's credentials a first launch could not forget yet
preferences/editor-draft.json        the editor's unsaved text as the app last left the front, so that the system
                                     ending it in the background does not end the text too; gone once it is saved or
                                     discarded
instance.lock / instance.endpoint    desktop only: what keeps a second process off the library (see app/desktop)
```

On Android and iOS `library/` and `preferences/preferences.json` are in the system backup and the transfer to a new
device; the sync credentials, `sync-index.json` and `editor-draft.json` are not, so a restored installation starts
disconnected and its first sync run compares by content. Android does it with an allow-list of paths in
`:app:android`, iOS with a Keychain item bound to the device and `FileStorage.keepOutOfDeviceBackup` on the index and
the draft. A reinstall starts disconnected too: a
launch that finds no preferences document forgets any credentials it finds, since the iOS Keychain outlives an
uninstall and nothing else does.

## Conventions

- Library modules apply the convention plugins from `gradle/build-logic` (`campfire-library`, or
  `campfire-compose-library` when they contain Compose). These configure the Android, `desktop` (JVM), `iosArm64`,
  `iosSimulatorArm64` and `wasmJs` (browser) targets and derive the Android namespace from the Gradle path. Sources live
  in `src/commonMain/kotlin`; platform code goes in `androidMain` / `desktopMain` / `iosMain` / `wasmJsMain` via
  `expect`/`actual`.
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
- The UI is Material 3 Expressive (`org.jetbrains.compose.material3:material3`, versioned separately from Compose
  Multiplatform in `jetbrains-compose-material3`); don't add `androidx.compose.material` (M2) back.
- `:app:android` is a plain Android module, `:app:desktop` a plain JVM one, `:app:ios` Kotlin/Native-only and `:app:web`
  Kotlin/Wasm-only; every other module (`:presentation` and `:chordpro` included) is a multiplatform library.
- **Koin is wired with Koin Annotations through the Koin compiler plugin** (`io.insert-koin.compiler.plugin`, applied
  by every module that declares a definition). A class declares itself: `@Single` on repositories, local sources,
  the platform storage and the authenticators, `@Factory` on use cases, `@KoinViewModel` on `CampfireViewModel`.
  Each module's top-level `Module.kt` holds one `@Module @ComponentScan object XxxModule`, empty where the classes
  annotate themselves and holding a `@Single` function where a definition is built rather than constructed (the
  HTTP client, the list of sync providers). Platform definitions are ordinary annotated classes in the platform
  source sets (`AndroidFileStorage`, `IosSyncAuthenticator`, …), found by the same scan, so there is no
  `expect`/`actual` factory between a platform and its Koin definition. `:app:di` names the five module objects in
  the one `@KoinApplication`, and `startCampfireDependencyGraph()` is what the four entry points start Koin with;
  the plugin checks the whole graph there at compile time, so a definition asking for something nobody declares
  fails the build. A dependency only a platform shell provides — the Android `Context` — is marked `@Provided`,
  which tells that check not to look for it. **Never inject a `List<T>`**: the plugin resolves a list parameter as
  `getAll<T>()`, every definition bound to `T`, and not as a definition whose type is the list, so it compiles, passes
  that check and arrives empty. A list that is itself a definition is wrapped in a type of its own (`SyncProviders`).
  `:chordpro` has none of this: it is a set of stateless objects, reached
  through use cases.
- Implementation classes are `internal` and named `<Interface>Impl`. Use cases are `operator fun invoke`.
- Repositories extend `BaseLocalDataRepository`, which holds the cached `DataState` and the read-once logic.
- Layer boundaries are crossed via mappers (`mapper/` packages), never by leaking document/entity types.
- **A setlist shows every song it names**, whatever the Songs screen is filtered to: the filters narrow a view of the
  library, while a setlist is the list somebody wrote down. What the Setlists screen's own controls ask is the order
  the setlists come in and whether the archived ones are among them. Archiving is how a setlist that has been played
  is put away without the songs in it being lost; it is a field of the `*.setlist.json` file rather than a
  preference, so it travels through an export, an import or a sync run the way a tag does. The **description** — an
  optional sentence about what a setlist is for, shown under its header and read by the screen's search — lives in
  the file for the same reason.
- **Both list screens are searched from a button rather than from a field that is always there**: the app bar holds
  the screen's name until the search is opened, and the one search icon is the one close button (the mark morphs
  between the two as the button travels from the actions to the start of the bar, with the field after it, see
  `:presentation`). On the desktop and the web Ctrl / Cmd + F opens it, in place of the browser's find bar there.
  The songs are searched by title and artist, ignoring case, accents, spaces and punctuation alike (`ymca` finds
  `Y.M.C.A.`); a setlist answers by its own title or description, or by holding a
  song that does — and a setlist that answers is shown **whole**, since a setlist is the list somebody wrote down and
  three of its twelve songs is not that list.
- **Tags are part of the song file**, not a store of their own: ChordPro `{tag}` directives, read by `:chordpro`
  into `Song.tags` at scan time and written back into the text the same way, so a tag travels with the file through
  an export, an import or a sync run. The library's set of tags is whatever the songs carry; the Songs screen's
  filter offers them counted and most used first, and the song details header is where one is put on or taken off.
- **The language of a song is carried the same way, and is its own category rather than one more tag**: a
  `{meta: language en}` directive per language, read into `Song.languages` as a lowercase ISO code — 639-2's three
  letter codes included, folded to their 639-1 equivalent where the standard has one (`eng` is `en`) and kept as
  they are where it does not (`rom`, Romani), so one language is one code however the file spells it. It gets its own
  filter group on the Songs screen — but only once the library holds more than one language, with an "Unknown" chip
  for the songs that declare none — and it is shown wherever a tag is: next to them under a song in the lists, and as
  a chip in the song details header, which is also what opens the picker. The **names are never shipped**: the app
  carries a list of codes and nothing else, and asks the platform what each is called in the language the app is set
  to (`java.util.Locale`, `NSLocale`, `Intl.DisplayNames` behind `:presentation`'s `languageDisplayName`), falling
  back to the code in capitals where it cannot say.
- **The app is shipped with two songs and one setlist**, in
  `presentation/src/commonMain/composeResources/files/demo`: public domain campfire standards, bundled as the plain
  ChordPro and setlist files they are and reaching the library through the ordinary import, so they collide, are
  numbered and are disregarded when the same file is already there like anything else. The songs are few on purpose
  and chosen so that between them they use the directives the song details screen draws, and every one carries a
  `Demo` tag, so that they can be filtered out of a library that has grown past them. They are planted once, on a
  run that finds no preferences document *and* an empty library — which is what a fresh installation looks like from
  the inside, and is why a library somebody has been using is never touched. That first run writes the preferences
  whether it planted anything or not, so an installation that started with an import of its own and was emptied
  later is not taken for a fresh one. Settings offers to add them for as long as the library is missing any of them, so
  a deleted one comes back by being asked for rather than on its own. Each file is named exactly as the library would
  name the song inside it, which is what lets one list both read the resources and answer whether they are already
  there.
- **The app says nothing about the other builds but where to find them.** Settings → About is one section on every
  platform, and the row that names no platform — "Every version of Campfire" — leads to the README's "Get Campfire"
  section, which is a page that can be kept up to date without a release and the one place a store has nothing to
  say about. `Distribution` (in `:presentation`'s `ui/platform/Platform.kt`) is now just the four app stores and
  their listing URLs, a null `listingUrl` marking one the app is not on yet; publishing is filling it in.
  Every platform has exactly one official way to get the app, so no build is told where it is handed out:
  `platformStore` is the store of the platform the app is **running** on — a Mac build made by hand is a Mac build
  like the one the Mac App Store hands out — and it decides both the one "Rate Campfire" row, absent on Linux, on the
  web and wherever that listing does not exist yet, and whether the app may ask for money at all
  (`canAskForDonations`: never on an Apple platform, guideline 3.1.1). The row says *rate* and never *install*: a store page
  carries an install button, and a second copy of the app would come with a library of its own. **GitHub is the
  project's website and its issue tracker**; the About section links nothing else but the author's own site, the
  privacy policy and the donation page.
- The file name is a song's (and a setlist's) identity. Nothing is ever overwritten implicitly: a new or imported file
  that collides gets a `_2`, `_3`… suffix (`FileNames.kt`). An **import decides before it writes**: every incoming
  file is held against the name it wants (`PrepareImportUseCase` -> `ImportPlan`), a song the library already holds
  under that name or a numbered sibling of it (`x_2.cho`) — or under the very name it arrived with, which is what an
  export of a file named by an older rule carries — is disregarded rather than copied (for a song, line endings
  and blank lines at either end of the file aside, `ChordProSplitter.comparable`), and two different files of one
  batch that want the same name are never a question: the second is numbered like any other collision — the
  library's own file among them: a song or setlist the batch brings back unchanged is never offered up for
  replacement, so a different one wanting its name is numbered next to it. The names
  taken by something *different* are put to the user as one question about the whole batch — keep both, replace,
  skip, or cancel the import. Replacing is the
  only thing in the app that ever overwrites a library file, and it takes an answer to that dialog.
- **Every name the app writes is normalized** — lowercase words joined with underscores, Latin letters without their
  accents and letters of every other script kept as they are (`катюша.cho`), capped at 120 UTF-8 bytes per half
  (`LibraryFiles.normalizedName`), a song's `artist` and `title` folded one at a time so the dash between them
  survives as structure: `tukorfurogep-arviz.cho`, `summer_set_2026.setlist.json`, colliding as `_2`. Three of the
  folding rules are there so that the same song written down by two people arrives at one name: an apostrophe is
  dropped rather than folded to a separator (`dont_cry`), `&` and `+` are spelled out (`rock_and_roll`), and a credit
  is filed under `ft` however it was abbreviated. Before any of that the name is brought to Unicode NFC
  (`normalizedToNfc`, an expect/actual in `:data:model`), since macOS and iOS hand out names decomposed and every other
  platform composed, and a non-Latin letter keeps its marks — so the two forms would be two songs; sync's name matching
  and the import's family lookup compose too. The rule is idempotent, which it has to be, since a name that left the
  app is normalized again on its way back in. Nothing is migrated, and a name that differs from the normalized one
  only by case or by Unicode form is taken as that name — it is the same file to APFS, NTFS and the sync service, and
  a move nothing else can see is one other devices never follow — so a capitalised or decomposed file keeps its
  spelling until **Update file name** (or, for a setlist, a new title) moves it for a reason that is part of the name.
- **A song is named by its own header, wherever it came from**: `{artist}`, `{title}` and `{subtitle}`, the subtitle
  joining the title half (`green_day-good_riddance_time_of_your_life.cho`) because it is part of the title everywhere
  else in the app. That holds for a song written in the editor, one that arrives through an import
  (`SongLocalSource.importFileName`) and one handed out by an export (`ExportFileNames.kt`) alike — the name a file
  arrives under counts for nothing except where the song inside it declares no `{title}`, in which case it stands in
  as the title, since that is what would title the song in the library anyway. So the invariant worth stating plainly
  is that **a file name is reproducible from its header alone**, and `Song.canUpdateFileName` is what notices where
  that has stopped being true. Inside an exported archive the entries keep their library names, since a setlist points
  at its songs by file name.
- **A file is only ever renamed by the app when the user asks for it, or when nothing is lost by it.** A setlist's
  file follows its title, because that title is written inside the document and the file name records nothing
  (`EditSetlistUseCase`, which is also where the description is written, since the two are the whole of what the
  user gets to say about a setlist). A song's does not: its name is what titles it wherever the file declares no `{title}`, it
  is what a setlist points at, and on the platforms where the library is a folder the user may have chosen it — an
  import is not one of those cases, since nothing has pointed at the incoming name yet. Where
  a song's name and its metadata have drifted apart (by more than case or Unicode form), `Song.canUpdateFileName` puts an **Update file name** entry in
  its menu, and taking it moves the file and everything that named it — every setlist entry, the saved transposition,
  the open screens (`RenameSongFileUseCase`), a setlist that already named the file under its new name keeping the one
  entry it had. Files that were named before any of this keep their names until one of those two things happens to
  them.
- A rename reaches **sync** as a deletion and a new file, since `SyncPlanner` is keyed by name and knows no moves. The
  "an edit beats a deletion" rule then applies: a device that edited the file under its old name since the last run
  puts that file back, leaving both.
- Only pure logic is tested: `commonTest` unit tests in `:chordpro`, `:domain:implementation` (`ImportPlanner`),
  `:data:source:local:implementation` (zip and the JVM file storage), `:data:source:remote:*` (hashing, encoders,
  the OAuth authorization URL) and
  `:data:repository:implementation` (`SyncPlanner`, which decides what happens to every file in a sync run), run on
  the desktop target with
  `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest`.
  The UI is untested by code. Before a release, `documentation/testing/release-check.md` is run on a Mac (its
  `README.md` says how): half an hour of the checks whose failure would block one. A change to what it exercises —
  the first run, importing, sync, the packaged builds — updates it in the same change.

## Build

- Dependency versions in `gradle/libs.versions.toml` (including `android-compileSdk` / `android-minSdk`). The
  version and the build number are `campfire.versionName` and `campfire.buildNumber`, one of each for every platform:
  the build number is Android's version code, the Mac build's `CFBundleVersion` and the iOS one's, the last written
  into the built
  `Info.plist` by a build phase of the Xcode project, which sets no version of its own, reading `gradle.properties`
  and then `local.properties` the way Gradle does.
- **Everything configurable is a `campfire.*` Gradle property**, declared with a default in `gradle.properties` and
  read with `project.property("campfire.x")`: the app version, the Android version code and the iOS build number, the
  Android release signing values, the Dropbox app key, which of its four distributions a desktop build is, the Mac App Store build number and
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

  CI has no `local.properties`, so every workflow writes one from its own secret store, with each value reaching the
  script through `env:` rather than being interpolated into it: a `-P` puts the value in the runner's process list,
  and a secret substituted into a `run:` block is re-read by the shell, so a password holding a `$`, a backtick or a
  quote would sign with something other than what is stored. Backslashes are doubled on the way in, since
  `java.util.Properties` reads one as an escape. The file is read as UTF-8, so a value outside ASCII survives as well.
- The `campfire-library` convention plugin sets each module's `archivesName` from its Gradle path, because a klib
  carries the name of the artifact it is built into and half the modules here are called `api` or `implementation`.
- `./gradlew :app:android:assembleDebug` — Android APK
- `./gradlew :app:desktop:run` — desktop app; `:app:desktop:packageDistributionForCurrentOS` for installers
- `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` — compile/link check of the iOS framework; run the app from
  Xcode (`app/ios/iosApp/iosApp.xcodeproj`) or with
  `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`,
  then `xcrun simctl install/launch`.
- `./gradlew :app:web:wasmJsBrowserDevelopmentRun` — web app on a dev server; `:app:web:wasmJsBrowserDistribution` writes
  the deployable site to `app/web/build/dist/wasmJs/productionExecutable`.
- **Publishing a GitHub release is the release.** `release.yml` answers it (a pre-release is left alone) by checking
  that the tag is the `campfire.versionName` of the commit it is on — a tag on a commit that still carries the last
  version would submit that version again under a new name — and that `campfire.buildNumber` is higher than the
  last published release's (the highest of the three per-store counters, for a release from before there was one),
  since a store would refuse a used one only after the other builds had gone out, and then calling the six workflows below side by side. Each of them is the local build command plus the secrets a checkout does not have, and each can still be
  dispatched by hand, to publish without a release or to repeat one half of a release that went wrong. Every build
  passes `campfire.dropbox.appKey` from the `DROPBOX_APP_KEY` secret, because a published app built without it would
  quietly have no sync provider at all — so each workflow, and `release.yml` before it calls any of them, refuses to
  start when that secret is empty. The check is in the workflows rather than in Gradle: an empty key is the
  checked-in default and has to keep building a fresh clone. **A release carries only what no official channel
  offers**: nothing that a store or the website already hands out is attached to it, which leaves the Linux `.deb`.
  Nothing for the Mac or for Windows is attached: the Mac App Store build is the Mac build, and it is Apple silicon
  only, and the Microsoft Store build is the Windows build. `packageReleaseMsi` and `packageDmg` still build, and
  nothing publishes either.
  - `publish-web.yml` builds the distribution and copies it over `campfire/` in the `pandulapeter.github.io`
    repository, which it reaches with the deploy key in `WEBSITE_DEPLOY_KEY`. The copy is an `rsync --delete`, so the
    folder holds nothing but the distribution — the privacy policy and the rest of the site live elsewhere there.
  - `publish-linux.yml` builds `packageReleaseDeb` on amd64 and arm64 — jpackage only packages for the machine it runs
    on — and attaches both to the release, which is the whole of how the Linux build is handed out (the README's "Get
    Campfire" section links to the latest release's page, and a `.deb` is not something anybody signs on its own).
    It builds on the oldest supported Ubuntu rather than the newest, since a `.deb` asks for the system libraries it
    was built against and the runner therefore decides the lowest distribution it installs on. The two legs do not
    cancel each other. ProGuard breaks an app in ways only starting it shows (see `app/desktop`), so each leg also
    builds the app image (`createReleaseDistributable`; the plugin packages the jars directly and leaves no image
    behind on its own) and starts it under Xvfb with an empty data directory, and attaches nothing unless the demo
    library appears, the process is still there after that, and its log names no exception.
  - `publish-windows.yml` builds `packageReleaseMsix` on a Windows runner (whose image has the SDK's makeappx),
    checks the identity and the version in the package's manifest against `gradle.properties`, starts the app image it
    was made of the way the Linux legs do (the Windows launcher writes no log, so there only an exit counts), keeps
    the `.msix` as an artifact of the run and, called by a release (a hand dispatch only when its box is ticked),
    submits it with `.github/scripts/microsoft_store_submission.py`. That finds the app by its package identity name,
    so no Store ID is kept anywhere, creates a submission — a copy of the last published one — swaps its package for
    the new one, writes the release's `whats-new` notes as its "What's new in this version", sets it to be published
    as soon as it passes certification, uploads and commits it, and waits for Partner Center to accept the commit. A
    green run means submitted, not certified. A submission already in progress with this version is left alone, so a
    repeated run succeeds; one in progress with anything else stops the run rather than being deleted, since it may
    be somebody's draft and a product has only one at a time. It signs in as a Microsoft Entra application with the
    Manager role in Partner Center (`MICROSOFT_STORE_TENANT_ID` and `_CLIENT_ID`) and **with no secret**: the
    application has a federated credential that trusts the OIDC token GitHub hands the job, for the subject
    `repo:pandulapeter/campfire:environment:microsoft-store` — which is why the job runs in the `microsoft-store`
    environment and why `release.yml` grants it `id-token: write` — so, like everything Apple's workflows use, nothing
    it signs in with expires (a client secret would, after two years at most). The package is unsigned, since the
    Store signs what it certifies, and nothing is attached to the release.
  - `publish-macos.yml` builds `packageReleasePkg` on an Apple silicon runner — asking for the `.pkg` is what signs
    and sandboxes it — signed with a Mac App
    Distribution and a Mac Installer Distribution certificate and the two Mac App Store provisioning profiles (the
    app's and the bundled Java runtime's) that the run creates for itself and revokes at the end (see below), all of
    them written into `local.properties` as a developer's machine keeps them. A build signed for
    the store does not start outside TestFlight, so the start check of the other desktop legs is made on a copy
    signed ad hoc with the same entitlements less the two that name the App ID — the demo library has to appear in
    the fresh sandbox container. It uploads the `.pkg` with `altool` and the same App Store Connect API key as iOS,
    and submits it for review the way iOS does (below); its `build_number` input uploads a release again under a
    number App Store Connect has not seen.
  - `publish-ios.yml` archives the app signed with an Apple Distribution certificate the run creates for itself and
    revokes at the end, lets xcodebuild make the App Store profile for it with the App Store Connect API key, and
    uploads the exported
    `.ipa` to App Store Connect, where it lands in TestFlight. Nothing is attached to the release.
  - **Both Apple workflows submit what they upload for review when a release calls them** (`submit_for_review`;
    a hand dispatch only when its box is ticked): `.github/scripts/app_store_submission.py` waits for App Store
    Connect to process the build, takes the platform's version for `campfire.versionName` — the existing one, the
    editable one renamed, or a new one — sets it to be released as soon as it is approved, attaches the build, writes
    the release's `whats-new` notes as its "What's New" (all but a platform's first version) and submits it. A green
    run means submitted, not approved; App Review answers by email, and a rejection is answered in App Store Connect.
    A version that is already in review with this build is left alone, so a repeated run succeeds; one in review with
    another build stops the run, since a platform can have only one version in review at a time. The Xcode project
    starts Gradle itself and passes it no properties, so the sync key is written into `local.properties` there —
    which the version build phase reads too, after `gradle.properties` and with the last value winning, which is how
    the hand-dispatched form's `build_number` uploads a release again under a number App Store Connect has not seen
    without a commit. The archived `Info.plist` is checked against the expected version before anything is uploaded.
  - **Nothing Apple signs with is stored, so nothing expires.** A distribution certificate lasts a year; the App Store
    Connect API key (`APP_STORE_CONNECT_KEY_ID`, `_ISSUER_ID` and `_PRIVATE_KEY`, the last one the `.p8` file's text
    rather than base64, an Admin key shared with Kubriko) does not. So the two Apple workflows make their own
    identities with `.github/scripts/app_store_signing.py`: a key generated on the runner, a certificate for it and the
    profiles that name it, created through the API into a keychain of the run's own, and revoked and deleted in an
    `always()` step at the end — exactly what the run created, recorded in a state file, and never anything made by
    hand. Revoking a distribution certificate does not touch builds already in TestFlight or on the store, which Apple
    signs again. It must never be used for a Developer ID certificate, whose revocation breaks every copy of an app
    already downloaded.
  - `publish-android.yml` writes the keystore out of `ANDROID_KEYSTORE_BASE64`, builds `assembleRelease` signed with
    the other three `ANDROID_*` secrets and uploads it and its mapping file to the production track with
    `PLAY_SERVICE_ACCOUNT_JSON`; nothing is attached to the release. It is an **APK** and not an app bundle because the Play listing predates the bundle
    requirement and was never migrated; a `bundleRelease` would be rejected on upload. The "what's new" text comes
    from the workflow's `release_notes` input, which `release.yml` fills from comments in the release's description
    that the rendered page hides (`<!-- whats-new en-US … -->`, written for every store and passed to the Apple and Windows workflows as well, and `<!-- play-store update-priority: 0 -->`; the
    format is in that file's header) — carried through as it is, backslashes included; only the hand-dispatched
    form's `\n` is expanded, since a single-line text box has no other way to ask for a line break. It falls back to the visible description with its markdown taken out — or,
    dispatched by hand with nothing given, to the commit log since the previous tag. Every store listing is in
    English only, however many languages the app itself speaks. Its `update_priority` input is
    what decides whether the new version says anything about itself inside the old one — see Updates below.

## Sync

Off until the user connects a cloud folder in Settings, and built so that Dropbox is the first provider rather than
the only possible one. The per-module `CLAUDE.md` files carry the detail; the short version:

- `SyncProvider` sees one flat remote folder addressed by `(kind, name)`, the same shape the library has. Revisions
  are **opaque strings** the engine never parses, and a service's content hash stays in the provider — which is what
  keeps Drive's file ids and MD5s out of the engine when it arrives. What is not a song or a setlist by its extension
  is invisible to the engine on both sides, so whatever else the user keeps in the folder is left alone. A remote file
  whose name the device cannot hold (a `\` anywhere, or `? : * " < > |` on Windows) is left out too, and named once in the run's summary
  rather than failed on every run.
- `SyncPlanner` is a pure function of (local hashes, remote listing, the index of what the last run saw) and is the
  part that is tested. Content decides what changed, never a clock: the platforms disagree about modification times
  and the web has none. An edit always beats a deletion.
- A plan that would delete, **on this device or in the cloud folder**, more than half of the files the index knows
  (and at least five of them), or every one of them, is not carried out: the run stops before anything moves and
  Settings asks, naming the side. On this device that is the shape of a remote folder that was emptied, renamed or
  replaced; in the cloud folder, of a library folder that was moved or deleted under the app — which lists as empty,
  so an empty library with an index that is not always asks, however small. Carried out faithfully either would leave
  every device with only what had been edited since the last run. **Delete them here too** / **Delete them from the
  cloud too** runs again with the deletions allowed; **Keep them and upload** / **Keep them and download** runs again
  with those files' index entries dropped, so they are new on the side that still has them and are copied back. An
  answer waives the guard of its own direction only, this device being asked about first. The answer belongs to that
  one run, and an ordinary run asks again for as long as the folder stays that way.
- A fresh installation never inherits a connection: a launch that finds no preferences document forgets whatever
  credentials a previous installation left in a store that outlived it (the iOS Keychain), locally and without a
  request, before anything restores them (`ForgetSyncConnectionUseCase`), so no run starts on an account nobody
  connected here. One that cannot forget them notes that it still owes it, in a file of its own (removed with the
  app, unlike the Keychain), and every start up tries again and restores nothing until it has; connecting on this
  installation crosses the note off.
- A run belongs to the **app**, not to the screen that started it: `SyncRepository` is a singleton with its own
  scope, so a run carries on while the user moves around or leaves. Android keeps the process alive with a
  foreground service and iOS with a background task, both driven by `SyncNotifier`, which each app shell provides
  the way it provides `FilePicker`. The strings are resolved in the UI so the notification follows the language
  chosen *in the app*, not the system's.
- `SyncEngine` runs the plan a few files at a time rather than one after another (which made a first sync one round
  trip per file), except the remote deletions, which go to the provider in one call — on Dropbox one batch job, about
  seven files a second rather than one — so that the folder spends as little time as possible half deleted, the
  state in which another device's guard can let part of a large deletion through unasked. It retries when the service asks
  it to slow down — being rate limited is the expected answer to a first sync of a whole library, not a reason to
  give up on it. A file that fails on its own is named in the
  run's summary rather than ending it, and such a run does not count as the last successful one.
- The index carries an "a run was going" marker, written before anything moves and cleared when it finishes, so a
  run the app never came back from — killed, swiped away, suspended by iOS — is reported as interrupted next time
  rather than silently forgotten, and that run is left for the user to start rather than started on launch.
- A file changed on both sides is never merged: the local one keeps the name and the incoming one lands next to it
  as ` (2)` — or the first number free both on this device and in the cloud folder, so that it never takes the name
  of a file still on its way down — a name of the other device's making, numbered the way any document is, rather
  than with the underscore a name the app derived itself collides with (`_2`).
- Authorization is OAuth 2.0 with PKCE and no client secret, which is what lets this work with no backend. The four
  platforms get back from the consent page in four different ways, all behind `SyncAuthenticator`.

## Updates

Play's in-app updates, and only on Android: `:presentation`'s `ui/platform/AppUpdate.kt` is the contract and
`ui/AppUpdateGate.kt` the UI, with the Play Core implementation in `androidMain` and a no-op actual on the other
three. The gate wraps the whole app inside `CampfireApp`, so it speaks the theme and the language chosen in the app.

- The **Play release's `updatePriority` is the entire policy** and it is chosen per release rather than in the code:
  0–1 is left to Play's own schedule, 2–3 offers a dismissible flexible update that downloads in the background,
  4–5 covers the app with a screen that cannot be dismissed until the update is there. The thresholds live in
  `AppUpdate.android.kt`. The priority also says which kind of flow an update already in progress is, since Play's
  answer does not — which is what lets an Activity recreated mid-download pick the download up instead of offering
  it again. `publish-android.yml` asks for the number as its `update_priority` input — which a release
  sets with a `<!-- play-store update-priority: N -->` comment in its description — defaulting to 0 — the number belongs to the release being published, not to the code being published.
- Back on the blocking screen closes the app. The app it covers is still composed behind it, so the gesture has to
  be taken rather than allowed through, and leaving is the only thing it can honestly mean there.
- The blocking screen is drawn **over** the app rather than in place of it, so a required update that turns out not
  to install leaves the library exactly where the user was. Nothing that is a window of its own — a dialog, a sheet,
  a menu — is shown while it is up.
- Neither the blocking screen (nor the immediate flow started with it) nor the flexible update's Restart is put over
  an editor with unsaved text: the gate waits until the text has been saved or let go of (`hasUnsavedEditorChanges`).
  Restart waits for a sync run too; a required update does not — a run it cuts off is reported as interrupted the
  ordinary way.
- Nothing of this exists outside a Play-installed build: a debug APK, a sideloaded release or a device with no Play
  answers every check with an error, which is why the flow can only be exercised from an internal testing track.
- **iOS has no equivalent.** Apple ships no API that tells an app the store has a newer build; the only way to ask
  is to poll their public lookup endpoint for the published version, which would make it the second thing in the app
  that reaches the network. iOS updates apps on its own, so the iOS actual stays `NotAvailable`. Desktop and the web
  answer to no store at all, and the web build is downloaded again every time it is opened.

## Web

The web build differs from the other three in where the files are. `FileStorage` has a `wasmJsMain` actual backed by
the **Origin Private File System**, so the library is a real directory tree in the browser's own storage, private to
the origin and invisible in the user's downloads. It is also the only build that has to be downloaded before it can
start, which is what the rest of `app/web` is about — see its `CLAUDE.md`.

- The library is the only copy of the user's own work, and the browser's storage for an origin is evictable until it
  is asked not to be, so `requestLibraryPersistence()` (in `:presentation`) asks for persistence as the app starts.
  Whether it is granted is the browser's business — engagement, a bookmark, an install — so the answer is reported in
  Settings rather than insisted on: a refusal says so there, next to the export that is the way to keep a copy
  elsewhere. Clearing the site's data still removes the library, as it does for anything a page stores.
- One tab per origin owns the library through a Web Lock taken before the app is downloaded. A second tab gets a
  localized page that asks it to close or continue in the first, which keeps OPFS from changing behind the running
  app's cached repositories.
- **Every screen has an address, and the browser's history is the app's back stack**: `/` is the songs, then
  `search`, `setlists`, `setlists/search`, `settings/{general,songs,library,about}`, `song/{song}`, `song/{song}/edit`
  and `setlist/{setlist}/{song}`, one history entry per step a back gesture would take (`:presentation`'s
  `ui/navigation/BrowserHistory.kt`). The app decides and the history follows — pushed, replaced or gone back through
  to match — and the browser's Back is sent into the navigation event dispatcher like Escape, so it closes a dialog
  or asks about unsaved text before it leaves a screen. An address that is opened is resolved once the library has
  been read, behind the launch screen; one naming nothing the library holds opens the songs. GitHub Pages serves a
  deep address as its site-wide 404 page, which hands it to `index.html` in the query string (`404.html` in the
  `pandulapeter.github.io` repository keeps the whole path for `campfire` only), and `index.html` writes a `<base>`
  for the folder it lives in, which every relative URL of the page and the app depends on.
- The loading screen has a determinate progress bar, fed by a `fetch` wrapper that counts the bytes of the binaries
  against the total the build wrote into the page. It is a page and not an installable app on purpose: there is no
  web app manifest and no service worker, because every platform that should have an installable Campfire has a
  native build. A browser without Wasm GC is told so before the download starts.
- `finishWebDistribution` (in `app/web/build.gradle.kts`) finalizes `wasmJsBrowserDistribution`: it writes that total
  into `index.html`, and precompresses the files when `campfire.web.precompress` is on — which it is not, since GitHub
  Pages ignores the copies (see `app/web`).
- OPFS, the file input and the download link are reached through `js(...)` blocks rather than through typed wrappers:
  one crossing of the Kotlin/Wasm boundary per operation is far cheaper than one per element, and several of these APIs
  have no binding. A Kotlin lambda cannot be passed into a `js(...)` block, so callbacks (file drops) come back as
  promises instead.
- `settings.gradle.kts` uses `RepositoriesMode.PREFER_SETTINGS` rather than `FAIL_ON_PROJECT_REPOS` because the
  Kotlin/Wasm tooling adds the Node.js, Yarn and Binaryen download repositories to the root project; those are declared
  in settings instead.
