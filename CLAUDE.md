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

Kotlin Multiplatform app (Android, iOS, JVM desktop, and wasmJs web; Compose UI shared by all) for viewing and editing
song lyrics and chords, with a metronome, a tuner, and chord diagrams. The app owns a library folder of plain
[ChordPro](https://www.chordpro.org) files, filled by the built-in editor or by importing ChordPro, plain text, PDF, Word,
zip archives, and other apps' songs and backups (OpenSong, OnSong, SongbookPro, JSON exports), picked in the app, opened with it, or shared to it (Android, the iOS share
extension). Every module has a `CLAUDE.md` of its own, as does every `:presentation` source set and every package with
something the code cannot say; read the nearest one before changing code there.

## Privacy: what reaches the network

Only these, all in `:data:source:remote:implementation`, the only module that makes a network call:

- **Sync**, off until the user connects their own cloud folder in Settings; no Campfire server exists.
- **Cover images**: downloaded once from the address a song file names, then kept on the device.
- **Cover search and song details lookup**: MusicBrainz and the iTunes Search API, only from the sheet the user opens
  for them; the "Cover art" and "Online song details" switches in Settings → Features remove them.
- Not Campfire's requests: the web build asks its own deployment which build is current; Android asks Play for updates
  over IPC (Play Store app); Android/iOS system backup copies `library/` and `preferences.json` — never the sync
  credentials, the sync index, the editor draft, the iOS share inbox, or `covers/` (`app/android/src/main/res/xml`; on
  iOS a device-bound Keychain item, `FileStorage.keepOutOfDeviceBackup`, and the share inbox's own exclusion). A launch
  with no preferences document forgets any credentials it finds, since the iOS Keychain outlives an uninstall.

## Architecture

Strict `api` / `implementation` split at every layer. Only `:app:*` modules see implementations; everything else
depends on `api` modules and gets wiring through Koin.

```
app:android / desktop / ios / web     entry points, platform chrome, "open with" and share intents
  app:baselineprofile                 Baseline Profile generator for app:android, run by hand
  app:di                              the Koin application; the one place every module is named
  presentation                        CampfireViewModel, Navigation 3 back stack, theme, every screen, strings;
                                      platform shells in its platform source sets
  domain:api / :implementation        use cases (single-method interfaces)
    data:repository:api / :implementation
    data:sync:implementation          the sync engine, planner, index, synced preferences
      data:source:local:api -> :implementation    files on Android/desktop/iOS, OPFS on web
        data:formats                  pure-Kotlin zip, PDF, and Word readers
      data:source:remote:api -> :implementation   sync providers, covers, catalogue searches (all network calls)
        data:model                    domain models
  metronome:api / :implementation     the click engine; depends on nothing of the app's
  tuner:api / :implementation         pitch detection, tones, mic input; depends on nothing of the app's
  tools:screenshots                   store screenshots rendered offscreen from the desktop build
  tools/promo-video                   the promo video: Remotion and a Python soundtrack over Promo.kt's footage
  chordpro                            dependency-free ChordPro parser, serializer, transposer, chords, tabs
```

Data flow: `FileStorage` → `LocalSource` (files in, models out) → `Repository` (emits `DataState<T>`, reads once, caches,
extends `BaseLocalDataRepository`) → use cases (`GetScreenDataUseCase` combines songs and setlists into `ScreenData`) →
`CampfireViewModel` (`StateFlow`s, all `SharingStarted.Eagerly`) → screens (`collectAsStateWithLifecycle`).

Library layout, inside each platform's app-private data directory:

```
library/songs/*.cho                    one song per file; the file name is its identity
library/setlists/*.setlist.json        one setlist per file
preferences/preferences.json           UserPreferences; never exported (.bad keeps the last undecodable one)
preferences/sync-credentials.json      desktop and web; Keystore (sync-credentials.bin) / Keychain on Android / iOS
preferences/sync-index.json            what sync last saw of both sides, file by file
preferences/sync-credentials-forget-pending
preferences/editor-draft.json          the editor's unsaved text, survives process death
covers/<sha256 of the address>         cover copies; never exported or synced, deleted once unreferenced
instance.lock / instance.endpoint      desktop only: single-instance guard
campfire.log / campfire.log.1          desktop only: bounded process log
```

## Conventions

### Code

- Modules apply the convention plugins in `gradle/build-logic` (`campfire-library`, `campfire-compose-library`).
  Sources in `src/commonMain/kotlin`; platform code in `androidMain` / `desktopMain` / `iosMain` / `wasmJsMain` via
  `expect`/`actual`, JVM code shared by Android and desktop in `jvmSharedMain`. The four platform `:app:*` modules,
  `:app:baselineprofile`, and `:tools:screenshots` are single-platform; every other module (`:app:di` included) is
  multiplatform.
- Shared code is JVM-free (the `code-style` skill lists the replacements).
- Implementation classes are `internal`, named `<Interface>Impl`; use cases are `operator fun invoke`. Layers are
  crossed through `mapper/` packages. `:chordpro`, `:metronome:api`, and
  `:tuner:api` use `explicitApi()`.
- Formatting is checked: `.editorconfig` + Spotless/ktlint (`./gradlew spotlessApply`), and
  `.github/scripts/check_license_headers.py` requires the MPL-2.0 header in every source file, script, workflow, XML
  resource, and `CLAUDE.md`. The rest of the style is the `code-style` skill's.
- Material 3 Expressive only (`org.jetbrains.compose.material3`, versioned as `jetbrains-compose-material3`); never M2.

### Koin (Koin Annotations via the Koin compiler plugin, `campfire-koin`)

- Classes declare themselves: `@Single` (repositories, sources, storage, authenticators), `@Factory` (use cases),
  `@KoinViewModel`. Each module has one `@Module @ComponentScan object XxxModule` in its top-level `Module.kt`, with
  `@Single` functions only for built definitions. Platform definitions are annotated classes in platform source sets.
- The application and its compile-time graph check: `app/di/CLAUDE.md`.
- **Never inject a `List<T>`**: it resolves as `getAll<T>()` and arrives empty. Wrap a list in its own type
  (`SyncProviders`).
- No defaulted parameter on a Koin definition's constructor or `@Single` function (see the `code-style` skill).

### Strings

- In `presentation/src/commonMain/composeResources/values[-hu]/strings.xml`, read as the `code-style` skill says.
- A counted sentence is a `<plurals>` with `one` and `other`, not a second key.

### UI rules

- **Every user-caused change is animated** (fade, size, crossfade, morph). Data arriving or a wrong initial value is
  fixed at the source so the first frame is right, never animated over.
- **Content scrolled under a bar fades out** (`fadingTopEdge` / `fadingVerticalEdges` in `components/EdgeFade.kt`,
  `ListTopFade`); bars never gain elevation, shadow, or a divider.
- **Every modal with a text input is a bottom sheet** (`TextFieldBottomSheet` over `CampfireBottomSheet`), the date
  picker too (calendar only). Save, Create, Done, Delete, sorting, and Add link sit in the sheet header; its close
  button cancels. Ctrl/Cmd + S presses Save, Create, or Done (never Delete).
- **Pinned height budget**: a 360 × 640 dp phone with the keyboard up leaves ~330 dp. Pin only the header, tabs, and
  a field typed into throughout; set-once controls scroll; finishing actions go in the header.
- **A short window** (`SHORT_WINDOW_HEIGHT`, 480 dp: a phone on its side, or the smallest with the keyboard up) gives
  the keyboard everything it can; each screen's own short-window rules are in its package `CLAUDE.md`.

### Files and names

- The file name is a song's and setlist's identity. Nothing is overwritten implicitly: a collision gets `_2`, `_3`…
  (`FileNames.kt`).
- Every name the app writes is normalized by `LibraryFiles.normalizedName`: NFC first, lowercase words joined by
  underscores, Latin accents folded, other scripts kept, ≤120 UTF-8 bytes per half, `artist-title.cho`; apostrophes
  dropped, `&`/`+` spelled out, credits filed under `ft`. The rule must stay idempotent.
- Whether two names are one file is always `LibraryFiles.identityKey` / `isSameLibraryName` (NFC + case fold). A name
  differing only by case or Unicode form is kept as is: nothing is migrated, and only **Update file name** (or a new
  setlist title) renames.

## Product rules

One line each; the detail is in the file named (`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`).

- Setlists show every song they name regardless of filters; archive, description, date, countdown live in the file:
  `ui/screens/setlists`.
- List screens are searched from a button; a matching setlist is shown whole: `ui/screens`.
- Selection mode starts from a song's menu, lives while Songs is on top, never deletes in bulk: `ui/screens/songs`.
- Tags, languages, cover, and links are ChordPro directives in the song file, so they travel with it: `ui`.
- Transposition, capo, and tempo are overridden per setlist or per device; only the time signature is written: `ui`.
- Feature switches in Settings → Features only hide, never write or delete: `ui`.
- Haptics only where the eye cannot follow (the fast scroller), through the platform's feedback: `ui`.
- Tabs are moved to the player's instrument and tuning; the player sees only tabs for their instrument: `ui/tabs`,
  `chordpro`.
- With six screens on, Metronome, Tuner, and Chords go behind one Tools item on a navigation bar or a short window's
  rail: `ui/chrome`.
- One instrument preference, picked with its tuning from song details, Tuner, Chords, Settings, and onboarding:
  `ui/instrument`.
- A song's chords are shown fingered: its own `{define}`, then the player's library-wide pick, then the app's first:
  `ui/chords`.
- The Chords tab browses each key's common chords, and a search for a full name finds any drawable one (`D/F#`); a
  shape picked there applies everywhere: `ui/screens/chords`.
- The tuner asks for the microphone only on a tap, listens only while on screen, keeps nothing: `ui/tuner`.
- The demo library (two songs, one setlist) is planted once on a fresh install through the import: `ui/firstRun`.
- Under three songs, the songs list ends in a hint on pasting from a website and importing another app's songs:
  `ui/screens/songs`.
- First run opens a skippable onboarding, one question a page, written as answered: `ui/screens/onboarding`.
- What's new shows once per version, after startup import questions: `ui/firstRun`.
- App icon follows the theme color where possible; brand colors are purple and orange: `ui/theme`.
- The app is set in its bundled Inter and JetBrains Mono on every platform, never the system's fonts: `ui/theme`.
- The app links only to its website, GitHub, the author's site, donations, and its own store's rating page:
  `ui/screens/settings`.
- Printing and export: `ui/screens/export`, `ui/print`.
- Documents are converted locally to ChordPro, never uploaded or kept: `data/formats`, `chordpro/convert`.
- Other apps' songs and backups are translated locally (SongbookPro, OpenSong and OpenSongApp, OnSong, JSON exports);
  backups are read as archives: `domain/implementation`, `data/source/local/implementation`.
- Files use standard chord notation; German, Latin, Nashville, and Roman are display (German and Latin also input):
  `chordpro`.
- The editor always opens over its song's details screen (a pasted or shared new song's once saved): `ui/navigation`.
- Imports are reviewed before writing (a lone song file opened, shared, or dropped only when its name is taken);
  existing files are skipped, collisions numbered or replaced after confirmation: `domain/implementation`.
