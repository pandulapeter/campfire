<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation

Document imports (`.pdf`, `.docx`, plain `.txt` and Android shared text) become ordinary songs before the UI sees them.
Import summaries report unreadable documents separately and append the number of converted songs actually written, in
both English and Hungarian. A single converted song offers a snackbar **Open** action, handled by
`CampfireViewModel.openImportedSong`; converted imports never navigate automatically. The existing behavior for an
unconverted ChordPro file is unchanged. The import screen's question carries the same unreadable-document note, and
settings/empty-library import labels name documents too.

The entire UI: all Compose components, plus the thin per-platform shells the `:app:*` modules call into. A single Kotlin
Multiplatform module (`campfire-compose-library`) — `commonMain` must stay free of JVM-only APIs (`java.*`,
`KoinJavaComponent`); use `kotlin.uuid.Uuid`, `androidx.compose.ui.text.intl.Locale` and `KoinPlatform.getKoin()`
instead (`desktopMain` may use `java.*`). Depends on `:domain:api` and `:chordpro` (the song model it renders) — never
on repositories or local sources. Koin wiring: `Module.kt` holds the `@Module @ComponentScan object PresentationModule`,
and `CampfireViewModel` is a `@KoinViewModel` — the compiler plugin writes the constructor call, so its thirty-odd
parameters are no concern of the wiring — obtained in Compose with `koinViewModel()`.

The platform shells all live in the `ui` package next to `CampfireApp`, one per platform source set. They exist only to
do what Compose cannot do in common code; anything non-trivial belongs in `commonMain` (platform-specific behavior such
as scrollbars is done there with `expect`/`actual`, see `ui/platform/Platform.kt`). Each of them provides
`LocalFilePicker` and passes on a `Flow` of files the operating system handed over ("open with", a share, a drop).

Each shell's notes are in its source set: `src/androidMain/CLAUDE.md`, `src/desktopMain/CLAUDE.md`,
`src/iosMain/CLAUDE.md` and `src/wasmJsMain/CLAUDE.md` (the web build's addresses and history included).

Everything else is `commonMain`:

The notes on it are split by directory, each in the `CLAUDE.md` of the package it is about, under
`src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`:

- `ui/` — the view model facade and its holders, `CampfireApp` and the navigation chrome, performance mode, and the
  product rules that span modules: what a song carries in its file, how it is played, the Features switches.
- `ui/navigation/` — `CampfireDestination` and the back stack.
- `ui/firstRun/` — the demo library.
- `ui/screens/` — what every screen shares (insets, padding, the list screens' common behaviour).
- `ui/screens/songs/`, `ui/screens/setlists/` — the two list screens (reorder mode, archived setlists, row cost).
- `ui/screens/songDetails/` — the song page: `SongLyrics`, the steppers, the app bar, keyboard reading, its metronome
  panel.
- `ui/screens/songEditor/` — the ChordPro editor.
- `ui/screens/settings/` — the five settings tabs and the sync rows.
- `ui/screens/importReport/` — the import screen.
- `ui/screens/metronome/` — the Metronome tab.
- `ui/screens/export/` — the export screen; `ui/print/` — the PDF pipeline behind it.
- `ui/components/` — the shared components (list items, tags, covers, the fast scroller, the app bar) and scrolling
  performance.
- `ui/dialogs/` — `CampfireDialogs`, the cover search, the import progress dialog.
- `ui/theme/` — the theme, the typography and the fonts.
- `ui/platform/` — the `expect` declarations and the interfaces the shells provide.
- `ui/update/` — in-app updates.
- `ui/chords/` — chord diagrams.
- `ui/metronome/` — the metronome's helpers and controls; `ui/playing/` — where a tempo and a capo live.

What is left here is about the module as a whole:

### `localization/ApplyLanguagePreference.kt`

`localization/ApplyLanguagePreference.kt` — `ApplyLanguagePreference` maps the user preference to the generated
`AppLocale` and sets `currentLanguage`, which every `stringResource` call observes.

### `composeResources/values[-hu]/strings.xml`

`composeResources/values[-hu]/strings.xml` — all UI strings. The `com.hyperether.localization` Gradle plugin turns them
into Kotlin string tables (`build/generated/compose/resourceGenerator/kotlin/commonCustomResClass`, package
`com.pandulapeter.campfire.presentation.localization`): `StringsDefault`, `StringsHu`, `AppLocale`, `currentLanguage`,
`LocalizedStrings` and the language-aware `stringResource` overloads. Adding a new `values-xx` folder needs a clean
build so the plugin regenerates `AppLocale`. It also needs the language added to `CFBundleLocalizations` in the iOS
`Info.plist`, which is how iOS learns that the app speaks it (see `app/ios`).

The plugin's `generateTranslateFile` task only declares the resources folder *path* as an input, so `build.gradle.kts`
registers the `strings.xml` files as inputs too; without that, new strings render as "???" until a clean build. A
sentence that counts something and has to read right at one as well as at several is a `<plurals>` with `one` and
`other` items rather than two keys, read with `pluralStringResource(Res.plurals.x, count, count)` — the count is passed
twice because it picks the item and then fills the `%1$d` in it. `textResource` and its plural twin `pluralTextResource`
are the other readers: the same templates with the user's own text put into them, filled in outside the plugin's
formatter because that one reads what it has put in as a template again; a plural that carries user text (a file name)
is read with `pluralTextResource`, passing the count as text.