- A pasted, dropped, or shared chord sheet that is one new song opens the editor on it, a share in read only mode
  included, with a share's page title, else placeholders, for a missing title and artist; nothing is written before
  Save, which needs a title; never a link followed: `presentation`.
- Files and text dragged onto the app are taken on every platform (Android and iOS from another app), never in read
  only mode: `presentation`.
- A share from another app (Android, the iOS share extension) is imported like a paste or an "Open with"; the
  extension never opens the app: `presentation`, `app/ios`.
- A song is named by its own header; renames happen only when asked or when lossless: `domain/implementation`,
  `data/source/local/implementation`.
- A rename reaches sync as delete + create, and an edit beats a deletion: `data/sync/implementation`.

### Feature invariants (detail in the named files)

- **Sync** (`data/sync/implementation`, `data/source/remote/api`): no Campfire server; a run belongs to the app, not
  the screen; content decides what changed, never a clock; a run that would delete most of the library on either side
  stops and asks.
- **Metronome** (`metronome/implementation`, `ui/metronome`, `ui/playing`): timing by sample count, never a timer; a
  click never outlives its screen; tempo overrides live where transpositions do.
- **Tuner** (`tuner/implementation`, `ui/tuner`): the desktop, which cannot tell whether it may listen, remembers its
  first ask.
- **Cover art** and **song details lookup** (`data/source/remote/implementation`, `data/repository/implementation`,
  `ui/dialogs`): lookup only fills the form's draft; Save is the only write.
- **Updates** (`ui/update`): Play in-app updates on Android only; the release's `updatePriority` is the policy.
- **Web** (`app/web`, `presentation/src/wasmJsMain`): library in OPFS, one tab owns it, addresses mirror the back
  stack, works offline.

## Tests

Pure logic is tested in `commonTest`, run on the desktop target; a few UI tests drive the whole app offscreen on the
desktop JVM, and a release is still checked by hand. Add or update tests with every change to tested logic.

```
./gradlew desktopTest                      # every module's commonTest, on the desktop target
./gradlew -p gradle :build-logic:test      # packaging helpers
./gradlew :tools:screenshots:test          # UI tests: the whole app driven offscreen (tools/screenshots/CLAUDE.md)
```

The web build's JavaScript has Node tests (`app/web`), and the Python scripts in `.github/scripts` have `unittest`s
beside them (`test_*.py`). `.github/workflows/tests.yml` runs all of them plus `spotlessCheck`, the license header and size
checks, and compiles every platform (iOS on macOS) on every PR, on every push to `master` that changes more than `documentation/`
(a newer push cancels the older run), and before `publish-all.yml` builds anything.

## Build

- **A shipped dependency is credited in the same change**, transitive ones included: an entry in
  `ui/dialogs/about/OpenSourceComponents.kt`, its license and NOTICE under `composeResources/files/licenses` of the source
  sets that ship it, and a row in the table in `documentation/THIRD_PARTY_SOFTWARE.md`.
- Versions in `gradle/libs.versions.toml`. `campfire.versionName` / `campfire.buildNumber` serve every platform (the
  Xcode build phase writes them into `Info.plist`).
- **Everything configurable is a `campfire.*` property** with a default in `gradle.properties`, read with
  `project.property(...)` (not `findProperty`, so a typo fails the build; inside `tasks.registering { }` it must be
  `project.property`). **`local.properties` overrides any of them and is never committed**; `settings.gradle.kts`
  copies its entries onto every project. Defaults use the committed debug keystore and an empty Dropbox key, so a fresh
  clone builds everything (unpublishable, sync not configured). Real signing and sync need:

  ```properties
  campfire.android.keyAlias=...
  campfire.android.keyPassword=...
  campfire.android.keystoreFile=release.keystore   # relative to app/android, or absolute
  campfire.android.keystorePassword=...
  campfire.dropbox.appKey=...
  ```
- **Configuration cache is on**: task actions capture only locals, providers, and file collections, never a script
  `val` or function. An incompatible plugin gets `notCompatibleWithConfigurationCache("<why>")`, never the cache off.
- `org.gradle.workers.max=4` because the shared Kotlin daemon ran out of memory; lower it rather than turning parallel
  off.
- **Run `git config core.hooksPath .githooks` once per clone**: the pre-commit hook runs the license header and size
  checks (`.github/scripts/check_sizes.py`, limits in the `code-style` skill) so a commit never leaves them to CI.
- `.run/` holds the IDE-written run configurations (no license header, the IDE would drop it).

Commands:

- `./gradlew :app:android:assembleDebug` (from the IDE, the shared "Android" configuration, never "Default Activity")
- `./gradlew :app:android:generateBaselineProfile` on an emulator; the output is committed. Regenerate when startup or
  the main screens change; prepare-release does it every release.
- `./gradlew :app:desktop:run`; `:app:desktop:packageDistributionForCurrentOS` for installers
- `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`; run from Xcode (`app/ios/iosApp/iosApp.xcodeproj`) or
  `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`
  then `xcrun simctl install/launch`
- `./gradlew :app:web:wasmJsBrowserDevelopmentRun`; `:app:web:wasmJsBrowserDistribution` for the deployable site
- `./gradlew :tools:screenshots:run` (driven by the `store-screenshots` skill)
- `tools/promo-video/regenerate.sh` (driven by the `promo-video` skill), into `~/Desktop/Campfire Promo Video`
- Publishing and CI secrets: `.github/CLAUDE.md`.
